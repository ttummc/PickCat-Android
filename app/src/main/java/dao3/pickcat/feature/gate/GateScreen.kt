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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dao3.pickcat.core.network.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val KITTER_SUPPORT_ID = "01a0faca-3c45-7654-bea1-4147d7af5165"
const val EXPERIENCE_TOPIC_ID = "01a1ddfe-e1d2-7c5c-a839-d21371f29089"
private const val EXPERIENCE_COMMENT = "已亲身体验"

data class GateUiState(
    val busy: Boolean = false,
    val error: String? = null,
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
                    ).items.containsExperienceComment(actualUserId)
                }.getOrDefault(false)
                if (!alreadyCommented) {
                    api.createReply(
                        EXPERIENCE_TOPIC_ID,
                        EXPERIENCE_COMMENT,
                        firstPost.postNumber,
                    )
                }

                onCompleted()
                _state.update { it.copy(busy = false) }
            } catch (error: Exception) {
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

private fun List<Post>.containsExperienceComment(userId: String): Boolean =
    any { post ->
        (post.author.id == userId && post.cookedHtml.contains(EXPERIENCE_COMMENT)) ||
            post.children.containsExperienceComment(userId)
    }

@Composable
fun GateScreen(
    currentUserId: String,
    onCompleted: () -> Unit,
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
                            title = "评论“已亲身体验”",
                            description = "自动发布体验评论",
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
