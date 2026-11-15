package dao3.pickcat.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.pickCatPreferences by preferencesDataStore(name = "pickcat_preferences")

data class AppPreferences(
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val backgroundNotifications: Boolean = true,
    val gateCompletedUserIds: Set<String> = emptySet(),
    val updateSource: String = "github",
)

class UserPreferences(private val context: Context) {
    private val store: DataStore<Preferences> = context.pickCatPreferences

    val preferences: Flow<AppPreferences> = store.data.map { values ->
        AppPreferences(
            themeMode = values[KEY_THEME] ?: "system",
            dynamicColor = values[KEY_DYNAMIC] ?: true,
            backgroundNotifications = values[KEY_BACKGROUND_NOTIFICATIONS] ?: true,
            gateCompletedUserIds = values[KEY_GATE_COMPLETED] ?: emptySet(),
            updateSource = values[KEY_UPDATE_SOURCE] ?: "github",
        )
    }

    val searchHistory: Flow<List<String>> = store.data.map { values ->
        parseList(values[KEY_SEARCH_HISTORY])
    }

    suspend fun setThemeMode(mode: String) {
        store.edit { it[KEY_THEME] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[KEY_DYNAMIC] = enabled }
    }

    suspend fun setBackgroundNotifications(enabled: Boolean) {
        store.edit { it[KEY_BACKGROUND_NOTIFICATIONS] = enabled }
    }

    suspend fun markGateCompleted(userId: String) {
        store.edit { values ->
            values[KEY_GATE_COMPLETED] =
                (values[KEY_GATE_COMPLETED] ?: emptySet()) + userId
        }
    }

    suspend fun setUpdateSource(source: String) {
        store.edit { it[KEY_UPDATE_SOURCE] = source }
    }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        store.edit { values ->
            val current = parseList(values[KEY_SEARCH_HISTORY])
            val next = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(12)
            values[KEY_SEARCH_HISTORY] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun clearSearchHistory() {
        store.edit { it.remove(KEY_SEARCH_HISTORY) }
    }

    private fun parseList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(SEPARATOR).filter { it.isNotBlank() }
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_BACKGROUND_NOTIFICATIONS = booleanPreferencesKey("background_notifications")
        val KEY_GATE_COMPLETED = stringSetPreferencesKey("gate_completed_user_ids")
        val KEY_UPDATE_SOURCE = stringPreferencesKey("update_source")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
    }
}

private val Context.pickCatDrafts by preferencesDataStore(name = "pickcat_drafts")

data class TopicDraft(
    val title: String = "",
    val markdown: String = "",
    val tagIds: List<String> = emptyList(),
    val savedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = title.isBlank() && markdown.isBlank()
}

class DraftStore(private val context: Context) {
    private val store: DataStore<Preferences> = context.pickCatDrafts

    val draft: Flow<TopicDraft> = store.data.map { values ->
        TopicDraft(
            title = values[KEY_TITLE].orEmpty(),
            markdown = values[KEY_MARKDOWN].orEmpty(),
            tagIds = parseTags(values[KEY_TAGS]),
            savedAt = values[KEY_SAVED_AT] ?: 0L,
        )
    }

    suspend fun save(title: String, markdown: String, tagIds: List<String>) {
        store.edit { values ->
            if (title.isBlank() && markdown.isBlank()) {
                values.remove(KEY_TITLE)
                values.remove(KEY_MARKDOWN)
                values.remove(KEY_TAGS)
                values.remove(KEY_SAVED_AT)
            } else {
                values[KEY_TITLE] = title
                values[KEY_MARKDOWN] = markdown
                values[KEY_TAGS] = JSONArray(tagIds).toString()
                values[KEY_SAVED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_TITLE = stringPreferencesKey("draft_title")
        val KEY_MARKDOWN = stringPreferencesKey("draft_markdown")
        val KEY_TAGS = stringPreferencesKey("draft_tags")
        val KEY_SAVED_AT = androidx.datastore.preferences.core.longPreferencesKey("draft_saved_at")
    }
}
