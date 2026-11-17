package dao3.pickcat.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.core.network.LikeGroup
import dao3.pickcat.core.network.NotificationItem
import dao3.pickcat.core.network.NotificationSummary
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.NotificationListSkeleton
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.relativeTime
import dao3.pickcat.core.ui.wideContentPadding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private enum class NotificationPageState { LOADING, ERROR, EMPTY, CONTENT }

private enum class NotificationFilter(val label: String) {
    ALL("全部"),
    MENTION("提到我的"),
    REPLY("回复我的"),
    SYSTEM("系统通知"),
}

private val systemNotificationTypes = setOf(
    "USER_FOLLOWED",
    "MANAGEMENT_ACTION",
    "TOPIC_EVENT",
    "LEVEL_CERTIFICATION",
    "TAKEDOWN_APPEAL_DECIDED",
    "QUESTION_DUPLICATE_REQUEST_DECIDED",
)

private fun matchesFilter(item: NotificationItem, filter: NotificationFilter): Boolean {
    return when (filter) {
        NotificationFilter.ALL -> true
        NotificationFilter.MENTION -> item.type in setOf("USER_MENTIONED", "WORK_COMMENT_MENTIONED")
        NotificationFilter.REPLY -> item.type in setOf(
            "REPLY_CREATED",
            "WORK_COMMENT_CREATED",
            "WORK_COMMENT_REPLY_CREATED",
        )
        NotificationFilter.SYSTEM -> item.type in systemNotificationTypes
    }
}

private fun notificationAction(item: NotificationItem): String = when (item.type) {
    "REPLY_CREATED" -> "回复了你的帖子"
    "POST_LIKED" -> "赞了你的帖子"
    "USER_MENTIONED" -> "在帖子中 @了你"
    "WORK_COMMENT_CREATED" -> "评论了你的作品"
    "WORK_COMMENT_REPLY_CREATED" -> "回复了你的评论"
    "WORK_COMMENT_MENTIONED" -> "在评论中 @了你"
    "WORK_LIKED" -> "赞了你的作品"
    "WORK_COMMENT_LIKED" -> "赞了你的评论"
    "USER_FOLLOWED" -> "关注了你"
    "MANAGEMENT_ACTION" -> "发布了社区管理通知"
    "TOPIC_EVENT" -> "帖子状态有了新变化"
    "LEVEL_CERTIFICATION" -> "等级认证状态更新"
    "TAKEDOWN_APPEAL_DECIDED" -> "下架申诉有结果了"
    "QUESTION_DUPLICATE_REQUEST_DECIDED" -> "问答重复申请有结果了"
    else -> "发来一条通知"
}

private fun notificationActor(item: NotificationItem): String =
    item.actor?.username?.takeIf { it.isNotBlank() }
        ?: if (item.type in systemNotificationTypes) "社区" else "系统"

private fun notificationTarget(item: NotificationItem): String? =
    item.topic?.title?.takeIf { it.isNotBlank() }
        ?: item.work?.workName?.takeIf { it.isNotBlank() }
        ?: item.details?.postTitle?.takeIf { it.isNotBlank() }
        ?: item.postTitle?.takeIf { it.isNotBlank() }

private fun notificationDetail(item: NotificationItem): String? {
    return sequenceOf(
        item.excerpt,
        item.replyExcerpt,
        item.details?.excerpt,
        item.details?.replyExcerpt,
        item.details?.reply,
        item.details?.publicReasonDetail,
        item.details?.summary,
        item.summary,
    ).firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun notificationIcon(type: String): ImageVector = when {
    type.contains("FOLLOW") -> Icons.Default.PersonAdd
    type.contains("LIKE") -> Icons.Default.Favorite
    type.contains("MENTION") -> Icons.Default.AlternateEmail
    type.contains("REPLY") || type.contains("COMMENT") -> Icons.AutoMirrored.Filled.Message
    else -> Icons.Default.Shield
}

data class NotificationsUiState(
    val items: List<NotificationItem> = emptyList(),
    val summary: NotificationSummary? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val marking: Boolean = false,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val endReached: Boolean = false,
)

class NotificationsViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
            try {
                coroutineScope {
                    val pageDeferred = async { api.getNotifications(limit = 20) }
                    val summaryDeferred = async { api.getNotificationSummary() }
                    val page = pageDeferred.await()
                    val summary = summaryDeferred.await()
                    _state.update {
                        it.copy(
                            items = page.items.map { item -> item.normalized() },
                            summary = summary,
                            cursor = page.cursor,
                            endReached = !page.canLoadMore,
                            loading = false,
                            error = null,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "加载失败") }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val cursor = snapshot.cursor
        if (snapshot.loadingMore || snapshot.endReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getNotifications(limit = 20, cursor = cursor)
                _state.update {
                    it.copy(
                        items = (it.items + page.items.map { item -> item.normalized() })
                            .distinctBy { item -> item.id },
                        cursor = page.cursor,
                        endReached = !page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun markRead(item: NotificationItem) {
        if (item.read) return
        val sameTypeUnread = _state.value.items.count {
            !it.read && (it.id == item.id || (item.type.isNotBlank() && it.type == item.type))
        }
        _state.update { state ->
            val currentUnread = state.summary?.unreadCount ?: state.items.count { !it.read }
            state.copy(
                items = state.items.map {
                    if (it.id == item.id || (item.type.isNotBlank() && it.type == item.type)) {
                        it.copy(read = true)
                    } else {
                        it
                    }
                },
                summary = (state.summary ?: NotificationSummary()).copy(
                    unreadCount = (currentUnread - sameTypeUnread).coerceAtLeast(0),
                ),
            )
        }
        if (item.type.isBlank()) return
        viewModelScope.launch {
            try {
                api.markNotificationsRead(type = item.type)
            } catch (_: Exception) {
                _messages.tryEmit("已读状态同步失败")
            }
        }
    }

    fun markAllRead() {
        if (_state.value.marking) return
        viewModelScope.launch {
            _state.update { it.copy(marking = true) }
            try {
                api.markNotificationsRead()
                _state.update {
                    it.copy(
                        items = it.items.map { item -> item.copy(read = true) },
                        summary = it.summary?.copy(unreadCount = 0),
                        marking = false,
                    )
                }
                _messages.tryEmit("已全部标记为已读")
            } catch (error: Exception) {
                _state.update { it.copy(marking = false) }
                _messages.tryEmit(error.message ?: "操作失败")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenTopic: (String, Int?) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLikes: () -> Unit,
) {
    val viewModel: NotificationsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(NotificationFilter.ALL) }
    val filteredItems = state.items.filter { item -> matchesFilter(item, filter) }
    val unread = maxOf(state.summary?.unreadCount ?: 0, state.items.count { !it.read })

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(unread) {
        appViewModel.setUnreadCount(unread)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("通知")
                            Text(
                                if (unread > 0) "$unread 条未读" else "没有未读通知",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenLikes) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "收到的赞")
                        }
                        if (unread > 0) {
                            IconButton(
                                onClick = viewModel::markAllRead,
                                enabled = !state.marking,
                            ) {
                                if (state.marking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.DoneAll, contentDescription = "全部已读")
                                }
                            }
                        }
                    },
                )
                PrimaryTabRow(
                    selectedTabIndex = filter.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    NotificationFilter.entries.forEach { tab ->
                        Tab(
                            selected = filter == tab,
                            onClick = { filter = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val pageState = when {
            state.loading && state.items.isEmpty() -> NotificationPageState.LOADING
            state.error != null && state.items.isEmpty() -> NotificationPageState.ERROR
            filteredItems.isEmpty() -> NotificationPageState.EMPTY
            else -> NotificationPageState.CONTENT
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (pageState) {
                NotificationPageState.LOADING -> NotificationListSkeleton(
                    modifier = Modifier.padding(16.dp),
                )
                NotificationPageState.ERROR -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )
                NotificationPageState.EMPTY -> EmptyState(
                    title = if (filter == NotificationFilter.ALL) "没有通知" else "暂无此类通知",
                    icon = Icons.Default.Notifications,
                    modifier = Modifier.fillMaxSize(),
                )
                NotificationPageState.CONTENT -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = wideContentPadding(760.dp),
                        end = wideContentPadding(760.dp),
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    itemsIndexed(
                        filteredItems,
                        key = { index, item -> "notification-${item.id}-$index" },
                    ) { _, item ->
                        Column(Modifier.animateItem()) {
                            NotificationRow(
                                item = item,
                                onClick = {
                                    viewModel.markRead(item)
                                    val topicId = item.topic?.id
                                    val workId = item.work?.id
                                    if (topicId != null) {
                                        onOpenTopic(topicId, item.post?.postNumber)
                                    } else if (workId != null) {
                                        onOpenWork(workId)
                                    }
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                    item {
                        LaunchedEffect(state.cursor, state.endReached) {
                            if (!state.endReached) viewModel.loadMore()
                        }
                        PagedFooter(loading = state.loadingMore, endReached = state.endReached)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
) {
    val target = notificationTarget(item)
    val detail = notificationDetail(item)?.takeIf { it != target }
    val actor = notificationActor(item)
    val headline = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontWeight = if (item.read) FontWeight.Medium else FontWeight.SemiBold,
            ),
        ) {
            append(actor)
        }
        append(" ")
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append(notificationAction(item))
        }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (item.read) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        leadingContent = {
            if (item.actor != null) {
                UserAvatar(item.actor, 40.dp)
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            notificationIcon(item.type),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                headline,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!target.isNullOrBlank()) {
                    Text(
                        target,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    relativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = if (!item.read) {
            {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "未读" },
                )
            }
        } else {
            null
        },
    )
}

// ------------------------------------------------------------------ likes page

data class LikesUiState(
    val groups: List<LikeGroup> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val endReached: Boolean = false,
)

private enum class LikesPageState { LOADING, ERROR, EMPTY, CONTENT }

class LikesViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(LikesUiState())
    val state: StateFlow<LikesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val page = api.getLikeNotificationGroups(limit = 20)
                _state.update {
                    it.copy(
                        groups = page.items,
                        cursor = page.cursor,
                        endReached = !page.canLoadMore,
                        loading = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "加载失败") }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val cursor = snapshot.cursor
        if (snapshot.loading || snapshot.loadingMore || snapshot.endReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getLikeNotificationGroups(limit = 20, cursor = cursor)
                _state.update {
                    it.copy(
                        groups = (it.groups + page.items).distinctBy { group ->
                            group.groupKey.ifBlank {
                                "${group.createdAt}-${group.topic?.id}-${group.work?.id}"
                            }
                        },
                        cursor = page.cursor,
                        endReached = !page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesScreen(
    onBack: () -> Unit,
) {
    val viewModel: LikesViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("收到的赞") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val pageState = when {
            state.loading -> LikesPageState.LOADING
            state.error != null -> LikesPageState.ERROR
            state.groups.isEmpty() -> LikesPageState.EMPTY
            else -> LikesPageState.CONTENT
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (pageState) {
                LikesPageState.LOADING -> LoadingState()
                LikesPageState.ERROR -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )
                LikesPageState.EMPTY -> EmptyState(
                    title = "还没有收到点赞",
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.fillMaxSize(),
                )
                LikesPageState.CONTENT -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        state.groups,
                        key = { index, group -> "like-${group.groupKey}-$index" },
                    ) { _, group ->
                        Card(
                            modifier = Modifier.animateItem(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        group.topicTitle ?: group.workName ?: group.topic?.title
                                        ?: group.work?.workName ?: group.groupKey,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    relativeTime(group.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (group.likers.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    group.likers.take(6).forEach { liker ->
                                        UserAvatar(liker, 24.dp)
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${group.count.takeIf { it > 0 } ?: group.likers.size} 人点赞",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        LaunchedEffect(state.cursor, state.endReached) {
                            if (!state.endReached) viewModel.loadMore()
                        }
                        PagedFooter(loading = state.loadingMore, endReached = state.endReached)
                    }
                }
            }
        }
    }
}
