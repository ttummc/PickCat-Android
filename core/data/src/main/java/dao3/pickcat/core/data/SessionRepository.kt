package dao3.pickcat.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import dao3.pickcat.core.network.ApiException
import dao3.pickcat.core.network.PICKCAT_ORIGIN
import dao3.pickcat.core.network.SessionUser
import dao3.pickcat.core.network.PersistentCookieJar
import dao3.pickcat.core.network.PickCatApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionUiState(
    val ready: Boolean = false,
    val loading: Boolean = true,
    val user: SessionUser? = null,
    val error: String? = null,
) {
    val signedIn: Boolean get() = user != null
}

class SessionRepository(
    private val context: Context,
    private val api: PickCatApi,
    private val cookieJar: PersistentCookieJar,
) {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Called once at startup. A 401 is a normal signed-out state, never an error dialog. */
    suspend fun restore() {
        mutex.withLock {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = api.getSession()
                val user = session.user
                if (user != null) {
                    _state.value = SessionUiState(ready = true, loading = false, user = user)
                } else {
                    _state.value = SessionUiState(ready = true, loading = false)
                }
            } catch (error: ApiException) {
                if (error.status == 401 || error.status == 403) {
                    // Stale cookies must not be offered to the official login page on
                    // the next launch. A 401 at startup is a normal signed-out state.
                    clearLocalSession()
                } else {
                    _state.value = SessionUiState(
                        ready = true,
                        loading = false,
                        error = error.message,
                    )
                }
            } catch (error: Exception) {
                _state.value = SessionUiState(
                    ready = true,
                    loading = false,
                    error = error.message ?: "无法连接 PickCat",
                )
            }
        }
    }

    /**
     * Copies the WebView's cookie string into OkHttp and asks /session whether the
     * login has completed. Returns the signed-in user, or null if the official page
     * has not finished authenticating yet.
     */
    suspend fun pollWebViewLogin(): SessionUser? {
        val header = withContext(Dispatchers.Main) {
            @SuppressLint("SetJavaScriptEnabled")
            val manager = CookieManager.getInstance()
            val originCookies = manager.getCookie(PICKCAT_ORIGIN).orEmpty()
            val apiCookies = manager.getCookie("$PICKCAT_ORIGIN/api/v1").orEmpty()
            sequenceOf(originCookies, apiCookies)
                .filter { it.isNotBlank() }
                .joinToString("; ")
        }
        if (header.isNullOrBlank()) return null
        val cookies = header.split(';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        cookieJar.syncFromWebView(cookies.joinToString("; "))
        return try {
            val session = api.getSession()
            val user = session.user ?: return null
            _state.value = SessionUiState(ready = true, loading = false, user = user)
            user
        } catch (error: ApiException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshAfterWebLogin(): SessionUser? = pollWebViewLogin()

    suspend fun pickcatSessionCookie(): String? = withContext(Dispatchers.Main) {
        val webCookie = CookieManager.getInstance()
            .getCookie(PICKCAT_ORIGIN)
            .orEmpty()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("pickcat_session=") }
        webCookie ?: cookieJar.peekSessionCookie()?.let { "${it.name}=${it.value}" }
    }

    fun forceSignedOut() {
        _state.value = SessionUiState(ready = true, loading = false)
    }

    suspend fun signOut(): Boolean {
        var serverConfirmed = true
        try {
            api.deleteSession()
        } catch (_: Exception) {
            serverConfirmed = false
        }
        clearLocalSession()
        return serverConfirmed
    }

    suspend fun expireSession() {
        clearLocalSession()
    }

    private suspend fun clearLocalSession() {
        cookieJar.clear()
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        _state.value = SessionUiState(ready = true, loading = false)
    }

    suspend fun retryRestore() {
        restore()
    }
}
