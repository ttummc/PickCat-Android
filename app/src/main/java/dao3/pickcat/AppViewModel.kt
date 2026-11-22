package dao3.pickcat

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dao3.pickcat.core.data.AppPreferences
import dao3.pickcat.core.ui.theme.ThemeMode
import dao3.pickcat.core.network.NotificationSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val container = AppGraph.container
    val sessionRepository = container.session
    val api = container.api
    val preferencesRepository = container.preferences

    val session = container.session.state
    val isOnline: StateFlow<Boolean> = container.networkMonitor.isOnline
    val maintenance: StateFlow<Boolean> = container.maintenance
    val preferences: StateFlow<AppPreferences> = container.preferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
    val searchHistory = container.preferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    private val _summary = MutableStateFlow<NotificationSummary?>(null)
    val summary: StateFlow<NotificationSummary?> = _summary.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var announcedExpiry = false
    private var foreground = true

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            foreground = true
            if (session.value.user != null) refreshUnread()
        }

        override fun onStop(owner: LifecycleOwner) {
            foreground = false
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        viewModelScope.launch {
            container.networkMonitor.isOnline.collect { online ->
                if (!online) {
                    _messages.tryEmit("当前没有网络连接")
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.restore()
        }
        viewModelScope.launch {
            session.collectLatest { state ->
                if (state.user != null) {
                    announcedExpiry = false
                    refreshUnread()
                } else {
                    _unreadCount.value = 0
                    _summary.value = null
                }
            }
        }
        viewModelScope.launch {
            container.authEvents.collect {
                if (session.value.user != null) {
                    sessionRepository.expireSession()
                    if (!announcedExpiry) {
                        announcedExpiry = true
                        _messages.tryEmit("登录状态已失效，请重新登录")
                    }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                if (foreground && session.value.user != null) refreshUnread()
            }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onCleared()
    }

    fun refreshUnread() {
        viewModelScope.launch {
            runCatching { api.getNotificationSummary() }
                .onSuccess {
                    _summary.value = it
                    _unreadCount.value = it.unreadCount.coerceAtLeast(0)
                }
        }
    }

    suspend fun tryLoginFromWebView() = sessionRepository.pollWebViewLogin()

    suspend fun retrySession() = sessionRepository.retryRestore()

    fun logout() {
        viewModelScope.launch {
            val confirmed = sessionRepository.signOut()
            _messages.tryEmit(if (confirmed) "已退出登录" else "已退出登录（服务器未确认）")
        }
    }

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(
                when (mode) {
                    ThemeMode.SYSTEM -> "system"
                    ThemeMode.LIGHT -> "light"
                    ThemeMode.DARK -> "dark"
                },
            )
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun updateBackgroundNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBackgroundNotifications(enabled) }
    }

    fun updateCodeChatEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCodeChatEnabled(enabled) }
    }

    fun updateUpdateSource(source: String) {
        viewModelScope.launch { preferencesRepository.setUpdateSource(source) }
    }

    fun recordSearch(query: String) {
        viewModelScope.launch { preferencesRepository.recordSearch(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { preferencesRepository.clearSearchHistory() }
    }

    fun notifyMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count.coerceAtLeast(0)
    }
}

fun String.toThemeMode(): ThemeMode = when (this) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}
