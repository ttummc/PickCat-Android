package dao3.pickcat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.core.ui.theme.ThemeMode
import dao3.pickcat.core.ui.theme.PickCatTheme

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.readViewUrl()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
            PickCatTheme(
                themeMode = when (preferences.themeMode) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                dynamicColor = preferences.dynamicColor,
            ) {
                PickCatRoot(
                    viewModel = appViewModel,
                    deepLink = deepLink.value,
                    onDeepLinkConsumed = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.readViewUrl()?.let { deepLink.value = it }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            NotificationSyncScheduler.syncNow(this)
        }
    }
}

private fun Intent.readViewUrl(): String? {
    if (action != Intent.ACTION_VIEW) return null
    return dataString?.takeIf { it.startsWith("https://pickcat.dao3.fun") }
}
