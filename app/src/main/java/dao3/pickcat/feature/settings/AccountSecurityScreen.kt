package dao3.pickcat.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SecuritySession(
    val id: String,
    val device: String,
    val network: String?,
    val lastActive: String?,
    val createdAt: String?,
    val expiresAt: String?,
    val current: Boolean,
)

data class AccountSecurityState(
    val loading: Boolean = true,
    val error: String? = null,
    val actionError: String? = null,
    val busy: Boolean = false,
    val secondFactorEnabled: Boolean? = null,
    val methods: List<String> = emptyList(),
    val recoveryCodesRemaining: Int? = null,
    val sessions: List<SecuritySession> = emptyList(),
    val grant: String? = null,
)

class AccountSecurityViewModel : ViewModel() {
    private val _state = MutableStateFlow(AccountSecurityState())
    val state: StateFlow<AccountSecurityState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val userId = AppGraph.container.session.state.value.user?.id
        if (userId.isNullOrBlank()) {
            _state.value = AccountSecurityState(loading = false, error = "请先登录")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, actionError = null)
            try {
                val security = AppGraph.container.api.getAccountSecurity(userId)
                val sessions = runCatching {
                    AppGraph.container.api.getAccountSessions(userId)
                }.getOrNull()
                val authorization = runCatching {
                    AppGraph.container.api.getManagementAuthorization()
                }.getOrNull()
                val headerGrant = runCatching {
                    AppGraph.container.api.getManagementAuthorizationHeader()
                }.getOrNull()
                _state.value = parseSecurity(security, sessions).copy(
                    grant = headerGrant ?: authorization?.let {
                        findGrant(JSONObject(it.toString()))
                    },
                )
            } catch (error: Exception) {
                _state.value = AccountSecurityState(
                    loading = false,
                    error = error.message ?: "无法读取账号安全信息",
                )
            }
        }
    }

    fun revokeSession(sessionId: String) {
        val userId = AppGraph.container.session.state.value.user?.id ?: return
        val grant = _state.value.grant
        if (grant.isNullOrBlank()) {
            _state.value = _state.value.copy(actionError = NEED_VERIFICATION)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                AppGraph.container.api.revokeAccountSession(userId, sessionId, grant)
                load()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    actionError = error.message ?: "退出设备失败",
                )
            }
        }
    }

    fun revokeAll() {
        val userId = AppGraph.container.session.state.value.user?.id ?: return
        val grant = _state.value.grant
        if (grant.isNullOrBlank()) {
            _state.value = _state.value.copy(actionError = NEED_VERIFICATION)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                AppGraph.container.api.revokeAllAccountSessions(userId, grant)
                load()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    actionError = error.message ?: "退出全部设备失败",
                )
            }
        }
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }

    private companion object {
        const val NEED_VERIFICATION = "当前会话没有账号安全管理授权，请先在官网完成身份验证"
    }
}

private fun parseSecurity(security: JsonObject, sessions: JsonObject?): AccountSecurityState {
    val root = runCatching { JSONObject(security.toString()) }.getOrNull()
        ?: return AccountSecurityState(loading = false, error = "账号安全数据格式异常")

    val secondFactor = findBooleanByKey(root, "secondfactor")
        ?: findBooleanByKey(root, "twofactor")
        ?: root.optJSONObject("loginSecondFactor")?.let { firstBoolean(it, "enabled", "active") }
        ?: root.optJSONObject("secondFactor")?.let { firstBoolean(it, "enabled", "active") }

    val methodObjects = findArrays(root, setOf("authenticators", "passkeys", "methods"))
        .flatMap { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
    val methods = methodObjects.mapNotNull { item ->
        sequenceOf("label", "name", "type", "method")
            .map { item.optString(it) }
            .firstOrNull { it.isNotBlank() }
    }.distinct()

    val recoveryArray = findArrayByKey(root, "recoverycodes")
        ?: root.optJSONObject("recoveryCodes")?.optJSONArray("unused")
    val recoveryRemaining = recoveryArray?.length()
        ?: root.optJSONObject("recoveryCodes")?.optInt("remaining", -1)?.takeIf { it >= 0 }

    val sessionRoot = sessions?.let { runCatching { JSONObject(it.toString()) }.getOrNull() }
    val parsedSessions = sessionRoot?.optJSONArray("sessions")?.let(::parseSessions).orEmpty()

    return AccountSecurityState(
        loading = false,
        secondFactorEnabled = secondFactor,
        methods = methods,
        recoveryCodesRemaining = recoveryRemaining,
        sessions = parsedSessions,
    )
}

private fun parseSessions(array: JSONArray): List<SecuritySession> = buildList {
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            SecuritySession(
                id = firstString(item, "id", "sessionId", "sessionRef", "deviceId")
                    ?: "session-$index",
                device = parseDeviceName(item),
                network = firstString(item, "network", "cidr", "ipMask", "ip", "ipAddress"),
                lastActive = firstString(
                    item,
                    "lastActiveAt",
                    "lastSeenAt",
                    "lastActivityAt",
                    "lastUsedAt",
                    "lastActive",
                ),
                createdAt = firstString(item, "createdAt", "created", "loginAt", "issuedAt"),
                expiresAt = firstString(item, "expiresAt", "expireAt", "expires"),
                current = item.optBoolean("current", false) || item.optBoolean("isCurrent", false),
            ),
        )
    }
}

private fun parseDeviceName(item: JSONObject): String {
    val explicit = firstString(item, "deviceName", "device", "browser", "name")
    if (!explicit.isNullOrBlank() && explicit != "desktop" && explicit != "mobile") {
        return explicit
    }
    val userAgent = item.optString("userAgent")
    return when {
        userAgent.contains("Chromium", ignoreCase = true) -> "Chromium 浏览器"
        userAgent.contains("Edg", ignoreCase = true) -> "Edge 浏览器"
        userAgent.contains("Chrome", ignoreCase = true) -> "Chrome 浏览器"
        userAgent.contains("Firefox", ignoreCase = true) -> "Firefox 浏览器"
        userAgent.contains("Safari", ignoreCase = true) -> "Safari 浏览器"
        userAgent.contains("Android", ignoreCase = true) -> "Android 设备"
        userAgent.contains("iPhone", ignoreCase = true) -> "iPhone"
        else -> "未知设备"
    }
}

private fun firstString(root: JSONObject, vararg keys: String): String? =
    keys.asSequence().map { root.optString(it) }.firstOrNull { it.isNotBlank() }

private fun firstBoolean(root: JSONObject, vararg keys: String): Boolean? {
    for (key in keys) if (root.has(key)) return root.optBoolean(key)
    return null
}

private fun findGrant(root: JSONObject): String? {
    val direct = firstString(root, "grant", "token", "authorization", "managementWriteAuthorization")
    if (!direct.isNullOrBlank()) return direct
    val nested = root.optJSONObject("managementWriteAuthorization")
    return nested?.let { firstString(it, "grant", "token", "value") }
}

private fun findBooleanByKey(root: JSONObject, needle: String): Boolean? {
    val names = root.keys()
    while (names.hasNext()) {
        val key = names.next()
        val value = root.opt(key)
        val keyMatches = key.contains(needle, ignoreCase = true)
        if (keyMatches && value is Boolean) return value
        if (keyMatches && value is JSONObject) {
            firstBoolean(value, "enabled", "active", "required")?.let { return it }
        }
        if (value is JSONObject) {
            findBooleanByKey(value, needle)?.let { return it }
        }
    }
    return null
}

private fun findArrays(root: JSONObject, names: Set<String>): List<JSONArray> {
    val found = mutableListOf<JSONArray>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = root.opt(key)
        if (key.lowercase() in names && value is JSONArray) found += value
        if (value is JSONObject) found += findArrays(value, names)
    }
    return found
}

private fun findArrayByKey(root: JSONObject, needle: String): JSONArray? {
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = root.opt(key)
        if (key.contains(needle, ignoreCase = true) && value is JSONArray) return value
        if (value is JSONObject) findArrayByKey(value, needle)?.let { return it }
    }
    return null
}

private val DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
private val RELATIVE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private fun formatTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(raw)
        DISPLAY_TIME.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrNull() ?: raw
}

private fun formatRelative(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(raw)
        val now = Instant.now()
        val days = java.time.Duration.between(instant, now).toDays()
        when {
            days > 0 -> "${days}天前"
            now.isAfter(instant) -> "刚刚"
            else -> "约 ${java.time.Duration.between(now, instant).toDays()}天后"
        }
    }.getOrNull() ?: raw
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    viewModel: AccountSecurityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.actionError) {
        if (!state.actionError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(state.actionError.orEmpty())
            viewModel.clearActionError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("账号安全") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> item {
                    Text(
                        "正在读取账号安全信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error != null -> item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("无法读取账号安全信息", style = MaterialTheme.typography.titleSmall)
                            Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = viewModel::load) { Text("重试") }
                        }
                    }
                }
                else -> {
                    item { SecurityOverview(state) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "登录设备",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.sessions.isNotEmpty()) {
                                TextButton(
                                    onClick = { confirmRevoke = "all" },
                                    enabled = !state.busy,
                                ) { Text("退出全部设备") }
                            }
                        }
                    }
                    if (state.sessions.isEmpty()) {
                        item {
                            Text(
                                "没有读取到登录设备信息。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.sessions, key = { it.id }) { session ->
                            val expanded = expandedId == session.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    session.device,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (session.current) {
                                                    Text(
                                                        "当前设备",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(start = 8.dp),
                                                    )
                                                }
                                            }
                                            val network = session.network
                                            val lastActive = formatTime(session.lastActive)
                                            if (!network.isNullOrBlank() || lastActive != null) {
                                                Text(
                                                    listOfNotNull(
                                                        network?.let { "网段 $it" },
                                                        lastActive?.let { "最近活动 $it" },
                                                    ).joinToString("  "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            expandedId = if (expanded) null else session.id
                                        }) {
                                            Icon(
                                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (expanded) "收起设备详情" else "展开设备详情",
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                                        exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                            DetailRow("设备编号", session.id.take(8))
                                            formatRelative(session.createdAt)?.let { DetailRow("登录时间", it) }
                                            formatRelative(session.expiresAt)?.let { DetailRow("会话到期", it) }
                                            OutlinedButton(
                                                onClick = { confirmRevoke = session.id },
                                                enabled = !state.busy,
                                                modifier = Modifier.padding(top = 4.dp),
                                            ) {
                                                Text(if (session.current) "退出当前设备" else "退出该设备")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { openExternalUrl(context, "https://pickcat.dao3.fun/settings") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("在官网管理账号安全")
                        }
                    }
                }
            }
        }
    }

    confirmRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            title = { Text(if (target == "all") "退出全部设备？" else "退出这个设备？") },
            text = {
                Text(
                    if (target == "all") {
                        "退出后所有设备都需要重新登录。"
                    } else {
                        "该设备的登录会话会立即失效。"
                    },
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        confirmRevoke = null
                        if (target == "all") viewModel.revokeAll() else viewModel.revokeSession(target)
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SecurityOverview(state: AccountSecurityState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text("安全状态", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                when (state.secondFactorEnabled) {
                    true -> "登录二次验证已开启"
                    false -> "登录二次验证未开启"
                    null -> "登录二次验证状态不可用"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.methods.isNotEmpty()) {
                Text(
                    "验证方式：${state.methods.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.recoveryCodesRemaining?.let { remaining ->
                Text(
                    "剩余恢复码：$remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 64.dp, height = 20.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
