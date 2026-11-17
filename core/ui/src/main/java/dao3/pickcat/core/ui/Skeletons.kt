package dao3.pickcat.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return alpha
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    alpha: Float,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}

@Composable
fun TopicFeedSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
) {
    val alpha = rememberSkeletonAlpha()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            SkeletonTopicCard(alpha)
        }
    }
}

@Composable
private fun SkeletonTopicCard(alpha: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(32.dp), alpha, RoundedCornerShape(50))
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SkeletonBlock(Modifier.width(112.dp).height(12.dp), alpha)
                    SkeletonBlock(Modifier.width(64.dp).height(10.dp), alpha)
                }
            }
            SkeletonBlock(Modifier.fillMaxWidth(0.92f).height(19.dp), alpha)
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), alpha)
            SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(12.dp), alpha)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonBlock(Modifier.width(76.dp).height(24.dp), alpha, RoundedCornerShape(8.dp))
                SkeletonBlock(Modifier.width(62.dp).height(24.dp), alpha, RoundedCornerShape(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.width(36.dp).height(11.dp), alpha)
                SkeletonBlock(Modifier.width(40.dp).height(11.dp), alpha)
                SkeletonBlock(Modifier.width(36.dp).height(11.dp), alpha)
            }
        }
    }
}

@Composable
fun WorkGridSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 2,
) {
    val alpha = rememberSkeletonAlpha()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonWorkCard(alpha, Modifier.weight(1f))
                SkeletonWorkCard(alpha, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonWorkCard(alpha: Float, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                alpha = alpha,
                shape = RoundedCornerShape(0.dp),
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth(0.88f).height(14.dp), alpha)
                SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(11.dp), alpha)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(Modifier.width(34.dp).height(10.dp), alpha)
                    SkeletonBlock(Modifier.width(34.dp).height(10.dp), alpha)
                }
            }
        }
    }
}

@Composable
fun NotificationListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    val alpha = rememberSkeletonAlpha()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    SkeletonBlock(Modifier.size(36.dp), alpha, RoundedCornerShape(50))
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.54f).height(12.dp), alpha)
                        SkeletonBlock(Modifier.fillMaxWidth(0.82f).height(10.dp), alpha)
                    }
                    Spacer(Modifier.width(12.dp))
                    SkeletonBlock(Modifier.width(42.dp).height(10.dp), alpha)
                }
            }
        }
    }
}
