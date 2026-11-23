package dao3.pickcat.feature.settings

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

data class SecuritySession(
    val id: String,
    val device: String,
    val lastActive: String?,
    val current: Boolean,
)

data class AccountSecurityState(
    val loading: Boolean = true,
    val error: String? = null,
    val secondFactorEnabled: Boolean? = null,
    val methods: List<String> = emptyList(),
    val recoveryCodesRemaining: Int? = null,
    val sessions: List<SecuritySession> = emptyList(),
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
            _state.value = AccountSecurityState(loading = true)
            try {
                val security = AppGraph.container.api.getAccountSecurity(userId)
                val sessionJson = runCatching {
                    AppGraph.container.api.getAccountSessions(userId)
                }.getOrNull()
                _state.value = parseSecurity(security, sessionJson)
            } catch (error: Exception) {
                _state.value = AccountSecurityState(
                    loading = false,
                    error = error.message ?: "无法读取账号安全信息",
                )
            }
        }
    }
}

private fun parseSecurity(security: JsonObject, sessions: JsonObject?): AccountSecurityState {
    val root = runCatching { JSONObject(security.toString()) }.getOrNull()
        ?: return AccountSecurityState(loading = false, error = "账号安全数据格式异常")

    val secondFactor = firstBoolean(
        root,
        "secondFactorEnabled",
        "loginSecondFactorEnabled",
        "twoFactorEnabled",
    ) ?: root.optJSONObject("loginSecondFactor")?.let {
        firstBoolean(it, "enabled", "active")
    } ?: root.optJSONObject("secondFactor")?.let {
        firstBoolean(it, "enabled", "active")
    }

    val methodObjects = listOf("authenticators", "passkeys", "methods")
        .mapNotNull { key -> root.optJSONArray(key) }
        .flatMap { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
    val methods = methodObjects.mapNotNull { item ->
        val label = sequenceOf("label", "name", "type", "method")
            .map { item.optString(it) }
            .firstOrNull { it.isNotBlank() }
        label
    }.distinct()

    val recoveryArray = root.optJSONArray("recoveryCodes")
        ?: root.optJSONObject("recoveryCodes")?.optJSONArray("unused")
    val recoveryRemaining = recoveryArray?.length()
        ?: root.optJSONObject("recoveryCodes")?.optInt("remaining", -1)
            ?.takeIf { it >= 0 }

    val sessionRoot = sessions?.let { runCatching { JSONObject(it.toString()) }.getOrNull() }
    val sessionArray = sessionRoot?.optJSONArray("sessions")
    val parsedSessions = sessionArray?.let { parseSessions(it) }.orEmpty()

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
                id = item.optString("id"),
                device = sequenceOf(
                    item.optString("deviceName"),
                    item.optString("device"),
                    item.optString("userAgent"),
                ).firstOrNull { it.isNotBlank() } ?: "未知设备",
                lastActive = sequenceOf(
                    item.optString("lastActiveAt"),
                    item.optString("lastSeenAt"),
                    item.optString("createdAt"),
                ).firstOrNull { it.isNotBlank() },
                current = item.optBoolean("current", false),
            ),
        )
    }
}

private fun firstBoolean(root: JSONObject, vararg keys: String): Boolean? {
    for (key in keys) {
        if (root.has(key)) return root.optBoolean(key)
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    viewModel: AccountSecurityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("账号安全") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
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
                    item {
                        SecurityOverview(state)
                    }
                    item {
                        Text(
                            "登录设备",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(session.device, maxLines = 1)
                                        if (session.current) {
                                            Text(
                                                "当前设备",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                },
                                supportingContent = session.lastActive?.let {
                                    { Text("最近活动：$it") }
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            )
                            HorizontalDivider()
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                openExternalUrl(context, "https://pickcat.dao3.fun/settings")
                            },
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
}

@Composable
private fun SecurityOverview(state: AccountSecurityState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
