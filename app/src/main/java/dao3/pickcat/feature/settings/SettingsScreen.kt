package dao3.pickcat.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.LoginWebViewActivity
import dao3.pickcat.SavedAccount
import dao3.pickcat.core.network.LevelProgress
import dao3.pickcat.core.network.OfficialAccount
import dao3.pickcat.core.network.SessionUser
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.avatarUrl
import dao3.pickcat.core.ui.compactNumber
import dao3.pickcat.core.ui.relativeTime
import dao3.pickcat.core.ui.theme.ThemeMode
import dao3.pickcat.core.ui.wideContentPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val CODECHAT_ACCESS_CODE = "67"
private const val OFFICIAL_SETTINGS_URL = "https://pickcat.dao3.fun/settings"
private const val OFFICIAL_ACCOUNT_DELETION_URL = "https://pickcat.dao3.fun/settings/account-deletion"

data class SettingsUiState(
    val levelProgress: LevelProgress? = null,
    val loading: Boolean = true,
)

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

private data class SettingsItemSpec(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val summary: String? = null,
    val onClick: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val tint: Color? = null,
)

private data class SettingsSectionSpec(
    val id: String,
    val title: String,
    val items: List<SettingsItemSpec>,
    val footer: (@Composable () -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGallery: () -> Unit = onOpenMedia,
    onOpenAccountSecurity: () -> Unit = {},
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val accounts by appViewModel.accounts.collectAsStateWithLifecycle()
    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showUpdateSourceSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var email by remember { mutableStateOf<String?>(null) }
    var officialAccount by remember { mutableStateOf<OfficialAccount?>(null) }
    var showCodeChatPassword by remember { mutableStateOf(false) }
    var codeChatPassword by remember { mutableStateOf("") }
    var codeChatPasswordError by remember { mutableStateOf(false) }

    val user = session.user
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val addAccountLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                appViewModel.finishAddAccount()
            } else {
                appViewModel.cancelAddAccount()
                snackbarHostState.showSnackbar("已取消添加账号")
            }
        }
    }

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

    LaunchedEffect(user?.id) {
        val id = user?.id
        if (id == null) {
            email = null
            officialAccount = null
            return@LaunchedEffect
        }
        email = runCatching { AppGraph.container.api.getUserEmail(id).email }.getOrNull()
        officialAccount = runCatching { AppGraph.container.api.getOfficialAccount() }.getOrNull()
        appViewModel.saveCurrentAccount()
    }

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

    val accountInfo = SettingsSectionSpec(
        id = "account",
        title = "账号",
        items = listOf(
            SettingsItemSpec(
                key = "account-id",
                icon = Icons.Default.Badge,
                title = "账号 ID",
                summary = user?.id ?: "未登录",
                onClick = copyAccountId,
                trailing = {
                    if (user != null) {
                        TextButton(onClick = copyAccountId) { Text("复制") }
                    }
                },
            ),
            SettingsItemSpec(
                key = "email",
                icon = Icons.Default.Email,
                title = "邮箱",
                summary = email.orEmpty().ifBlank { "未绑定" },
            ),
        ),
        footer = settingsState.levelProgress?.let { progress ->
            { LevelProgressRow(progress) }
        },
    )

    val sections = buildList {
        add(accountInfo)
        add(
            SettingsSectionSpec(
                id = "profile",
                title = "个人资料",
                items = listOf(
                    SettingsItemSpec(
                        key = "edit-profile",
                        icon = Icons.Default.Edit,
                        title = "编辑个人资料",
                        summary = "简介、地区和隐私设置",
                        onClick = onEditProfile,
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "home-display",
                        icon = Icons.Default.Link,
                        title = "主页展示",
                        summary = "精选帖子、作品和徽章，在官网管理",
                        onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                        trailing = { Chevron() },
                    ),
                ),
            ),
        )
        add(
            SettingsSectionSpec(
                id = "security",
                title = "登录与安全",
                items = listOf(
                    SettingsItemSpec(
                        key = "switch-account",
                        icon = Icons.Default.SwitchAccount,
                        title = "切换账号",
                        summary = user?.username ?: "选择或添加账号",
                        onClick = { showAccountSheet = true },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "account-security",
                        icon = Icons.Default.Security,
                        title = "账号安全",
                        summary = "两步验证、通行密钥、恢复码和登录设备",
                        onClick = onOpenAccountSecurity,
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "security-center",
                        icon = Icons.Default.Security,
                        title = "登录与安全",
                        summary = "邮箱、手机号、密码和实名认证，在官网管理",
                        onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "authorized-apps",
                        icon = Icons.Default.Security,
                        title = "授权应用",
                        summary = "查看并撤销第三方应用，在官网管理",
                        onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "delete-account",
                        icon = Icons.Default.Delete,
                        title = "注销账号",
                        summary = "永久删除账号和内容，需要官网验证",
                        onClick = { openExternalUrl(context, OFFICIAL_ACCOUNT_DELETION_URL) },
                        trailing = { Chevron() },
                        tint = MaterialTheme.colorScheme.error,
                    ),
                ),
            ),
        )
        add(
            SettingsSectionSpec(
                id = "appearance",
                title = "外观",
                items = listOf(
                    SettingsItemSpec(
                        key = "theme",
                        icon = Icons.Default.ColorLens,
                        title = "主题",
                        summary = themeLabel(preferences.themeMode),
                        onClick = { showThemeSheet = true },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "dynamic-color",
                        icon = Icons.Default.DarkMode,
                        title = "动态取色",
                        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "使用系统壁纸配色"
                        } else {
                            "当前系统不支持"
                        },
                        trailing = {
                            Switch(
                                checked = preferences.dynamicColor &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onCheckedChange = appViewModel::updateDynamicColor,
                            )
                        },
                    ),
                    SettingsItemSpec(
                        key = "home-works",
                        icon = Icons.Default.VideoLibrary,
                        title = "首页作品推荐",
                        summary = "显示精选作品和关注作品",
                        trailing = {
                            Switch(
                                checked = preferences.homeWorksEnabled,
                                onCheckedChange = appViewModel::updateHomeWorksEnabled,
                            )
                        },
                    ),
                    SettingsItemSpec(
                        key = "background-notifications",
                        icon = Icons.Default.Notifications,
                        title = "后台通知",
                        summary = "约每 15 分钟检查一次",
                        trailing = {
                            Switch(
                                checked = preferences.backgroundNotifications,
                                onCheckedChange = appViewModel::updateBackgroundNotifications,
                            )
                        },
                    ),
                ),
            ),
        )
        add(
            SettingsSectionSpec(
                id = "content",
                title = "内容",
                items = listOf(
                    SettingsItemSpec(
                        key = "media",
                        icon = Icons.Default.PhotoLibrary,
                        title = "个人图盘",
                        summary = "上传图片、查看已用容量",
                        onClick = onOpenMedia,
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "gallery",
                        icon = Icons.Default.PhotoLibrary,
                        title = "媒体库",
                        summary = "图库、回收站和批量删除",
                        onClick = onOpenGallery,
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "library",
                        icon = Icons.Default.Star,
                        title = "个人作品库",
                        summary = "我发布过的作品和帖子",
                        onClick = onOpenLibrary,
                        trailing = { Chevron() },
                    ),
                ),
            ),
        )
        add(
            SettingsSectionSpec(
                id = "integrations",
                title = "互联",
                items = listOf(
                    SettingsItemSpec(
                        key = "codemao",
                        icon = Icons.Default.Link,
                        title = if (officialAccount == null) "绑定编程猫账号" else "已绑定编程猫账号",
                        summary = officialAccount?.let {
                            "ID ${it.sourceUserId.orEmpty()}" +
                                it.lastSyncedAt?.let { time -> "\n最近同步 ${relativeTime(time)}" }.orEmpty()
                        } ?: "绑定后可把作品同步到社区，在官网管理",
                        onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "codechat",
                        icon = Icons.Default.Chat,
                        title = "CodeChat 聊天",
                        summary = "由 CodeChat 提供，开启后显示在底部导航",
                        trailing = {
                            Switch(
                                checked = preferences.codeChatEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        appViewModel.updateCodeChatEnabled(false)
                                    } else {
                                        codeChatPassword = ""
                                        codeChatPasswordError = false
                                        showCodeChatPassword = true
                                    }
                                },
                            )
                        },
                    ),
                ),
            ),
        )
        add(
            SettingsSectionSpec(
                id = "about",
                title = "关于",
                items = listOf(
                    SettingsItemSpec(
                        key = "version",
                        icon = Icons.Default.Info,
                        title = "PickCat Android",
                        summary = "Kotlin 与 Jetpack Compose",
                        trailing = {
                            Text(
                                if (versionName.isBlank()) "开发版" else "v$versionName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ),
                    SettingsItemSpec(
                        key = "check-update",
                        icon = Icons.Default.Refresh,
                        title = "检查更新",
                        summary = when {
                            updateState.checking -> "正在检查更新"
                            updateState.downloading -> "正在下载更新"
                            updateState.update != null -> "发现 ${updateState.update?.tag}"
                            else -> "从 ${updateSourceLabel(preferences.updateSource)} 检查"
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
                                Chevron()
                            }
                        },
                    ),
                    SettingsItemSpec(
                        key = "update-source",
                        icon = Icons.Default.Refresh,
                        title = "更新来源",
                        summary = updateSourceLabel(preferences.updateSource),
                        onClick = { showUpdateSourceSheet = true },
                        trailing = { Chevron() },
                    ),
                    SettingsItemSpec(
                        key = "login-method",
                        icon = Icons.Default.Badge,
                        title = "登录方式",
                        summary = "官方登录页完成验证与 Cookie 会话",
                    ),
                ),
            ),
        )
        if (user != null) {
            add(
                SettingsSectionSpec(
                    id = "session",
                    title = "账户",
                    items = listOf(
                        SettingsItemSpec(
                            key = "sign-out",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = "退出登录",
                            summary = "清除本地会话与 Cookie",
                            onClick = { confirmSignOut = true },
                            tint = MaterialTheme.colorScheme.error,
                        ),
                    ),
                ),
            )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val visibleSections = remember(query, sections) {
            if (query.isBlank()) {
                sections
            } else {
                sections.mapNotNull { section ->
                    val sectionMatches = section.title.contains(query, ignoreCase = true)
                    val items = if (sectionMatches) {
                        section.items
                    } else {
                        section.items.filter { item ->
                            item.title.contains(query, ignoreCase = true) ||
                                item.summary.orEmpty().contains(query, ignoreCase = true)
                        }
                    }
                    if (items.isEmpty()) null else section.copy(items = items)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = wideContentPadding(760.dp),
                end = wideContentPadding(760.dp),
                top = 4.dp,
                bottom = 32.dp,
            ),
        ) {
            item(key = "settings-search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索设置") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }
            item(key = "settings-account-header") {
                SettingsAccountHeader(
                    user = user,
                    email = email,
                    onClick = if (user != null) onEditProfile else null,
                )
            }

            visibleSections.forEach { section ->
                item(key = "section-${section.id}") {
                    SectionTitle(section.title)
                }
                itemsIndexed(
                    items = section.items,
                    key = { _, item -> "${section.id}-${item.key}" },
                ) { index, item ->
                    SettingsRow(item)
                    if (index < section.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                section.footer?.let { footer ->
                    item(key = "footer-${section.id}") { footer() }
                }
            }
            if (query.isNotBlank() && visibleSections.isEmpty()) {
                item(key = "settings-empty") {
                    Text(
                        "没有找到设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }

            item(key = "settings-footer") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
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

    if (showThemeSheet) {
        SettingsChoiceSheet(
            title = "主题",
            options = listOf(
                "system" to "跟随系统",
                "light" to "浅色",
                "dark" to "深色",
            ),
            selectedValue = preferences.themeMode,
            onSelect = { value ->
                appViewModel.updateTheme(
                    when (value) {
                        "light" -> ThemeMode.LIGHT
                        "dark" -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    },
                )
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false },
        )
    }

    if (showUpdateSourceSheet) {
        SettingsChoiceSheet(
            title = "更新来源",
            options = listOf(
                "mirror" to "Kitter镜像源",
                "github" to "GitHub Releases",
            ),
            selectedValue = preferences.updateSource,
            onSelect = { value ->
                appViewModel.updateUpdateSource(value)
                showUpdateSourceSheet = false
            },
            onDismiss = { showUpdateSourceSheet = false },
        )
    }

    val update = updateState.update
    when {
        updateState.installError != null -> AlertDialog(
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
        updateState.error != null -> AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("检查更新失败") },
            text = { Text(updateState.error.orEmpty()) },
            confirmButton = {
                Button(onClick = updateViewModel::dismiss) { Text("知道了") }
            },
        )
        updateState.upToDate -> AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("已是最新版本") },
            text = {
                Text(if (versionName.isBlank()) "当前为开发版。" else "当前版本 v$versionName。")
            },
            confirmButton = {
                Button(onClick = updateViewModel::dismiss) { Text("知道了") }
            },
        )
        updateState.downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${((updateState.progress ?: 0f) * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
        update != null -> UpdatePromptSheet(
            update = update,
            onDownload = { updateViewModel.download(context) },
            onDismiss = updateViewModel::dismiss,
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

    if (showAccountSheet) {
        AccountSwitcherSheet(
            currentUserId = user?.id,
            accounts = accounts,
            onSelect = { account ->
                showAccountSheet = false
                scope.launch {
                    val switched = appViewModel.switchAccount(account)
                    snackbarHostState.showSnackbar(
                        if (switched) "已切换到 ${account.username}" else "切换账号失败，请重新登录",
                    )
                }
            },
            onAdd = {
                showAccountSheet = false
                scope.launch {
                    appViewModel.prepareAddAccount()
                    addAccountLauncher.launch(Intent(context, LoginWebViewActivity::class.java))
                }
            },
            onDismiss = { showAccountSheet = false },
        )
    }

    if (showCodeChatPassword) {
        AlertDialog(
            onDismissRequest = {
                showCodeChatPassword = false
                codeChatPassword = ""
                codeChatPasswordError = false
            },
            title = { Text("开启 CodeChat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CodeChat 目前处于测试阶段，请输入访问密码。")
                    OutlinedTextField(
                        value = codeChatPassword,
                        onValueChange = {
                            codeChatPassword = it
                            codeChatPasswordError = false
                        },
                        label = { Text("访问密码") },
                        singleLine = true,
                        isError = codeChatPasswordError,
                        supportingText = if (codeChatPasswordError) {
                            { Text("密码错误") }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = codeChatPassword.isNotBlank(),
                    onClick = {
                        if (codeChatPassword == CODECHAT_ACCESS_CODE) {
                            appViewModel.updateCodeChatEnabled(true)
                            showCodeChatPassword = false
                            codeChatPassword = ""
                            codeChatPasswordError = false
                        } else {
                            codeChatPasswordError = true
                        }
                    },
                ) { Text("开启") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCodeChatPassword = false
                        codeChatPassword = ""
                        codeChatPasswordError = false
                    },
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(item: SettingsItemSpec) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                item.icon,
                contentDescription = null,
                tint = item.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        headlineContent = {
            Text(
                item.title,
                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = item.summary?.let { summary ->
            {
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = when {
            item.trailing != null -> item.trailing
            item.onClick != null -> {
                { Chevron() }
            }
            else -> null
        },
        modifier = if (item.onClick != null) {
            Modifier.clickable(onClick = item.onClick)
        } else {
            Modifier
        },
    )
}

@Composable
private fun SettingsAccountHeader(
    user: SessionUser?,
    email: String?,
    onClick: (() -> Unit)?,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (user != null) {
                UserAvatar(
                    name = user.username,
                    url = avatarUrl(user),
                    size = 52.dp,
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = {
            Text(
                user?.username ?: "未登录",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        },
        trailingContent = if (onClick != null) {
            { Chevron() }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 4.dp),
    )
}

@Composable
private fun LevelProgressRow(progress: LevelProgress) {
    val percent = remember(progress) {
        val next = progress.nextLevel ?: return@remember 0
        val requirements = (next.scores.values + next.familiarity.values)
        if (requirements.isEmpty()) {
            0
        } else {
            val total = requirements.sumOf { item ->
                if (item.required <= 0.0) 1.0 else (item.current / item.required).coerceIn(0.0, 1.0)
            }
            ((total / requirements.size) * 100).toInt()
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "等级进度",
                style = MaterialTheme.typography.labelLarge,
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
        Text(
            "能力 ${progress.scores.ability}  责任 ${progress.scores.responsibility}  关怀 ${progress.scores.care}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    currentUserId: String?,
    accounts: List<SavedAccount>,
    onSelect: (SavedAccount) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                "切换账号",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                "已保存的账号可直接切换，会话保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            accounts.forEach { account ->
                val current = account.id == currentUserId
                ListItem(
                    headlineContent = { Text(account.username) },
                    supportingContent = if (current) {
                        { Text("当前账号") }
                    } else {
                        null
                    },
                    leadingContent = {
                        UserAvatar(
                            name = account.username,
                            url = account.avatarUrl,
                            size = 40.dp,
                        )
                    },
                    trailingContent = if (current) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "当前账号",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !current) { onSelect(account) }
                        .padding(horizontal = 8.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("添加账号") },
                supportingContent = { Text("登录另一个 PickCat 账号") },
                leadingContent = {
                    Icon(Icons.Default.Add, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            options.forEach { (value, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        RadioButton(
                            selected = selectedValue == value,
                            onClick = null,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}
