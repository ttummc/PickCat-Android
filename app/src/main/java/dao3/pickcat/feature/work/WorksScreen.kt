package dao3.pickcat.feature.work

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.LocalTabReselect
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.SectionHeader
import dao3.pickcat.core.ui.WorkCard
import dao3.pickcat.core.ui.WorkGridSkeleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkSort(val apiValue: String, val label: String) {
    RECOMMENDED("recommended", "推荐"),
    LATEST("latest", "最新"),
    FOLLOWING("following", "关注"),
}

private fun WorkSort.apiSort(): String = if (this == WorkSort.FOLLOWING) "recommended" else apiValue

private fun WorkSort.apiScope(): String = if (this == WorkSort.FOLLOWING) "following" else "all"

private enum class WorksPageState { LOADING, ERROR, EMPTY, CONTENT }

data class WorksUiState(
    val featured: List<Work> = emptyList(),
    val items: List<Work> = emptyList(),
    val sort: WorkSort = WorkSort.RECOMMENDED,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val endReached: Boolean = false,
    val error: String? = null,
)

class WorksViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(WorksUiState())
    val state: StateFlow<WorksUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun setSort(sort: WorkSort) {
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
                coroutineScope {
                    val featuredDeferred = async {
                        if (_state.value.featured.isEmpty()) {
                            runCatching { api.getFeaturedWorks(7).items }.getOrDefault(emptyList())
                        } else {
                            _state.value.featured
                        }
                    }
                    val feedDeferred = async {
                        api.getWorkRecommendations(
                            sort = _state.value.sort.apiSort(),
                            scope = _state.value.sort.apiScope(),
                            limit = 12,
                        )
                    }
                    val featured = featuredDeferred.await()
                    val page = feedDeferred.await()
                    _state.update {
                        it.copy(
                            featured = featured,
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
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore || snapshot.endReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getWorkRecommendations(
                    sort = snapshot.sort.apiSort(),
                    scope = snapshot.sort.apiScope(),
                    limit = 12,
                    cursor = cursor,
                )
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy { work -> work.id },
                        cursor = page.cursor,
                        endReached = !page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksScreen(
    onOpenWork: (Work) -> Unit,
    onSearch: () -> Unit,
    viewModel: WorksViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val tabReselect = LocalTabReselect.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(tabReselect) {
        tabReselect?.collect { gridState.animateScrollToItem(0) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("作品", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val pageState = when {
            state.loading && state.items.isEmpty() -> WorksPageState.LOADING
            state.error != null && state.items.isEmpty() -> WorksPageState.ERROR
            state.items.isEmpty() && state.featured.isEmpty() -> WorksPageState.EMPTY
            else -> WorksPageState.CONTENT
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            AnimatedContent(
                targetState = pageState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                },
                label = "works-content-state",
            ) { stage ->
                when (stage) {
                    WorksPageState.LOADING -> WorkGridSkeleton(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    WorksPageState.ERROR -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::refresh,
                    )
                    WorksPageState.EMPTY -> EmptyState(
                        title = "还没有作品",
                        modifier = Modifier.fillMaxSize(),
                    )
                    WorksPageState.CONTENT -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(168.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.featured.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                FeaturedWorksRow(
                                    works = state.featured,
                                    onOpenWork = onOpenWork,
                                )
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "发现作品",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val options = WorkSort.entries
                                    options.forEach { sort ->
                                        FilterChip(
                                            selected = state.sort == sort,
                                            onClick = { viewModel.setSort(sort) },
                                            label = { Text(sort.label) },
                                        )
                                    }
                                }
                            }
                        }
                        items(state.items, key = { it.id }) { work ->
                            WorkCard(
                                work = work,
                                onClick = { onOpenWork(work) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
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
private fun FeaturedWorksRow(
    works: List<Work>,
    onOpenWork: (Work) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "精选作品",
            action = {
                Text(
                    "策划推荐",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(works, key = { it.id }) { work ->
                Box(Modifier.width(252.dp)) {
                    WorkCard(work = work, onClick = { onOpenWork(work) })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
