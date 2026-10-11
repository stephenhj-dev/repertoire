package app.oslo.repertoire.ui.watchlists

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/** Content type of rows that can be dragged; other rows (headers) are never drop targets. */
const val REORDERABLE = "reorderable"

/**
 * Drag-to-reorder for a LazyColumn without extra libraries. Rows are identified by key; while
 * dragging, the row follows the finger and swaps with a neighbour once its centre passes into
 * it ([onMove] with both keys, applied to an in-memory list). [onDrop] persists the order.
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        offset += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        val center = current.offset + offset + current.size / 2f
        val target = visible.firstOrNull {
            it.key != key && it.contentType == REORDERABLE && center >= it.offset && center <= it.offset + it.size
        } ?: return
        // The row will be laid out where the target was; keep it under the finger.
        offset += (current.offset - target.offset).toFloat()
        onMove(key, target.key)
    }

    fun end() {
        if (draggingKey != null) onDrop()
        draggingKey = null
        offset = 0f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (Any, Any) -> Unit, onDrop: () -> Unit): ReorderState {
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    return remember(listState) { ReorderState(listState, { a, b -> move(a, b) }, { drop() }) }
}

/** Lifts the dragged row above its neighbours and moves it with the finger. */
fun Modifier.draggedRow(state: ReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offset else 0f
            shadowElevation = if (dragging) 12f else 0f
        }
}

/** The drag handle: drag immediately. */
fun Modifier.dragHandle(state: ReorderState, key: Any): Modifier = pointerInput(key) {
    detectVerticalDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onVerticalDrag = { change, dy ->
            change.consume()
            state.drag(dy)
        },
    )
}

/** Long-press anywhere on the row, then drag. */
fun Modifier.longPressDrag(state: ReorderState, key: Any): Modifier = pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}
