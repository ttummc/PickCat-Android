package dao3.pickcat.feature.work

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dao3.pickcat.PlayerActivity
import dao3.pickcat.core.network.Work
import dao3.pickcat.core.ui.PickCatImage

/**
 * The Codemao players are keyed by the source work id and live on different hosts
 * depending on the engine. Types without a player deliberately return null.
 */
fun playerUrlFor(work: Work): String? {
    val id = work.sourceWorkId.trim().ifBlank { return null }
    return when (work.type.uppercase()) {
        "KITTEN4" -> "https://player.codemao.cn/new/$id"
        "KITTEN3" -> "https://player.codemao.cn/old/$id?origin=kitten_qrcode"
        "KITTENN" -> "https://kn.codemao.cn/player?type=2&workId=$id"
        "COCO" -> "https://coco.codemao.cn/editor/player/$id?channel=h5"
        "NEMO" -> "https://nemo.codemao.cn/w/$id"
        else -> null
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPlayerWebView(context: Context, url: String): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        setBackgroundColor(Color.BLACK)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        loadUrl(url)
    }
}

@Composable
fun CodemaoPlayer(
    work: Work,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerUrl = remember(work.id, work.type, work.sourceWorkId) { playerUrlFor(work) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(playerUrl != null) }
    var reloadToken by remember { mutableStateOf(0) }
    var chromeDialog by remember { mutableStateOf<Dialog?>(null) }

    val playerShape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .clip(playerShape)
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        if (playerUrl == null) {
            RecordFallback(work = work)
        } else {
            key(reloadToken) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().clip(playerShape),
                    factory = { ctx ->
                        createPlayerWebView(ctx, playerUrl).apply {
                            val radius = 16.dp.value * resources.displayMetrics.density
                            outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                                }
                            }
                            clipToOutline = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loading = true
                                    failed = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        loading = false
                                        failed = true
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    if (view == null) return
                                    val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    dialog.setContentView(
                                        view,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        ),
                                    )
                                    dialog.setOnDismissListener {
                                        callback?.onCustomViewHidden()
                                        chromeDialog = null
                                    }
                                    dialog.window?.let { configureImmersive(it) }
                                    dialog.show()
                                    dialog.window?.let { configureImmersive(it) }
                                    chromeDialog = dialog
                                }

                                override fun onHideCustomView() {
                                    chromeDialog?.dismiss()
                                    chromeDialog = null
                                }
                            }
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.removeAllViews()
                        view.destroy()
                    },
                )
            }
            if (failed) {
                ColumnOverlay(
                    title = "播放器加载失败",
                    subtitle = work.coverUrl?.let { "该作品可能需要前往点猫社区查看" } ?: "请检查网络后重试",
                    action = "重试",
                    onAction = {
                        failed = false
                        reloadToken++
                    },
                )
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        context.startActivity(PlayerActivity.intent(context, playerUrl, title))
                    },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "全屏")
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        shape = playerShape,
                    ),
            )
        }
    }
}

@Composable
private fun RecordFallback(work: Work) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (work.coverUrl != null) {
            PickCatImage(
                url = work.coverUrl,
                contentDescription = work.workName,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(16.dp),
            ) {
                androidx.compose.foundation.layout.Column {
                    Text(
                        "${work.type} 作品",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "该作品类型没有可用的内置播放器，已显示封面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "该作品类型暂不支持内置播放",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ColumnOverlay(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.TextButton(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                Text(action, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

private fun configureImmersive(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
