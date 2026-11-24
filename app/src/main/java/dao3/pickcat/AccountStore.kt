package dao3.pickcat

import android.content.Context
import dao3.pickcat.core.network.SessionUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val cookie: String,
)

class AccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _accounts = MutableStateFlow(read())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    fun save(user: SessionUser, cookie: String?) {
        if (user.id.isBlank() || cookie.isNullOrBlank()) return
        val next = (listOf(
            SavedAccount(
                id = user.id,
                username = user.username,
                avatarUrl = user.avatar?.url,
                cookie = cookie,
            ),
        ) + _accounts.value.filterNot { it.id == user.id || it.cookie == cookie })
            .distinctBy { it.id }
            .take(MAX_ACCOUNTS)
        write(next)
        _accounts.value = next
    }

    fun remove(id: String) {
        val next = _accounts.value.filterNot { it.id == id }
        write(next)
        _accounts.value = next
    }

    fun find(id: String): SavedAccount? = _accounts.value.firstOrNull { it.id == id }

    private fun write(accounts: List<SavedAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("username", account.username)
                    .put("avatar", account.avatarUrl.orEmpty())
                    .put("cookie", account.cookie),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    private fun read(): List<SavedAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val cookie = item.optString("cookie")
                    if (id.isBlank() || cookie.isBlank()) continue
                    add(
                        SavedAccount(
                            id = id,
                            username = item.optString("username"),
                            avatarUrl = item.optString("avatar").takeIf { it.isNotBlank() },
                            cookie = cookie,
                        ),
                    )
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "pickcat_accounts"
        const val KEY_ACCOUNTS = "accounts"
        const val MAX_ACCOUNTS = 5
    }
}
