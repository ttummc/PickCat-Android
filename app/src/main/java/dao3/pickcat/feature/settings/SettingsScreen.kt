package dao3.pickcat.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import dao3.pickcat.core.network.SessionUser
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

private const val OFFICIAL_SETTINGS_URL = "https://pickcat.dao3.fun/settings"
private const val OFFICIAL_ACCOUNT_DELETION_URL = "https://pickcat.dao3.fun/settings/account-deletion"

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun updateSourceLabel(source: String): String =
    if (source == "mirror") "Kitter镜像源" else "GitHub Releases"

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
        val result = launchUpdateInstaller(context, file)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUpdateSourceDialog by remember { mutableStateOf(false) }

    val copyAccountId: () -> Unit = {
        val id = user?.id.orEmpty()
        if (id.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("账号 ID 暂不可用") }
        } else {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("PickCat 账号 ID", id))
            scope.launch { snackbarHostState.showSnackbar("账号 ID 已复制") }
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = wideContentPadding(760.dp),
                end = wideContentPadding(760.dp),
                bottom = 32.dp,
            ),
        ) {
            item {
                SettingsAccountHeader(
                    user = user,
                    email = email,
                    onClick = if (user != null) onEditProfile else null,
                )
            }

            item { SettingsSectionTitle("账号信息") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Badge,
                        title = "账号 ID",
                        summary = user?.id ?: "未登录",
                        onClick = copyAccountId,
                        trailing = if (user != null) {
                            {
                                TextButton(onClick = copyAccountId) {
                                    Text("复制")
                                }
                            }
                        } else null,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Email,
                        title = "邮箱",
                        summary = if (email.isNullOrBlank()) "未绑定" else email,
                    )
                }
            }
            if (state.levelProgress != null) {
                item {
                    LevelProgressRow(state.levelProgress!!)
                }
            }

            item { SettingsSectionTitle("个人资料") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Edit,
                        title = "编辑个人资料",
                        summary = "简介、地区和隐私设置",
                        onClick = onEditProfile,
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Link,
                        title = "主页展示",
                        summary = "精选帖子、精选作品和徽章展示，在官网管理",
                        onClick = {
                            openExternalUrl(context, OFFICIAL_SETTINGS_URL)
                        },
                        showChevron = true,
                    )
                }
            }

            item { SettingsSectionTitle("登录与安全") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Security,
                        title = "登录与安全",
                        summary = "邮箱、手机号、密码和实名认证，在官网管理",
                        onClick = {
                            openExternalUrl(context, OFFICIAL_SETTINGS_URL)
                        },
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Security,
                        title = "授权应用",
                        summary = "查看并撤销已授权的第三方应用，在官网管理",
                        onClick = {
                            openExternalUrl(context, OFFICIAL_SETTINGS_URL)
                        },
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Delete,
                        title = "注销账号",
                        summary = "永久删除账号和内容，需要官网验证",
                        onClick = {
                            openExternalUrl(context, OFFICIAL_ACCOUNT_DELETION_URL)
                        },
                        showChevron = true,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { SettingsSectionTitle("编程猫互联") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Link,
                        title = if (officialAccount == null) "绑定编程猫账号" else "已绑定编程猫账号",
                        summary = if (officialAccount == null) {
                            "绑定后可把作品同步到社区，在官网管理"
                        } else {
                            buildString {
                                append("编程猫 ID：")
                                append(officialAccount?.sourceUserId.orEmpty())
                                officialAccount?.lastSyncedAt?.let { time ->
                                    append("\n最近同步：")
                                    append(relativeTime(time))
                                }
                            }
                        },
                        onClick = {
                            openExternalUrl(context, OFFICIAL_SETTINGS_URL)
                        },
                        showChevron = true,
                    )
                }
            }

            item { SettingsSectionTitle("外观") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.ColorLens,
                        title = "主题",
                        summary = themeLabel(preferences.themeMode),
                        onClick = { showThemeDialog = true },
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        title = "动态取色",
                        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Android 12+ 使用系统壁纸配色"
                        } else {
                            "当前系统版本不支持，使用 PickCat 配色"
                        },
                        trailing = {
                            Switch(
                                checked = preferences.dynamicColor &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onCheckedChange = appViewModel::updateDynamicColor,
                            )
                        },
                    )
                }
            }

            item { SettingsSectionTitle("通知") }
            item {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = "后台通知",
                    summary = "约每 15 分钟检查一次",
                    trailing = {
                        Switch(
                            checked = preferences.backgroundNotifications,
                            onCheckedChange = appViewModel::updateBackgroundNotifications,
                        )
                    },
                )
            }

            item { SettingsSectionTitle("内容") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "个人图盘",
                        summary = "上传图片、查看已用容量",
                        onClick = onOpenMedia,
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "媒体库",
                        summary = "图库与回收站、批量恢复和彻底删除",
                        onClick = onOpenGallery,
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Star,
                        title = "个人作品库",
                        summary = "我发布过的作品和帖子",
                        onClick = onOpenLibrary,
                        showChevron = true,
                    )
                }
            }

            item { SettingsSectionTitle("关于") }
            item {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "PickCat Android",
                        summary = "Kotlin 与 Jetpack Compose",
                        trailing = {
                            Text(if (versionName.isBlank()) "开发版" else "v$versionName")
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "检查更新",
                        summary = when {
                            updateState.checking -> "正在检查更新"
                            updateState.downloading -> "正在下载更新"
                            updateState.update != null -> "发现 ${updateState.update?.tag}"
                            else -> "GitHub Releases 或 Kitter镜像源"
                        },
                        onClick = {
                            if (!updateState.checking && !updateState.downloading) {
                                updateViewModel.check(context, preferences.updateSource)
                            }
                        },
                        trailing = {
                            if (updateState.checking || updateState.downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "更新来源",
                        summary = if (preferences.updateSource == "mirror") {
                            "Kitter镜像源，失败时自动切 GitHub"
                        } else {
                            "GitHub Releases，失败时自动切 Kitter镜像源"
                        },
                        onClick = { showUpdateSourceDialog = true },
                        showChevron = true,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Badge,
                        title = "登录方式",
                        summary = "官方登录页完成验证与 Cookie 会话",
                    )
                }
            }

            if (user != null) {
                item {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "退出登录",
                        summary = "会清除本地会话与 Cookie",
                        onClick = { confirmSignOut = true },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    val options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色",
                    )
                    options.forEach { (mode, label) ->
                        val selected = when (mode) {
                            ThemeMode.SYSTEM -> preferences.themeMode == "system"
                            ThemeMode.LIGHT -> preferences.themeMode == "light"
                            ThemeMode.DARK -> preferences.themeMode == "dark"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appViewModel.updateTheme(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    appViewModel.updateTheme(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showUpdateSourceDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateSourceDialog = false },
            title = { Text("更新来源") },
            text = {
                Column {
                    val options = listOf(
                        "github" to "GitHub Releases",
                        "mirror" to "Kitter镜像源",
                    )
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appViewModel.updateUpdateSource(value)
                                    showUpdateSourceDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preferences.updateSource == value,
                                onClick = {
                                    appViewModel.updateUpdateSource(value)
                                    showUpdateSourceDialog = false
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdateSourceDialog = false }) { Text("关闭") }
            },
        )
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
                        openExternalUrl(context, update.releaseUrl)
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

@Composable
private fun SettingsAccountHeader(
    user: SessionUser?,
    email: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (user != null) {
            UserAvatar(
                name = user.username,
                url = avatarUrl(user),
                size = 64.dp,
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user?.username ?: "未登录",
                style = MaterialTheme.typography.titleLarge,
            )
            if (!email.isNullOrBlank()) {
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user != null) {
                Text(
                    "Lv.${user.level.current}  ${compactNumber(user.tokenBalance)} 猫币",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                title,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = summary?.let { text ->
            {
                Text(
                    text,
                    color = if (tint != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingContent = when {
            trailing != null -> trailing
            showChevron -> {
                {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> null
        },
        modifier = if (onClick != null) Modifier.clickableRow(onClick) else Modifier,
    )
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
