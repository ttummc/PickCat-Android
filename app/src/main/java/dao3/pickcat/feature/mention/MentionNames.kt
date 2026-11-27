package dao3.pickcat.feature.mention

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dao3.pickcat.AppGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

private val MENTION_USER_ID = Regex(
    """@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
)

/** Cache of `ccUid`/user id to nickname for mention rendering. */
private object MentionNameCache {
    private val names = ConcurrentHashMap<String, String>()

    fun get(id: String): String? = names[id]

    fun put(id: String, name: String) {
        names[id] = name
    }
}

private fun mentionedUserIds(html: String?): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    return MENTION_USER_ID.findAll(html)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

/**
 * PickCat mentions are stored as `@<user id>`. Replace them with the user's
 * nickname for display, keeping a tappable link back to the profile.
 */
@Composable
fun rememberMentionHtml(html: String?): String? {
    if (html.isNullOrBlank()) return html
    val ids = remember(html) { mentionedUserIds(html) }
    var names by remember(html) {
        mutableStateOf(ids.mapNotNull { id -> MentionNameCache.get(id)?.let { id to it } }.toMap())
    }

    LaunchedEffect(html) {
        val missing = ids.filter { MentionNameCache.get(it) == null }
        if (missing.isEmpty()) return@LaunchedEffect
        val fetched = coroutineScope {
            missing.map { id ->
                async {
                    runCatching { AppGraph.container.api.getUser(id).username }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name ->
                            MentionNameCache.put(id, name)
                            id to name
                        }
                }
            }.awaitAll().filterNotNull()
        }
        if (fetched.isNotEmpty()) {
            names = names + fetched.toMap()
        }
    }

    return remember(html, names) {
        if (names.isEmpty()) {
            html
        } else {
            MENTION_USER_ID.replace(html) { match ->
                val id = match.groupValues[1]
                val name = names[id] ?: return@replace match.value
                """<a href="pickcat://user/$id">@$name</a>"""
            }
        }
    }
}
