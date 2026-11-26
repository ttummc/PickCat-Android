package dao3.pickcat.feature.codechat

import android.content.Context
import android.util.Log
import dao3.pickcat.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

data class ChatQuote(
    val id: String,
    val name: String,
    val text: String,
    val image: Boolean,
)

data class CodeChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val text: String,
    val image: String?,
    val quote: ChatQuote?,
    val time: Long,
    val fromMe: Boolean,
    val peerUid: String,
    val recalled: Boolean = false,
)

data class CodeChatUser(
    val id: String,
    val nickname: String,
    val avatar: String?,
    val online: Boolean,
    val status: String,
)

data class DmSession(
    val peerUid: String,
    val nickname: String,
    val avatar: String?,
    val lastText: String,
    val lastTime: Long,
    val unreadCount: Int,
    val online: Boolean,
)

data class CodeChatState(
    val consentGranted: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val canChat: Boolean = false,
    val messages: List<CodeChatMessage> = emptyList(),
    val sessions: List<DmSession> = emptyList(),
    val onlineUsers: List<CodeChatUser> = emptyList(),
    val unreadByPeer: Map<String, Int> = emptyMap(),
    val activePeerUid: String? = null,
    val error: String? = null,
)

class CodeChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val _state = MutableStateFlow(
        CodeChatState(consentGranted = prefs.getBoolean(KEY_CONSENT, false)),
    )
    val state: StateFlow<CodeChatState> = _state.asStateFlow()

    private var connectJob: Job? = null
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var intentionalClose = false
    private var credential: Credential? = loadCredential()

    fun grantConsent() {
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
        _state.update { it.copy(consentGranted = true) }
        connect()
    }

    fun connect() {
        if (!prefs.getBoolean(KEY_CONSENT, false)) return
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            _state.update { it.copy(connecting = true, error = null) }
            try {
                val session = ensureSession()
                val me = api("GET", "/api/cc/me", token = session.token)
                if (!me.optBoolean("success", true) && me.optInt("statusCode", 200) >= 400) {
                    error(me.optString("msg").ifBlank { "CodeChat 登录失败" })
                }
                val canChat = me.optBoolean("canChat", false)
                _state.update {
                    it.copy(
                        connecting = false,
                        canChat = canChat,
                        error = if (canChat) null else "当前账号没有聊天权限",
                    )
                }
                if (!canChat) return@launch
                loadHistory(session)
                loadDmSessions(session)
                openSocket(session)
            } catch (error: Exception) {
                Log.w(TAG, "login failed", error)
                _state.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        error = error.message ?: "无法连接 CodeChat",
                    )
                }
            }
        }
    }

    fun disconnect() {
        intentionalClose = true
        connectJob?.cancel()
        connectJob = null
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, "client close")
        socket = null
        _state.update { it.copy(connected = false, connecting = false) }
    }

    fun clearActivePeer() {
        _state.update { it.copy(activePeerUid = null) }
    }

    fun setActivePeer(peerUid: String?) {
        _state.update { current ->
            current.copy(
                activePeerUid = peerUid,
                unreadByPeer = peerUid?.let { peer -> current.unreadByPeer - peer }
                    ?: current.unreadByPeer,
                sessions = current.sessions.map {
                    if (it.peerUid == peerUid) it.copy(unreadCount = 0) else it
                },
            )
        }
    }

    fun ensureDmSession(peerUid: String, nickname: String, avatar: String?) {
        if (peerUid.isBlank()) return
        _state.update { current ->
            if (current.sessions.any { it.peerUid == peerUid }) current
            else current.copy(
                sessions = listOf(
                    DmSession(peerUid, nickname, avatar, "", 0L, 0, false),
                ) + current.sessions,
            )
        }
    }

    suspend fun resolveCodeChatUid(pcUuid: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = api("GET", "/api/cc/resolve/${pcUuid.trim()}")
            response.optString("ccUid").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun resolvePickCatUserId(ccUid: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = api("GET", "/api/cc/resolve/${ccUid.trim()}")
            response.optString("pcUuid").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun send(text: String, to: String = "", quote: ChatQuote? = null) {
        val session = credential ?: return
        val clean = text.trim().take(2000)
        if (clean.isEmpty()) return
        sendPayload(
            JSONObject()
                .put("type", "chat_message")
                .put("text", clean)
                .put("to", to)
                .put("senderProvider", "pickcat")
                .put("pcAssertion", session.pAssert)
                .apply { quote?.let { put("quote", it.toJson()) } },
        )
    }

    fun sendImage(bytes: ByteArray, mime: String, to: String = "", quote: ChatQuote? = null) {
        scope.launch {
            val session = credential ?: return@launch
            try {
                val url = uploadImage(session.token, bytes, mime)
                sendPayload(
                    JSONObject()
                        .put("type", "chat_message")
                        .put("image", url)
                        .put("to", to)
                        .put("senderProvider", "pickcat")
                        .put("pcAssertion", session.pAssert)
                        .apply { quote?.let { put("quote", it.toJson()) } },
                )
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "图片发送失败") }
            }
        }
    }

    fun recall(messageId: String) {
        sendPayload(JSONObject().put("type", "recall_message").put("messageId", messageId))
    }

    fun report(message: CodeChatMessage, reason: String, note: String) {
        scope.launch {
            val session = credential ?: return@launch
            runCatching {
                api(
                    "POST",
                    "/api/codemao/report",
                    body = JSONObject()
                        .put("messageId", message.id)
                        .put("reporterId", session.ccUid)
                        .put("reason", reason)
                        .put("channel", "chat")
                        .put("note", note.take(300)),
                    token = session.token,
                )
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "举报失败") }
            }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun ensureSession(): Credential {
        credential?.let { saved ->
            val me = runCatching { api("GET", "/api/cc/me", token = saved.token) }.getOrNull()
            if (me != null && me.optBoolean("loginState", true)) return saved
        }
        val cookie = AppGraph.container.session.pickcatSessionCookie()
            ?: error("请先登录 PickCat")
        val response = api(
            "POST",
            "/api/cc/assert/pickcat",
            body = JSONObject().put("cookie", cookie),
            token = null,
        )
        if (!response.optBoolean("success", false)) {
            error(response.optString("msg").ifBlank { "CodeChat 账号验证失败" })
        }
        val newCredential = Credential(
            token = response.optString("token"),
            ccUid = response.optString("ccUid"),
            pAssert = response.optString("pAssert"),
        )
        if (newCredential.token.isBlank() || newCredential.ccUid.isBlank()) {
            error("CodeChat 登录响应不完整")
        }
        credential = newCredential
        saveCredential(newCredential)
        return newCredential
    }

    private suspend fun loadHistory(session: Credential) {
        val response = api(
            "GET",
            "/api/codemao/messages",
            query = mapOf("userId" to session.ccUid),
            token = session.token,
        )
        val array = response.optJSONArray("messages") ?: return
        val loaded = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { item ->
                    parseMessage(item, session.ccUid, null)?.let(::add)
                }
            }
        }
        _state.update { current ->
            current.copy(messages = mergeMessages(current.messages, loaded))
        }
    }

    private suspend fun loadDmSessions(session: Credential) {
        val response = api(
            "GET",
            "/api/cc/dm-sessions",
            query = mapOf("userId" to session.ccUid),
            token = session.token,
        )
        val array = response.optJSONArray("sessions") ?: return
        val sessions = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val peer = item.optString("peerUid")
                if (peer.isBlank()) continue
                add(
                    DmSession(
                        peerUid = peer,
                        nickname = item.optString("nickname").ifBlank { "CodeChat 用户" },
                        avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                        lastText = item.optString("lastText"),
                        lastTime = item.optLong("lastTime"),
                        unreadCount = item.optInt("count", 0),
                        online = item.optBoolean("online", false),
                    ),
                )
            }
        }
        _state.update { current ->
            current.copy(
                sessions = sessions,
                unreadByPeer = sessions.associate { it.peerUid to it.unreadCount }
                    .filterValues { it > 0 },
            )
        }
    }

    private fun openSocket(session: Credential) {
        intentionalClose = false
        socket?.cancel()
        val request = Request.Builder()
            .url(WS_URL)
            .header("Cookie", "codechat_token=${session.token}")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _state.update { it.copy(connected = true, connecting = false) }
                val user = AppGraph.container.session.state.value.user
                sendJson(
                    JSONObject()
                        .put("type", "user_online")
                        .put("userId", session.ccUid)
                        .put("status", "online")
                        .put("projectId", "")
                        .put("nickname", user?.username.orEmpty())
                        .put("avatar", user?.avatar?.url.orEmpty())
                        .put("level", 1)
                        .put("pcAlive", true)
                        .put("pcAssertion", session.pAssert)
                        .put("fromPickcat", true),
                )
                sendJson(JSONObject().put("type", "sync_online"))
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(15_000)
                        sendJson(JSONObject().put("type", "heartbeat").put("pcAlive", true))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handleEvent(JSONObject(text), session) }
                    .onFailure { Log.w(TAG, "event parse failed", it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                _state.update { it.copy(connected = false, connecting = false) }
                if (!intentionalClose) scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _state.update { it.copy(connected = false, connecting = false) }
                if (code == 1008 || code == 4002) {
                    intentionalClose = true
                    _state.update {
                        it.copy(error = reason.ifBlank { "CodeChat 连接被服务器关闭" })
                    }
                    return
                }
                if (!intentionalClose) scheduleReconnect()
            }
        })
    }

    private fun handleEvent(event: JSONObject, session: Credential) {
        when (event.optString("type")) {
            "new_message" -> {
                val message = event.optJSONObject("message") ?: return
                val fromMe = event.optBoolean("fromMe", false)
                val toMe = event.optBoolean("toMe", false)
                val peer = when {
                    fromMe && message.optString("to").isNotBlank() -> message.optString("to")
                    toMe && message.optString("senderId") != session.ccUid ->
                        message.optString("senderId")
                    else -> message.optString("to")
                }
                val parsed = parseMessage(message, session.ccUid, peer, fromMe) ?: return
                _state.update { current ->
                    if (current.messages.any { it.id == parsed.id }) current
                    else {
                        val sessions = updateSession(current.sessions, parsed, peer)
                        val active = current.activePeerUid
                        val unread = if (!parsed.fromMe && peer.isNotBlank() && active != peer) {
                            current.unreadByPeer + (peer to (current.unreadByPeer[peer] ?: 0) + 1)
                        } else {
                            current.unreadByPeer - peer
                        }
                        current.copy(
                            messages = (current.messages + parsed),
                            sessions = sessions,
                            unreadByPeer = unread.filterValues { it > 0 },
                        )
                    }
                }
            }
            "online_users" -> {
                val users = event.optJSONArray("users") ?: return
                val list = buildList {
                    for (index in 0 until users.length()) {
                        val item = users.optJSONObject(index) ?: continue
                        add(
                            CodeChatUser(
                                id = item.optString("id"),
                                nickname = item.optString("nickname").ifBlank { "CodeChat 用户" },
                                avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                                online = item.optBoolean("online", false),
                                status = item.optString("status"),
                            ),
                        )
                    }
                }
                _state.update { it.copy(onlineUsers = list) }
            }
            "message_recalled" -> {
                val id = event.optString("messageId")
                _state.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == id) it.copy(recalled = true, text = "", image = null) else it
                        },
                    )
                }
            }
            "pc_denied", "cc_caps_denied" -> {
                _state.update {
                    it.copy(
                        connected = false,
                        error = event.optString("msg").ifBlank { "CodeChat 登录状态已失效" },
                    )
                }
                intentionalClose = true
                socket?.close(1000, "pc denied")
            }
            "cc_caps" -> {
                val caps = event.optJSONObject("caps")
                if (caps != null) {
                    _state.update { it.copy(canChat = caps.optBoolean("canChat", it.canChat)) }
                }
            }
        }
    }

    private fun parseMessage(
        item: JSONObject,
        ccUid: String,
        explicitPeer: String?,
        fromMeOverride: Boolean? = null,
    ): CodeChatMessage? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        val senderId = item.optString("senderId")
        val fromMe = fromMeOverride ?: (senderId == ccUid)
        val to = item.optString("to")
        val peer = explicitPeer ?: when {
            fromMe -> to
            to.isNotBlank() -> senderId
            else -> ""
        }
        val quote = item.optJSONObject("quote")?.let {
            ChatQuote(
                id = it.optString("id"),
                name = it.optString("name"),
                text = it.optString("text"),
                image = it.optBoolean("image", false),
            )
        }
        return CodeChatMessage(
            id = id,
            senderId = senderId,
            senderName = item.optString("senderName").ifBlank { "CodeChat 用户" },
            senderAvatar = item.optString("senderAvatar").takeIf { it.isNotBlank() },
            text = item.optString("text"),
            image = item.optString("image").takeIf { it.isNotBlank() },
            quote = quote,
            time = item.optLong("time"),
            fromMe = fromMe,
            peerUid = peer,
            recalled = item.optBoolean("recalled", false),
        )
    }

    private fun mergeMessages(
        current: List<CodeChatMessage>,
        loaded: List<CodeChatMessage>,
    ): List<CodeChatMessage> = (current + loaded)
        .associateBy { it.id }
        .values
        .sortedBy { it.time }

    private fun updateSession(
        sessions: List<DmSession>,
        message: CodeChatMessage,
        peer: String,
    ): List<DmSession> {
        if (peer.isBlank()) return sessions
        val existing = sessions.firstOrNull { it.peerUid == peer }
        val updated = DmSession(
            peerUid = peer,
            nickname = existing?.nickname
                ?: message.senderName.takeIf { !message.fromMe }
                ?: "CodeChat 用户",
            avatar = existing?.avatar
                ?: message.senderAvatar.takeIf { !message.fromMe },
            lastText = when {
                message.text.isNotBlank() -> message.text
                message.image != null -> "[图片]"
                else -> existing?.lastText.orEmpty()
            },
            lastTime = message.time,
            unreadCount = existing?.unreadCount ?: 0,
            online = existing?.online ?: false,
        )
        return (listOf(updated) + sessions.filterNot { it.peerUid == peer })
            .sortedByDescending { it.lastTime }
    }

    private fun sendPayload(payload: JSONObject) {
        sendJson(payload) ?: _state.update { it.copy(error = "CodeChat 尚未连接") }
    }

    private fun sendJson(payload: JSONObject): Boolean =
        socket?.send(payload.toString()) == true

    private fun scheduleReconnect() {
        if (intentionalClose || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val wait = min(30_000L, 3_000L * (1L shl reconnectAttempt.coerceAtMost(4))) +
                Random.nextLong(0, 1_000)
            reconnectAttempt++
            delay(wait)
            _state.update { it.copy(connecting = true) }
            connect()
        }
    }

    private suspend fun uploadImage(token: String, bytes: ByteArray, mime: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "codechat.${if (mime.contains("png")) "png" else "jpg"}",
                bytes.toRequestBody(mime.toMediaTypeOrNull()),
            )
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/api/codemao/chat/upload")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", true)) {
                error(json.optString("msg").ifBlank { "图片上传失败" })
            }
            return json.optString("url").ifBlank { error("图片上传返回缺少 URL") }
        }
    }

    private suspend fun api(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append(BASE_URL)
            append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" })
            }
        }
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(JSON_MEDIA))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(JSON_MEDIA))
            "DELETE" -> builder.delete()
            else -> error("unsupported method")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (response.code == 401) error(json.optString("msg").ifBlank { "登录状态已失效" })
            if (!response.isSuccessful) error(json.optString("msg").ifBlank { "请求失败（HTTP ${response.code}）" })
            if (json.has("success") && !json.optBoolean("success")) {
                error(json.optString("msg").ifBlank { "CodeChat 操作失败" })
            }
            json
        }
    }

    private fun loadCredential(): Credential? {
        val token = prefs.getString(KEY_TOKEN, null).orEmpty()
        val ccUid = prefs.getString(KEY_CC_UID, null).orEmpty()
        val pAssert = prefs.getString(KEY_PASSERT, null).orEmpty()
        return if (token.isNotBlank() && ccUid.isNotBlank() && pAssert.isNotBlank()) {
            Credential(token, ccUid, pAssert)
        } else null
    }

    private fun saveCredential(credential: Credential) {
        prefs.edit()
            .putString(KEY_TOKEN, credential.token)
            .putString(KEY_CC_UID, credential.ccUid)
            .putString(KEY_PASSERT, credential.pAssert)
            .apply()
    }

    private data class Credential(
        val token: String,
        val ccUid: String,
        val pAssert: String,
    )

    private fun ChatQuote.toJson(): JSONObject = JSONObject()
        .put("id", id.take(60))
        .put("name", name.take(40))
        .put("text", text.take(120))
        .put("image", image)

    private companion object {
        const val BASE_URL = "https://codechat.codemao.64mowan.top"
        const val WS_URL = "wss://codechat.codemao.64mowan.top/codemao-ws"
        const val PREFS = "codechat_session_v2"
        const val KEY_CONSENT = "consent"
        const val KEY_TOKEN = "token"
        const val KEY_CC_UID = "cc_uid"
        const val KEY_PASSERT = "p_assert"
        const val TAG = "CodeChat"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
