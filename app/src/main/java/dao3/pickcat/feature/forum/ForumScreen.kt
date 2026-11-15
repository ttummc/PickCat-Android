package dao3.pickcat.feature.forum

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.LocalTabReselect
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.TopicFeedSkeleton
import dao3.pickcat.core.ui.rememberFabVisibleOnScroll
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow

enum class TopicSort { LATEST, ACTIVE }

private enum class TopicFeedPageState { LOADING, ERROR, EMPTY, CONTENT }

data class TopicFeedUiState(
    val items: List<Topic> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagSlug: String? = null,
    val sort: TopicSort = TopicSort.LATEST,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val tag: Tag? get() = tags.firstOrNull { it.slug == tagSlug }
}

class TopicFeedViewModel(private val fixedTag: String? = null) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(TopicFeedUiState(tagSlug = fixedTag))
    val state: StateFlow<TopicFeedUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            runCatching { api.getTags().items }.onSuccess { tags ->
                _state.update { it.copy(tags = tags) }
            }
        }
        refresh()
    }

    fun setSort(sort: TopicSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
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
                val sort = _state.value.sort
                if (sort == TopicSort.ACTIVE) {
                    val collected = mutableListOf<Topic>()
                    var cursor: String? = null
                    for (pageIndex in 0 until 3) {
                        val page = api.getTopics(
                            sort = "latest",
                            tag = fixedTag,
                            limit = 20,
                            cursor = cursor,
                        )
                        collected += page.items
                        cursor = page.cursor
                        if (!page.canLoadMore || cursor == null) break
                    }
                    val ranked = collected
                        .filterNot { it.pinned }
                        .distinctBy { it.id }
                        .sortedByDescending(::activityScore)
                        .take(60)
                    _state.update {
                        it.copy(
                            items = ranked,
                            cursor = null,
                            endReached = true,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                } else {
                    val page = api.getTopics(
                        sort = "latest",
                        tag = fixedTag,
                        limit = 20,
                    )
                    _state.update {
                        it.copy(
                            items = page.items,
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
        if (snapshot.sort == TopicSort.ACTIVE) return
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore || snapshot.endReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getTopics(
                    sort = "latest",
                    tag = fixedTag,
                    limit = 20,
                    cursor = cursor,
                )
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

    private fun activityScore(topic: Topic): Double {
        val instant = dao3.pickcat.core.ui.parseInstant(topic.lastActivityAt ?: topic.createdAt)
        val ageHours = instant?.let {
            ((System.currentTimeMillis() - it.toEpochMilli()) / 3_600_000.0).coerceAtLeast(0.0)
        } ?: 0.0
        val engagement = topic.replyCount * 4.0 +
            topic.likeCount * 2.0 +
            topic.bookmarkCount * 2.0 +
            ln(1.0 + topic.viewCount)
        return engagement / (1 + ageHours / 48).pow(1.3)
    }
}

@Composable
private fun topicFeedViewModel(tagSlug: String? = null): TopicFeedViewModel {
    val factory = viewModelFactory {
        initializer { TopicFeedViewModel(tagSlug) }
    }
    return viewModel(key = "topic-feed-${tagSlug.orEmpty()}", factory = factory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
    onOpenTopic: (Topic) -> Unit,
    onOpenCategory: (Tag) -> Unit,
    onOpenUser: (UserRef) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel: TopicFeedViewModel = topicFeedViewModel()
    TopicFeedScaffold(
        viewModel = viewModel,
        title = "论坛",
        subtitle = "所有话题，按分类归档",
        showBack = false,
        onBack = {},
        onOpenTopic = onOpenTopic,
        onOpenCategory = onOpenCategory,
        onOpenUser = onOpenUser,
        onCompose = onCompose,
        onSearch = onSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    tagSlug: String,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOpenUser: (UserRef) -> Unit,
    onCompose: () -> Unit,
) {
    val viewModel: TopicFeedViewModel = topicFeedViewModel(tagSlug)
    val state by viewModel.state.collectAsStateWithLifecycle()
    TopicFeedScaffold(
        viewModel = viewModel,
        title = "#${state.tag?.name ?: tagSlug}",
        subtitle = state.tag?.description ?: "这个分类下的话题",
        showBack = true,
        onBack = onBack,
        onOpenTopic = onOpenTopic,
        onOpenCategory = {},
        onOpenUser = onOpenUser,
        onCompose = onCompose,
        onSearch = {},
        showTags = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicFeedScaffold(
    viewModel: TopicFeedViewModel,
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOpenCategory: (Tag) -> Unit,
    onOpenUser: (UserRef) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    showTags: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val fabVisible = rememberFabVisibleOnScroll(listState)
    val tabReselect = LocalTabReselect.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(tabReselect) {
        tabReselect?.collect { listState.animateScrollToItem(0) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (!showBack) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
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
                    text = { Text(if (showBack) "在这里发帖" else "发布主题") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showTags && state.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(state.tags, key = { it.id }) { tag ->
                            AssistChip(
                                onClick = { onOpenCategory(tag) },
                                label = { Text("#${tag.name}") },
                            )
                        }
                    }
                    SortSelector(
                        sort = state.sort,
                        onSort = viewModel::setSort,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "排序",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    SortSelector(
                        sort = state.sort,
                        onSort = viewModel::setSort,
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val pageState = when {
                    state.loading && state.items.isEmpty() -> TopicFeedPageState.LOADING
                    state.error != null && state.items.isEmpty() -> TopicFeedPageState.ERROR
                    state.items.isEmpty() -> TopicFeedPageState.EMPTY
                    else -> TopicFeedPageState.CONTENT
                }
                AnimatedContent(
                    targetState = pageState,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    },
                    label = "topic-feed-state",
                ) { stage ->
                    when (stage) {
                        TopicFeedPageState.LOADING -> TopicFeedSkeleton(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        TopicFeedPageState.ERROR -> ErrorState(
                            message = state.error.orEmpty(),
                            onRetry = viewModel::refresh,
                        )
                        TopicFeedPageState.EMPTY -> EmptyState(
                            title = "还没有帖子",
                            actionLabel = "发帖",
                            onAction = onCompose,
                        )
                        TopicFeedPageState.CONTENT -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.items, key = { it.id }) { topic ->
                                TopicCard(
                                    topic,
                                    onClick = { onOpenTopic(topic) },
                                    onAuthorClick = { onOpenUser(topic.author) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item {
                                if (state.sort == TopicSort.LATEST) {
                                    LaunchedEffect(state.cursor, state.endReached) {
                                        if (!state.endReached) viewModel.loadMore()
                                    }
                                }
                                PagedFooter(
                                    loading = state.loadingMore,
                                    endReached = state.endReached,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortSelector(
    sort: TopicSort,
    onSort: (TopicSort) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.width(160.dp)) {
        val options = listOf(TopicSort.LATEST to "最新", TopicSort.ACTIVE to "活跃")
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = sort == value,
                onClick = { onSort(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}
