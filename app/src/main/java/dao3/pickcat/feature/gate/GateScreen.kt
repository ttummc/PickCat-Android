package dao3.pickcat.feature.gate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.R
import dao3.pickcat.core.network.ApiException
import dao3.pickcat.core.network.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

const val KITTER_SUPPORT_ID = "01a0faca-3c45-7654-bea1-4147d7af5165"
const val EXPERIENCE_TOPIC_ID = "01a1ddfe-e1d2-7c5c-a839-d21371f29089"

val GATE_COMMENTS = listOf(
    "已亲身体验",
    "支持一下",
    "来学习啦",
    "感谢分享",
    "很有帮助",
    "收藏了",
    "期待更多",
    "内容不错",
    "路过支持",
    "认真看完",
    "写得很清楚",
    "这个点子不错",
    "学到了",
    "谢谢作者",
    "继续加油",
    "很有启发",
    "讲得明白",
    "不错不错",
    "支持官方",
    "体验一下",
    "已阅",
    "好帖",
    "赞一个",
    "收藏学习",
    "有帮助",
    "感谢整理",
    "内容很实在",
    "关注了",
    "来打卡",
    "写得用心",
    "值得一看",
    "支持支持",
    "这个很好",
    "受教了",
    "正在学习",
    "讲得透彻",
    "感谢解答",
    "很有价值",
    "已收藏",
    "期待更新",
    "路过看看",
    "很不错",
    "前排支持",
    "感谢分享经验",
    "学到新东西",
    "观点很好",
    "内容扎实",
    "值得推荐",
    "继续创作",
    "体验完成",
)

data class GateUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val quotaExemptOffered: Boolean = false,
)

class GateViewModel : ViewModel() {
    private val api = AppGraph.container.api
    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    fun complete(currentUserId: String, onCompleted: () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val actualUserId = AppGraph.container.session.state.value.user?.id
                    ?: currentUserId
                if (actualUserId != KITTER_SUPPORT_ID) {
                    val profile = api.getUser(KITTER_SUPPORT_ID)
                    if (profile.viewerState?.following != true) {
                        api.followUser(KITTER_SUPPORT_ID, true)
                    }
                }

                val topic = api.getTopic(EXPERIENCE_TOPIC_ID)
                val firstPost = topic.firstPost ?: error("无法读取指定帖子")
                if (firstPost.viewerState?.liked != true) {
                    api.likePost(firstPost.id, true)
                }

                val alreadyCommented = runCatching {
                    api.getTopicPosts(
                        EXPERIENCE_TOPIC_ID,
                        limit = 50,
                        sort = "latest",
                    ).items.containsGateComment(actualUserId)
                }.getOrDefault(false)
                if (!alreadyCommented) {
                    val comment = GATE_COMMENTS[Random.nextInt(GATE_COMMENTS.size)]
                    api.createReply(
                        EXPERIENCE_TOPIC_ID,
                        comment,
                        firstPost.postNumber,
                    )
                }

                onCompleted()
                _state.update { it.copy(busy = false) }
            } catch (error: Exception) {
                if (error.isQuotaInsufficient()) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = null,
                            quotaExemptOffered = true,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: "体验确认失败，请重试",
                        )
                    }
                }
            }
        }
    }

    fun dismissQuotaOffer() {
        _state.update { it.copy(quotaExemptOffered = false) }
    }
}

private fun Throwable.isQuotaInsufficient(): Boolean {
    val apiError = this as? ApiException ?: return false
    val text = "${apiError.code.orEmpty()} ${apiError.message}".lowercase()
    return listOf(
        "quota",
        "额度",
        "配额",
        "token",
        "积分",
        "次数",
        "不足",
        "insufficient",
        "exceeded",
        "rate limit",
        "participation",
        "level",
    ).any { keyword -> text.contains(keyword) }
}

private fun List<Post>.containsGateComment(userId: String): Boolean =
    any { post ->
        (post.author.id == userId && GATE_COMMENTS.any { it in post.cookedHtml }) ||
            post.children.containsGateComment(userId)
    }

@Composable
fun GateScreen(
    currentUserId: String,
    onCompleted: () -> Unit,
    onQuotaExempt: () -> Unit,
) {
    BackHandler(enabled = true) {}
    val viewModel: GateViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.pickcat_logo),
                    contentDescription = "PickCat",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "开始使用 PickCat",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "完成以下体验确认后即可继续",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        GateStep(
                            icon = Icons.Default.PersonAdd,
                            title = "关注 Kitter支持",
                            description = "关注官方支持账号",
                        )
                        GateStep(
                            icon = Icons.Default.Favorite,
                            title = "点赞指定帖子",
                            description = "为指定帖子点赞",
                        )
                        GateStep(
                            icon = Icons.Default.ChatBubbleOutline,
                            title = "发布体验评论",
                            description = "从预选评论中随机发布一条",
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        viewModel.complete(currentUserId) {
                            onCompleted()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !state.busy,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("一键完成并进入")
                    }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (state.quotaExemptOffered) {
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaOffer,
            title = { Text("额度不足") },
            text = {
                Text("当前账号额度不足，无法完成关注、点赞或评论。可以豁免 3 天后继续使用。")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissQuotaOffer()
                    onQuotaExempt()
                }) { Text("豁免 3 天") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuotaOffer) {
                    Text("稍后重试")
                }
            },
        )
    }
}

@Composable
private fun GateStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
