package com.gheorghe.penframe.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.rotateAround
import com.gheorghe.penframe.render.RenderContext
import com.gheorghe.penframe.render.drawNode
import com.gheorghe.penframe.render.drawScene
import com.gheorghe.penframe.render.penPath
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

private val CanvasBackground = Color(0xFFEDEBF0)
private val Selection = Color(0xFF7C4DFF)
private val GuideColor = Color(0xFFFF4081)

@Composable
fun EditorCanvas(state: EditorState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val ctx = remember(measurer) { RenderContext(measurer) }
    val density = LocalDensity.current.density
    val slop = LocalViewConfiguration.current.touchSlop
    state.density = density

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitPointerEventScope {
                    var lastTapAt = 0L
                    var lastTapId: String? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when {
                            event.type == PointerEventType.Exit -> state.hoverId = null
                            !change.pressed && (change.type == PointerType.Stylus || change.type == PointerType.Mouse) -> {
                                // S Pen hovering above the screen: highlight what it would pick.
                                state.hoverId = state.nodeAt(state.toCanvas(change.position), 4f / state.zoom)?.id
                            }
                            change.pressed && event.type == PointerEventType.Press -> {
                                val tapped = handleGesture(state, event, slop)
                                if (tapped != null) {
                                    val now = System.currentTimeMillis()
                                    if (tapped == lastTapId && now - lastTapAt < 350) {
                                        state.onDoubleTap(tapped)
                                        lastTapId = null
                                    } else {
                                        lastTapId = tapped; lastTapAt = now
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        state.viewSize = size
        drawRect(CanvasBackground)
        drawDots(state)
        val index = state.index
        withTransform({
            translate(state.offset.x, state.offset.y)
            scale(state.scale, state.scale, Offset.Zero)
        }) {
            drawScene(index, ctx, devices = state.showBezels)
            state.draft?.let { drawNode(it, ctx) }
            if (state.penDraft.size > 1) drawPenDraft(state)
        }
        drawFrameLabels(state, index.roots.filter { it.isFrame && it.visible }, ctx)
        state.hoverId?.takeIf { it != state.selectedId }?.let { id -> index.byId[id]?.let { outline(state, it, Selection.copy(alpha = 0.6f), 1.5f) } }
        state.selected?.let { drawSelection(state, it, ctx) }
        drawGuides(state)
    }
}

/** Returns the id of the node that was tapped (for double-tap detection), or null if it wasn't a tap. */
private suspend fun AwaitPointerEventScope.handleGesture(state: EditorState, first: PointerEvent, slop: Float): String? {
    val down = first.changes.first()
    val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
    if (isPen) state.penDetected = true
    val penButton = isPen && first.buttons.isPrimaryPressed
    val start = down.position
    val startCanvas = state.toCanvas(start)
    val startTime = down.uptimeMillis

    // What a single pointer does for this gesture.
    var mode = when {
        state.tool == Tool.HAND || penButton -> Mode.PAN
        !isPen && state.penDetected && state.fingersPanWithPen -> Mode.PAN
        else -> Mode.PENDING
    }
    var targetId: String? = null
    var handle = -1
    var moved = false
    var multiTouch = false

    if (mode == Mode.PENDING) {
        when (state.tool) {
            Tool.SELECT -> {
                handle = state.selected?.let { handleAt(state, it, start) } ?: -1
                if (handle >= 0) {
                    targetId = state.selectedId
                    mode = Mode.RESIZE
                } else {
                    val hit = state.nodeAt(startCanvas, 6f / state.zoom)
                    if (hit != null) {
                        state.select(hit.id)
                        targetId = hit.id
                        mode = Mode.MOVE
                    } else {
                        state.select(null)
                        mode = Mode.PAN
                    }
                }
            }
            Tool.RECT, Tool.ELLIPSE, Tool.LINE -> { state.startDraft(state.tool, startCanvas); mode = Mode.DRAW }
            Tool.PEN -> { state.penDraft = listOf(startCanvas); mode = Mode.INK }
            Tool.TEXT -> mode = Mode.TEXT
            Tool.HAND -> mode = Mode.PAN
        }
    }
    down.consume()

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size >= 2) {
            // Two fingers always pan and zoom; abandon whatever the first finger started.
            if (!multiTouch) {
                multiTouch = true
                when (mode) {
                    Mode.MOVE -> { targetId?.let { state.endMove(it) }; state.undo() }
                    Mode.RESIZE -> { state.endResize(); state.undo() }
                    Mode.DRAW -> state.draft = null
                    Mode.INK -> state.penDraft = emptyList()
                    else -> {}
                }
                mode = Mode.PAN
            }
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            val centroid = event.calculateCentroid()
            if (zoom != 1f) state.zoomBy(zoom, centroid)
            state.panBy(pan)
            event.changes.forEach { it.consume() }
            continue
        }
        if (multiTouch) { event.changes.forEach { it.consume() }; continue }
        val change = pressed.first()
        val total = change.position - start
        if (!moved && hypot(total.x, total.y) > slop) {
            moved = true
            when (mode) {
                Mode.MOVE -> targetId?.let { state.beginMove(it) }
                Mode.RESIZE -> targetId?.let { state.beginResize(it, handle) }
                else -> {}
            }
        }
        val canvasTotal = total / state.scale
        when (mode) {
            Mode.PAN -> state.panBy(change.positionChange())
            Mode.MOVE -> if (moved) targetId?.let { state.moveTo(it, canvasTotal) }
            Mode.RESIZE -> if (moved) targetId?.let { id ->
                val node = state.selected
                state.resizeTo(id, canvasTotal, keepRatio = node?.type == com.gheorghe.penframe.model.NodeType.IMAGE || event.buttons.isPrimaryPressed && isPen)
            }
            Mode.DRAW -> state.updateDraft(state.toCanvas(change.position), square = isPen && event.buttons.isPrimaryPressed)
            Mode.INK -> {
                val p = state.toCanvas(change.position)
                val last = state.penDraft.lastOrNull()
                if (last == null || hypot(p.x - last.x, p.y - last.y) > 0.8f / state.zoom) state.penDraft = state.penDraft + p
            }
            else -> {}
        }
        change.consume()
    }

    if (multiTouch) return null
    val isTap = !moved && (System.currentTimeMillis() - startTime < 500 || true)
    when (mode) {
        Mode.MOVE -> if (moved) targetId?.let { state.endMove(it) }
        Mode.RESIZE -> if (moved) state.endResize()
        Mode.DRAW -> state.finishDraft(tapped = !moved)
        Mode.INK -> state.commitPen()
        Mode.TEXT -> if (!moved) state.addText(startCanvas)
        else -> {}
    }
    return if (isTap && (mode == Mode.MOVE)) targetId else null
}

private enum class Mode { PENDING, PAN, MOVE, RESIZE, DRAW, INK, TEXT }

/** Double tap edits text, or zooms into a frame. */
private fun EditorState.onDoubleTap(id: String) {
    val node = nodes.firstOrNull { it.id == id } ?: return
    when {
        node.type == com.gheorghe.penframe.model.NodeType.TEXT -> editingTextId = id
        node.isFrame -> zoomToFit(node)
    }
}

// MARK: Selection geometry (screen space)

private fun handlePoints(state: EditorState, node: Node): List<Offset> {
    val r = node.rect
    val pts = listOf(
        Offset(r.left, r.top), Offset(r.center.x, r.top), Offset(r.right, r.top), Offset(r.right, r.center.y),
        Offset(r.right, r.bottom), Offset(r.center.x, r.bottom), Offset(r.left, r.bottom), Offset(r.left, r.center.y),
    )
    return pts.map { p -> state.toScreen(if (node.rotation == 0f) p else rotateAround(p, node.center, node.rotation)) }
}

private fun handleAt(state: EditorState, node: Node, screen: Offset): Int {
    if (node.locked) return -1
    val radius = 22f * state.density
    val pts = handlePoints(state, node)
    // Tiny nodes: only corners, so the middle stays draggable.
    val small = node.w * state.scale < 48f * state.density || node.h * state.scale < 48f * state.density
    var best = -1
    var bestD = Float.MAX_VALUE
    pts.forEachIndexed { i, p ->
        if (small && i % 2 == 1) return@forEachIndexed
        val d = hypot(p.x - screen.x, p.y - screen.y)
        if (d < radius && d < bestD) { best = i; bestD = d }
    }
    return best
}

private fun DrawScope.outline(state: EditorState, node: Node, color: Color, width: Float) {
    val tl = state.toScreen(Offset(node.x, node.y))
    val size = Size(node.w * state.scale, node.h * state.scale)
    rotate(node.rotation, pivot = state.toScreen(node.center)) {
        drawRect(color, tl, size, style = Stroke(width * state.density))
    }
}

private fun DrawScope.drawSelection(state: EditorState, node: Node, ctx: RenderContext) {
    outline(state, node, Selection, 1.5f)
    if (node.locked) return
    val d = state.density
    val small = node.w * state.scale < 48f * d || node.h * state.scale < 48f * d
    handlePoints(state, node).forEachIndexed { i, p ->
        if (small && i % 2 == 1) return@forEachIndexed
        drawCircle(Color.White, 6f * d, p)
        drawCircle(Selection, 6f * d, p, style = Stroke(1.5f * d))
    }
    // Size badge under the selection.
    val bottom = state.toScreen(Offset(node.center.x, node.y + node.h))
    val label = "${fmt(node.w)} × ${fmt(node.h)}"
    val layout = ctx.layout(label, 11f * d, Color.White, 500)
    val bw = layout.size.width + 12f * d
    val bh = layout.size.height + 6f * d
    val topLeft = Offset(bottom.x - bw / 2, bottom.y + 10f * d)
    drawRoundRect(Selection, topLeft, Size(bw, bh), CornerRadius(4f * d))
    drawText(layout, topLeft = topLeft + Offset(6f * d, 3f * d))
}

private fun fmt(v: Float): String = if (abs(v - v.toInt()) < 0.05f) v.toInt().toString() else String.format("%.1f", v)

private fun DrawScope.drawFrameLabels(state: EditorState, frames: List<Node>, ctx: RenderContext) {
    val d = state.density
    for (frame in frames) {
        val pos = state.toScreen(Offset(frame.x, frame.y))
        val selected = frame.id == state.selectedId
        val text = "${frame.name}  ${frame.w.toInt()}×${frame.h.toInt()}"
        val layout = ctx.layout(text, 12f * d, if (selected) Selection else Color(0xFF5E5A66), 500,
            maxWidth = (frame.w * state.scale).coerceAtLeast(60f * d))
        drawText(layout, topLeft = Offset(pos.x, pos.y - layout.size.height - 6f * d - if (frame.props["bezel"] == "true" && state.showBezels) 10f * state.scale else 0f))
    }
}

private fun DrawScope.drawGuides(state: EditorState) {
    for (g in state.guides) {
        val a = state.toScreen(if (g.vertical) Offset(g.pos, g.from) else Offset(g.from, g.pos))
        val b = state.toScreen(if (g.vertical) Offset(g.pos, g.to) else Offset(g.to, g.pos))
        drawLine(GuideColor, a, b, 1f * state.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    }
}

private fun DrawScope.drawPenDraft(state: EditorState) {
    val pts = state.penDraft
    val left = pts.minOf { it.x }
    val top = pts.minOf { it.y }
    val w = (pts.maxOf { it.x } - left).coerceAtLeast(0.001f)
    val h = (pts.maxOf { it.y } - top).coerceAtLeast(0.001f)
    val path = penPath(pts.flatMap { listOf((it.x - left) / w, (it.y - top) / h) }, w, h)
    withTransform({ translate(left, top) }) {
        drawPath(path, Color(state.penColor.toInt()), style = Stroke(state.penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Dot grid that stays put relative to the canvas, thinning out when zoomed away. */
private fun DrawScope.drawDots(state: EditorState) {
    var step = 24f * state.scale
    while (step < 14f * state.density) step *= 4f
    val color = Color(0xFFCBC6D2)
    val startX = state.offset.x - floor(state.offset.x / step) * step
    val startY = state.offset.y - floor(state.offset.y / step) * step
    var x = startX
    val r = 1.1f * state.density
    while (x < size.width) {
        var y = startY
        while (y < size.height) {
            drawCircle(color, r, Offset(x, y))
            y += step
        }
        x += step
    }
}

@Suppress("unused")
private fun Rect.toScreen(state: EditorState) = Rect(state.toScreen(topLeft), state.toScreen(bottomRight))
