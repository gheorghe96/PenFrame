package com.gheorghe.penframe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.render.RenderContext
import com.gheorghe.penframe.render.SceneIndex
import com.gheorghe.penframe.render.drawFrame
import kotlin.math.min

/** Full-screen preview of each screen in its device; swipe to move between them. */
@Composable
fun PresentDialog(state: EditorState, onDismiss: () -> Unit) {
    val index = remember(state.nodes) { SceneIndex(state.nodes) }
    val frames = index.roots.filter { it.isFrame && it.visible }
    val start = frames.indexOfFirst { it.id == state.targetFrame()?.id }.coerceAtLeast(0)
    var current by remember { mutableIntStateOf(start) }
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val ctx = remember(measurer) { RenderContext(measurer) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
            if (frames.isEmpty()) {
                Text("No screens yet", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                val frame = frames[current.coerceIn(0, frames.lastIndex)]
                Canvas(
                    Modifier.fillMaxSize().padding(24.dp).pointerInput(frames.size) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -80) current = (current + 1).coerceAtMost(frames.lastIndex)
                                if (drag > 80) current = (current - 1).coerceAtLeast(0)
                            },
                        ) { _, amount -> drag += amount }
                    },
                ) {
                    val pad = 12f
                    val s = min(size.width / (frame.w + pad * 2), size.height / (frame.h + pad * 2))
                    withTransform({
                        translate(size.width / 2 - frame.center.x * s, size.height / 2 - frame.center.y * s)
                        scale(s, s, Offset.Zero)
                    }) {
                        drawFrame(frame, index.children[frame.id].orEmpty(), ctx, bezel = true)
                    }
                }
                Text(
                    "${frame.name}  ·  ${current + 1}/${frames.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White)
            }
        }
    }
}
