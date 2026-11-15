package dao3.pickcat.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.core.network.AvatarPreset
import dao3.pickcat.core.network.LevelProgress
import dao3.pickcat.core.network.OfficialAccount
import dao3.pickcat.core.ui.relativeTime
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.absoluteUrl
import dao3.pickcat.core.ui.avatarUrl
import dao3.pickcat.core.ui.compactNumber
import dao3.pickcat.core.ui.wideContentPadding
import dao3.pickcat.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class SettingsUiState(
    val levelProgress: LevelProgress? = null,
    val loading: Boolean = true,
)

class SettingsViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val level = runCatching { api.getLevelProgress() }.getOrNull()
            _state.update { SettingsUiState(levelProgress = level, loading = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGallery: () -> Unit = onOpenMedia,
) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf<String?>(null) }
    var officialAccount by remember { mutableStateOf<OfficialAccount?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(updateState.installFile) {
        val file = updateState.installFile ?: return@LaunchedEffect
        val result = installApk(context, file)
        updateViewModel.consumeInstallFile()
        if (result.isSuccess) {
            updateViewModel.dismiss()
        } else {
            updateViewModel.reportInstallError(
                result.exceptionOrNull()?.message ?: "无法打开系统安装器",
            )
        }
    }
    val user = session.user

    LaunchedEffect(user?.id) {
        val id = user?.id
        if (id == null) {
            email = null
            officialAccount = null
            return@LaunchedEffect
        }
        email = runCatching { AppGraph.container.api.getUserEmail(id).email }.getOrNull()
        officialAccount = runCatching { AppGraph.container.api.getOfficialAccount() }.getOrNull()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = wideContentPadding(760.dp),
                end = wideContentPadding(760.dp),
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionTitle("账户")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        ListItem(
                            leadingContent = {
                                if (user != null) {
                                    UserAvatar(
                                        name = user.username,
                                        url = avatarUrl(user),
                                        size = 48.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(44.dp))
                                }
                            },
                            headlineContent = { Text(user?.username ?: "未登录") },
                            supportingContent = {
                                if (user != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Lv.${user.level.current}")
                                        Text("${compactNumber(user.tokenBalance)} 猫币")
                                    }
                                } else {
                                    Text("登录后可以发帖、点赞和查看通知")
                                }
                            },
                        )
                        if (state.levelProgress != null) {
                            HorizontalDivider()
                            LevelProgressRow(state.levelProgress!!)
                        }
                        if (user != null) {
                            HorizontalDivider()
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                headlineContent = { Text("编辑个人资料") },
                                supportingContent = { Text("修改地区和简介；用户名修改需要在网页端验证") },
                                modifier = Modifier.clickableRow(onEditProfile),
                            )
                        }
                        if (!email.isNullOrBlank()) {
                            HorizontalDivider()
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.Email, contentDescription = null)
                                },
                                headlineContent = { Text("邮箱") },
                                supportingContent = {
                                    Column {
                                        Text(email.orEmpty())
                                        Text("已验证")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (officialAccount != null) {
                item {
                    SectionTitle("编程猫互联")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            },
                            headlineContent = { Text("已绑定编程猫账号") },
                            supportingContent = {
                                Column {
                                    Text("编程猫 ID：${officialAccount?.sourceUserId.orEmpty()}")
                                    officialAccount?.lastSyncedAt?.let { time ->
                                        Text("最近同步：" + relativeTime(time))
                                    }
                                }
                            },
                        )
                    }
                }
            }

            item {
                SectionTitle("外观")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ListItem(
                            leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                            headlineContent = { Text("应用主题") },
                            supportingContent = {
                                Text(
                                    when (preferences.themeMode) {
                                        "light" -> "浅色"
                                        "dark" -> "深色"
                                        else -> "跟随系统"
                                    },
                                )
                            },
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            val modes = listOf(
                                ThemeMode.SYSTEM to "跟随系统",
                                ThemeMode.LIGHT to "浅色",
                                ThemeMode.DARK to "深色",
                            )
                            modes.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = when (mode) {
                                        ThemeMode.SYSTEM -> preferences.themeMode == "system"
                                        ThemeMode.LIGHT -> preferences.themeMode == "light"
                                        ThemeMode.DARK -> preferences.themeMode == "dark"
                                    },
                                    onClick = { appViewModel.updateTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                ) { Text(label) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        ListItem(
                            leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                            headlineContent = { Text("动态取色") },
                            supportingContent = {
                                Text(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        "Android 12+ 使用系统壁纸配色"
                                    } else {
                                        "当前系统版本不支持，使用 PickCat 配色"
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                    onCheckedChange = appViewModel::updateDynamicColor,
                                )
                            },
                        )
                    }
                }
            }

            item {
                SectionTitle("通知")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        headlineContent = { Text("后台通知") },
                        supportingContent = {
                            Text("约每 15 分钟检查一次")
                        },
                        trailingContent = {
                            Switch(
                                checked = preferences.backgroundNotifications,
                                onCheckedChange = appViewModel::updateBackgroundNotifications,
                            )
                        },
                    )
                }
            }

            item {
                SectionTitle("内容")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        ListItem(
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            headlineContent = { Text("个人图盘") },
                            supportingContent = { Text("上传图片、查看已用容量") },
                            modifier = Modifier.clickableRow(onOpenMedia),
                        )
                        HorizontalDivider()
                        ListItem(
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            headlineContent = { Text("媒体库") },
                            supportingContent = { Text("图库与回收站、批量恢复和彻底删除") },
                            modifier = Modifier.clickableRow(onOpenGallery),
                        )
                        HorizontalDivider()
                        ListItem(
                            leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                            headlineContent = { Text("个人作品库") },
                            supportingContent = { Text("我发布过的作品和帖子") },
                            modifier = Modifier.clickableRow(onOpenLibrary),
                        )
                    }
                }
            }

            item {
                SectionTitle("关于")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        ListItem(
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                            headlineContent = { Text("PickCat Android 原生版") },
                            supportingContent = { Text("Kotlin 与 Jetpack Compose") },
                            trailingContent = {
                                Text(if (versionName.isBlank()) "开发版" else "v$versionName")
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            },
                            headlineContent = { Text("检查更新") },
                            supportingContent = {
                                Text(
                                    when {
                                        updateState.checking -> "正在检查 GitHub Releases"
                                        updateState.downloading -> "正在下载更新"
                                        updateState.update != null -> "发现 ${updateState.update?.tag}"
                                        else -> "从 GitHub Releases 获取最新版本"
                                    },
                                )
                            },
                            trailingContent = {
                                if (updateState.checking || updateState.downloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    TextButton(
                                        onClick = { updateViewModel.check(context) },
                                    ) {
                                        Text(if (updateState.update != null) "查看" else "检查")
                                    }
                                }
                            },
                            modifier = Modifier.clickableRow {
                                if (!updateState.checking && !updateState.downloading) {
                                    updateViewModel.check(context)
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            leadingContent = { Icon(Icons.Default.Badge, contentDescription = null) },
                            headlineContent = { Text("登录方式") },
                            supportingContent = { Text("官方登录页完成验证与 Cookie 会话") },
                        )
                    }
                }
            }

            if (user != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            },
                            headlineContent = {
                                Text("退出登录", color = MaterialTheme.colorScheme.onErrorContainer)
                            },
                            supportingContent = {
                                Text(
                                    "会清除本地会话与 Cookie",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = { confirmSignOut = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) { Text("退出") }
                            },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "非官方客户端",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "接口来自 pickcat.dao3.fun",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val update = updateState.update
    if (updateState.installError != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("安装未完成") },
            text = { Text(updateState.installError.orEmpty()) },
            confirmButton = {
                if (update != null) {
                    Button(onClick = {
                        openReleasePage(context, update.releaseUrl)
                        updateViewModel.dismiss()
                    }) { Text("打开 Release 页面") }
                } else {
                    Button(onClick = updateViewModel::dismiss) { Text("知道了") }
                }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) { Text("关闭") }
            },
        )
    } else if (updateState.error != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("检查更新失败") },
            text = { Text(updateState.error.orEmpty()) },
            confirmButton = {
                Button(onClick = updateViewModel::dismiss) { Text("知道了") }
            },
        )
    } else if (updateState.upToDate) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("已是最新版本") },
            text = {
                Text(if (versionName.isBlank()) "当前为开发版。" else "当前版本 v$versionName。")
            },
            confirmButton = {
                Button(onClick = updateViewModel::dismiss) { Text("知道了") }
            },
        )
    } else if (updateState.downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${((updateState.progress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    } else if (update != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("发现 ${update.tag}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(update.title, style = MaterialTheme.typography.titleSmall)
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = { updateViewModel.download(context) }) { Text("下载并安装") }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) { Text("稍后") }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("退出 PickCat？") },
            text = { Text("退出后需要重新登录才能发帖和查看通知。") },
            confirmButton = {
                Button(onClick = {
                    confirmSignOut = false
                    appViewModel.logout()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("取消") }
            },
        )
    }
}

private fun installApk(context: Context, file: File): Result<Unit> = runCatching {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openReleasePage(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LevelProgressRow(progress: LevelProgress) {
    val percent = remember(progress) {
        val next = progress.nextLevel ?: return@remember 0
        val requirements = (next.scores.values + next.familiarity.values)
        if (requirements.isEmpty()) 0 else {
            val total = requirements.sumOf { item ->
                if (item.required <= 0.0) 1.0 else (item.current / item.required).coerceIn(0.0, 1.0)
            }
            ((total / requirements.size) * 100).toInt()
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "等级进度 Lv.${progress.currentLevel}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                String.format(Locale.US, "%d%%", percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "能力 ${progress.scores.ability}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "责任 ${progress.scores.responsibility}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "关怀 ${progress.scores.care}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
