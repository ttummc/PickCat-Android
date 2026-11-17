package dao3.pickcat.feature.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.FileStorage
import dao3.pickcat.core.network.PICKCAT_ORIGIN
import dao3.pickcat.core.network.PickedImage
import dao3.pickcat.core.network.PickCatApi
import dao3.pickcat.core.network.StoredFile
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PickCatImage
import dao3.pickcat.core.ui.rememberFabVisibleOnGridScroll
import dao3.pickcat.core.ui.formatBytes
import dao3.pickcat.core.ui.relativeTime
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

enum class FileStateTab(val apiValue: String, val label: String) {
    READY("READY", "图库"),
    DELETED("DELETED", "回收站"),
}

data class MediaUiState(
    val tab: FileStateTab = FileStateTab.READY,
    val files: List<StoredFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val storage: FileStorage? = null,
    val storageError: Boolean = false,
    val uploading: Int = 0,
    val busy: Boolean = false,
)

class MediaViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun setTab(tab: FileStateTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, selected = emptySet()) }
        loadFirstPage()
        loadStorage()
    }

    fun refresh() {
        loadFirstPage()
        loadStorage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, selected = emptySet()) }
            try {
                val page = api.getFiles(_state.value.tab.apiValue)
                _state.update {
                    it.copy(
                        files = page.items,
                        cursor = page.cursor,
                        hasMore = page.canLoadMore,
                        loading = false,
                        error = null,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "加载失败", files = emptyList())
                }
            }
        }
    }

    private fun loadStorage() {
        viewModelScope.launch {
            runCatching { api.getStorage() }
                .onSuccess { storage -> _state.update { it.copy(storage = storage, storageError = false) } }
                .onFailure { _state.update { it.copy(storageError = true) } }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val cursor = snapshot.cursor
        if (snapshot.loadingMore || !snapshot.hasMore || cursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.getFiles(snapshot.tab.apiValue, cursor)
                _state.update {
                    it.copy(
                        files = (it.files + page.items).distinctBy { file -> file.id },
                        cursor = page.cursor,
                        hasMore = page.canLoadMore,
                        loadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun toggleSelection(id: String) {
        _state.update {
            if (id in it.selected) it.copy(selected = it.selected - id)
            else it.copy(selected = it.selected + id)
        }
    }

    fun selectAll() {
        _state.update {
            if (it.selected.size == it.files.size) it.copy(selected = emptySet())
            else it.copy(selected = it.files.map { file -> file.id }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun reportLocalError(message: String) {
        _messages.tryEmit(message)
    }

    fun uploadImages(images: List<PickedImage>) {
        if (images.isEmpty()) return        _state.update { it.copy(uploading = it.uploading + images.size) }
        viewModelScope.launch {
            var success = 0
            for (image in images) {
                try {
                    api.uploadFile(image)
                    success++
                } catch (error: Exception) {
                    _messages.tryEmit(error.message ?: "上传失败")
                } finally {
                    _state.update { it.copy(uploading = (it.uploading - 1).coerceAtLeast(0)) }
                }
            }
            if (success > 0) {
                _messages.tryEmit("已上传 $success 张图片")
                refresh()
            }
        }
    }

    fun runBatch(action: BatchAction) {
        if (_state.value.busy) return
        val ids = _state.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            var success = 0
            var failed = 0
            for (id in ids) {
                try {
                    when (action) {
                        BatchAction.TRASH -> api.trashFile(id)
                        BatchAction.RESTORE -> api.restoreFile(id)
                        BatchAction.PURGE -> api.purgeFile(id)
                    }
                    success++
                } catch (error: Exception) {
                    failed++
                    if (failed == 1) _messages.tryEmit(error.message ?: "操作失败")
                }
            }
            _state.update { it.copy(busy = false, selected = emptySet()) }
            refresh()
            if (success > 0) {
                val verb = when (action) {
                    BatchAction.TRASH -> "移入回收站"
                    BatchAction.RESTORE -> "恢复"
                    BatchAction.PURGE -> "彻底删除"
                }
                _messages.tryEmit("已${verb} $success 个文件" + if (failed > 0) "，$failed 个失败" else "")
            }
        }
    }
}

enum class BatchAction { TRASH, RESTORE, PURGE }

private enum class MediaPageState { LOADING, ERROR, EMPTY, CONTENT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(
    title: String,
    initialTab: FileStateTab,
    onBack: () -> Unit,
) {
    val viewModel: MediaViewModel = viewModel(key = "media-${initialTab.apiValue}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewFile by remember { mutableStateOf<StoredFile?>(null) }
    var confirmPurge by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val fabVisible = rememberFabVisibleOnGridScroll(gridState)

    LaunchedEffect(initialTab) {
        viewModel.setTab(initialTab)
    }
    LaunchedEffect(state.tab) {
        selecting = false
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val errors = mutableListOf<String>()
            val images = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { readMediaImage(context, uri) }
                        .onFailure { errors += (it.message ?: "无法读取图片") }
                        .getOrNull()
                }
            }
            if (images.isEmpty()) {
                viewModel.reportLocalError(errors.firstOrNull() ?: "无法读取所选图片")
            } else {
                viewModel.uploadImages(images)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = {
                        Text(
                            if (state.selected.isEmpty()) "选择图片" else "已选 ${state.selected.size}",
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selecting = false
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::selectAll) {
                            Text(
                                if (state.files.isNotEmpty() &&
                                    state.selected.size == state.files.size
                                ) {
                                    "取消全选"
                                } else {
                                    "全选"
                                },
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(
                            onClick = { selecting = true },
                            enabled = state.files.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "选择图片")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting && state.selected.isNotEmpty()) {
                SelectionActionBar(
                    tab = state.tab,
                    selectedCount = state.selected.size,
                    busy = state.busy,
                    onTrash = { viewModel.runBatch(BatchAction.TRASH) },
                    onRestore = { viewModel.runBatch(BatchAction.RESTORE) },
                    onPurge = { confirmPurge = true },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selecting && state.tab == FileStateTab.READY && fabVisible,
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
                    onClick = { picker.launch("image/*") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(if (state.uploading > 0) "上传中 ${state.uploading}" else "上传图片")
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StorageSummary(
                state = state,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                FileStateTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }
            if (state.files.isNotEmpty()) {
                Text(
                    "${state.files.size} 张图片",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val pageState = when {
                state.loading -> MediaPageState.LOADING
                state.error != null -> MediaPageState.ERROR
                state.files.isEmpty() -> MediaPageState.EMPTY
                else -> MediaPageState.CONTENT
            }
            AnimatedContent(
                targetState = pageState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                },
                label = "media-content",
            ) { page ->
                when (page) {
                    MediaPageState.LOADING -> LoadingState()
                    MediaPageState.ERROR -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::refresh,
                    )
                    MediaPageState.EMPTY -> EmptyState(
                        title = if (state.tab == FileStateTab.DELETED) "回收站是空的" else "图盘还是空的",
                        description = if (state.tab == FileStateTab.DELETED) "删除的图片会先放在这里。"
                        else "发帖时上传的图片会先存在这里。",
                        icon = Icons.Default.Image,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MediaPageState.CONTENT -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.files, key = { it.id }) { file ->
                            MediaTile(
                                file = file,
                                selected = file.id in state.selected,
                                selectionMode = selecting,
                                onClick = {
                                    if (selecting) {
                                        viewModel.toggleSelection(file.id)
                                    } else {
                                        previewFile = file
                                    }
                                },
                                onLongClick = {
                                    selecting = true
                                    viewModel.toggleSelection(file.id)
                                },
                                onToggleSelection = {
                                    selecting = true
                                    viewModel.toggleSelection(file.id)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(state.cursor, state.hasMore) {
                                if (state.hasMore) viewModel.loadMore()
                            }
                            if (state.loadingMore) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewFile?.let { file ->
        MediaPreviewDialog(file = file, onDismiss = { previewFile = null })
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("彻底删除？") },
            text = { Text("选中的 ${state.selected.size} 个文件将无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    confirmPurge = false
                    viewModel.runBatch(BatchAction.PURGE)
                }) { Text("彻底删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StorageSummary(state: MediaUiState, modifier: Modifier = Modifier) {
    val storage = state.storage
    val unlimited = storage != null && storage.limitBytes >= FileStorage.UNLIMITED
    val used = storage?.let {
        it.usedBytes ?: (it.limitBytes - it.remainingBytes).coerceAtLeast(0)
    } ?: 0L
    val percent = if (storage == null || unlimited || storage.limitBytes <= 0) {
        0f
    } else {
        (used.toDouble() / storage.limitBytes).toFloat().coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        } else {
            null
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "存储空间",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        storage == null && state.storageError -> "读取失败"
                        storage == null -> "读取中"
                        unlimited -> "无上限"
                        else -> "${(percent * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (storage == null) {
                Text(
                    if (state.storageError) "容量额度读取失败" else "正在读取容量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "已用 ${formatBytes(used).ifBlank { "0 B" }}" +
                        if (unlimited) "" else " / ${formatBytes(storage.limitBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!unlimited) {
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    file: StoredFile,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = "$PICKCAT_ORIGIN/api/v1/files/${file.id}"
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        PickCatImage(
            url = url,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            corner = 0.dp,
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }
        if (selectionMode) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                IconButton(
                    onClick = onToggleSelection,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "取消选择" else "选择",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    tab: FileStateTab,
    selectedCount: Int,
    busy: Boolean,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "已选 $selectedCount",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.weight(1f))
        if (tab == FileStateTab.READY) {
            TextButton(onClick = onTrash, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("移入回收站")
            }
        } else {
            TextButton(onClick = onRestore, enabled = !busy) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("恢复")
            }
            TextButton(onClick = onPurge, enabled = !busy) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("彻底删除")
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    file: StoredFile,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss),
        ) {
            PickCatImage(
                url = "$PICKCAT_ORIGIN/api/v1/files/${file.id}",
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit,
                corner = 0.dp,
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name ?: file.id.take(8),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val size = formatBytes(file.size)
                        Text(
                            listOfNotNull(
                                size.ifBlank { null },
                                relativeTime(file.createdAt),
                            ).joinToString("  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭预览")
                    }
                }
            }
        }
    }
}

private fun readMediaImage(context: Context, uri: Uri): PickedImage {
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
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取图片")
    if (bytes.size > PickCatApi.MAX_UPLOAD_BYTES) {
        throw IllegalArgumentException("图片太大（${bytes.size / 1024 / 1024} MB），上限 20 MB")
    }
    return PickedImage(
        name = name,
        mimeType = resolver.getType(uri) ?: "image/*",
        bytes = bytes,
    )
}
