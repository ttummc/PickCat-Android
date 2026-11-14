package dao3.pickcat.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A single step of a comment thread: indentation plus a quiet thread rail. */
@Composable
fun ThreadedReply(
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val safeDepth = depth.coerceIn(0, 3)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (safeDepth * 16).dp)
            .height(IntrinsicSize.Min),
    ) {
        if (safeDepth > 0) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}
