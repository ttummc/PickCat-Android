package dao3.pickcat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dao3.pickcat.core.ui.ErrorState
import dao3.pickcat.feature.login.LoginScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class RootStage { SPLASH, ERROR, LOGIN, MAIN }

@Composable
fun PickCatRoot(viewModel: AppViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        val stage = when {
            !session.ready -> RootStage.SPLASH
            session.user == null && session.error != null -> RootStage.ERROR
            session.user == null -> RootStage.LOGIN
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
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.pickcat_logo),
            contentDescription = "PickCat",
            modifier = Modifier.size(92.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PickCat",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            "正在检查登录状态…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
