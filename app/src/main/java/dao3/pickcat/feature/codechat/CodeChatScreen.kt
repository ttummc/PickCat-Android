package dao3.pickcat.feature.codechat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.core.ui.PickCatImage
import dao3.pickcat.core.ui.UserAvatar
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

class CodeChatViewModel : ViewModel() {
    private val repository = AppGraph.codeChat
    val state = repository.state

    init {
        repository.connect()
    }

    fun retry() = repository.retry()

    fun grantConsent() = repository.grantConsent()

    fun send(text: String, to: String = "") = repository.sendMessage(text, to)

    fun sendImage(imageUrl: String, to: String = "") = repository.sendImage(imageUrl, to)

    suspend fun uploadImage(context: Context, uri: Uri): Result<String> =
        repository.uploadImage(context, uri)

    fun refreshDmSessions() = repository.refreshDmSessions()

    fun openConversation(peerUid: String?) = repository.openConversation(peerUid)

    fun loadOlder(peerUid: String?) = repository.loadOlder(peerUid)

    fun setActivePeer(peerUid: String?) = repository.setActivePeer(peerUid)

    fun ensureDmSession(peerUid: String, nickname: String, avatar: String?) =
        repository.ensureDmSession(peerUid, nickname, avatar)

    suspend fun resolvePickCatUserId(ccUid: String): String? =
        repository.resolvePickCatUserId(ccUid)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeChatScreen(
    modifier: Modifier = Modifier,
    initialPeerUid: String? = null,
    onOpenProfile: (String) -> Unit,
    onLeave: () -> Unit = {},
    viewModel: CodeChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.consentGranted) {
        CodeChatConsentScreen(
            onAccept = viewModel::grantConsent,
            onDecline = onLeave,
        )
        return
    }

    var draft by rememberSaveable { mutableStateOf("") }
    var publicOpen by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf<DmSession?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uploading by remember { mutableStateOf(false) }

    val inConversation = publicOpen || selectedPeer != null
    val visibleMessages = when {
        selectedPeer != null -> state.messages.filter { it.peerId == selectedPeer?.peerUid }
        publicOpen -> state.messages.filter { it.peerId.isBlank() }
        else -> emptyList()
    }
    val publicMessages = state.messages.filter { it.peerId.isBlank() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val result = viewModel.uploadImage(context, uri)
            uploading = false
            result.onSuccess { url ->
                viewModel.sendImage(url, selectedPeer?.peerUid.orEmpty())
            }.onFailure { error ->
                snackbarHostState.showSnackbar(error.message ?: "图片上传失败")
            }
        }
    }

    fun closeConversation() {
        publicOpen = false
        selectedPeer = null
        draft = ""
    }

    fun openPublic() {
        selectedPeer = null
        publicOpen = true
        draft = ""
        viewModel.openConversation(null)
    }

    fun choosePeer(session: DmSession) {
        viewModel.ensureDmSession(session.peerUid, session.nickname, session.avatar)
        publicOpen = false
        selectedPeer = session
        draft = ""
        viewModel.openConversation(session.peerUid)
    }

    fun startDm(message: CodeChatMessage) {
        choosePeer(
            DmSession(
                peerUid = message.senderId,
                nickname = message.senderName,
                avatar = message.senderAvatar,
                lastTime = message.time,
                lastText = "",
                online = false,
            ),
        )
    }

    LaunchedEffect(initialPeerUid, state.dmSessions) {
        val peerUid = initialPeerUid
        if (peerUid.isNullOrBlank()) return@LaunchedEffect
        val session = state.dmSessions.firstOrNull { it.peerUid == peerUid }
        if (selectedPeer == null && !publicOpen) {
            choosePeer(
                session ?: DmSession(
                    peerUid = peerUid,
                    nickname = "",
                    avatar = null,
                    lastTime = 0L,
                    lastText = "",
                    online = false,
                ),
            )
        } else if (selectedPeer?.peerUid == peerUid && session != null) {
            selectedPeer = session
        }
    }

    LaunchedEffect(visibleMessages.lastOrNull()?.id, selectedPeer?.peerUid, publicOpen) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    LaunchedEffect(selectedPeer?.peerUid, publicOpen) {
        viewModel.setActivePeer(if (publicOpen) null else selectedPeer?.peerUid)
    }

    LaunchedEffect(inConversation) {
        if (!inConversation) viewModel.refreshDmSessions()
    }

    BackHandler(enabled = inConversation) {
        closeConversation()
    }

    val openUserProfile: (CodeChatMessage) -> Unit = { message ->
        if (message.senderPcLinked == false) {
            scope.launch {
                snackbarHostState.showSnackbar("该用户没有关联 PickCat 账号，无法打开主页")
            }
        } else if (!message.senderPcUuid.isNullOrBlank()) {
            onOpenProfile(message.senderPcUuid)
        } else {
            scope.launch {
                val userId = viewModel.resolvePickCatUserId(message.senderId)
                if (userId.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("该用户没有关联 PickCat 账号，无法打开主页")
                } else {
                    onOpenProfile(userId)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                selectedPeer != null -> selectedPeer?.nickname.orEmpty()
                                publicOpen -> "CodeChat 官方聊群"
                                else -> "CodeChat"
                            },
                        )
                        Text(
                            if (inConversation) {
                                "消息服务由 CodeChat 提供"
                            } else {
                                "由 CodeChat 提供的公共群聊和私聊"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (inConversation) {
                        IconButton(onClick = ::closeConversation) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回会话列表",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = inConversation,
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(2_000) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        if (state.connected) "发消息" else "正在连接",
                                    )
                                },
                                enabled = state.connected,
                                minLines = 1,
                                maxLines = 4,
                                shape = MaterialTheme.shapes.medium,
                            )
                            IconButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                enabled = state.connected && !uploading,
                                modifier = Modifier.size(48.dp),
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = "发送图片",
                                    )
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    val text = draft.trim()
                                    if (text.isNotEmpty()) {
                                        viewModel.send(text, selectedPeer?.peerUid.orEmpty())
                                        draft = ""
                                    }
                                },
                                enabled = state.connected && draft.isNotBlank(),
                                modifier = Modifier.size(52.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送消息",
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val error = state.error
            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = viewModel::retry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("重试")
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = inConversation,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                            ) { it / 8 }) togetherWith
                            (fadeOut(tween(140)) +
                                slideOutHorizontally(tween(180)) { -it / 12 })
                    } else {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                            ) { -it / 8 }) togetherWith
                            (fadeOut(tween(140)) +
                                slideOutHorizontally(tween(180)) { it / 12 })
                    }
                },
                label = "codechat-content",
            ) { chatVisible ->
                when {
                    !chatVisible -> ConversationList(
                        publicMessages = publicMessages,
                        sessions = state.dmSessions,
                        unreadByPeer = state.unreadByPeer,
                        onOpenPublic = ::openPublic,
                        onOpenSession = ::choosePeer,
                    )
                    visibleMessages.isEmpty() -> EmptyConversation(
                        isPrivate = selectedPeer != null,
                        isConnecting = state.connecting,
                    )
                    else -> {
                        val conversationKey = selectedPeer?.peerUid ?: "public"
                        val hasMore = state.hasMoreByConversation[conversationKey] == true
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (hasMore) {
                                item(key = "load-older") {
                                    TextButton(
                                        onClick = { viewModel.loadOlder(selectedPeer?.peerUid) },
                                        enabled = !state.loadingHistory,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (state.loadingHistory) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text("加载更早消息")
                                        }
                                    }
                                }
                            }
                            items(visibleMessages, key = { it.id }) { message ->
                                Box(Modifier.animateItem()) {
                                    CodeChatMessageRow(
                                        message = message,
                                        onOpenProfile = openUserProfile,
                                        onStartDm = if (message.fromMe) null else ::startDm,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeChatConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CodeChat")
                        Text(
                            "消息服务由 CodeChat 提供",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                "使用 CodeChat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Text(
                "CodeChat 是独立的第三方聊天服务，消息由 CodeChat 提供和存储。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "首次使用会使用你的 PickCat 身份注册或关联一个 CodeChat 账号，并向 CodeChat 提供昵称、头像和等级。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "点击同意后才会建立连接并创建账号；不同意不会向 CodeChat 发送身份信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("同意并继续")
            }
            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("暂不使用")
            }
        }
    }
}

@Composable
private fun ConversationList(
    publicMessages: List<CodeChatMessage>,
    sessions: List<DmSession>,
    unreadByPeer: Map<String, Int>,
    onOpenPublic: () -> Unit,
    onOpenSession: (DmSession) -> Unit,
) {
    val lastPublic = publicMessages.lastOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "public") {
            ListItem(
                headlineContent = {
                    Text(
                        "CodeChat 官方聊群",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        lastPublic?.text?.ifBlank { "图片" } ?: "公共频道",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                trailingContent = {
                    if (lastPublic != null) {
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(Date(lastPublic.time)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.clickable(onClick = onOpenPublic).animateItem(),
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        item(key = "dm-title") {
            Text(
                "私聊",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        if (sessions.isEmpty()) {
            item(key = "dm-empty") {
                Text(
                    "还没有私聊会话。从公共群聊点击其他用户的私聊开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            items(sessions, key = { it.peerUid }) { session ->
                Box(Modifier.animateItem()) {
                    DmSessionRow(
                        session = session,
                        unread = unreadByPeer[session.peerUid] ?: 0,
                        onClick = { onOpenSession(session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DmSessionRow(
    session: DmSession,
    unread: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                session.nickname.ifBlank { session.peerUid },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (session.lastText.isNotBlank()) {
            {
                Text(
                    session.lastText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            UserAvatar(
                name = session.nickname,
                url = session.avatar,
                size = 44.dp,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (unread > 0) {
                    Badge {
                        Text(if (unread > 99) "99+" else unread.toString())
                    }
                }
                if (session.lastTime > 0L) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(session.lastTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.online) {
                    Text(
                        "在线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun EmptyConversation(
    isPrivate: Boolean,
    isConnecting: Boolean,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                if (isPrivate) "还没有私聊消息" else "CodeChat 官方聊群",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                when {
                    isConnecting -> "正在连接 CodeChat"
                    isPrivate -> "发一条消息开始会话"
                    else -> "还没有消息，发一条试试"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CodeChatMessageRow(
    message: CodeChatMessage,
    onOpenProfile: (CodeChatMessage) -> Unit,
    onStartDm: ((CodeChatMessage) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.fromMe) {
            UserAvatar(
                name = message.senderName,
                url = message.senderAvatar,
                size = 32.dp,
                modifier = Modifier.clickable { onOpenProfile(message) },
            )
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        ) {
            if (!message.fromMe) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.senderName.ifBlank { "CodeChat 用户" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onStartDm != null && message.senderId.isNotBlank()) {
                        TextButton(
                            onClick = { onStartDm(message) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("私聊")
                        }
                    }
                }
                Spacer(Modifier.size(3.dp))
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (message.fromMe) 16.dp else 4.dp,
                    topEnd = if (message.fromMe) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                color = if (message.fromMe) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (message.fromMe) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (!message.imageUrl.isNullOrBlank()) {
                        PickCatImage(
                            url = message.imageUrl,
                            contentDescription = "聊天图片",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            contentScale = ContentScale.Fit,
                            corner = 10.dp,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.time)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
