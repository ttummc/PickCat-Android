package dao3.pickcat

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.SharedFlow

/**
 * Emits when the user taps the already selected bottom destination. Top-level
 * screens use it to animate back to the top instead of rebuilding the page.
 */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<Unit>?> { null }
