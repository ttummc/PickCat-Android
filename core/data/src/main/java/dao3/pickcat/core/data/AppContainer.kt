package dao3.pickcat.core.data

import android.content.Context
import dao3.pickcat.core.network.PersistentCookieJar
import dao3.pickcat.core.network.PickCatApi
import kotlinx.coroutines.flow.MutableSharedFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val cookieJar = PersistentCookieJar(appContext)
    val authEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val api = PickCatApi(cookieJar) {
        authEvents.tryEmit(Unit)
    }
    val session = SessionRepository(appContext, api, cookieJar)
    val preferences = UserPreferences(appContext)
    val drafts = DraftStore(appContext)
    val networkMonitor = NetworkMonitor(appContext)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }

        fun getOrNull(): AppContainer? = instance
    }
}
