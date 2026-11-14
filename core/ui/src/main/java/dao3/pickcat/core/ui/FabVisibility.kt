package dao3.pickcat.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps a FAB out of the way while the user reads downward, then brings it back
 * as soon as they reverse direction or reach the top.
 */
@Composable
fun rememberFabVisibleOnScroll(state: LazyListState): Boolean {
    var previousIndex by remember { mutableIntStateOf(state.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(state.firstVisibleItemScrollOffset) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= 4
                val scrollingDown = index > previousIndex ||
                    (index == previousIndex && offset > previousOffset)
                visible = atTop || !scrollingDown
                previousIndex = index
                previousOffset = offset
            }
    }
    return visible
}

@Composable
fun rememberFabVisibleOnGridScroll(state: LazyGridState): Boolean {
    var previousIndex by remember { mutableIntStateOf(state.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(state.firstVisibleItemScrollOffset) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= 4
                val scrollingDown = index > previousIndex ||
                    (index == previousIndex && offset > previousOffset)
                visible = atTop || !scrollingDown
                previousIndex = index
                previousOffset = offset
            }
    }
    return visible
}
