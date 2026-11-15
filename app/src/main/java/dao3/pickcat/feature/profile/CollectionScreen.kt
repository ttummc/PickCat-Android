package dao3.pickcat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.TopicCollection
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PagedFooter
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.wideContentPadding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val collection: TopicCollection? = null,
    val topics: List<Topic> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val cursor: String? = null,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
)

class CollectionViewModel(private val collectionId: String) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                coroutineScope {
                    val collection = async { api.getTopicCollection(collectionId) }
                    val page = async { api.getTopicCollectionTopics(collectionId, limit = 20) }
                    _state.update {
                        it.copy(
                            collection = collection.await(),
                            topics = page.await().items,
                            cursor = page.await().cursor,
                            endReached = !page.await().canLoadMore,
                            loading = false,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "无法打开这个合集")
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val cursor = snapshot.cursor
        if (cursor == null || snapshot.loadingMore || snapshot.endReached) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getTopicCollectionTopics(collectionId, limit = 20, cursor = cursor)
                _state.update {
                    it.copy(
                        topics = (it.topics + page.items).distinctBy { topic -> topic.id },
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
fun CollectionScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    viewModel: CollectionViewModel = viewModel(
        key = "collection-$collectionId",
        factory = viewModelFactory {
            initializer { CollectionViewModel(collectionId) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.collection?.name ?: "合集",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.collection == null -> LoadingState(Modifier.padding(padding))
            state.collection == null -> ErrorState(
                message = state.error ?: "无法打开这个合集",
                onRetry = viewModel::reload,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = wideContentPadding(920.dp),
                    end = wideContentPadding(920.dp),
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            state.collection?.name.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (!state.collection?.description.isNullOrBlank()) {
                            Text(
                                state.collection?.description.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${state.topics.size} 篇帖子",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.topics.isEmpty()) {
                    item {
                        EmptyState(
                            title = "合集里还没有帖子",
                            description = "作者整理后会出现在这里。",
                            modifier = Modifier.fillParentMaxHeight(0.75f),
                        )
                    }
                } else {
                    items(state.topics, key = { it.id }) { topic ->
                        TopicCard(
                            topic = topic,
                            onClick = { onOpenTopic(topic) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item {
                    androidx.compose.runtime.LaunchedEffect(state.cursor, state.endReached) {
                        if (!state.endReached) viewModel.loadMore()
                    }
                    PagedFooter(loading = state.loadingMore, endReached = state.endReached)
                }
            }
        }
    }
}
