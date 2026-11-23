package dao3.pickcat.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.AppViewModel
import dao3.pickcat.core.network.LevelProgress
import dao3.pickcat.core.network.OfficialAccount
import dao3.pickcat.core.network.SessionUser
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.avatarUrl
import dao3.pickcat.core.ui.compactNumber
import dao3.pickcat.core.ui.relativeTime
import dao3.pickcat.core.ui.wideContentPadding
import dao3.pickcat.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsUiState(
    val levelProgress: LevelProgress? = null,
    val loading: Boolean = true,
)

private const val OFFICIAL_SETTINGS_URL = "https://pickcat.dao3.fun/settings"
private const val OFFICIAL_ACCOUNT_DELETION_URL = "https://pickcat.dao3.fun/settings/account-deletion"
private const val CODECHAT_ACCESS_CODE = "67"

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
    val keywords: List<String> = emptyList(),
    val onClick: (() -> Unit)? = null,
    val showChevron: Boolean = false,
    val tint: Color? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

private data class SettingsSectionSpec(
    val id: String,
    val title: String,
    val keywords: List<String> = emptyList(),
    val items: List<SettingsItemSpec>,
    val footer: (@Composable () -> Unit)? = null,
)

private fun SettingsItemSpec.matches(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        summary.orEmpty().contains(query, ignoreCase = true) ||
        keywords.any { it.contains(query, ignoreCase = true) }

private fun SettingsSectionSpec.matches(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        keywords.any { it.contains(query, ignoreCase = true) }

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
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUpdateSourceDialog by remember { mutableStateOf(false) }
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

    val trimmedQuery = query.trim()
    val accountMatches = trimmedQuery.isBlank() ||
        listOf("账号", "邮箱", "资料", user?.username.orEmpty(), email.orEmpty())
            .any { it.contains(trimmedQuery, ignoreCase = true) }

    val sections = listOf(
        SettingsSectionSpec(
            id = "account-info",
            title = "账号信息",
            keywords = listOf("账号", "ID", "邮箱", "等级"),
            items = listOf(
                SettingsItemSpec(
                    key = "account-id",
                    icon = Icons.Default.Badge,
                    title = "账号 ID",
                    summary = user?.id ?: "未登录",
                    keywords = listOf("账号", "ID", "复制"),
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
                    keywords = listOf("邮箱", "绑定"),
                ),
            ),
            footer = state.levelProgress?.let { progress ->
                { LevelProgressRow(progress) }
            },
        ),
        SettingsSectionSpec(
            id = "profile",
            title = "个人资料",
            keywords = listOf("个人资料", "简介", "地区", "隐私", "主页", "展示"),
            items = listOf(
                SettingsItemSpec(
                    key = "edit-profile",
                    icon = Icons.Default.Edit,
                    title = "编辑个人资料",
                    summary = "简介、地区和隐私设置",
                    keywords = listOf("资料", "简介", "地区", "隐私"),
                    onClick = onEditProfile,
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "home-display",
                    icon = Icons.Default.Link,
                    title = "主页展示",
                    summary = "精选帖子、精选作品和徽章展示，在官网管理",
                    keywords = listOf("主页", "展示", "官网"),
                    onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                    showChevron = true,
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "security",
            title = "登录与安全",
            keywords = listOf("登录", "安全", "邮箱", "手机", "密码", "实名", "授权", "注销"),
            items = listOf(
                SettingsItemSpec(
                    key = "account-security",
                    icon = Icons.Default.Security,
                    title = "账号安全",
                    summary = "两步验证、通行密钥、恢复码和登录设备",
                    keywords = listOf("账号安全", "两步验证", "通行密钥", "恢复码", "设备", "登录"),
                    onClick = onOpenAccountSecurity,
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "security-center",
                    icon = Icons.Default.Security,
                    title = "登录与安全",
                    summary = "邮箱、手机号、密码和实名认证，在官网管理",
                    keywords = listOf("登录", "安全", "密码", "实名"),
                    onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "authorized-apps",
                    icon = Icons.Default.Security,
                    title = "授权应用",
                    summary = "查看并撤销已授权的第三方应用，在官网管理",
                    keywords = listOf("授权", "应用", "第三方"),
                    onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "delete-account",
                    icon = Icons.Default.Delete,
                    title = "注销账号",
                    summary = "永久删除账号和内容，需要官网验证",
                    keywords = listOf("注销", "删除", "账号"),
                    onClick = { openExternalUrl(context, OFFICIAL_ACCOUNT_DELETION_URL) },
                    showChevron = true,
                    tint = MaterialTheme.colorScheme.error,
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "codemao",
            title = "编程猫互联",
            keywords = listOf("编程猫", "绑定", "作品", "同步"),
            items = listOf(
                SettingsItemSpec(
                    key = "codemao",
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
                    keywords = listOf("编程猫", "绑定", "同步"),
                    onClick = { openExternalUrl(context, OFFICIAL_SETTINGS_URL) },
                    showChevron = true,
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "codechat",
            title = "CodeChat",
            keywords = listOf("CodeChat", "聊天", "消息", "社区"),
            items = listOf(
                SettingsItemSpec(
                    key = "codechat-enabled",
                    icon = Icons.Default.Chat,
                    title = "CodeChat 聊天",
                    summary = "由 CodeChat 提供的社区聊天，开启后显示在底部导航；测试阶段需要访问密码",
                    keywords = listOf("CodeChat", "聊天", "消息", "导航"),
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
        SettingsSectionSpec(
            id = "appearance",
            title = "外观",
            keywords = listOf("外观", "主题", "动态", "取色", "颜色"),
            items = listOf(
                SettingsItemSpec(
                    key = "theme",
                    icon = Icons.Default.ColorLens,
                    title = "主题",
                    summary = themeLabel(preferences.themeMode),
                    keywords = listOf("主题", "浅色", "深色", "跟随系统"),
                    onClick = { showThemeDialog = true },
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "dynamic-color",
                    icon = Icons.Default.DarkMode,
                    title = "动态取色",
                    summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Android 12 及以上使用系统壁纸配色"
                    } else {
                        "当前系统版本不支持，使用 PickCat 配色"
                    },
                    keywords = listOf("动态", "取色", "壁纸", "颜色"),
                    trailing = {
                        Switch(
                            checked = preferences.dynamicColor &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = appViewModel::updateDynamicColor,
                        )
                    },
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "notifications",
            title = "通知",
            keywords = listOf("通知", "后台"),
            items = listOf(
                SettingsItemSpec(
                    key = "background-notifications",
                    icon = Icons.Default.Notifications,
                    title = "后台通知",
                    summary = "约每 15 分钟检查一次",
                    keywords = listOf("通知", "后台"),
                    trailing = {
                        Switch(
                            checked = preferences.backgroundNotifications,
                            onCheckedChange = appViewModel::updateBackgroundNotifications,
                        )
                    },
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "content",
            title = "内容",
            keywords = listOf("内容", "图盘", "媒体", "作品库", "图片"),
            items = listOf(
                SettingsItemSpec(
                    key = "media",
                    icon = Icons.Default.PhotoLibrary,
                    title = "个人图盘",
                    summary = "上传图片、查看已用容量",
                    keywords = listOf("图盘", "图片", "上传"),
                    onClick = onOpenMedia,
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "gallery",
                    icon = Icons.Default.PhotoLibrary,
                    title = "媒体库",
                    summary = "图库与回收站、批量恢复和彻底删除",
                    keywords = listOf("媒体", "图库", "回收站", "删除"),
                    onClick = onOpenGallery,
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "library",
                    icon = Icons.Default.Star,
                    title = "个人作品库",
                    summary = "我发布过的作品和帖子",
                    keywords = listOf("作品库", "作品", "帖子"),
                    onClick = onOpenLibrary,
                    showChevron = true,
                ),
            ),
        ),
        SettingsSectionSpec(
            id = "about",
            title = "关于",
            keywords = listOf("关于", "版本", "更新", "来源", "GitHub", "Kitter", "登录方式"),
            items = listOf(
                SettingsItemSpec(
                    key = "version",
                    icon = Icons.Default.Info,
                    title = "PickCat Android",
                    summary = "Kotlin 与 Jetpack Compose",
                    keywords = listOf("版本", "关于"),
                    trailing = {
                        Text(if (versionName.isBlank()) "开发版" else "v$versionName")
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
                    keywords = listOf("更新", "版本", "检查"),
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
                ),
                SettingsItemSpec(
                    key = "update-source",
                    icon = Icons.Default.Refresh,
                    title = "更新来源",
                    summary = updateSourceLabel(preferences.updateSource),
                    keywords = listOf("更新", "来源", "GitHub", "Kitter"),
                    onClick = { showUpdateSourceDialog = true },
                    showChevron = true,
                ),
                SettingsItemSpec(
                    key = "login-method",
                    icon = Icons.Default.Badge,
                    title = "登录方式",
                    summary = "官方登录页完成验证与 Cookie 会话",
                    keywords = listOf("登录", "Cookie", "会话"),
                ),
            ),
        ),
    ).let { allSections ->
        if (user == null) allSections else allSections + SettingsSectionSpec(
            id = "account",
            title = "账户",
            keywords = listOf("账户", "退出", "登录", "账号"),
            items = listOf(
                SettingsItemSpec(
                    key = "sign-out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    summary = "会清除本地会话与 Cookie",
                    keywords = listOf("退出", "登录", "账号"),
                    onClick = { confirmSignOut = true },
                    tint = MaterialTheme.colorScheme.error,
                ),
            ),
        )
    }

    val filteredSections = sections.map { section ->
        val items = if (section.matches(trimmedQuery)) {
            section.items
        } else {
            section.items.filter { it.matches(trimmedQuery) }
        }
        section.copy(items = items)
    }.filter { it.items.isNotEmpty() }

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
                top = 8.dp,
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
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            if (accountMatches) {
                item(key = "account-header") {
                    SettingsGroup {
                        SettingsAccountHeader(
                            user = user,
                            email = email,
                            onClick = if (user != null) onEditProfile else null,
                        )
                    }
                }
            }

            filteredSections.forEach { section ->
                item(key = "section-${section.id}") {
                    Column {
                        SettingsSectionTitle(section.title)
                        SettingsGroup {
                            section.items.forEachIndexed { index, item ->
                                SettingsRow(item)
                                if (index < section.items.lastIndex || section.footer != null) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            section.footer?.invoke()
                        }
                    }
                }
            }

            if (!accountMatches && filteredSections.isEmpty()) {
                item(key = "settings-empty") {
                    EmptyState(
                        title = "没有找到设置",
                        description = "换个关键词试试。",
                        modifier = Modifier.fillParentMaxHeight(0.55f),
                    )
                }
            }

            item(key = "settings-footer") {
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
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showUpdateSourceDialog) {
        SettingsChoiceSheet(
            title = "更新来源",
            options = listOf(
                "mirror" to "Kitter镜像源",
                "github" to "GitHub Releases",
            ),
            selectedValue = preferences.updateSource,
            onSelect = { value ->
                appViewModel.updateUpdateSource(value)
                showUpdateSourceDialog = false
            },
            onDismiss = { showUpdateSourceDialog = false },
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

@Composable
private fun SettingsAccountHeader(
    user: SessionUser?,
    email: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (user != null) {
            UserAvatar(
                name = user.username,
                url = avatarUrl(user),
                size = 56.dp,
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                user?.username ?: "未登录",
                style = MaterialTheme.typography.titleMedium,
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
private fun SettingsGroup(
    content: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (dark) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (dark) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(item: SettingsItemSpec) {
    val iconContainer = if (item.tint != null) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val iconColor = if (item.tint != null) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
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
            item.showChevron -> {
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
        modifier = if (item.onClick != null) {
            Modifier.clickable(onClick = item.onClick)
        } else {
            Modifier
        },
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
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
