package dao3.pickcat.feature.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private fun matchesFilter(item: NotificationItem, filter: NotificationFilter): Boolean {
    return when (filter) {
        NotificationFilter.ALL -> true
        NotificationFilter.MENTION -> item.type in setOf("USER_MENTIONED", "WORK_COMMENT_MENTIONED")
        NotificationFilter.REPLY -> item.type in setOf(
            "REPLY_CREATED",
            "WORK_COMMENT_CREATED",
            "WORK_COMMENT_REPLY_CREATED",
        )
        NotificationFilter.SYSTEM -> item.type in setOf(
            "USER_FOLLOWED",
            "MANAGEMENT_ACTION",
            "TOPIC_EVENT",
            "LEVEL_CERTIFICATION",
            "TAKEDOWN_APPEAL_DECIDED",
            "QUESTION_DUPLICATE_REQUEST_DECIDED",
        )
    }
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
                        items = (it.items + page.items.map { item -> item.normalized() }).distinctBy { item -> item.id },
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

    fun markAllRead() {
        if (_state.value.marking) return
        viewModelScope.launch {
            _state.update { it.copy(marking = true) }
            try {
                api.markNotificationsRead()
                refresh()
                _messages.tryEmit("已全部标记为已读")
            } catch (error: Exception) {
                _messages.tryEmit(error.message ?: "操作失败")
            } finally {
                _state.update { it.copy(marking = false) }
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
                            Text("通知", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (unread > 0) "$unread 条未读" else "暂时没有未读通知",
                                style = MaterialTheme.typography.labelSmall,
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
                        if (unread > 0) {
                            IconButton(onClick = viewModel::markAllRead, enabled = !state.marking) {
                                Icon(Icons.Default.DoneAll, contentDescription = "全部已读")
                            }
                        }
                    },
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = filter.ordinal,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    NotificationFilter.entries.forEach { tab ->
                        Tab(
                            selected = filter == tab,
                            onClick = { filter = tab },
                            text = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                    Tab(
                        selected = false,
                        onClick = onOpenLikes,
                        text = {
                            Text(
                                "收到的赞",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
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
        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "notifications-content",
        ) { stage ->
            when (stage) {
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
                )
                NotificationPageState.CONTENT -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = wideContentPadding(760.dp),
                        end = wideContentPadding(760.dp),
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        NotificationRow(
                            item = item,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                if (item.topic?.id != null) {
                                    onOpenTopic(item.topic!!.id, item.post?.postNumber)
                                } else if (item.work?.id != null) {
                                    onOpenWork(item.work!!.id)
                                }
                            },
                        )
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!item.read) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "未读" },
                )
            } else {
                Spacer(Modifier.width(6.dp))
            }
            if (item.actor != null) {
                UserAvatar(item.actor, 38.dp)
            }
            Column(Modifier.weight(1f)) {
                    Text(
                        item.actor?.username ?: "系统",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (item.read) FontWeight.Medium else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                val target = item.topic?.title ?: item.work?.workName
                if (!target.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            target,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        item.post?.postNumber?.let { number ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "#$number",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    relativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ likes page

data class LikesUiState(
    val groups: List<LikeGroup> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesScreen(
    onBack: () -> Unit,
) {
    val viewModel: LikesViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "likes-content",
        ) { stage ->
            when (stage) {
                LikesPageState.LOADING -> LoadingState()
                LikesPageState.ERROR -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )
                LikesPageState.EMPTY -> EmptyState(
                    title = "还没有收到点赞",
                    icon = Icons.Default.Favorite,
                )
                LikesPageState.CONTENT -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.groups, key = { it.groupKey }) { group ->
                        Card(
                            modifier = Modifier.animateItem(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
        }
    }
}
}
