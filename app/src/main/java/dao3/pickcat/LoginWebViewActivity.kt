package dao3.pickcat

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginWebViewActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        webView = WebView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setGeolocationEnabled(true)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            loadUrl(LOGIN_URL)
        }
        setContentView(webView)

        lifecycleScope.launch {
            while (isActive) {
                delay(2_000)
                CookieManager.getInstance().flush()
                val user = AppGraph.container.session.pollWebViewLogin()
                if (user != null) {
                    Toast.makeText(this@LoginWebViewActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                    return@launch
                }
            }
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val LOGIN_URL = "https://pickcat.dao3.fun/login"
    }
}
