package dao3.pickcat.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.LocalTabReselect
import dao3.pickcat.R
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.TopicFeedSkeleton
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.avatarUrl
import dao3.pickcat.core.ui.greeting
import dao3.pickcat.core.ui.rememberFabVisibleOnScroll
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeFeedKind { RECOMMENDED, LATEST }

data class HomeUiState(
    val kind: HomeFeedKind = HomeFeedKind.RECOMMENDED,
    val items: List<Topic> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val username: String? = null,
)

class HomeViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var profileLoaded = false

    init {
        viewModelScope.launch {
            AppGraph.container.session.state.collect { sessionState ->
                _state.update { it.copy(username = sessionState.user?.username) }
            }
        }
        refresh()
    }

    fun selectKind(kind: HomeFeedKind) {
        if (_state.value.kind == kind) return
        _state.update { it.copy(kind = kind) }
        refresh()
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.items.isEmpty(),
                    refreshing = it.items.isNotEmpty(),
                    error = null,
                )
            }
            try {
                coroutineScope {
                    val kind = _state.value.kind
                    val pageDeferred = async {
                        if (kind == HomeFeedKind.RECOMMENDED) {
                            api.getTopicRecommendations(limit = 20)
                        } else {
                            api.getTopics(sort = "latest", limit = 20)
                        }
                    }
                    val tagsDeferred = async {
                        if (_state.value.tags.isEmpty()) runCatching { api.getTags().items }.getOrDefault(emptyList())
                        else _state.value.tags
                    }
                    val page = pageDeferred.await()
                    val tags = tagsDeferred.await()
                    _state.update {
                        it.copy(
                            items = page.items,
                            tags = tags,
                            cursor = page.cursor,
                            endReached = !page.canLoadMore,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message ?: "加载失败",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val cursor = snapshot.cursor
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore || snapshot.endReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = if (snapshot.kind == HomeFeedKind.RECOMMENDED) {
                    api.getTopicRecommendations(limit = 20, cursor = cursor)
                } else {
                    api.getTopics(sort = "latest", limit = 20, cursor = cursor)
                }
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy { topic -> topic.id },
                        cursor = page.cursor,
                        endReached = !page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(loadingMore = false, error = error.message ?: "加载更多失败") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onOpenTopic: (Topic) -> Unit,
    onOpenCategory: (Tag) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by appViewModel.unreadCount.collectAsStateWithLifecycle()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val currentUser = session.user
    val listState = rememberLazyListState()
    val fabVisible = rememberFabVisibleOnScroll(listState)
    val tabReselect = LocalTabReselect.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(tabReselect) {
        tabReselect?.collect { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(Unit) {
        // The first session refresh can finish after the VM's init read.
        val user = AppGraph.container.session.state.value.user
        if (user != null) appViewModel.refreshUnread()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.pickcat_logo),
                            contentDescription = "PickCat",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "PickCat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onNotifications) {
                        BadgedBox(
                            badge = {
                                if (unread > 0) {
                                    Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "通知")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn(tween(180)) + scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialScale = 0.88f,
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    animationSpec = tween(160),
                    targetScale = 0.88f,
                ),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("发帖") },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "home-greeting") {
                    HomeGreeting(
                        username = currentUser?.username ?: state.username,
                        avatarUrl = avatarUrl(currentUser),
                    )
                }
                item(key = "home-filters") {
                    HomeFilters(
                        kind = state.kind,
                        onKind = viewModel::selectKind,
                    )
                }
                when {
                    state.loading && state.items.isEmpty() -> item {
                        TopicFeedSkeleton()
                    }
                    state.error != null && state.items.isEmpty() -> item {
                        ErrorState(
                            message = state.error.orEmpty(),
                            onRetry = viewModel::refresh,
                        )
                    }
                    state.items.isEmpty() -> item {
                        EmptyState(
                            title = "还没有内容",
                            description = "稍后再来看看。",
                        )
                    }
                    else -> {
                        items(state.items, key = { it.id }) { topic ->
                            TopicCard(
                                topic = topic,
                                onClick = { onOpenTopic(topic) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "home-footer") {
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
}

@Composable
private fun HomeGreeting(
    username: String?,
    avatarUrl: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${greeting()}${username?.let { "，$it" }.orEmpty()}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        UserAvatar(
            name = username,
            url = avatarUrl,
            size = 52.dp,
        )
    }
}

@Composable
private fun HomeFilters(
    kind: HomeFeedKind,
    onKind: (HomeFeedKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "社区动态",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        SingleChoiceSegmentedButtonRow(Modifier.width(172.dp)) {
            val options = listOf(HomeFeedKind.RECOMMENDED to "推荐", HomeFeedKind.LATEST to "最新")
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = kind == value,
                    onClick = { onKind(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}
