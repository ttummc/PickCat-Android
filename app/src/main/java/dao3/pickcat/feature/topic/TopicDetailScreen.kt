package dao3.pickcat.feature.topic

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.ApiException
import dao3.pickcat.core.network.Post
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.core.network.TopicDetail
import dao3.pickcat.core.ui.AuthorRow
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PostContent
import dao3.pickcat.core.ui.PickCatTextField
import dao3.pickcat.core.ui.TagCloud
import dao3.pickcat.core.ui.ThreadedReply
import dao3.pickcat.core.ui.compactNumber
import dao3.pickcat.core.ui.relativeTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopicDetailUiState(
    val topic: TopicDetail? = null,
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val likedPosts: Set<String> = emptySet(),
    val postLikeCounts: Map<String, Int> = emptyMap(),
    val bookmarkId: String? = null,
    val bookmarkCount: Int = 0,
    val submitting: Boolean = false,
    val loadingMore: Boolean = false,
    val postsCursor: String? = null,
    val postsEndReached: Boolean = false,
    val postsSort: String = "hot",
)

data class ThreadedPostItem(
    val post: Post,
    val depth: Int,
)

private fun collectReplyPosts(posts: List<Post>): List<Post> {
    val result = mutableListOf<Post>()
    val seen = mutableSetOf<String>()
    fun visit(children: List<Post>) {
        for (post in children) {
            if (!seen.add(post.id)) continue
            result += post
            visit(post.children)
        }
    }
    visit(posts)
    return result
}

private fun buildThreadedPosts(posts: List<Post>): List<ThreadedPostItem> {
    if (posts.any { it.children.isNotEmpty() }) {
        val result = mutableListOf<ThreadedPostItem>()
        val seen = mutableSetOf<String>()
        fun visit(children: List<Post>, depth: Int) {
            for (post in children) {
                if (!seen.add(post.id)) continue
                result += ThreadedPostItem(post, depth.coerceAtMost(3))
                visit(post.children, depth + 1)
            }
        }
        visit(posts, 0)
        return result
    }

    // Some endpoints return replies as a flat list. Derive a conservative depth
    // from replyToPostNumber while preserving the server ordering.
    val depthByNumber = mutableMapOf<Int, Int>()
    return posts.map { post ->
        val depth = post.replyToPostNumber
            ?.let { parent -> (depthByNumber[parent]?.plus(1) ?: 0).coerceAtMost(3) }
            ?: 0
        depthByNumber[post.postNumber] = depth
        ThreadedPostItem(post, depth)
    }
}

class TopicDetailViewModel(private val topicId: String) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(TopicDetailUiState())
    val state: StateFlow<TopicDetailUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.topic == null, error = null) }
            try {
                val detail = api.getTopic(topicId)
                val page = api.getTopicPosts(
                    topicId,
                    limit = 50,
                    sort = _state.value.postsSort,
                )
                val liked = mutableSetOf<String>()
                val counts = mutableMapOf<String, Int>()
                detail.firstPost?.let {
                    if (it.viewerState?.liked == true) liked += it.id
                    counts[it.id] = it.likeCount
                }
                page.items.forEach {
                    if (it.viewerState?.liked == true) liked += it.id
                    counts[it.id] = it.likeCount
                }
                collectReplyPosts(page.items).forEach {
                    if (it.viewerState?.liked == true) liked += it.id
                    counts[it.id] = it.likeCount
                }
                _state.update {
                    it.copy(
                        topic = detail,
                        posts = page.items,
                        loading = false,
                        error = null,
                        likedPosts = liked,
                        postLikeCounts = counts,
                        bookmarkId = detail.viewerState?.bookmarkId,
                        bookmarkCount = detail.bookmarkCount,
                        postsCursor = page.cursor,
                        postsEndReached = !page.canLoadMore,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "无法打开这个帖子")
                }
            }
        }
    }

    fun loadMorePosts() {        val snapshot = _state.value
        val cursor = snapshot.postsCursor
        if (snapshot.loadingMore || snapshot.postsEndReached || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getTopicPosts(
                    topicId,
                    limit = 50,
                    sort = snapshot.postsSort,
                    cursor = cursor,
                )
                _state.update {
                    it.copy(
                        posts = (it.posts + page.items).distinctBy { post -> post.id },
                        postsCursor = page.cursor,
                        postsEndReached = !page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(loadingMore = false) }
                _messages.tryEmit(error.message ?: "加载回复失败")
            }
        }
    }

    fun togglePostLike(post: Post) {
        val snapshot = _state.value
        val liked = post.id in snapshot.likedPosts
        val nextLiked = !liked
        val nextCount = ((snapshot.postLikeCounts[post.id] ?: post.likeCount) + if (nextLiked) 1 else -1)
            .coerceAtLeast(0)
        _state.update {
            it.copy(
                likedPosts = if (nextLiked) it.likedPosts + post.id else it.likedPosts - post.id,
                postLikeCounts = it.postLikeCounts + (post.id to nextCount),
            )
        }
        viewModelScope.launch {
            runCatching { api.likePost(post.id, nextLiked) }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            likedPosts = if (liked) current.likedPosts + post.id else current.likedPosts - post.id,
                            postLikeCounts = current.postLikeCounts + (post.id to post.likeCount),
                        )
                    }
                    _messages.tryEmit(it.message ?: "点赞失败")
                }
        }
    }

    fun toggleBookmark() {
        val snapshot = _state.value
        val currentId = snapshot.bookmarkId
        viewModelScope.launch {
            try {
                if (currentId != null) {
                    api.deleteBookmark(currentId)
                    _state.update { it.copy(bookmarkId = null, bookmarkCount = (it.bookmarkCount - 1).coerceAtLeast(0)) }
                    _messages.tryEmit("已取消收藏")
                } else {
                    val created = api.createBookmark("TOPIC", topicId)
                    _state.update { it.copy(bookmarkId = created.ifBlank { "created" }, bookmarkCount = it.bookmarkCount + 1) }
                    _messages.tryEmit("已收藏")
                }
            } catch (error: Exception) {
                _messages.tryEmit(error.message ?: "收藏失败")
            }
        }
    }

    fun setPostsSort(sort: String) {
        if (_state.value.postsSort == sort) return
        _state.update {
            it.copy(
                postsSort = sort,
                posts = emptyList(),
                postsCursor = null,
                postsEndReached = false,
            )
        }
        reload()
    }

    fun submitReply(text: String, replyTo: Post?, onSuccess: () -> Unit) {
        val markdown = text.trim()
        if (markdown.isEmpty() || _state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            try {
                api.createReply(topicId, markdown, replyTo?.postNumber)
                _state.update { it.copy(submitting = false) }
                _messages.tryEmit("回复已提交审核，通过后展示")
                onSuccess()
                reload()
            } catch (error: ApiException) {
                _state.update { it.copy(submitting = false) }
                val message = when {
                    error.code == "PARTICIPATION_LEVEL_REQUIRED" ->
                        "社区等级达到 Lv.1 才能回复（需要通过入站考试）"
                    error.status == 422 -> "内容未通过审核：${error.message}"
                    else -> error.message
                }
                _messages.tryEmit(message)
            } catch (error: Exception) {
                _state.update { it.copy(submitting = false) }
                _messages.tryEmit(error.message ?: "回复失败")
            }
        }
    }

    fun report(post: Post, reasonCode: String, detail: String) {
        viewModelScope.launch {
            runCatching { api.reportPost(post.id, reasonCode, detail.takeIf { it.isNotBlank() }) }
                .onSuccess { _messages.tryEmit("举报已提交") }
                .onFailure { _messages.tryEmit(it.message ?: "举报失败") }
        }
    }
}

private data class ReportReason(val code: String, val label: String)

private val REPORT_REASONS = listOf(
    ReportReason("SPAM", "垃圾广告"),
    ReportReason("HARASSMENT", "人身攻击 / 骚扰"),
    ReportReason("NSFW", "不适宜内容"),
    ReportReason("MISINFORMATION", "虚假信息"),
    ReportReason("COPYRIGHT", "侵犯著作权"),
    ReportReason("OTHER", "其他"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicDetailScreen(
    topicId: String,
    postNumber: Int?,
    onBack: () -> Unit,
    onOpenCategory: (Tag) -> Unit,
    onOpenUser: (UserRef) -> Unit,
) {
    val viewModel: TopicDetailViewModel = viewModel(
        key = "topic-$topicId",
        factory = viewModelFactory {
            initializer { TopicDetailViewModel(topicId) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val threadedPosts = remember(state.posts) { buildThreadedPosts(state.posts) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    var replyText by rememberSaveable(topicId) { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Post?>(null) }
    val commentHeaderIndex = if (state.topic?.firstPost != null) 2 else 1
    val showComposer by remember(listState, commentHeaderIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val firstVisibleIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: -1
            replyTo != null ||
                layout.visibleItemsInfo.any { it.index == commentHeaderIndex } ||
                firstVisibleIndex > commentHeaderIndex
        }
    }
    var reportTarget by remember { mutableStateOf<Post?>(null) }
    var reportReason by remember { mutableStateOf("SPAM") }
    var reportDetail by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(postNumber, threadedPosts.size, state.topic?.firstPost?.id) {
        if (postNumber == null) return@LaunchedEffect
        val index = threadedPosts.indexOfFirst { it.post.postNumber == postNumber }
        if (index >= 0) {
            val headerCount = if (state.topic != null) 1 + (if (state.topic?.firstPost != null) 1 else 0) + 1 else 0
            listState.animateScrollToItem(headerCount + index)
        }
    }
    LaunchedEffect(replyTo) {
        if (replyTo != null) composerFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.topic?.title ?: "帖子",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.topic != null) {
                        IconButton(
                            onClick = {
                                val title = state.topic?.title.orEmpty()
                                val url = "https://pickcat.dao3.fun/forum/post/$topicId"
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        if (title.isBlank()) url else "$title\n$url",
                                    )
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                }
                                context.startActivity(
                                    Intent.createChooser(share, "分享帖子"),
                                )
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "分享帖子")
                        }
                        IconButton(onClick = viewModel::toggleBookmark) {
                            AnimatedContent(
                                targetState = state.bookmarkId != null,
                                transitionSpec = {
                                    fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                                },
                                label = "topic-bookmark-icon",
                            ) { bookmarked ->
                                Icon(
                                    if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "收藏",
                                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
            state.loading && state.topic == null -> LoadingState(Modifier.padding(padding))
            state.topic == null -> ErrorState(
                message = state.error ?: "无法打开这个帖子",
                onRetry = viewModel::reload,
                modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(
                modifier = Modifier.fillMaxSize().padding(padding),
                isRefreshing = state.loading,
                onRefresh = viewModel::reload,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.topic?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!state.topic?.tags.isNullOrEmpty()) {
                            TagCloud(state.topic?.tags.orEmpty(), onTagClick = onOpenCategory)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MetaText("浏览 ${compactNumber(state.topic?.viewCount)}")
                            MetaText("回复 ${compactNumber(state.topic?.replyCount)}")
                            MetaText(
                                "点赞 ${compactNumber(state.topic?.firstPost?.likeCount ?: state.topic?.likeCount)}",
                            )
                        }
                        Text(
                            relativeTime(state.topic?.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.topic?.firstPost?.let { post ->
                    item(key = "first-${post.id}") {
                        PostCard(
                            post = post,
                            liked = post.id in state.likedPosts,
                            likeCount = state.postLikeCounts[post.id] ?: post.likeCount,
                            onLike = { viewModel.togglePostLike(post) },
                            onReply = { replyTo = post },
                            onReport = { reportTarget = post },
                            onOpenUser = onOpenUser,
                        )
                    }
                }
                item {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "全部回复",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            compactNumber(threadedPosts.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val options = listOf("hot" to "热评", "latest" to "最新")
                            options.forEach { (value, label) ->
                                FilterChip(
                                    selected = state.postsSort == value,
                                    onClick = { viewModel.setPostsSort(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
                if (threadedPosts.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有回复",
                            description = "来抢第一个沙发。",
                            modifier = Modifier.fillParentMaxHeight(0.35f),
                        )
                    }
                }
                items(threadedPosts, key = { it.post.id }) { item ->
                        ThreadedReply(depth = item.depth) {
                        CommentRow(
                            post = item.post,
                            liked = item.post.id in state.likedPosts,
                            likeCount = state.postLikeCounts[item.post.id] ?: item.post.likeCount,
                            onLike = { viewModel.togglePostLike(item.post) },
                            onReply = { replyTo = item.post },
                            onReport = { reportTarget = item.post },
                            onOpenUser = onOpenUser,
                            showDivider = item.depth == 0,
                        )
                    }
                }
                item {
                    if (!state.postsEndReached) {
                        LaunchedEffect(state.postsCursor, state.postsEndReached) {
                            viewModel.loadMorePosts()
                        }
                    }
                    TextButton(
                        onClick = viewModel::loadMorePosts,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (!state.postsEndReached) {
                            Text("加载更多回复")
                        }
                    }
                }
            }
            if (state.topic != null && showComposer) {
                ReplyComposer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    replyText = replyText,
                    replyTo = replyTo,
                    submitting = state.submitting,
                    focusRequester = composerFocusRequester,
                    onTextChange = { replyText = it },
                    onCancelReply = { replyTo = null },
                    onSubmit = {
                        if (replyText.isNotBlank() && !state.submitting) {
                            viewModel.submitReply(replyText, replyTo) {
                                replyText = ""
                                replyTo = null
                            }
                        }
                    },
                )
            }
        }
    }
}
}
}

    val target = reportTarget
    if (target != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { reportTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "举报回复",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "#${target.postNumber}  ${target.author.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REPORT_REASONS.forEach { reason ->
                        FilterChip(
                            selected = reportReason == reason.code,
                            onClick = { reportReason = reason.code },
                            label = { Text(reason.label) },
                        )
                    }
                }
                PickCatTextField(
                    value = reportDetail,
                    onValueChange = { reportDetail = it },
                    label = "补充说明（可选）",
                    minLines = 2,
                    maxLines = 4,
                )
                Button(
                    onClick = {
                        viewModel.report(target, reportReason, reportDetail)
                        reportTarget = null
                        reportDetail = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("提交举报")
                }
            }
        }
    }
}

@Composable
private fun ReplyComposer(
    replyText: String,
    replyTo: Post?,
    submitting: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickCatTextField(
                    value = replyText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = if (replyTo == null) {
                        "写回复"
                    } else {
                        "回复"
                    },
                    singleLine = true,
                    minLines = 1,
                    maxLines = 1,
                    enabled = !submitting,
                    trailingIcon = if (replyTo != null) {
                        {
                            IconButton(onClick = onCancelReply) {
                                Icon(Icons.Default.Close, contentDescription = "取消回复")
                            }
                        }
                    } else {
                        null
                    },
                )
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = replyText.isNotBlank() && !submitting,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送回复",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    post: Post,
    liked: Boolean,
    likeCount: Int,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onReport: () -> Unit,
    onOpenUser: (UserRef) -> Unit,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AuthorRow(
            user = post.author,
            time = post.createdAt,
            modifier = Modifier.clickable { onOpenUser(post.author) },
            trailing = {
                Text(
                    "#${post.postNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (post.deleted) {
            Text(
                "该楼层已删除",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PostContent(post)
        }
        if (post.replyToPostNumber != null) {
            Text(
                "回复 #${post.replyToPostNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(
                onClick = onLike,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                AnimatedContent(
                    targetState = liked,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                    },
                    label = "comment-like-icon",
                ) { active ->
                    Icon(
                        if (active) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(compactNumber(likeCount))
            }
            TextButton(
                onClick = onReply,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("回复")
            }
            TextButton(
                onClick = onReport,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("举报")
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    liked: Boolean,
    likeCount: Int,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onReport: () -> Unit,
    onOpenUser: (UserRef) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AuthorRow(
            user = post.author,
            time = post.createdAt,
            modifier = Modifier.clickable { onOpenUser(post.author) },
            trailing = {
                Text(
                    "#${post.postNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (post.deleted) {
            Text(
                "该楼层已删除",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PostContent(post)
        }
        if (post.replyToPostNumber != null) {
            Text(
                "回复 #${post.replyToPostNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onLike) {
                AnimatedContent(
                    targetState = liked,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                    },
                    label = "post-like-icon",
                ) { active ->
                    Icon(
                        if (active) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(compactNumber(likeCount))
            }
            TextButton(onClick = onReply) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("回复")
            }
            TextButton(onClick = onReport) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("举报")
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
