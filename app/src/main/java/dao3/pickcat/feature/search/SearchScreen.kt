package dao3.pickcat.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.UserProfile
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.SectionHeader
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.UserRow
import dao3.pickcat.core.ui.WorkCard
import dao3.pickcat.core.ui.wideContentPadding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    ALL("全部"),
    TOPICS("帖子"),
    WORKS("作品"),
    USERS("用户"),
    TAGS("标签"),
}

data class SearchUiState(
    val query: String = "",
    val topics: List<Topic> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val works: List<Work> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val tab: SearchTab = SearchTab.ALL,
)

private data class SearchBundle(
    val topics: Result<List<Topic>>,
    val users: Result<List<UserProfile>>,
    val works: Result<List<Work>>,
    val tags: Result<List<Tag>>,
)

class SearchViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun setTab(tab: SearchTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(query = query, loading = true, searched = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                val topics = async { runCatching { api.searchTopics(query, 20).items } }
                val users = async { runCatching { api.searchUsers(query, 20).items } }
                val works = async { runCatching { api.searchWorks(query, 20).items } }
                val tags = async {
                    runCatching {
                        api.getTags().items.filter { tag ->
                            tag.name.contains(query, ignoreCase = true) ||
                                tag.slug.contains(query, ignoreCase = true)
                        }
                    }
                }
                SearchBundle(topics.await(), users.await(), works.await(), tags.await())
            }
            val topics = results.topics
            val users = results.users
            val works = results.works
            val tags = results.tags
            val anySuccess = topics.isSuccess || users.isSuccess || works.isSuccess || tags.isSuccess
            _state.update {
                it.copy(
                    topics = topics.getOrDefault(emptyList()),
                    users = users.getOrDefault(emptyList()),
                    works = works.getOrDefault(emptyList()),
                    tags = tags.getOrDefault(emptyList()),
                    loading = false,
                    error = if (anySuccess) null else topics.exceptionOrNull()?.message ?: "搜索失败",
                )
            }
        }
    }
}

private enum class SearchContentPhase { LOADING, ERROR, IDLE, EMPTY, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOpenUser: (UserProfile) -> Unit,
    onOpenWork: (Work) -> Unit,
    onOpenTag: (Tag) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by appViewModel.searchHistory.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (query.isBlank()) return
        appViewModel.recordSearch(query)
        viewModel.search(query)
        searchExpanded = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                inputField = {
                    androidx.compose.material3.SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { submit() },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        placeholder = { Text("搜索帖子、作品或用户…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空")
                                }
                            }
                        },
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {}
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val phase = when {
                state.loading -> SearchContentPhase.LOADING
                state.error != null -> SearchContentPhase.ERROR
                !state.searched -> SearchContentPhase.IDLE
                state.topics.isEmpty() && state.users.isEmpty() && state.works.isEmpty() &&
                    state.tags.isEmpty() -> SearchContentPhase.EMPTY
                else -> SearchContentPhase.RESULTS
            }
            AnimatedContent(
                targetState = phase to state.tab,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                },
                label = "search-content",
            ) { (targetPhase, targetTab) ->
                when (targetPhase) {
                    SearchContentPhase.LOADING -> LoadingState()
                    SearchContentPhase.ERROR -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.search(query) },
                    )
                    SearchContentPhase.IDLE -> {
                        if (history.isEmpty()) {
                            EmptyState(
                                title = "开始搜索",
                                description = "输入关键词开始搜索。",
                                icon = Icons.Default.Search,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "最近搜索",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    FilledTonalIconButton(onClick = appViewModel::clearSearchHistory) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "清除搜索历史",
                                        )
                                    }
                                }
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    history.forEach { item ->
                                        AssistChip(
                                            onClick = {
                                                query = item
                                                appViewModel.recordSearch(item)
                                                viewModel.search(item)
                                            },
                                            label = { Text(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SearchContentPhase.EMPTY -> EmptyState(
                        title = "没有找到相关内容",
                        description = "换一个关键词。",
                        icon = Icons.Default.Search,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SearchContentPhase.RESULTS -> Column(Modifier.fillMaxSize()) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = targetTab.ordinal,
                            edgePadding = 16.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            SearchTab.entries.forEach { tab ->
                                Tab(
                                    selected = targetTab == tab,
                                    onClick = { viewModel.setTab(tab) },
                                    text = {
                                        Text(
                                            tab.label,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    },
                                )
                            }
                        }
                        SearchResults(
                            state = state,
                            onOpenTopic = onOpenTopic,
                            onOpenUser = onOpenUser,
                            onOpenWork = onOpenWork,
                            onOpenTag = onOpenTag,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onOpenTopic: (Topic) -> Unit,
    onOpenUser: (UserProfile) -> Unit,
    onOpenWork: (Work) -> Unit,
    onOpenTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = wideContentPadding(840.dp),
            end = wideContentPadding(840.dp),
            bottom = 24.dp,
            top = 4.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.tab) {
            SearchTab.ALL -> {
                if (state.topics.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "帖子",
                            action = { SearchCount("${state.topics.size} 条") },
                        )
                    }
                    items(state.topics.take(3), key = { "t-${it.id}" }) { topic ->
                        TopicCard(
                            topic,
                            onClick = { onOpenTopic(topic) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.works.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "作品",
                            action = { SearchCount("${state.works.size} 个") },
                        )
                    }
                    item {
                        SimpleWorkGrid(state.works.take(4), onOpenWork)
                    }
                }
                if (state.users.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "用户",
                            action = { SearchCount("${state.users.size} 位") },
                        )
                    }
                    items(state.users.take(3), key = { "u-${it.id}" }) { user ->
                        UserRow(
                            user,
                            onClick = { onOpenUser(user) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.tags.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "标签",
                            action = { SearchCount("${state.tags.size} 个") },
                        )
                    }
                    items(state.tags.take(5), key = { "g-${it.id}" }) { tag ->
                        TagResultRow(
                            tag = tag,
                            onClick = { onOpenTag(tag) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            SearchTab.TOPICS -> {
                if (state.topics.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有找到帖子",
                            modifier = Modifier.fillParentMaxHeight(0.5f),
                        )
                    }
                } else {
                    items(state.topics, key = { it.id }) { topic ->
                        TopicCard(
                            topic,
                            onClick = { onOpenTopic(topic) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            SearchTab.USERS -> {
                if (state.users.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有找到用户",
                            modifier = Modifier.fillParentMaxHeight(0.5f),
                        )
                    }
                } else {
                    items(state.users, key = { it.id }) { user ->
                        UserRow(
                            user,
                            onClick = { onOpenUser(user) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            SearchTab.WORKS -> {
                if (state.works.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有找到作品",
                            modifier = Modifier.fillParentMaxHeight(0.5f),
                        )
                    }
                } else {
                    item { SimpleWorkGrid(state.works, onOpenWork) }
                }
            }
            SearchTab.TAGS -> {
                if (state.tags.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有找到标签",
                            modifier = Modifier.fillParentMaxHeight(0.5f),
                        )
                    }
                } else {
                    items(state.tags, key = { it.id }) { tag ->
                        TagResultRow(
                            tag = tag,
                            onClick = { onOpenTag(tag) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagResultRow(
    tag: Tag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                "#${tag.name}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchCount(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SimpleWorkGrid(works: List<Work>, onOpenWork: (Work) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        works.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { work ->
                    WorkCard(work = work, onClick = { onOpenWork(work) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
