package dao3.pickcat.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** A single step of a comment thread: indentation plus a quiet thread rail. */
@Composable
fun ThreadedReply(
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val safeDepth = depth.coerceIn(0, 3)
    val indent = (safeDepth * 16).dp
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (safeDepth > 0) {
                    val x = indent.toPx()
                    drawLine(
                        color = railColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            .padding(start = indent),
    ) {
        if (safeDepth > 0) {
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}
