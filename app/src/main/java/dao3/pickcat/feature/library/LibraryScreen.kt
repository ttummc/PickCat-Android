package dao3.pickcat.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.WorkCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    WORKS("我的作品"),
    TOPICS("我的帖子"),
}

data class LibraryUiState(
    val works: List<Work> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val tab: LibraryTab = LibraryTab.WORKS,
    val loading: Boolean = true,
    val error: String? = null,
)

class LibraryViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun load() {
        val userId = AppGraph.container.session.state.value.user?.id
        if (userId.isNullOrBlank()) {
            _state.update { it.copy(loading = false, error = "请先登录") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                coroutineScope {
                    val works = async { api.getUserWorks(userId, 40) }
                    val topics = async { api.getUserTopics(userId, 40) }
                    val worksPage = works.await()
                    val topicsPage = topics.await()
                    _state.update {
                        it.copy(
                            works = worksPage.items,
                            topics = topicsPage.items,
                            loading = false,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "加载失败") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenWork: (Work) -> Unit,
    onOpenTopic: (Topic) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("个人作品库", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "你发布过的作品和帖子都在这里",
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
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size),
                    ) { Text(tab.label) }
                }
            }
            when {
                state.loading -> LoadingState()
                state.error != null -> EmptyState(
                    title = "无法加载作品库",
                    description = state.error,
                    icon = Icons.Default.Collections,
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )
                state.tab == LibraryTab.WORKS -> {
                    if (state.works.isEmpty()) {
                        EmptyState(
                            title = "还没有作品",
                            description = "去 KittenN 或 BoxPlay 做一个吧。",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    state.works.chunked(2).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEach { work ->
                                                WorkCard(
                                                    work = work,
                                                    onClick = { onOpenWork(work) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (state.topics.isEmpty()) {
                        EmptyState(title = "还没有发过帖子")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.topics, key = { it.id }) { topic ->
                                TopicCard(topic, onClick = { onOpenTopic(topic) })
                            }
                        }
                    }
                }
            }
        }
    }
}
