package dao3.pickcat.feature.codechat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dao3.pickcat.AppGraph
import dao3.pickcat.core.ui.PickCatImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CodeChatViewModel : ViewModel() {
    private val repository = AppGraph.codeChat
    val state: StateFlow<CodeChatState> = repository.state

    init {
        if (repository.state.value.consentGranted) repository.connect()
    }

    fun grantConsent() = repository.grantConsent()
    fun retry() = repository.connect()
    fun openPeer(peerUid: String?) = repository.setActivePeer(peerUid)
    fun send(text: String, to: String, quote: ChatQuote?) = repository.send(text, to, quote)
    fun sendImage(bytes: ByteArray, mime: String, to: String, quote: ChatQuote?) =
        repository.sendImage(bytes, mime, to, quote)
    fun recall(message: CodeChatMessage) = repository.recall(message.id)
    fun report(message: CodeChatMessage, reason: String, note: String) =
        repository.report(message, reason, note)
    fun clearError() = repository.consumeError()

    suspend fun resolvePickCatUserId(ccUid: String): String? =
        repository.resolvePickCatUserId(ccUid)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeChatScreen(
    initialPeerUid: String? = null,
    onOpenProfile: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var activePeer by remember { mutableStateOf(initialPeerUid) }
    var quote by remember { mutableStateOf<ChatQuote?>(null) }
    var actionTarget by remember { mutableStateOf<CodeChatMessage?>(null) }
    var reportTarget by remember { mutableStateOf<CodeChatMessage?>(null) }
    var showOnline by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportNote by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@launch
            if (bytes.size > 8 * 1024 * 1024) {
                snackbar.showSnackbar("图片不能超过 8MB")
                return@launch
            }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.sendImage(bytes, mime, activePeer.orEmpty(), quote)
            quote = null
        }
    }

    LaunchedEffect(activePeer) {
        viewModel.openPeer(activePeer)
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearError()
    }

    if (!state.consentGranted) {
        CodeChatConsent(onAccept = viewModel::grantConsent, onDecline = onLeave)
        return
    }

    val visibleMessages = remember(state.messages, activePeer) {
        state.messages
            .filter { message ->
                if (activePeer == null) message.peerUid.isBlank()
                else message.peerUid == activePeer
            }
            .sortedBy { it.time }
    }
    val peerSession = state.sessions.firstOrNull { it.peerUid == activePeer }
    val inConversation = activePeer != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                activePeer.isNullOrBlank() -> "CodeChat 官方聊群"
                                peerSession != null -> peerSession.nickname
                                else -> "私聊"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "消息服务由 CodeChat 提供",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (inConversation) {
                                activePeer = null
                                viewModel.openPeer(null)
                            } else {
                                onLeave()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showOnline = true }) {
                        Icon(Icons.Default.People, contentDescription = "在线成员")
                    }
                },
            )
        },
        bottomBar = {
            if (inConversation) {
                CodeChatComposer(
                    draft = draft,
                    onDraftChange = { draft = it.take(2000) },
                    quote = quote,
                    onClearQuote = { quote = null },
                    sending = false,
                    enabled = state.connected && state.canChat,
                    onPickImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            viewModel.send(text, activePeer.orEmpty(), quote)
                            draft = ""
                            quote = null
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!inConversation) {
                CodeChatConversationList(
                    state = state,
                    onOpenPublic = { activePeer = "" },
                    onOpenSession = { activePeer = it.peerUid },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (visibleMessages.isEmpty()) {
                        item {
                            Text(
                                if (state.connecting) "正在连接 CodeChat" else "还没有消息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(visibleMessages, key = { it.id }) { message ->
                        CodeChatMessageRow(
                            message = message,
                            onLongPress = { actionTarget = message },
                            onOpenProfile = { ccUid ->
                                scope.launch {
                                    val pcUuid = viewModel.resolvePickCatUserId(ccUid)
                                    if (pcUuid != null) onOpenProfile(pcUuid)
                                    else snackbar.showSnackbar("该用户没有关联 PickCat 账号")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showOnline) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOnline = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "在线成员 ${state.onlineUsers.count { it.online }}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                state.onlineUsers.filter { it.online }.forEach { user ->
                    ListItem(
                        headlineContent = { Text(user.nickname) },
                        supportingContent = if (user.status.isNotBlank()) {
                            { Text(user.status) }
                        } else null,
                        leadingContent = {
                            CodeChatAvatar(user.avatar, user.nickname, 40.dp)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }

    actionTarget?.let { message ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "消息操作",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                TextButton(
                    onClick = {
                        quote = ChatQuote(
                            id = message.id,
                            name = message.senderName,
                            text = message.text,
                            image = message.image != null,
                        )
                        actionTarget = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) { Text("引用") }
                if (message.fromMe) {
                    TextButton(
                        onClick = {
                            viewModel.recall(message)
                            actionTarget = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("撤回") }
                }
                TextButton(
                    onClick = {
                        actionTarget = null
                        reportTarget = message
                        showReport = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) { Text("举报") }
            }
        }
    }

    if (showReport && reportTarget != null) {
        val target = reportTarget!!
        val reasons = listOf(
            "ad" to "广告",
            "harass" to "骚扰",
            "abuse" to "辱骂",
            "fake" to "虚假信息",
            "other" to "其他",
        )
        var selectedReason by remember { mutableStateOf("other") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("举报消息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { (code, label) ->
                        TextButton(onClick = { selectedReason = code }) {
                            Text(if (selectedReason == code) "已选择 $label" else label)
                        }
                    }
                    OutlinedTextField(
                        value = reportNote,
                        onValueChange = { reportNote = it.take(300) },
                        label = { Text("补充说明（可选）") },
                        minLines = 1,
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.report(target, selectedReason, reportNote)
                    showReport = false
                    reportTarget = null
                    reportNote = ""
                }) { Text("提交举报") }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CodeChatConversationList(
    state: CodeChatState,
    onOpenPublic: () -> Unit,
    onOpenSession: (DmSession) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "public") {
            ListItem(
                headlineContent = { Text("CodeChat 官方聊群") },
                supportingContent = {
                    Text(state.messages.lastOrNull { it.peerUid.isBlank() }?.text ?: "公共频道")
                },
                leadingContent = {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                    }
                },
                trailingContent = if (state.unreadByPeer[""] ?: 0 > 0) {
                    { Text("${state.unreadByPeer[""]}", color = MaterialTheme.colorScheme.primary) }
                } else null,
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.combinedClickable(onClick = onOpenPublic, onLongClick = {}),
            )
        }
        item { Text("私聊", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
        items(state.sessions, key = { it.peerUid }) { session ->
            ListItem(
                headlineContent = { Text(session.nickname) },
                supportingContent = session.lastText.takeIf { it.isNotBlank() }?.let { text ->
                    { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                leadingContent = { CodeChatAvatar(session.avatar, session.nickname, 44.dp) },
                trailingContent = if (session.unreadCount > 0) {
                    { Text("${session.unreadCount}", color = MaterialTheme.colorScheme.primary) }
                } else null,
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenSession(session) },
                    onLongClick = {},
                ),
            )
        }
    }
}

@Composable
private fun CodeChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    quote: ChatQuote?,
    onClearQuote: () -> Unit,
    sending: Boolean,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (quote != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "引用 ${quote.name}：${quote.text.take(60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onClearQuote, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "取消引用")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发消息") },
                    enabled = enabled,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                )
                IconButton(onClick = onPickImage, enabled = enabled) {
                    Icon(Icons.Default.Image, contentDescription = "发送图片")
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && draft.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeChatMessageRow(
    message: CodeChatMessage,
    onLongPress: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
    ) {
        if (!message.fromMe) {
            CodeChatAvatar(message.senderAvatar, message.senderName, 36.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
                .combinedClickable(onClick = {
                    if (!message.fromMe) onOpenProfile(message.senderId)
                }, onLongClick = onLongPress),
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        ) {
            if (!message.fromMe) {
                Text(message.senderName, style = MaterialTheme.typography.labelMedium)
            }
            if (message.recalled) {
                Text("消息已撤回", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                message.quote?.let { quote ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(
                            "${quote.name}：${quote.text}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
                if (!message.image.isNullOrBlank()) {
                    PickCatImage(
                        url = message.image,
                        contentDescription = "聊天图片",
                        modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 260.dp),
                        corner = 12.dp,
                    )
                }
                if (message.text.isNotBlank()) {
                    Surface(
                        color = if (message.fromMe) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(top = if (message.image.isNullOrBlank()) 0.dp else 4.dp),
                    ) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Text(
                formatCodeChatTime(message.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CodeChatAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    val proxied = url?.takeIf { it.startsWith("/api/v1/") }
        ?.let { "https://codechat.codemao.64mowan.top/api/cc/avatar-proxy?url=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        ?: url
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (proxied.isNullOrBlank()) {
            Text(name.take(1), style = MaterialTheme.typography.labelLarge)
        } else {
            AsyncImage(
                model = proxied,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun CodeChatConsent(onAccept: () -> Unit, onDecline: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(16.dp))
            Text("使用 CodeChat", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(8.dp))
            Text(
                "CodeChat 是独立的第三方聊天服务，消息由 CodeChat 提供和存储。首次使用会使用当前 PickCat 身份注册或关联 CodeChat 账号，并向 CodeChat 提供昵称、头像和等级。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(20.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("同意并继续")
            }
            TextButton(onClick = onDecline) { Text("暂不使用") }
        }
    }
}

private fun formatCodeChatTime(time: Long): String {
    if (time <= 0) return ""
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(time))
}
