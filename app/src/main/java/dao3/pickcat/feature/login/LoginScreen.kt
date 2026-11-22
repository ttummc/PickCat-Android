package dao3.pickcat.feature.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dao3.pickcat.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOGIN_URL = "https://pickcat.dao3.fun/login"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val webViewVersion = remember {
        runCatching {
            WebView.getCurrentWebViewPackage()?.versionName
        }.getOrNull()
    }

    fun reload(clearCache: Boolean = false) {
        loadError = null
        if (clearCache) {
            webView.clearCache(true)
            webView.clearHistory()
        }
        webView.reload()
    }

    fun openWebViewUpdate() {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.webview"),
        )
        val playStore = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"),
        )
        runCatching { context.startActivity(market) }
            .recoverCatching { context.startActivity(playStore) }
    }

    fun checkRenderedContent(view: WebView?, attempt: Int) {
        if (view == null) return
        view.postDelayed({
            view.evaluateJavascript(
                "(function(){" +
                    "var b=document.body;" +
                    "var n=document.getElementById('__nuxt');" +
                    "return JSON.stringify({" +
                    "text:((b&&b.innerText)||'').trim().length," +
                    "bodyH:b?b.getBoundingClientRect().height:0," +
                    "nuxtH:n?n.getBoundingClientRect().height:0});" +
                    "})()",
            ) { result ->
                val textLength = Regex("\"text\"\\s*:\\s*(\\d+)")
                    .find(result)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                val bodyHeight = Regex("\"bodyH\"\\s*:\\s*([0-9.]+)")
                    .find(result)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toFloatOrNull()
                    ?: 0f
                val nuxtHeight = Regex("\"nuxtH\"\\s*:\\s*([0-9.]+)")
                    .find(result)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toFloatOrNull()
                    ?: 0f
                if (textLength > 0 && bodyHeight > 0f && nuxtHeight > 0f) {
                    loadError = null
                } else if (attempt < 2) {
                    checkRenderedContent(view, attempt + 1)
                } else if (loadError == null) {
                    loadError = if (textLength > 0) {
                        "登录页布局未正确渲染"
                    } else {
                        "登录页没有返回可显示内容"
                    }
                }
            }
        }, 1_200L)
    }

    fun checkLogin(manual: Boolean) {
        if (manual && checking) return
        if (manual) checking = true
        scope.launch {
            val user = viewModel.tryLoginFromWebView()
            if (manual) checking = false
            if (user == null && manual) {
                viewModel.notifyMessage("还没有检测到登录，请先在官方页面完成验证")
            }
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                loadError = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                CookieManager.getInstance().flush()
                checkLogin(manual = false)
                checkRenderedContent(view, attempt = 0)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    loading = false
                    loadError = error?.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: "登录页加载失败"
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    loading = false
                    loadError = "登录页返回 HTTP ${errorResponse?.statusCode ?: "错误"}"
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val isPickCat = url.startsWith("https://pickcat.dao3.fun") ||
                    url.startsWith("https://www.pickcat.dao3.fun")
                if (!isPickCat) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, request!!.url),
                        )
                    }
                    return true
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress / 100f
            }
        }
        webView.loadUrl(LOGIN_URL)
        onDispose {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    // Poll /session instead of trusting a URL redirect: the official login page may
    // finish without changing paths, and /session is the only authoritative answer.
    LaunchedEffect(webView) {
        while (isActive) {
            delay(2_500)
            viewModel.tryLoginFromWebView()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("登录 PickCat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "验证码在官方页面内完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新加载")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (checking) "正在检测登录状态…" else "在官方页面完成账号和滑块验证",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "检测到会话后会自动进入首页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            enabled = !checking,
                            onClick = { checkLogin(manual = true) },
                        ) {
                            Text("我已完成登录")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading || progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize().weight(1f)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loadError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                    Column(
                        modifier = Modifier.widthIn(max = 360.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            "登录页加载失败",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            loadError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (!webViewVersion.isNullOrBlank()) {
                            Text(
                                "当前 WebView：$webViewVersion",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Button(
                            onClick = { reload() },
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) {
                            Text("重新加载")
                        }
                        TextButton(onClick = { reload(clearCache = true) }) {
                            Text("清除缓存后重试")
                        }
                        TextButton(onClick = ::openWebViewUpdate) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("更新 Android System WebView", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
}
