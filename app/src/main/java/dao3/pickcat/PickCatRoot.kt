package dao3.pickcat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.feature.gate.GateScreen
import dao3.pickcat.feature.gate.KITTER_SUPPORT_ID
import dao3.pickcat.feature.login.LoginScreen
import dao3.pickcat.feature.settings.UpdateViewModel
import dao3.pickcat.feature.settings.launchUpdateInstaller
import dao3.pickcat.feature.settings.openExternalUrl
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class RootStage { SPLASH, ERROR, LOGIN, GATE, MAIN }

@Composable
fun PickCatRoot(viewModel: AppViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(preferences.updateSource) {
        updateViewModel.check(context, preferences.updateSource)
    }
    LaunchedEffect(updateState.installFile) {
        val file = updateState.installFile ?: return@LaunchedEffect
        val result = launchUpdateInstaller(context, file)
        updateViewModel.consumeInstallFile()
        if (result.isSuccess) {
            updateViewModel.dismiss()
        } else {
            updateViewModel.reportInstallError(
                result.exceptionOrNull()?.message ?: "无法打开系统安装器",
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        val user = session.user
        val exemptUntil = user?.let { preferences.gateExemptUntil[it.id] } ?: 0L
        val stage = when {
            !session.ready -> RootStage.SPLASH
            user == null && session.error != null -> RootStage.ERROR
            user == null -> RootStage.LOGIN
            user.id == KITTER_SUPPORT_ID -> RootStage.MAIN
            user.id !in preferences.gateCompletedUserIds &&
                exemptUntil <= System.currentTimeMillis() -> RootStage.GATE
            else -> RootStage.MAIN
        }
        AnimatedContent(
            targetState = stage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(320)) togetherWith fadeOut(tween(180))
            },
            label = "root-stage",
        ) { target ->
            when (target) {
                RootStage.SPLASH -> SplashScreen()
                RootStage.ERROR -> ErrorState(
                    message = session.error.orEmpty(),
                    onRetry = {
                        scope.launch { viewModel.retrySession() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                RootStage.LOGIN -> LoginScreen(viewModel)
                RootStage.GATE -> GateScreen(
                    currentUserId = user?.id.orEmpty(),
                    onCompleted = {
                        user?.id?.let(viewModel::completeGate)
                    },
                    onQuotaExempt = {
                        user?.id?.let(viewModel::exemptGateForThreeDays)
                    },
                )
                RootStage.MAIN -> PickCatMainShell(
                    appViewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
        if (session.ready && session.user == null && session.error == null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
            )
        }
    }

    val update = updateState.update
    if (updateState.installError != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("安装未完成") },
            text = { Text(updateState.installError.orEmpty()) },
            confirmButton = {
                if (update != null) {
                    Button(onClick = {
                        openExternalUrl(context, update.releaseUrl)
                        updateViewModel.dismiss()
                    }) { Text("打开 Release 页面") }
                } else {
                    Button(onClick = updateViewModel::dismiss) { Text("知道了") }
                }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) { Text("关闭") }
            },
        )
    } else if (updateState.downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${((updateState.progress ?: 0f) * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
    } else if (update != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("发现新版本 ${update.tag}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = { updateViewModel.download(context) }) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) { Text("稍后") }
            },
        )
    }
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(420)) + scaleIn(
                    animationSpec = tween(420),
                    initialScale = 0.96f,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.pickcat_logo),
                        contentDescription = "PickCat",
                        modifier = Modifier.size(112.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "PickCat",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(30.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.width(104.dp).height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "正在检查登录状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
