package dao3.pickcat.feature.work

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.network.WorkComment
import dao3.pickcat.core.ui.AuthorRow
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PickCatImage
import dao3.pickcat.core.ui.PickCatTextField
import dao3.pickcat.core.ui.RichContent
import dao3.pickcat.core.ui.ThreadedReply
import dao3.pickcat.core.ui.WorkCard
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

data class WorkDetailUiState(
    val work: Work? = null,
    val comments: List<WorkComment> = emptyList(),
    val stats: dao3.pickcat.core.network.WorkStats? = null,
    val authorWorks: List<Work> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val likeCount: Int = 0,
    val favoriteCount: Int = 0,
    val submitting: Boolean = false,
    val commentLikes: Set<Int> = emptySet(),
    val commentLikeCounts: Map<Int, Int> = emptyMap(),
)

data class ThreadedWorkComment(
    val comment: WorkComment,
    val depth: Int,
)

private fun collectWorkComments(comments: List<WorkComment>): List<WorkComment> {
    val result = mutableListOf<WorkComment>()
    val seen = mutableSetOf<Int>()
    fun visit(children: List<WorkComment>) {
        for (comment in children) {
            if (!seen.add(comment.commentNumber)) continue
            result += comment
            visit(comment.children)
        }
    }
    visit(comments)
    return result
}

private fun buildThreadedWorkComments(comments: List<WorkComment>): List<ThreadedWorkComment> {
    if (comments.any { it.children.isNotEmpty() }) {
        val result = mutableListOf<ThreadedWorkComment>()
        val seen = mutableSetOf<Int>()
        fun visit(children: List<WorkComment>, depth: Int) {
            for (comment in children) {
                if (!seen.add(comment.commentNumber)) continue
                result += ThreadedWorkComment(comment, depth.coerceAtMost(3))
                visit(comment.children, depth + 1)
            }
        }
        visit(comments, 0)
        return result
    }

    val depthByNumber = mutableMapOf<Int, Int>()
    return comments.map { comment ->
        val depth = comment.replyToCommentNumber
            ?.let { parent -> (depthByNumber[parent]?.plus(1) ?: 0).coerceAtMost(3) }
            ?: 0
        depthByNumber[comment.commentNumber] = depth
        ThreadedWorkComment(comment, depth)
    }
}

class WorkDetailViewModel(private val workId: String) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(WorkDetailUiState())
    val state: StateFlow<WorkDetailUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.work == null, error = null) }
            try {
                val work = api.getWork(workId)
                val stats = runCatching { api.getWorkStats(workId) }.getOrNull()
                val authorWorks = runCatching { api.getUserWorks(work.author.id, limit = 4).items }
                    .getOrDefault(emptyList())
                val comments = runCatching { api.getWorkComments(workId, limit = 30).items }
                    .getOrDefault(emptyList())
                val allComments = collectWorkComments(comments)
                val likedComments = allComments.filter { it.viewerState?.liked == true }
                    .map { it.commentNumber }.toSet()
                _state.update {
                    it.copy(
                        work = work,
                        comments = comments,
                        stats = stats,
                        authorWorks = authorWorks,
                        loading = false,
                        error = null,
                        liked = work.viewerState?.liked == true,
                        favorited = work.viewerState?.favorited == true,
                        likeCount = work.likeCount,
                        favoriteCount = work.favoriteCount,
                        commentLikes = likedComments,
                        commentLikeCounts = allComments.associate { comment -> comment.commentNumber to comment.likeCount },
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "无法打开这个作品")
                }
            }
        }
    }

    fun toggleLike() {
        val snapshot = _state.value
        val next = !snapshot.liked
        _state.update {
            it.copy(
                liked = next,
                likeCount = (it.likeCount + if (next) 1 else -1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            runCatching { api.setWorkLike(workId, next) }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            liked = snapshot.liked,
                            likeCount = snapshot.likeCount,
                        )
                    }
                    _messages.tryEmit(it.message ?: "点赞失败")
                }
        }
    }

    fun toggleFavorite() {
        val snapshot = _state.value
        val next = !snapshot.favorited
        _state.update {
            it.copy(
                favorited = next,
                favoriteCount = (it.favoriteCount + if (next) 1 else -1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            runCatching { api.setWorkFavorite(workId, next) }
                .onSuccess { _messages.tryEmit(if (next) "已收藏" else "已取消收藏") }
                .onFailure {
                    _state.update { current ->
                        current.copy(favorited = snapshot.favorited, favoriteCount = snapshot.favoriteCount)
                    }
                    _messages.tryEmit(it.message ?: "收藏失败")
                }
        }
    }

    fun submitComment(text: String, replyTo: Int?, onSuccess: () -> Unit) {
        val markdown = text.trim()
        if (markdown.isEmpty() || _state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            try {
                api.createWorkComment(workId, markdown, replyTo)
                _state.update { it.copy(submitting = false) }
                _messages.tryEmit("评论已提交审核，通过后展示")
                onSuccess()
                reload()
            } catch (error: Exception) {
                _state.update { it.copy(submitting = false) }
                _messages.tryEmit(error.message ?: "评论失败")
            }
        }
    }

    fun toggleCommentLike(comment: WorkComment) {
        val snapshot = _state.value
        val liked = comment.commentNumber in snapshot.commentLikes
        val next = !liked
        val nextCount = ((snapshot.commentLikeCounts[comment.commentNumber] ?: comment.likeCount) +
            if (next) 1 else -1).coerceAtLeast(0)
        _state.update {
            it.copy(
                commentLikes = if (next) it.commentLikes + comment.commentNumber
                else it.commentLikes - comment.commentNumber,
                commentLikeCounts = it.commentLikeCounts + (comment.commentNumber to nextCount),
            )
        }
        viewModelScope.launch {
            runCatching { api.likeWorkComment(workId, comment.commentNumber, next) }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            commentLikes = if (liked) current.commentLikes + comment.commentNumber
                            else current.commentLikes - comment.commentNumber,
                            commentLikeCounts = current.commentLikeCounts +
                                (comment.commentNumber to comment.likeCount),
                        )
                    }
                    _messages.tryEmit(it.message ?: "操作失败")
                }
        }
    }

    fun deleteComment(comment: WorkComment) {
        viewModelScope.launch {
            runCatching { api.deleteWorkComment(workId, comment.commentNumber) }
                .onSuccess {
                    _messages.tryEmit("评论已删除")
                    reload()
                }
                .onFailure { _messages.tryEmit(it.message ?: "删除失败") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkDetailScreen(
    workId: String,
    onBack: () -> Unit,
    onOpenAuthor: (UserRef) -> Unit,
    onOpenWork: (Work) -> Unit = {},
    embedded: Boolean = false,
) {
    val viewModel: WorkDetailViewModel = viewModel(
        key = "work-$workId",
        factory = viewModelFactory {
            initializer { WorkDetailViewModel(workId) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val threadedComments = remember(state.comments) { buildThreadedWorkComments(state.comments) }
    val snackbarHostState = remember { SnackbarHostState() }
    var commentText by rememberSaveable(workId) { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Int?>(null) }
    val sessionState by AppGraph.container.session.state.collectAsStateWithLifecycle()
    val currentUserId = sessionState.user?.id

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.work?.workName ?: "作品",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (!embedded) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading && state.work == null -> LoadingState(Modifier.padding(padding))
            state.work == null -> ErrorState(
                message = state.error ?: "无法打开这个作品",
                onRetry = viewModel::reload,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val work = state.work!!
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().fillMaxHeight(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    item {
                        CodemaoPlayer(
                            work = work,
                            title = work.workName,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                work.workName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            AuthorRow(
                                user = work.author,
                                time = work.createdAt,
                                modifier = Modifier.clickable { onOpenAuthor(work.author) },
                                trailing = {
                                    Text(
                                        work.type,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val stats = state.stats
                                WorkStat(Icons.Default.Visibility, compactNumber(stats?.viewCount ?: work.viewCount))
                                WorkStat(Icons.Default.MonetizationOn, compactNumber(stats?.coinCount ?: work.coinCount))
                                WorkStat(Icons.Default.FavoriteBorder, compactNumber(stats?.likeCount ?: state.likeCount))
                                WorkStat(Icons.Default.Star, compactNumber(stats?.favoriteCount ?: state.favoriteCount))
                                WorkStat(Icons.Default.ChatBubbleOutline, compactNumber(stats?.commentCount ?: work.commentCount))
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (state.liked) {
                                    FilledTonalButton(
                                        onClick = viewModel::toggleLike,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        AnimatedContent(
                                            targetState = state.liked,
                                            transitionSpec = {
                                                fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                                            },
                                            label = "work-like-icon",
                                        ) { liked ->
                                            Icon(
                                                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("已点赞")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::toggleLike,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("点赞")
                                    }
                                }
                                if (state.favorited) {
                                    FilledTonalButton(
                                        onClick = viewModel::toggleFavorite,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        AnimatedContent(
                                            targetState = state.favorited,
                                            transitionSpec = {
                                                fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                                            },
                                            label = "work-favorite-icon",
                                        ) { favorited ->
                                            Icon(
                                                if (favorited) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("已收藏")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::toggleFavorite,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Default.StarBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("收藏")
                                    }
                                }
                            }
                        }
                    }
                    if (!work.description.isNullOrBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                ExpandableText(
                                    text = work.description.orEmpty(),
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    if (!work.operation.isNullOrBlank()) {
                        item {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ExpandableText(
                                    text = work.operation.orEmpty(),
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    if (state.authorWorks.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    "作者的其他作品",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(state.authorWorks, key = { it.id }) { otherWork ->
                                        Box(Modifier.width(210.dp)) {
                                            WorkCard(
                                                work = otherWork,
                                                onClick = { onOpenWork(otherWork) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "评论",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                compactNumber(work.commentCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (replyTo != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "回复 #$replyTo",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { replyTo = null }) { Text("取消") }
                                    }
                                }
                                PickCatTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "写评论，支持 Markdown…",
                                    minLines = 2,
                                    maxLines = 6,
                                )
                                Row {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        enabled = commentText.isNotBlank() && !state.submitting,
                                        onClick = {
                                            viewModel.submitComment(commentText, replyTo) {
                                                commentText = ""
                                                replyTo = null
                                            }
                                        },
                                    ) {
                                        if (state.submitting) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("发表评论")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (threadedComments.isEmpty()) {
                        item {
                            EmptyState(title = "还没有评论", description = "来说点什么吧。")
                        }
                    } else {
                        items(threadedComments, key = { it.comment.commentNumber }) { item ->
                            val comment = item.comment
                            ThreadedReply(depth = item.depth) {
                                WorkCommentCard(
                                    comment = comment,
                                    liked = comment.commentNumber in state.commentLikes,
                                    likeCount = state.commentLikeCounts[comment.commentNumber] ?: comment.likeCount,
                                    canDelete = currentUserId != null && comment.author.id == currentUserId,
                                    onLike = { viewModel.toggleCommentLike(comment) },
                                    onReply = { replyTo = comment.commentNumber },
                                    onDelete = { viewModel.deleteComment(comment) },
                                    onOpenAuthor = { onOpenAuthor(comment.author) },
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

@Composable
private fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 6,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) hasOverflow = result.hasVisualOverflow
            },
        )
        if (hasOverflow || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (expanded) "收起" else "展开")
            }
        }
    }
}

@Composable
private fun WorkStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkCommentCard(
    comment: WorkComment,
    liked: Boolean,
    likeCount: Int,
    canDelete: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onOpenAuthor: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthorRow(
                user = comment.author,
                time = comment.createdAt,
                modifier = Modifier.clickable { onOpenAuthor() },
                trailing = {
                    Text(
                        "#${comment.commentNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (comment.replyToCommentNumber != null) {
                Text(
                    "回复 #${comment.replyToCommentNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (comment.deleted) {
                Text(
                    "该评论已删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RichContent(html = comment.displayHtml)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onLike) {
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
                TextButton(onClick = onReply) { Text("回复") }
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}
