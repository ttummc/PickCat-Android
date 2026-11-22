package dao3.pickcat.feature.codechat

import android.content.Context
import android.net.Uri
import android.util.Log
import dao3.pickcat.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

data class CodeChatMessage(
    val id: String,
    val seq: Long = 0L,
    val clientMessageId: String? = null,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val senderPcUuid: String? = null,
    val senderPcLinked: Boolean? = null,
    val text: String,
    val imageUrl: String?,
    val fromMe: Boolean,
    val time: Long,
    val peerId: String = "",
    val read: Boolean? = null,
)

data class DmSession(
    val peerUid: String,
    val nickname: String,
    val avatar: String?,
    val lastTime: Long,
    val lastText: String,
    val online: Boolean,
    val unreadCount: Int = 0,
    val lastMessageId: String? = null,
    val lastReadMessageId: String? = null,
)

data class CodeChatState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val canChat: Boolean = true,
    val messages: List<CodeChatMessage> = emptyList(),
    val dmSessions: List<DmSession> = emptyList(),
    val unreadByPeer: Map<String, Int> = emptyMap(),
    val activePeerUid: String? = null,
    val hasMoreByConversation: Map<String, Boolean> = emptyMap(),
    val nextCursorByConversation: Map<String, Long> = emptyMap(),
    val loadingHistory: Boolean = false,
    val error: String? = null,
)

/**
 * CodeChat is a separate community chat service operated by CodeChat. It maps the
 * current PickCat session to a CodeChat identity on first use, then keeps a
 * WebSocket open for the public channel.
 */
class CodeChatRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CodeChatState())
    val state: StateFlow<CodeChatState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private var connectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    @Volatile private var manualClose = false

    fun connect() {
        if (_state.value.connecting || _state.value.connected) return
        manualClose = false
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal() }
    }

    fun retry() {
        connectJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        socket?.close(1000, "retry")
        socket = null
        heartbeatJob?.cancel()
        _state.update {
            it.copy(connecting = false, connected = false, error = null)
        }
        connect()
    }

    fun disconnect() {
        manualClose = true
        retryJob?.cancel()
        connectJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, "disabled")
        socket = null
        _state.value = CodeChatState()
    }

    fun sendMessage(raw: String, to: String = "") {
        val text = raw.trim()
        if (text.isEmpty()) return
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val assertion = prefs.getString(KEY_ASSERT, null) ?: return
        val message = JSONObject()
            .put("type", "chat_message")
            .put("text", text)
            .put("to", to)
            .put("senderProvider", "pickcat")
            .put("clientMessageId", "android_${UUID.randomUUID()}")
            .put("pcAssertion", assertion)
        if (socket?.send(message.toString()) != true) {
            _state.update { it.copy(error = "消息发送失败，请重新连接") }
        }
    }

    fun sendImage(imageUrl: String, to: String = "") {
        if (imageUrl.isBlank()) return
        val assertion = prefs.getString(KEY_ASSERT, null) ?: return
        val message = JSONObject()
            .put("type", "chat_message")
            .put("image", imageUrl)
            .put("to", to)
            .put("senderProvider", "pickcat")
            .put("clientMessageId", "android_${UUID.randomUUID()}")
            .put("pcAssertion", assertion)
        if (socket?.send(message.toString()) != true) {
            _state.update { it.copy(error = "图片发送失败，请重新连接") }
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = prefs.getString(KEY_TOKEN, null)
                    ?: error("CodeChat 尚未连接")
                val resolver = context.contentResolver
                val mime = resolver.getType(uri)
                    ?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取图片")
                if (bytes.size > 8 * 1024 * 1024) error("图片不能超过 8MB")
                val extension = when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "chat.$extension",
                        bytes.toRequestBody(mime.toMediaType()),
                    )
                    .build()
                val request = Request.Builder()
                    .url("$BASE_URL/api/codemao/chat/upload")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val json = response.jsonBody()
                    if (!response.isSuccessful || !json.optBoolean("success", false)) {
                        error(json.optString("msg").ifBlank { "图片上传失败" })
                    }
                    json.optString("url").takeIf { it.isNotBlank() }
                        ?: error("图片上传响应缺少链接")
                }
            }
        }

    suspend fun resolvePickCatUserId(ccUid: String): String? {
        if (ccUid.isBlank()) return null
        val request = Request.Builder()
            .url("$BASE_URL/api/cc/resolve/$ccUid")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = response.jsonBody()
                if (json.has("linked") && !json.optBoolean("linked", false)) return null
                json.optString("pcUuid").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun ownMessageIds(): Set<String> =
        prefs.getStringSet(KEY_OWN_MESSAGE_IDS, emptySet()).orEmpty()

    private fun rememberOwnMessage(id: String) {
        val next = (ownMessageIds().toList() + id).takeLast(200).toSet()
        prefs.edit().putStringSet(KEY_OWN_MESSAGE_IDS, next).apply()
    }

    private suspend fun connectInternal() {
        _state.update { it.copy(connecting = true, connected = false, error = null) }
        try {
            var token = prefs.getString(KEY_TOKEN, null)
            var ccUid = prefs.getString(KEY_UID, null)
            var assertion = prefs.getString(KEY_ASSERT, null)
            val currentPcUid = AppGraph.container.session.state.value.user?.id.orEmpty()
            val storedPcUid = prefs.getString(KEY_PC_UID, null).orEmpty()
            if (
                token.isNullOrBlank() ||
                ccUid.isNullOrBlank() ||
                assertion.isNullOrBlank() ||
                currentPcUid.isBlank() ||
                storedPcUid != currentPcUid
            ) {
                val credential = login()
                token = credential.token
                ccUid = credential.ccUid
                assertion = credential.assertion
                saveCredential(credential)
            }
            if (!verify(token)) {
                val credential = login()
                token = credential.token
                ccUid = credential.ccUid
                assertion = credential.assertion
                saveCredential(credential)
            }
            fetchHistory(token, ccUid, peerUid = null, before = null)
            loadDmSessions(token)
            openSocket(token, ccUid, assertion)
        } catch (error: Exception) {
            Log.w(TAG, "CodeChat connect failed", error)
            _state.update {
                it.copy(
                    connecting = false,
                    connected = false,
                    error = error.message ?: "无法连接 CodeChat",
                )
            }
        }
    }

    private suspend fun login(): CodeChatCredential {
        val cookie = runBlockingCookie() ?: error("请先登录 PickCat 后再使用 CodeChat")
        val body = JSONObject().put("cookie", cookie).toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/api/cc/assert/pickcat")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.jsonBody()
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("msg").ifBlank { "CodeChat 登录失败（HTTP ${response.code}）" })
            }
            val token = json.optString("token")
            val ccUid = json.optString("ccUid")
            val assertion = json.optString("pAssert")
            if (token.isBlank() || ccUid.isBlank() || assertion.isBlank()) {
                error("CodeChat 登录响应缺少凭证")
            }
            return CodeChatCredential(
                token = token,
                ccUid = ccUid,
                assertion = assertion,
            )
        }
    }

    private fun verify(token: String): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/cc/me")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            return response.jsonBody().optBoolean("canChat", true)
        }
    }

    fun refreshDmSessions() {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        scope.launch { runCatching { loadDmSessions(token) } }
    }

    fun openConversation(peerUid: String?) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val ccUid = prefs.getString(KEY_UID, null) ?: return
        scope.launch { fetchHistory(token, ccUid, peerUid, before = null) }
    }

    fun loadOlder(peerUid: String?) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val ccUid = prefs.getString(KEY_UID, null) ?: return
        val key = peerUid ?: "public"
        if (_state.value.hasMoreByConversation[key] != true) return
        if (_state.value.loadingHistory) return
        val before = _state.value.nextCursorByConversation[key] ?: return
        scope.launch { fetchHistory(token, ccUid, peerUid, before = before) }
    }

    private fun fetchHistory(
        token: String,
        ccUid: String,
        peerUid: String?,
        before: Long?,
    ) {
        _state.update { it.copy(loadingHistory = true) }
        try {
            val url = buildString {
                append("$BASE_URL/api/codemao/messages?limit=50&scope=")
                append(if (peerUid == null) "public" else "dm")
                if (peerUid != null) append("&peerUid=$peerUid")
                if (before != null) append("&before=$before")
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val json = response.jsonBody()
                val array = json.optJSONArray("messages") ?: return
                val loaded = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        parseMessage(item, ccUid)?.let(::add)
                    }
                }
                val hasMore = json.optBoolean("hasMore", false)
                val cursor = json.optLong("nextCursor", 0L).takeIf { it > 0L }
                applyHistory(peerUid, loaded, hasMore, cursor, replace = before == null)
            }
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "消息加载失败") }
        } finally {
            _state.update { it.copy(loadingHistory = false) }
        }
    }

    private fun parseMessage(message: JSONObject, ccUid: String): CodeChatMessage? {
        val to = message.optString("to")
        val senderId = message.optString("senderId")
        val fromMe = message.optBoolean("fromMe", senderId == ccUid)
        val text = message.optString("text")
            .ifBlank { message.optString("origText") }
        val image = message.optString("image").takeIf { it.isNotBlank() }
        if (text.isBlank() && image == null) return null
        val id = message.optString("id").takeIf { it.isNotBlank() } ?: return null
        return CodeChatMessage(
            id = id,
            seq = message.optLong("seq", 0L),
            clientMessageId = message.optString("clientMessageId").takeIf { it.isNotBlank() },
            senderId = senderId,
            senderName = message.optString("senderName"),
            senderAvatar = message.optString("senderAvatar").takeIf { it.isNotBlank() },
            senderPcUuid = message.optString("senderPcUuid").takeIf { it.isNotBlank() },
            senderPcLinked = if (message.has("senderPcLinked")) {
                message.optBoolean("senderPcLinked")
            } else {
                null
            },
            text = text,
            imageUrl = image,
            fromMe = fromMe,
            time = message.optLong("time", System.currentTimeMillis()),
            peerId = when {
                to.isBlank() -> ""
                fromMe -> to
                else -> senderId
            },
            read = if (message.has("read")) message.optBoolean("read") else null,
        )
    }

    private fun applyHistory(
        peerUid: String?,
        loaded: List<CodeChatMessage>,
        hasMore: Boolean,
        cursor: Long?,
        replace: Boolean,
    ) {
        val key = peerUid ?: "public"
        _state.update { current ->
            val base = when {
                !replace -> current.messages
                peerUid == null -> current.messages.filterNot { it.peerId.isBlank() }
                else -> current.messages.filterNot { it.peerId == peerUid }
            }
            val merged = (base + loaded)
                .distinctBy { it.id }
                .sortedBy { if (it.seq > 0L) it.seq else it.time }
            current.copy(
                messages = merged,
                hasMoreByConversation = current.hasMoreByConversation + (key to hasMore),
                nextCursorByConversation = if (cursor != null) {
                    current.nextCursorByConversation + (key to cursor)
                } else {
                    current.nextCursorByConversation
                },
            )
        }
    }

    private fun loadDmSessions(token: String) {
        val request = Request.Builder()
            .url("$BASE_URL/api/cc/dm-sessions")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val array = response.jsonBody().optJSONArray("sessions") ?: return
                val sessions = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val peerUid = item.optString("peerUid")
                        if (peerUid.isBlank()) continue
                        add(
                            DmSession(
                                peerUid = peerUid,
                                nickname = item.optString("nickname"),
                                avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                                lastTime = item.optLong("lastTime", 0L),
                                lastText = item.optString("lastText"),
                                online = item.optBoolean("online", false) ||
                                    item.optString("status").equals("online", ignoreCase = true),
                                unreadCount = item.optInt("unreadCount", 0),
                                lastMessageId = item.optString("lastMessageId")
                                    .takeIf { it.isNotBlank() },
                                lastReadMessageId = item.optString("lastReadMessageId")
                                    .takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }.sortedByDescending { it.lastTime }
                _state.update { current ->
                    val active = current.activePeerUid
                    current.copy(
                        dmSessions = sessions,
                        unreadByPeer = sessions.associate { session ->
                            session.peerUid to if (session.peerUid == active) 0 else session.unreadCount
                        },
                    )
                }
            }
        }
    }

    fun ensureDmSession(peerUid: String, nickname: String, avatar: String?) {
        if (peerUid.isBlank()) return
        _state.update { current ->
            if (current.dmSessions.any { it.peerUid == peerUid }) {
                current
            } else {
                current.copy(
                    dmSessions = listOf(
                        DmSession(
                            peerUid = peerUid,
                            nickname = nickname,
                            avatar = avatar,
                            lastTime = System.currentTimeMillis(),
                            lastText = "",
                            online = false,
                        ),
                    ) + current.dmSessions,
                )
            }
        }
    }

    fun setActivePeer(peerUid: String?) {
        _state.update { current ->
            current.copy(
                activePeerUid = peerUid,
                unreadByPeer = if (peerUid == null) {
                    current.unreadByPeer
                } else {
                    current.unreadByPeer - peerUid
                },
                dmSessions = if (peerUid == null) {
                    current.dmSessions
                } else {
                    current.dmSessions.map { session ->
                        if (session.peerUid == peerUid) session.copy(unreadCount = 0) else session
                    }
                },
            )
        }
        if (peerUid != null) markRead(peerUid)
    }

    private fun markRead(peerUid: String) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE_URL/api/cc/dm/$peerUid/read")
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(JSON))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("标记已读失败")
                }
            }
        }
    }

    private fun openSocket(token: String, ccUid: String, assertion: String) {
        val request = Request.Builder()
            .url(WS_URL)
            .header("Cookie", "codechat_token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryCount = 0
                _state.update {
                    it.copy(connecting = false, connected = true, error = null)
                }
                sendOnline(webSocket, ccUid, assertion)
                webSocket.send(JSONObject().put("type", "sync_online").toString())
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleEvent(text, ccUid)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _state.update { it.copy(connected = false, connecting = false) }
                if (!manualClose) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                Log.w(TAG, "CodeChat WebSocket failure", t)
                _state.update {
                    it.copy(
                        connected = false,
                        connecting = false,
                        error = "CodeChat 连接中断",
                    )
                }
                if (!manualClose) scheduleReconnect()
            }
        })
    }

    private fun sendOnline(webSocket: WebSocket, ccUid: String, assertion: String) {
        val user = AppGraph.container.session.state.value.user
        val payload = JSONObject()
            .put("type", "user_online")
            .put("userId", ccUid)
            .put("status", "online")
            .put("projectId", "pickcat-android")
            .put("nickname", user?.username.orEmpty())
            .put("avatar", user?.avatar?.url.orEmpty())
            .put("level", user?.level?.current ?: 0)
            .put("pcAlive", true)
            .put("pcAssertion", assertion)
            .put("fromPickcat", true)
        webSocket.send(payload.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (webSocket.send(JSONObject().put("type", "heartbeat").toString()).not()) {
                    break
                }
            }
        }
    }

    private fun handleEvent(raw: String, ccUid: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (event.optString("type")) {
            "new_message" -> {
                val message = event.optJSONObject("message") ?: return
                val id = message.optString("id").ifBlank { UUID.randomUUID().toString() }
                val to = message.optString("to")
                val senderId = message.optString("senderId")
                val fromMe = event.optBoolean(
                    "fromMe",
                    message.optBoolean("fromMe", senderId == ccUid),
                )
                val peerId = when {
                    to.isBlank() -> ""
                    fromMe -> to
                    else -> senderId
                }
                val text = message.optString("text")
                    .ifBlank { message.optString("origText") }
                val image = message.optString("image").takeIf { it.isNotBlank() }
                if (text.isBlank() && image == null) return
                val item = CodeChatMessage(
                    id = id,
                    seq = message.optLong("seq", 0L),
                    clientMessageId = message.optString("clientMessageId")
                        .takeIf { it.isNotBlank() },
                    senderId = senderId,
                    senderName = message.optString("senderName"),
                    senderAvatar = message.optString("senderAvatar").takeIf { it.isNotBlank() },
                    senderPcUuid = message.optString("senderPcUuid")
                        .takeIf { it.isNotBlank() },
                    senderPcLinked = if (message.has("senderPcLinked")) {
                        message.optBoolean("senderPcLinked")
                    } else {
                        null
                    },
                    text = text,
                    imageUrl = image,
                    fromMe = fromMe,
                    time = message.optLong("time", System.currentTimeMillis()),
                    peerId = peerId,
                    read = if (message.has("read")) message.optBoolean("read") else null,
                )
                if (item.fromMe) rememberOwnMessage(id)
                if (peerId.isNotBlank()) {
                    _state.update { current ->
                        val existing = current.dmSessions.firstOrNull {
                            it.peerUid == peerId
                        }
                        val updated = DmSession(
                            peerUid = peerId,
                            nickname = item.senderName.ifBlank { existing?.nickname.orEmpty() },
                            avatar = item.senderAvatar ?: existing?.avatar,
                            lastTime = item.time,
                            lastText = item.text,
                            online = existing?.online ?: false,
                        )
                        val unread = if (!item.fromMe && current.activePeerUid != peerId) {
                            current.unreadByPeer + (
                                peerId to ((current.unreadByPeer[peerId] ?: 0) + 1)
                            )
                        } else {
                            current.unreadByPeer
                        }
                        current.copy(
                            dmSessions = (listOf(updated) + current.dmSessions.filterNot {
                                it.peerUid == peerId
                            }).sortedByDescending { it.lastTime },
                            unreadByPeer = unread,
                        )
                    }
                }
                _state.update { current ->
                    if (current.messages.any { it.id == item.id }) {
                        current
                    } else {
                        current.copy(messages = (current.messages + item).takeLast(300))
                    }
                }
            }
            "pc_denied", "punish_blocked" -> {
                _state.update {
                    it.copy(
                        error = event.optString("reason").ifBlank { "CodeChat 拒绝了当前会话" },
                    )
                }
            }
            "message_ack" -> {
                if (!event.optBoolean("duplicate", false)) {
                    _state.update { it.copy(error = null) }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (manualClose) return
        retryJob?.cancel()
        val delayMs = (minOf(3_000L * (1L shl retryCount.coerceAtMost(4)), 30_000L) +
            (0..1_000).random()).coerceAtLeast(3_000L)
        retryCount++
        retryJob = scope.launch {
            delay(delayMs)
            if (!manualClose) connect()
        }
    }

    private fun saveCredential(credential: CodeChatCredential) {
        prefs.edit()
            .putString(KEY_TOKEN, credential.token)
            .putString(KEY_UID, credential.ccUid)
            .putString(KEY_ASSERT, credential.assertion)
            .putString(KEY_PC_UID, AppGraph.container.session.state.value.user?.id.orEmpty())
            .apply()
    }

    private suspend fun runBlockingCookie(): String? {
        return AppGraph.container.session.pickcatSessionCookie()
    }

    private fun Response.jsonBody(): JSONObject {
        val raw = body?.string().orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse {
            error("CodeChat 返回了无法解析的数据")
        }
    }

    private data class CodeChatCredential(
        val token: String,
        val ccUid: String,
        val assertion: String,
    )

    companion object {
        private const val BASE_URL = "https://codechat.codemao.64mowan.top"
        private const val WS_URL = "wss://codechat.codemao.64mowan.top/codemao-ws"
        private const val PREFS = "codechat_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_UID = "cc_uid"
        private const val KEY_ASSERT = "assertion"
        private const val KEY_PC_UID = "pc_uid"
        private const val KEY_OWN_MESSAGE_IDS = "own_message_ids"
        private const val TAG = "CodeChat"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
