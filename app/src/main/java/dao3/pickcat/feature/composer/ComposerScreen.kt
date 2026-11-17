package dao3.pickcat.feature.composer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.PickedImage
import dao3.pickcat.core.network.PickCatApi
import dao3.pickcat.core.network.Tag
import dao3.pickcat.core.ui.MarkdownPreview
import dao3.pickcat.core.ui.PickCatTextField
import dao3.pickcat.core.ui.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_TITLE = 100
private const val MAX_MARKDOWN = 20000

data class ComposerUiState(
    val title: String = "",
    val markdown: String = "",
    val tags: List<Tag> = emptyList(),
    val selectedTagIds: Set<String> = emptySet(),
    val loadingTags: Boolean = true,
    val publishing: Boolean = false,
    val uploading: Int = 0,
    val draftRestored: Boolean = false,
    val kind: String = "DISCUSSION",
    val submitted: Boolean = false,
)

class ComposerViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val draftStore = AppGraph.container.drafts
    private val _state = MutableStateFlow(ComposerUiState())
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var draftJob: Job? = null

    init {
        viewModelScope.launch {
            val draft = runCatching { draftStore.draft.first() }.getOrNull()
            if (draft != null && !draft.isEmpty) {
                _state.update {
                    if (it.title.isBlank() && it.markdown.isBlank()) {
                        it.copy(
                            title = draft.title,
                            markdown = draft.markdown,
                            selectedTagIds = draft.tagIds.toSet(),
                            draftRestored = true,
                        )
                    } else it
                }
            }
        }
        viewModelScope.launch {
            val tags = runCatching { api.getTags(assignable = true).items }
                .getOrElse { runCatching { api.getTags().items }.getOrDefault(emptyList()) }
            _state.update { it.copy(tags = tags, loadingTags = false) }
        }
    }

    fun setTitle(value: String) {
        _state.update { it.copy(title = value) }
        scheduleDraftSave()
    }

    fun setMarkdown(value: String) {
        _state.update { it.copy(markdown = value.take(MAX_MARKDOWN)) }
        scheduleDraftSave()
    }

    fun setKind(value: String) {
        _state.update { it.copy(kind = value) }
    }

    fun dismissSubmitted() {
        _state.update { it.copy(submitted = false) }
    }

    fun toggleTag(id: String) {
        _state.update {
            val next = if (id in it.selectedTagIds) it.selectedTagIds - id else it.selectedTagIds + id
            it.copy(selectedTagIds = next)
        }
        scheduleDraftSave()
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(500)
            saveDraftNow()
        }
    }

    fun saveDraftNow() {
        val snapshot = _state.value
        viewModelScope.launch {
            draftStore.save(snapshot.title, snapshot.markdown, snapshot.selectedTagIds.toList())
        }
    }

    fun uploadImages(
        images: List<PickedImage>,
        insertAt: Int? = null,
        replaceEnd: Int? = null,
    ) {
        if (images.isEmpty()) return
        _state.update { it.copy(uploading = it.uploading + images.size) }
        viewModelScope.launch {
            var cursor = insertAt ?: _state.value.markdown.length
            var end = replaceEnd ?: cursor
            for (image in images) {
                try {
                    val uploaded = api.uploadFile(image)
                    val reference = "![${uploaded.id}]"
                    _state.update { current ->
                        val start = cursor.coerceIn(0, current.markdown.length)
                        val finish = end.coerceIn(start, current.markdown.length)
                        val next = current.markdown.substring(0, start) +
                            reference +
                            current.markdown.substring(finish)
                        cursor = start + reference.length
                        end = cursor
                        current.copy(markdown = next.take(MAX_MARKDOWN))
                    }
                    scheduleDraftSave()
                    _messages.tryEmit("图片已上传并存入个人图盘")
                } catch (error: Exception) {
                    _messages.tryEmit(error.message ?: "图片上传失败")
                } finally {
                    _state.update { it.copy(uploading = (it.uploading - 1).coerceAtLeast(0)) }
                }
            }
        }
    }

    fun reportMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun publish(onSuccess: () -> Unit) {
        val snapshot = _state.value
        val title = snapshot.title.trim()
        val markdown = snapshot.markdown.trim()
        when {
            title.isEmpty() -> {
                _messages.tryEmit("标题不能为空")
                return
            }
            markdown.isEmpty() -> {
                _messages.tryEmit("正文不能为空")
                return
            }
            snapshot.selectedTagIds.isEmpty() -> {
                _messages.tryEmit("请至少选择一个分类")
                return
            }
            snapshot.publishing -> return
        }
        viewModelScope.launch {
            _state.update { it.copy(publishing = true) }
            try {
                api.createTopic(
                    title,
                    markdown,
                    snapshot.selectedTagIds.toList(),
                    kind = snapshot.kind,
                )
                draftStore.clear()
                _state.update { it.copy(publishing = false, submitted = true) }
            } catch (error: Exception) {
                _state.update { it.copy(publishing = false) }
                _messages.tryEmit(error.message ?: "发布失败")
            }
        }
    }

    fun clearDraftAfterPublish() {
        viewModelScope.launch { draftStore.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    val viewModel: ComposerViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by rememberSaveable { mutableStateOf(false) }
    var fieldValue by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var imageInsertRange by remember { mutableStateOf<TextRange?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.markdown) {
        if (state.markdown != fieldValue.text) {
            val caret = if (fieldValue.text.isEmpty()) {
                state.markdown.length
            } else {
                fieldValue.selection.start.coerceAtMost(state.markdown.length)
            }
            fieldValue = fieldValue.copy(
                text = state.markdown,
                selection = TextRange(caret),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.saveDraftNow() }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val errors = mutableListOf<String>()
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { readPickedImage(context, uri) }
                        .onFailure { errors += (it.message ?: "无法读取图片") }
                        .getOrNull()
                }
            }
            if (picked.isEmpty()) {
                viewModel.reportMessage(errors.firstOrNull() ?: "无法读取所选图片")
            } else {
                viewModel.uploadImages(
                    images = picked,
                    insertAt = imageInsertRange?.start,
                    replaceEnd = imageInsertRange?.end,
                )
                imageInsertRange = null
            }
        }
    }

    fun applyEdit(transform: (TextFieldValue) -> TextFieldValue) {
        val next = transform(fieldValue)
        fieldValue = next
        viewModel.setMarkdown(next.text)
    }

    val publish: () -> Unit = {
        viewModel.publish {
            viewModel.clearDraftAfterPublish()
            onPublished()
        }
    }

    val editorPane: @Composable (Modifier, Boolean) -> Unit = { modifier, showPreviewToggle ->
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.draftRestored) {
                Text(
                    "已恢复上次未发布的草稿",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            PickCatTextField(
                value = state.title,
                onValueChange = { if (it.length <= MAX_TITLE) viewModel.setTitle(it) },
                modifier = Modifier.fillMaxWidth(),
                label = "标题",
                placeholder = "一句话说清楚你想聊什么",
                supportingText = "${state.title.length}/$MAX_TITLE",
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "帖子类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.kind == "DISCUSSION",
                        onClick = { viewModel.setKind("DISCUSSION") },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("普通帖") }
                    SegmentedButton(
                        selected = state.kind == "QUESTION",
                        onClick = { viewModel.setKind("QUESTION") },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("问答帖") }
                }
            }
            if (state.loadingTags) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.tags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = tag.id in state.selectedTagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            label = { Text("#${tag.name}") },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "正文",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (showPreviewToggle) {
                    TextButton(onClick = { preview = !preview }) {
                        Text(if (preview) "继续编辑" else "预览")
                    }
                }
            }
            if (preview && showPreviewToggle) {
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MarkdownPreview(state.markdown)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    MarkdownToolbar(
                        onBold = {
                            applyEdit { value -> wrapValue(value, "**", "**", "粗体") }
                        },
                        onItalic = {
                            applyEdit { value -> wrapValue(value, "*", "*", "斜体") }
                        },
                        onStrike = {
                            applyEdit { value -> wrapValue(value, "~~", "~~", "删除线") }
                        },
                        onHeading = {
                            applyEdit { value -> prefixLine(value, "## ") }
                        },
                        onList = {
                            applyEdit { value -> prefixLine(value, "- ") }
                        },
                        onQuote = {
                            applyEdit { value -> prefixLine(value, "> ") }
                        },
                        onCode = {
                            applyEdit { value -> wrapValue(value, "`", "`", "代码") }
                        },
                        onTable = {
                            applyEdit { value ->
                                insertBlock(
                                    value,
                                    "\n| 列 1 | 列 2 |\n| --- | --- |\n| 内容 | 内容 |\n",
                                )
                            }
                        },
                        onFormula = {
                            applyEdit { value -> wrapValue(value, "$", "$", "公式") }
                        },
                        onLink = {
                            applyEdit { value -> wrapValue(value, "[", "](https://)", "链接文字") }
                        },
                        onImage = {
                            imageInsertRange = fieldValue.selection
                            picker.launch("image/*")
                        },
                        uploading = state.uploading,
                    )
                    PickCatTextField(
                        value = fieldValue,
                        onValueChange = {
                            if (it.text.length <= MAX_MARKDOWN) {
                                fieldValue = it
                                viewModel.setMarkdown(it.text)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = "开始写作，支持 Markdown",
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("发帖") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveDraftNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.uploading > 0) "正在上传 ${state.uploading} 张图片…"
                        else "${state.markdown.length} / $MAX_MARKDOWN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = publish, enabled = !state.publishing) {
                        if (state.publishing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("发布帖子")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    editorPane(Modifier.weight(0.58f).fillMaxHeight(), false)
                    VerticalDivider()
                    Column(
                        modifier = Modifier.weight(0.42f).fillMaxHeight().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("预览", style = MaterialTheme.typography.titleMedium)
                        OutlinedCard(Modifier.fillMaxWidth().weight(1f), shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                MarkdownPreview(state.markdown)
                            }
                        }
                    }
                }
            } else {
                editorPane(Modifier.fillMaxSize(), true)
            }
        }
    }

    if (state.submitted) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissSubmitted()
                onPublished()
            },
            title = { Text("已提交审核") },
            text = { Text("帖子已提交审核，审核通过后会展示在社区。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSubmitted()
                    onPublished()
                }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onHeading: () -> Unit,
    onList: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onTable: () -> Unit,
    onFormula: () -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    uploading: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Default.FormatBold, "粗体", onBold)
            ToolButton(Icons.Default.FormatItalic, "斜体", onItalic)
            ToolButton(Icons.Default.StrikethroughS, "删除线", onStrike)
            ToolButton(Icons.Default.Title, "标题", onHeading)
            ToolButton(Icons.Default.FormatListBulleted, "列表", onList)
            ToolButton(Icons.Default.FormatQuote, "引用", onQuote)
            ToolButton(Icons.Default.Code, "代码", onCode)
            ToolButton(Icons.Default.TableChart, "表格", onTable)
            ToolButton(Icons.Default.Functions, "公式", onFormula)
            ToolButton(Icons.Default.Link, "链接", onLink)
            Spacer(Modifier.width(6.dp))
            if (uploading > 0) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onImage) {
                Icon(Icons.Default.Image, contentDescription = "上传图片")
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description)
    }
}

private fun wrapValue(
    value: TextFieldValue,
    before: String,
    after: String,
    placeholder: String,
): TextFieldValue {
    val selected = value.text.substring(value.selection.start, value.selection.end)
    val content = selected.ifBlank { placeholder }
    val start = value.selection.start
    val nextText = value.text.substring(0, start) + before + content + after + value.text.substring(value.selection.end)
    return value.copy(
        text = nextText,
        selection = TextRange(start + before.length, start + before.length + content.length),
    )
}

private fun prefixLine(value: TextFieldValue, prefix: String): TextFieldValue {
    val lineStart = value.text.lastIndexOf('\n', (value.selection.start - 1).coerceAtLeast(0)).let {
        if (it < 0) 0 else it + 1
    }
    val next = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    return value.copy(
        text = next,
        selection = TextRange(value.selection.start + prefix.length, value.selection.end + prefix.length),
    )
}

private fun insertBlock(value: TextFieldValue, block: String): TextFieldValue {
    val start = value.selection.start
    val next = value.text.substring(0, start) + block + value.text.substring(value.selection.end)
    return value.copy(
        text = next,
        selection = TextRange(start + block.length),
    )
}

private fun readPickedImage(context: Context, uri: Uri): PickedImage {
    val resolver = context.contentResolver
    var name = "image.png"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    if (size > PickCatApi.MAX_UPLOAD_BYTES) {
        throw IllegalArgumentException("图片太大（${size / 1024 / 1024} MB），上限 20 MB")
    }
    val mime = resolver.getType(uri) ?: "image/*"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取图片")
    if (bytes.size > PickCatApi.MAX_UPLOAD_BYTES) {
        throw IllegalArgumentException("图片太大（${bytes.size / 1024 / 1024} MB），上限 20 MB")
    }
    return PickedImage(name = name, mimeType = mime, bytes = bytes)
}
