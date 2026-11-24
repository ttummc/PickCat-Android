package dao3.pickcat.feature.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import dao3.pickcat.LoginWebViewActivity
import dao3.pickcat.R
import dao3.pickcat.SavedAccount
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.TopicFeedSkeleton
import dao3.pickcat.core.ui.greeting
import dao3.pickcat.core.ui.rememberFabVisibleOnScroll
import dao3.pickcat.feature.settings.AccountSwitcherSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    onOpenUser: (UserRef) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visibleItems = remember(state.items, state.kind) {
        if (state.kind == HomeFeedKind.LATEST) {
            state.items.filterNot { topic ->
                topic.pinned || topic.pinnedGlobally || !topic.pinnedTagId.isNullOrBlank()
            }
        } else {
            state.items
        }
    }
    val unread by appViewModel.unreadCount.collectAsStateWithLifecycle()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val accounts by appViewModel.accounts.collectAsStateWithLifecycle()
    val currentUser = session.user
    var showAccountSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addAccountLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                appViewModel.finishAddAccount()
            } else {
                appViewModel.cancelAddAccount()
                snackbarHostState.showSnackbar("已取消添加账号")
            }
        }
    }
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

    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) appViewModel.saveCurrentAccount()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "home-greeting") {
                    HomeGreeting(
                        username = currentUser?.username ?: state.username,
                        onSwitchAccount = { showAccountSheet = true },
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
                            modifier = Modifier.fillParentMaxHeight(0.7f),
                        )
                    }
                    visibleItems.isEmpty() && !state.endReached -> item(key = "home-footer") {
                        LaunchedEffect(state.cursor, state.endReached) {
                            viewModel.loadMore()
                        }
                        PagedFooter(loading = true, endReached = false)
                    }
                    visibleItems.isEmpty() -> item {
                        EmptyState(
                            title = "还没有内容",
                            description = "稍后再来看看。",
                            modifier = Modifier.fillParentMaxHeight(0.7f),
                        )
                    }
                    else -> {
                        items(visibleItems, key = { it.id }) { topic ->
                            TopicCard(
                                topic = topic,
                                onClick = { onOpenTopic(topic) },
                                onAuthorClick = { onOpenUser(topic.author) },
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

    if (showAccountSheet) {
        AccountSwitcherSheet(
            currentUserId = currentUser?.id,
            accounts = accounts,
            onSelect = { account ->
                showAccountSheet = false
                scope.launch {
                    val switched = appViewModel.switchAccount(account)
                    snackbarHostState.showSnackbar(
                        if (switched) "已切换到 ${account.username}" else "切换账号失败，请重新登录",
                    )
                }
            },
            onAdd = {
                showAccountSheet = false
                scope.launch {
                    appViewModel.prepareAddAccount()
                    addAccountLauncher.launch(Intent(context, LoginWebViewActivity::class.java))
                }
            },
            onDismiss = { showAccountSheet = false },
        )
    }
}

@Composable
private fun HomeGreeting(
    username: String?,
    onSwitchAccount: () -> Unit,
) {
    val greetingText by produceState(initialValue = greeting()) {
        while (true) {
            delay(60_000)
            value = greeting()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            greetingText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitchAccount),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                username ?: "未登录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "切换账号",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val options = listOf(HomeFeedKind.RECOMMENDED to "推荐", HomeFeedKind.LATEST to "最新")
            options.forEach { (value, label) ->
                FilterChip(
                    selected = kind == value,
                    onClick = { onKind(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
