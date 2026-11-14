package dao3.pickcat.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.BadgeGrant
import dao3.pickcat.core.network.UserProfile
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.core.ui.LoadingState
import dao3.pickcat.core.ui.PickCatTextField
import dao3.pickcat.core.ui.UserAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val profile: UserProfile? = null,
    val region: String = "",
    val bio: String = "",
    val showFollowingList: Boolean = true,
    val showFollowersList: Boolean = true,
    val badges: List<BadgeGrant> = emptyList(),
    val badgeMode: String = "AUTO_LATEST",
    val badgeId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class EditProfileViewModel(private val userId: String) : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.getUser(userId) }
                .onSuccess { profile ->
                    val badges = runCatching { api.getUserBadges(userId, 50).items }
                        .getOrDefault(emptyList())
                    val display = runCatching { api.getBadgeDisplay(userId) }.getOrNull()
                    _state.update {
                        it.copy(
                            profile = profile,
                            region = profile.region.orEmpty(),
                            bio = profile.bio.orEmpty(),
                            showFollowingList = profile.showFollowingList ?: true,
                            showFollowersList = profile.showFollowersList ?: true,
                            badges = badges,
                            badgeMode = display?.mode ?: "AUTO_LATEST",
                            badgeId = display?.badgeId,
                            loading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "无法加载个人资料")
                    }
                }
        }
    }

    fun setRegion(value: String) {
        _state.update { it.copy(region = value.take(40)) }
    }

    fun setBio(value: String) {
        _state.update { it.copy(bio = value.take(120)) }
    }

    fun setShowFollowingList(value: Boolean) {
        _state.update { it.copy(showFollowingList = value) }
    }

    fun setShowFollowersList(value: Boolean) {
        _state.update { it.copy(showFollowersList = value) }
    }

    fun setBadgeMode(mode: String) {
        _state.update {
            it.copy(
                badgeMode = mode,
                badgeId = if (mode == "SPECIFIC") (it.badgeId ?: it.badges.firstOrNull()?.badgeId) else null,
            )
        }
    }

    fun setBadgeId(id: String) {
        _state.update { it.copy(badgeId = id) }
    }

    fun save(onSaved: () -> Unit) {
        val snapshot = _state.value
        if (snapshot.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                api.updateUserProfile(
                    userId,
                    snapshot.region.trim(),
                    snapshot.bio.trim(),
                    showFollowingList = snapshot.showFollowingList,
                    showFollowersList = snapshot.showFollowersList,
                )
                val mode = if (snapshot.badgeMode == "SPECIFIC" && snapshot.badgeId.isNullOrBlank()) {
                    "AUTO_LATEST"
                } else {
                    snapshot.badgeMode
                }
                api.updateBadgeDisplay(
                    userId,
                    mode,
                    if (mode == "SPECIFIC") snapshot.badgeId else null,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                onSaved()
            }.onFailure { error ->
                _state.update {
                    it.copy(saving = false, error = error.message ?: "保存失败")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(
        key = "edit-profile-$userId",
        factory = viewModelFactory { initializer { EditProfileViewModel(userId) } },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("编辑个人资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.save(onBack) },
                        enabled = !state.saving && state.profile != null,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("保存")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.profile == null -> ErrorState(
                message = state.error ?: "无法加载个人资料",
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UserAvatar(state.profile, 72.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            state.profile?.username.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "用户名修改需要验证码，请在网页端完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PickCatTextField(
                    value = state.region,
                    onValueChange = viewModel::setRegion,
                    modifier = Modifier.fillMaxWidth(),
                    label = "地区",
                    placeholder = "例如：中国香港",
                    supportingText = "最多 40 字",
                    singleLine = true,
                )
                PickCatTextField(
                    value = state.bio,
                    onValueChange = viewModel::setBio,
                    modifier = Modifier.fillMaxWidth(),
                    label = "个人简介",
                    placeholder = "介绍一下自己或正在创作的内容",
                    supportingText = "${state.bio.length}/120",
                    minLines = 4,
                    maxLines = 6,
                )
                Text("主页展示", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "展示关注的人列表",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.showFollowingList,
                        onCheckedChange = viewModel::setShowFollowingList,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "展示粉丝列表",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.showFollowersList,
                        onCheckedChange = viewModel::setShowFollowersList,
                    )
                }
                Text("展示徽章", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        "AUTO_LATEST" to "自动展示最新",
                        "NONE" to "不展示",
                        "SPECIFIC" to "指定徽章",
                    )
                    modes.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.badgeMode == value,
                            onClick = { viewModel.setBadgeMode(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            enabled = value != "SPECIFIC" || state.badges.isNotEmpty(),
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
                if (state.badgeMode == "SPECIFIC" && state.badges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.badges.forEach { grant ->
                            FilterChip(
                                selected = state.badgeId == grant.badgeId,
                                onClick = { viewModel.setBadgeId(grant.badgeId) },
                                label = { Text(grant.badge?.name ?: grant.badgeId) },
                            )
                        }
                    }
                }
                if (!state.error.isNullOrBlank()) {
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
