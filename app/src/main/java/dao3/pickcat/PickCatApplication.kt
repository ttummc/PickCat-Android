package dao3.pickcat

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dao3.pickcat.core.data.AppContainer
import okhttp3.OkHttpClient

object AppGraph {
    lateinit var container: AppContainer
        private set
    lateinit var accountStore: AccountStore
        private set

    fun install(context: Application) {
        if (!::container.isInitialized) {
            container = AppContainer(context)
        }
        if (!::accountStore.isInitialized) {
            accountStore = AccountStore(context)
        }
    }
}

class PickCatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.install(this)
        NotificationSyncScheduler.schedule(this)
        NotificationSyncScheduler.syncNow(this)
        val imageHttpClient = OkHttpClient.Builder().build()
        SingletonImageLoader.setSafe { platformContext: PlatformContext ->
            ImageLoader.Builder(platformContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
                }
                .crossfade(true)
                .build()
        }
    }
}
