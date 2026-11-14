package dao3.pickcat.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal padding that keeps long-form content near a readable line length
 * on tablets while preserving the standard phone padding.
 */
@Composable
fun wideContentPadding(
    maxContentWidth: Dp = 840.dp,
    minPadding: Dp = 16.dp,
): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return ((screenWidth - maxContentWidth) / 2).coerceAtLeast(minPadding)
}
