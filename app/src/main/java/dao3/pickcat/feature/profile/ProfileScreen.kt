package dao3.pickcat.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.LocalTabReselect
import dao3.pickcat.core.network.BadgeGrant
import dao3.pickcat.core.network.LevelContribution
import dao3.pickcat.core.network.LevelContributions
import dao3.pickcat.core.network.Post
import dao3.pickcat.core.network.ProfileActivity
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.network.Topic
import dao3.pickcat.core.network.TopicCollection
import dao3.pickcat.core.network.UserProfile
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PickCatImage
import dao3.pickcat.core.ui.SectionHeader
import dao3.pickcat.core.ui.TopicCard
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.UserRow
import dao3.pickcat.core.ui.WorkCard
import dao3.pickcat.core.ui.avatarUrl
import dao3.pickcat.core.ui.compactNumber
import dao3.pickcat.core.ui.plainTextFromHtml
import dao3.pickcat.core.ui.relativeTime
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ProfileTab(val label: String) {
    HOME("主页"),
    COLLECTIONS("合集"),
    TOPICS("帖子"),
    WORKS("作品"),
    REPLIES("回复"),
    RELATION("关系"),
    BADGES("徽章"),
}

data class ProfileUiState(
    val profile: UserProfile? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val following: Boolean = false,
    val followBusy: Boolean = false,
    val tab: ProfileTab = ProfileTab.HOME,
    val tabLoading: Boolean = false,
    val tabError: String? = null,
    val topics: List<Topic> = emptyList(),
    val works: List<Work> = emptyList(),
    val replies: List<Post> = emptyList(),
    val collections: List<TopicCollection> = emptyList(),
    val followingUsers: List<UserProfile> = emptyList(),
    val followers: List<UserProfile> = emptyList(),
    val activities: List<ProfileActivity> = emptyList(),
    val contributions: List<LevelContribution> = emptyList(),
    val badges: List<BadgeGrant> = emptyList(),
    val featuredTopics: List<Topic> = emptyList(),
    val featuredWorks: List<Work> = emptyList(),
    val editorOpen: Boolean = false,
    val editorLoading: Boolean = false,
    val editorTopics: List<Topic> = emptyList(),
    val editorWorks: List<Work> = emptyList(),
    val selectedTopicIds: Set<String> = emptySet(),
    val selectedWorkIds: Set<String> = emptySet(),
    val editorSaving: Boolean = false,
)

class ProfileViewModel(private val userId: String) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                coroutineScope {
                    val profileDeferred = async { api.getUser(userId) }
                    val badgesDeferred = async {
                        runCatching { api.getUserBadges(userId).items }.getOrDefault(emptyList())
                    }
                    val featuredTopicsDeferred = async {
                        runCatching { api.getFeaturedTopics(userId).items }.getOrDefault(emptyList())
                    }
                    val featuredWorksDeferred = async {
                        runCatching { api.getFeaturedWorks(userId).items }.getOrDefault(emptyList())
                    }
                    val contributionsDeferred = async {
                        runCatching {
                            api.getLevelContributions(userId, LocalDate.now().year).days
                        }.getOrDefault(emptyList())
                    }
                    val profile = profileDeferred.await()
                    _state.update {
                        it.copy(
                            profile = profile,
                            following = profile.viewerState?.following == true,
                            loading = false,
                            error = null,
                            badges = badgesDeferred.await(),
                            featuredTopics = featuredTopicsDeferred.await(),
                            featuredWorks = featuredWorksDeferred.await(),
                            contributions = contributionsDeferred.await(),
                        )
                    }
                }
                loadTab(_state.value.tab)
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "加载失败")
                }
            }
        }
    }

    fun selectTab(tab: ProfileTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, tabError = null) }
        loadTab(tab)
    }

    private fun loadTab(tab: ProfileTab) {
        viewModelScope.launch {
            _state.update { it.copy(tabLoading = true, tabError = null) }
            try {
                when (tab) {
                    ProfileTab.HOME -> {
                        val page = api.getProfileActivities(userId, LocalDate.now().year, 20)
                        _state.update { it.copy(activities = page.items, tabLoading = false) }
                    }
                    ProfileTab.COLLECTIONS -> {
                        val page = api.getTopicCollections(userId, 20)
                        _state.update { it.copy(collections = page.items, tabLoading = false) }
                    }
                    ProfileTab.TOPICS -> {
                        val page = api.getUserTopics(userId, 20)
                        _state.update { it.copy(topics = page.items, tabLoading = false) }
                    }
                    ProfileTab.WORKS -> {
                        val page = api.getUserWorks(userId, 20)
                        _state.update { it.copy(works = page.items, tabLoading = false) }
                    }
                    ProfileTab.REPLIES -> {
                        val page = api.getUserPosts(userId, 20)
                        _state.update { it.copy(replies = page.items, tabLoading = false) }
                    }
                    ProfileTab.RELATION -> {
                        val following = api.getFollowing(userId, 50)
                        val followers = api.getFollowers(userId, 50)
                        _state.update {
                            it.copy(
                                followingUsers = following.items.mapNotNull { edge -> edge.user.takeIf { user -> user.id.isNotBlank() } },
                                followers = followers.items.mapNotNull { edge -> edge.user.takeIf { user -> user.id.isNotBlank() } },
                                tabLoading = false,
                            )
                        }
                    }
                    ProfileTab.BADGES -> {
                        val page = api.getUserBadges(userId, 50)
                        _state.update { it.copy(badges = page.items, tabLoading = false) }
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(tabLoading = false, tabError = error.message ?: "加载失败")
                }
            }
        }
    }

    fun toggleFollow() {
        val snapshot = _state.value
        if (snapshot.followBusy) return
        val next = !snapshot.following
        _state.update { it.copy(followBusy = true, following = next) }
        viewModelScope.launch {
            try {
                api.followUser(userId, next)
                _state.update { current ->
                    val profile = current.profile
                    current.copy(
                        followBusy = false,
                        profile = profile?.copy(
                            stats = profile.stats.copy(
                                followers = (profile.stats.followers + if (next) 1 else -1).coerceAtLeast(0),
                            ),
                        ),
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(followBusy = false, following = snapshot.following) }
                _messages.tryEmit(error.message ?: "关注失败")
            }
        }
    }

    fun openFeaturedEditor() {
        _state.update { it.copy(editorOpen = true, editorLoading = true) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val topics = async { api.getUserTopics(userId, 50).items }
                    val works = async { api.getUserWorks(userId, 50).items }
                    topics.await() to works.await()
                }
            }.onSuccess { (topics, works) ->
                _state.update {
                    it.copy(
                        editorLoading = false,
                        editorTopics = topics,
                        editorWorks = works,
                        selectedTopicIds = it.featuredTopics.map { topic -> topic.id }.toSet(),
                        selectedWorkIds = it.featuredWorks.map { work -> work.id }.toSet(),
                    )
                }
            }.onFailure {
                _state.update { it.copy(editorLoading = false, editorOpen = false) }
                _messages.tryEmit(it.message ?: "无法加载可精选内容")
            }
        }
    }

    fun closeFeaturedEditor() {
        _state.update { it.copy(editorOpen = false) }
    }

    fun toggleEditorTopic(id: String) {
        _state.update { state ->
            val next = if (id in state.selectedTopicIds) state.selectedTopicIds - id else state.selectedTopicIds + id
            if (next.size > 6) state else state.copy(selectedTopicIds = next)
        }
    }

    fun toggleEditorWork(id: String) {
        _state.update { state ->
            val next = if (id in state.selectedWorkIds) state.selectedWorkIds - id else state.selectedWorkIds + id
            if (next.size > 6) state else state.copy(selectedWorkIds = next)
        }
    }

    fun saveFeatured() {
        val snapshot = _state.value
        if (snapshot.editorSaving) return
        _state.update { it.copy(editorSaving = true) }
        viewModelScope.launch {
            try {
                api.setFeaturedTopics(userId, snapshot.selectedTopicIds.toList())
                api.setFeaturedWorks(userId, snapshot.selectedWorkIds.toList())
                _state.update {
                    it.copy(
                        editorSaving = false,
                        editorOpen = false,
                        featuredTopics = it.editorTopics.filter { topic -> topic.id in it.selectedTopicIds },
                        featuredWorks = it.editorWorks.filter { work -> work.id in it.selectedWorkIds },
                    )
                }
                _messages.tryEmit("精选已更新")
            } catch (error: Exception) {
                _state.update { it.copy(editorSaving = false) }
                _messages.tryEmit(error.message ?: "保存失败")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    isSelf: Boolean,
    showBack: Boolean = true,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOpenWork: (Work) -> Unit,
    onOpenUser: (UserProfile) -> Unit,
    onOpenActivity: (ProfileActivity) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCollection: (TopicCollection) -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel(
        key = "profile-$userId",
        factory = viewModelFactory { initializer { ProfileViewModel(userId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val tabReselect = LocalTabReselect.current
    var relationMode by remember { mutableStateOf(0) }

    LaunchedEffect(tabReselect) {
        tabReselect?.collect { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.username ?: "用户主页") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (isSelf) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("媒体库 / 个人图盘") },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenMedia()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("个人作品库") },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenLibrary()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("编辑精选") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.openFeaturedEditor()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading && state.profile == null -> LoadingState(Modifier.padding(padding))
            state.profile == null -> ErrorState(
                message = state.error ?: "找不到这个用户",
                onRetry = viewModel::loadProfile,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val isWide = LocalConfiguration.current.screenWidthDp >= 900
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(top = if (isWide) 12.dp else 0.dp),
                ) {
                    if (isWide) {
                        ProfileSidebar(
                            profile = state.profile!!,
                            isSelf = isSelf,
                            following = state.following,
                            followBusy = state.followBusy,
                            onFollow = viewModel::toggleFollow,
                            onSettings = onOpenSettings,
                            badges = state.badges,
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                        )
                        VerticalDivider()
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = if (isWide) {
                            PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                        } else {
                            PaddingValues(bottom = 24.dp)
                        },
                    ) {
                        if (!isWide) {
                            item {
                                ProfileHeader(
                                    profile = state.profile!!,
                                    isSelf = isSelf,
                                    following = state.following,
                                    followBusy = state.followBusy,
                                    onFollow = viewModel::toggleFollow,
                                    onSettings = onOpenSettings,
                                    onOpenMedia = onOpenMedia,
                                )
                            }
                        }
                item {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = state.tab.ordinal,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        ProfileTab.entries.forEach { tab ->
                            Tab(
                                selected = state.tab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            )
                        }
                    }
                }
                if (state.tabLoading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                val tabError = state.tabError
                if (tabError != null) {
                    item {
                        ErrorState(
                            message = tabError,
                            onRetry = { viewModel.selectTab(ProfileTab.HOME) },
                        )
                    }
                } else when (state.tab) {
                    ProfileTab.HOME -> {
                        if (state.featuredTopics.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    "精选帖子",
                                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).animateItem(),
                                )
                            }
                            items(state.featuredTopics, key = { "ft-${it.id}" }) { topic ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .animateItem(),
                                ) {
                                    TopicCard(topic, onClick = { onOpenTopic(topic) })
                                }
                            }
                        }
                        if (state.featuredWorks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    "精选作品",
                                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).animateItem(),
                                )
                            }
                            item {
                                WorkGrid(
                                    works = state.featuredWorks,
                                    onOpenWork = onOpenWork,
                                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                                )
                            }
                        }
                        item {
                            SectionHeader(
                                "贡献",
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).animateItem(),
                            )
                            ContributionHeatmap(
                                contributions = state.contributions,
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                            )
                        }
                        item {
                            SectionHeader(
                                "最近动态",
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).animateItem(),
                            )
                        }
                        if (state.activities.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有动态",
                                    description = "参与讨论后会出现在这里。",
                                    icon = Icons.Default.History,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            itemsIndexed(state.activities, key = { _, activity -> "activity-${activity.id}" }) { index, activity ->
                                Box(Modifier.animateItem()) {
                                    ActivityRow(
                                        activity = activity,
                                        isLast = index == state.activities.lastIndex,
                                        onClick = if (activity.topicId.isNotBlank()) {
                                            { onOpenActivity(activity) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    ProfileTab.COLLECTIONS -> {
                        if (state.collections.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有合集",
                                    description = "整理的帖子合集会出现在这里。",
                                    icon = Icons.Default.Article,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            items(state.collections, key = { it.id }) { collection ->
                                CollectionRow(
                                    collection = collection,
                                    onClick = { onOpenCollection(collection) },
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .animateItem(),
                                )
                            }
                        }
                    }
                    ProfileTab.TOPICS -> {
                        if (state.topics.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有发过帖子",
                                    icon = Icons.Default.Article,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            items(state.topics, key = { it.id }) { topic ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .animateItem(),
                                ) {
                                    TopicCard(topic, onClick = { onOpenTopic(topic) })
                                }
                            }
                        }
                    }
                    ProfileTab.WORKS -> {
                        if (state.works.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有发布作品",
                                    icon = Icons.Default.VideoLibrary,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            item {
                                WorkGrid(
                                    works = state.works,
                                    onOpenWork = onOpenWork,
                                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                                )
                            }
                        }
                    }
                    ProfileTab.REPLIES -> {
                        if (state.replies.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有回复过",
                                    icon = Icons.Default.ChatBubbleOutline,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            items(state.replies, key = { it.id }) { reply ->
                                Box(Modifier.animateItem()) {
                                    ReplyRow(reply)
                                }
                            }
                        }
                    }
                    ProfileTab.RELATION -> {
                        item {
                            SingleChoiceSegmentedButtonRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                SegmentedButton(
                                    selected = relationMode == 0,
                                    onClick = { relationMode = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text("关注") }
                                SegmentedButton(
                                    selected = relationMode == 1,
                                    onClick = { relationMode = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text("粉丝") }
                            }
                        }
                        val people = if (relationMode == 0) state.followingUsers else state.followers
                        if (people.isEmpty()) {
                            item {
                                EmptyState(
                                    title = if (relationMode == 0) "还没有关注任何人" else "还没有粉丝",
                                    icon = Icons.Default.People,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            items(people, key = { it.id }) { user ->
                                UserRow(
                                    user,
                                    onClick = { onOpenUser(user) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    ProfileTab.BADGES -> {
                        if (state.badges.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "还没有徽章",
                                    description = "参与社区活动后可以获得徽章。",
                                    icon = Icons.Default.Badge,
                                    modifier = Modifier.fillParentMaxHeight(0.75f).animateItem(),
                                )
                            }
                        } else {
                            items(state.badges, key = { it.badgeId }) { grant ->
                                BadgeRow(
                                    grant = grant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .animateItem(),
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

    if (state.editorOpen) {
        FeaturedEditorDialog(
            state = state,
            onDismiss = viewModel::closeFeaturedEditor,
            onToggleTopic = viewModel::toggleEditorTopic,
            onToggleWork = viewModel::toggleEditorWork,
            onSave = viewModel::saveFeatured,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSidebar(
    profile: UserProfile,
    isSelf: Boolean,
    following: Boolean,
    followBusy: Boolean,
    onFollow: () -> Unit,
    onSettings: () -> Unit,
    badges: List<BadgeGrant>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(156.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (!profile.backgroundFileId.isNullOrBlank()) {
                        PickCatImage(
                            url = "https://pickcat.dao3.fun/api/v1/files/${profile.backgroundFileId}",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            corner = 0.dp,
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
            UserAvatar(
                name = profile.username,
                url = avatarUrl(profile),
                size = 72.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp)
                    .offset(y = 84.dp)
                    .zIndex(1f)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        Text(
            profile.username,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "Lv.${profile.level.current}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            profile.displayedBadge?.name?.let { badge ->
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!profile.bio.isNullOrBlank()) {
            Text(
                profile.bio.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${relativeTime(profile.createdAt)}加入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!profile.region.isNullOrBlank()) {
                Text(
                    profile.region.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat(compactNumber(profile.stats.followers), "粉丝", Modifier.weight(1f))
                    StatDivider()
                    ProfileStat(compactNumber(profile.stats.following), "关注", Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat(compactNumber(profile.stats.topics), "帖子", Modifier.weight(1f))
                    StatDivider()
                    ProfileStat(compactNumber(profile.stats.replies), "回复", Modifier.weight(1f))
                }
            }
        }
        if (isSelf) {
            FilledTonalButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("编辑资料")
            }
        } else {
            Button(
                onClick = onFollow,
                enabled = !followBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (followBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (following) "已关注" else "关注")
                }
            }
        }
        if (badges.isNotEmpty()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Text("徽章", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                badges.take(6).forEach { grant ->
                    Column(
                        modifier = Modifier.width(64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!grant.badge?.image?.url.isNullOrBlank()) {
                            PickCatImage(
                                url = grant.badge?.image?.url,
                                contentDescription = grant.badge?.name,
                                modifier = Modifier.size(44.dp),
                                corner = 22.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text(
                            grant.badge?.name ?: grant.badgeId,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    isSelf: Boolean,
    following: Boolean,
    followBusy: Boolean,
    onFollow: () -> Unit,
    onSettings: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(156.dp)) {
            if (!profile.backgroundFileId.isNullOrBlank()) {
                PickCatImage(
                    url = "https://pickcat.dao3.fun/api/v1/files/${profile.backgroundFileId}",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    corner = 0.dp,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                UserAvatar(
                    name = profile.username,
                    url = avatarUrl(profile),
                    size = 88.dp,
                    modifier = Modifier
                        .offset(y = (-42).dp)
                        .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
                Spacer(Modifier.weight(1f))
                if (isSelf) {
                    FilledTonalButton(
                        onClick = onSettings,
                        modifier = Modifier.offset(y = (-10).dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("编辑资料")
                    }
                } else {
                    Button(
                        onClick = onFollow,
                        enabled = !followBusy,
                        modifier = Modifier.offset(y = (-10).dp),
                    ) {
                        if (followBusy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (following) "已关注" else "关注")
                        }
                    }
                }
            }
            Text(
                profile.username,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Lv.${profile.level.current}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                profile.displayedBadge?.name?.let { badge ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            if (!profile.bio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.bio.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${relativeTime(profile.createdAt)}加入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!profile.region.isNullOrBlank()) {
                    Text(
                        profile.region.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileStat(compactNumber(profile.stats.followers), "粉丝", Modifier.weight(1f))
                    StatDivider()
                    ProfileStat(compactNumber(profile.stats.following), "关注", Modifier.weight(1f))
                    StatDivider()
                    ProfileStat(compactNumber(profile.stats.topics), "帖子", Modifier.weight(1f))
                    StatDivider()
                    ProfileStat(compactNumber(profile.stats.replies), "回复", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        modifier = Modifier.height(24.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollectionRow(
    collection: TopicCollection,
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(collection.name, style = MaterialTheme.typography.titleSmall)
                if (!collection.description.isNullOrBlank()) {
                    Text(
                        collection.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                collection.updatedAt?.let { time ->
                    Text(
                        "更新于 " + relativeTime(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgeRow(
    grant: BadgeGrant,
    modifier: Modifier = Modifier,
) {
    val badge = grant.badge
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!badge?.image?.url.isNullOrBlank()) {
                PickCatImage(
                    url = badge?.image?.url,
                    contentDescription = badge?.name,
                    modifier = Modifier.size(48.dp),
                    corner = 24.dp,
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    badge?.name ?: grant.badgeId,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!badge?.description.isNullOrBlank()) {
                    Text(
                        badge?.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkGrid(
    works: List<Work>,
    onOpenWork: (Work) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        works.chunked(2).forEach { row ->
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

@Composable
private fun ActivityRow(
    activity: ProfileActivity,
    isLast: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val isLike = activity.kind.contains("LIKE")
    val isReply = activity.kind.contains("REPLY")
    val isPost = activity.kind.contains("POST") || activity.kind.contains("TOPIC")
    val isFollow = activity.kind.contains("FOLLOW")
    val icon = when {
        isLike -> Icons.Default.FavoriteBorder
        isReply -> Icons.Default.ChatBubbleOutline
        isPost -> Icons.Default.Article
        isFollow -> Icons.Default.PersonAdd
        else -> Icons.Default.Star
    }
    val container = when {
        isLike -> MaterialTheme.colorScheme.primaryContainer
        isReply -> MaterialTheme.colorScheme.tertiaryContainer
        isPost -> MaterialTheme.colorScheme.secondaryContainer
        isFollow -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        isLike -> MaterialTheme.colorScheme.onPrimaryContainer
        isReply -> MaterialTheme.colorScheme.onTertiaryContainer
        isPost -> MaterialTheme.colorScheme.onSecondaryContainer
        isFollow -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        isLike -> "点赞"
        isReply -> "回复"
        isPost -> "发布"
        isFollow -> "关注"
        else -> "动态"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = container) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(1.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                activity.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    relativeTime(activity.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开动态",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplyRow(reply: Post) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(
                    reply.topic?.title ?: "帖子",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    relativeTime(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                plainTextFromHtml(reply.cookedHtml).take(180),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContributionHeatmap(
    contributions: List<LevelContribution>,
    modifier: Modifier = Modifier,
) {
    val counts = remember(contributions) {
        contributions.mapNotNull { day ->
            val date = day.date?.take(10) ?: return@mapNotNull null
            date to day.total
        }.toMap()
    }
    val today = remember { LocalDate.now() }
    val start = remember(today) {
        var monday = today.minusWeeks(52)
        while (monday.dayOfWeek.value != 1) monday = monday.minusDays(1)
        monday
    }
    val weeks = remember(start, counts) {
        (0 until 53).map { week ->
            (0 until 7).map { day ->
                val date = start.plusWeeks(week.toLong()).plusDays(day.toLong())
                val count = counts[date.format(DateTimeFormatter.ISO_DATE)] ?: 0
                date to count
            }
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("近一年贡献", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { (_, count) ->
                            val alpha = when {
                                count <= 0 -> 0.1f
                                count < 2 -> 0.3f
                                count < 5 -> 0.5f
                                count < 10 -> 0.7f
                                else -> 1f
                            }
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (count <= 0) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                    ),
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(0.1f, 0.3f, 0.5f, 0.7f, 1f).forEach { alpha ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                    )
                }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("近一年", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FeaturedEditorDialog(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onToggleTopic: (String) -> Unit,
    onToggleWork: (String) -> Unit,
    onSave: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("编辑精选", style = MaterialTheme.typography.titleLarge)
                Text(
                    "最多选择 6 个帖子与 6 个作品。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.editorLoading) {
                    LoadingState()
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).height(460.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item { Text("精选帖子", style = MaterialTheme.typography.titleSmall) }
                        items(state.editorTopics, key = { "et-${it.id}" }) { topic ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = topic.id in state.selectedTopicIds,
                                    onCheckedChange = { onToggleTopic(topic.id) },
                                )
                                Text(
                                    topic.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("精选作品", style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.editorWorks, key = { "ew-${it.id}" }) { work ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = work.id in state.selectedWorkIds,
                                    onCheckedChange = { onToggleWork(work.id) },
                                )
                                Text(
                                    work.workName,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = onSave, enabled = !state.editorSaving && !state.editorLoading) {
                        if (state.editorSaving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}
