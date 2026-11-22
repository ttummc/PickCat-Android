package dao3.pickcat.feature.codechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.AppGraph
import dao3.pickcat.core.ui.PickCatImage
import dao3.pickcat.core.ui.UserAvatar
import java.text.DateFormat
import java.util.Date

class CodeChatViewModel : ViewModel() {
    private val repository = AppGraph.codeChat
    val state = repository.state

    init {
        repository.connect()
    }

    fun retry() = repository.retry()

    fun send(text: String) = repository.sendMessage(text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeChatScreen(
    modifier: Modifier = Modifier,
    viewModel: CodeChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CodeChat")
                        Text(
                            "消息服务由 CodeChat 提供",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().imePadding(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(2_000) },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (state.connected) {
                                        "发消息到 CodeChat 公共频道"
                                    } else {
                                        "正在连接 CodeChat"
                                    },
                                )
                            },
                            enabled = state.connected,
                            minLines = 1,
                            maxLines = 4,
                            shape = MaterialTheme.shapes.medium,
                        )
                        FilledIconButton(
                            onClick = {
                                val text = draft.trim()
                                if (text.isNotEmpty()) {
                                    viewModel.send(text)
                                    draft = ""
                                }
                            },
                            enabled = state.connected && draft.isNotBlank(),
                            modifier = Modifier.size(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送消息",
                            )
                        }
                    }
                    Text(
                        "公共频道消息由 CodeChat 提供",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val error = state.error
            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = viewModel::retry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("重试")
                        }
                    }
                }
            }
            if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            "CodeChat 公共频道",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            if (state.connecting) "正在连接 CodeChat" else "还没有消息，发一条试试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        CodeChatMessageRow(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeChatMessageRow(message: CodeChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.fromMe) {
            UserAvatar(
                name = message.senderName,
                url = message.senderAvatar,
                size = 32.dp,
            )
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        ) {
            if (!message.fromMe) {
                Text(
                    message.senderName.ifBlank { "CodeChat 用户" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (message.fromMe) 16.dp else 4.dp,
                    topEnd = if (message.fromMe) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                color = if (message.fromMe) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (message.fromMe) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (!message.imageUrl.isNullOrBlank()) {
                        PickCatImage(
                            url = message.imageUrl,
                            contentDescription = "聊天图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            contentScale = ContentScale.Fit,
                            corner = 10.dp,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.time)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
