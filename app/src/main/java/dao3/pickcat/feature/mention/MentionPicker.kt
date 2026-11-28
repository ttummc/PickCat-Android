package dao3.pickcat.feature.mention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dao3.pickcat.AppGraph
import dao3.pickcat.core.network.UserProfile
import dao3.pickcat.core.ui.UserAvatar
import dao3.pickcat.core.ui.avatarUrl
import kotlinx.coroutines.delay

/**
 * Material 3 bottom sheet that searches PickCat users and returns the chosen
 * username so the caller can insert `@昵称` into a draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionPickerSheet(
    onPick: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            results = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(300)
        runCatching { AppGraph.container.api.searchUsers(keyword, limit = 20).items }
            .onSuccess { items ->
                results = items
                error = null
            }
            .onFailure { throwable ->
                error = throwable.message ?: "搜索失败"
            }
        loading = false
    }

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
                "提及用户",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                placeholder = { Text("搜索昵称") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                error != null -> Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                query.isBlank() -> Text(
                    "输入昵称开始搜索",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                results.isEmpty() -> Text(
                    "没有找到用户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = { Text(user.username) },
                            supportingContent = user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                                { Text(bio, maxLines = 1) }
                            },
                            leadingContent = {
                                UserAvatar(
                                    name = user.username,
                                    url = avatarUrl(user),
                                    size = 40.dp,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(user)
                                }
                                .padding(horizontal = 8.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/** Text inserted into a draft when a mention is chosen. PickCat mentions use the user id. */
fun mentionText(userId: String): String = "@{$userId} "

/** Appends a mention to the end of the current draft. */
fun appendMention(current: String, userId: String): String {
    if (current.isBlank()) return mentionText(userId)
    val separator = if (current.last().isWhitespace()) "" else " "
    return current + separator + mentionText(userId)
}

/** Inserts a mention at the caret of a text field. */
fun insertMention(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    userId: String,
): Pair<String, Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val prefix = text.substring(0, start)
    val suffix = text.substring(end)
    val needsLeadingSpace = prefix.isNotEmpty() && !prefix.last().isWhitespace()
    val insertion = (if (needsLeadingSpace) " " else "") + mentionText(userId)
    val updated = prefix + insertion + suffix
    return updated to (prefix.length + insertion.length)
}
