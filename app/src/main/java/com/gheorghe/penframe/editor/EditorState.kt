package com.gheorghe.penframe.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.gheorghe.penframe.model.ComponentKind
import com.gheorghe.penframe.model.DevicePreset
import com.gheorghe.penframe.model.Devices
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.model.Project
import com.gheorghe.penframe.model.Viewport
import com.gheorghe.penframe.model.newId
import com.gheorghe.penframe.model.rotateAround
import com.gheorghe.penframe.model.union
import com.gheorghe.penframe.render.DEFAULT_PRIMARY
import com.gheorghe.penframe.render.SceneIndex
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Tool(val title: String) {
    SELECT("Select"), HAND("Hand"), RECT("Rectangle"), ELLIPSE("Ellipse"), LINE("Line"), TEXT("Text"), PEN("Pen")
}

/** A snapping guide: vertical (x = [pos]) or horizontal (y = [pos]) from [from] to [to], in canvas units. */
data class Guide(val vertical: Boolean, val pos: Float, val from: Float, val to: Float)

enum class Align { LEFT, CENTER_H, RIGHT, TOP, CENTER_V, BOTTOM }

/**
 * Everything the editor knows: the layers, the selection, the tool and the viewport.
 * All edits go through here so undo/redo and autosave see them.
 */
class EditorState(project: Project) {
    val projectId = project.id
    val createdAt = project.createdAt
    var name by mutableStateOf(project.name)
    var nodes by mutableStateOf(project.nodes)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var hoverId by mutableStateOf<String?>(null)
    var tool by mutableStateOf(Tool.SELECT)
    var guides by mutableStateOf<List<Guide>>(emptyList())
    var draft by mutableStateOf<Node?>(null)
    var penDraft by mutableStateOf<List<Offset>>(emptyList())
    var editingTextId by mutableStateOf<String?>(null)
    /** A project that was never viewed frames all its screens when it first opens. */
    var needsFit = project.viewport == Viewport()

    /** Once an S Pen (or any stylus) has touched the canvas, fingers pan and the pen edits. */
    var penDetected by mutableStateOf(false)
    var fingersPanWithPen by mutableStateOf(true)
    var snapping by mutableStateOf(true)
    var showBezels by mutableStateOf(true)

    // Viewport: screen px = offset + canvas units * zoom * density
    var offset by mutableStateOf(Offset(project.viewport.offsetX, project.viewport.offsetY))
    var zoom by mutableFloatStateOf(project.viewport.zoom)
    var density = 1f
    var viewSize by mutableStateOf(Size.Zero)
    val scale: Float get() = zoom * density

    // Style defaults for new shapes
    var penColor by mutableStateOf(0xFF1D1B20)
    var penWidth by mutableFloatStateOf(3f)

    private val undoStack = ArrayDeque<List<Node>>()
    private val redoStack = ArrayDeque<List<Node>>()
    var historyVersion by mutableIntStateOf(0)
        private set
    private var lastCheckpointKey: String? = null
    private var lastCheckpointAt = 0L

    val canUndo get() = historyVersion >= 0 && undoStack.isNotEmpty()
    val canRedo get() = historyVersion >= 0 && redoStack.isNotEmpty()

    val index: SceneIndex get() = SceneIndex(nodes)
    val selected: Node? get() = selectedId?.let { id -> nodes.firstOrNull { it.id == id } }

    fun toProject(): Project = Project(
        id = projectId, name = name, createdAt = createdAt, updatedAt = System.currentTimeMillis(), nodes = nodes,
        viewport = Viewport(offset.x, offset.y, zoom),
    )

    // MARK: History

    /** Call before a change. Repeated calls with the same [key] within 1.5 s count as one step (typing, sliders). */
    fun checkpoint(key: String? = null) {
        val now = System.currentTimeMillis()
        if (key != null && key == lastCheckpointKey && now - lastCheckpointAt < 1500) {
            lastCheckpointAt = now
            return
        }
        lastCheckpointKey = key
        lastCheckpointAt = now
        undoStack.addLast(nodes)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        historyVersion++
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(nodes)
        nodes = prev
        lastCheckpointKey = null
        fixSelection()
        historyVersion++
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(nodes)
        nodes = next
        lastCheckpointKey = null
        fixSelection()
        historyVersion++
    }

    private fun fixSelection() {
        if (selectedId != null && nodes.none { it.id == selectedId }) selectedId = null
    }

    // MARK: Coordinates

    fun toCanvas(screen: Offset): Offset = (screen - offset) / scale
    fun toScreen(canvas: Offset): Offset = canvas * scale + offset

    fun panBy(delta: Offset) { offset += delta }

    fun zoomBy(factor: Float, focus: Offset) {
        val newZoom = (zoom * factor).coerceIn(0.05f, 16f)
        val f = newZoom / zoom
        offset = focus - (focus - offset) * f
        zoom = newZoom
    }

    fun zoomTo(value: Float) = zoomBy(value / zoom, Offset(viewSize.width / 2, viewSize.height / 2))

    /** Frames the whole design (or [only] that node) in the view. */
    fun zoomToFit(only: Node? = null) {
        val target = only?.rect ?: nodes.filter { it.parentId == null && it.visible }.map { it.rect }
            .reduceOrNull { a, b -> a.union(b) } ?: return
        if (viewSize.width <= 0f) return
        val pad = 48f * density
        val z = min((viewSize.width - pad * 2) / (target.width * density), (viewSize.height - pad * 2) / (target.height * density))
            .coerceIn(0.05f, 8f)
        zoom = z
        offset = Offset(
            viewSize.width / 2 - target.center.x * scale,
            viewSize.height / 2 - target.center.y * scale,
        )
    }

    // MARK: Queries

    /** Topmost node under [p] (canvas units). Children win over their frame; locked layers are skipped. */
    fun nodeAt(p: Offset, slop: Float = 0f): Node? {
        val order = index.paintOrder
        for (node in order.asReversed()) {
            if (!node.visible || node.locked) continue
            if (node.parentId != null) {
                val parent = index.byId[node.parentId]
                if (parent != null && (!parent.visible || !parent.rect.inflate(slop).contains(p))) continue
            }
            if (node.contains(p, slop)) return node
        }
        // A frame can also be grabbed by its title label above it.
        return nodes.lastOrNull { it.isFrame && it.visible && !it.locked && labelRect(it).contains(p) }
    }

    fun labelRect(frame: Node): Rect {
        val h = 22f / zoom
        return Rect(frame.x, frame.y - h - 6f / zoom, frame.x + frame.w, frame.y - 2f / zoom)
    }

    fun frameAt(p: Offset): Node? = nodes.lastOrNull { it.isFrame && it.visible && it.rect.contains(p) }

    /** Frame to drop new elements into: the selected one, the selection's parent, or the one in view. */
    fun targetFrame(): Node? {
        val sel = selected
        if (sel != null) {
            if (sel.isFrame) return sel
            sel.parentId?.let { pid -> return nodes.firstOrNull { it.id == pid } }
        }
        val center = toCanvas(Offset(viewSize.width / 2, viewSize.height / 2))
        return frameAt(center) ?: nodes.filter { it.isFrame }.minByOrNull { hypot(it.center.x - center.x, it.center.y - center.y) }
    }

    // MARK: Editing

    fun select(id: String?) {
        selectedId = id
    }

    fun update(id: String, key: String? = null, transform: (Node) -> Node) {
        val current = nodes.firstOrNull { it.id == id } ?: return
        val next = transform(current)
        if (next == current) return
        checkpoint(key?.let { "$id:$it" })
        nodes = nodes.map { if (it.id == id) next else it }
    }

    private fun replace(updated: Map<String, Node>) {
        nodes = nodes.map { updated[it.id] ?: it }
    }

    fun add(node: Node, select: Boolean = true) {
        checkpoint()
        val placed = if (node.isFrame) node else node.copy(parentId = node.parentId ?: frameAt(node.center)?.id)
        nodes = nodes + placed
        if (select) selectedId = placed.id
    }

    fun addDevice(preset: DevicePreset, withSystemBars: Boolean = true) {
        val frames = nodes.filter { it.isFrame }
        val x = frames.maxOfOrNull { it.x + it.w + 120f } ?: 0f
        val y = frames.minOfOrNull { it.y } ?: 0f
        val count = frames.count { it.device == preset.id }
        val frame = Node(
            type = NodeType.FRAME,
            name = if (count == 0) preset.name else "${preset.name} ${count + 1}",
            x = x, y = y, w = preset.width.toFloat(), h = preset.height.toFloat(),
            fill = 0xFFFFFFFF, cornerRadius = preset.cornerRadius, device = preset.id,
            props = mapOf("bezel" to if (preset.cornerRadius > 0f && !preset.round) "true" else "false"),
        )
        checkpoint()
        val extra = mutableListOf<Node>()
        if (withSystemBars && preset.statusBar > 0) {
            extra += componentNode(ComponentKind.STATUS_BAR, frame.x, frame.y, frame.w, preset.statusBar.toFloat(), frame.id)
        }
        if (withSystemBars && preset.navBar > 0) {
            extra += componentNode(ComponentKind.SYSTEM_NAV, frame.x, frame.y + frame.h - preset.navBar, frame.w, preset.navBar.toFloat(), frame.id)
                .copy(props = mapOf("style" to if (preset.navBar >= 40) "buttons" else "gesture"))
        }
        nodes = nodes + frame + extra
        selectedId = frame.id
        zoomToFit(frame)
    }

    fun componentNode(kind: ComponentKind, x: Float, y: Float, w: Float = kind.width, h: Float = kind.height, parent: String? = null) = Node(
        type = NodeType.COMPONENT, name = kind.title, x = x, y = y, w = w, h = h,
        parentId = parent, component = kind.name, props = kind.defaults, cornerRadius = defaultRadius(kind, h),
        fill = when (kind) {
            ComponentKind.ICON -> 0xFF49454F
            ComponentKind.STATUS_BAR, ComponentKind.SYSTEM_NAV -> null
            else -> DEFAULT_PRIMARY
        },
    )

    /** The corner radius each component is drawn with, so shadows and borders follow its shape. */
    private fun defaultRadius(kind: ComponentKind, h: Float): Float = when (kind) {
        ComponentKind.BUTTON_FILLED, ComponentKind.BUTTON_TONAL, ComponentKind.BUTTON_OUTLINED, ComponentKind.BUTTON_TEXT,
        ComponentKind.BUTTON_ELEVATED, ComponentKind.SEARCH_BAR, ComponentKind.SEGMENTED_BUTTON, ComponentKind.STEPPER,
        ComponentKind.SWITCH, ComponentKind.ICON_BUTTON, ComponentKind.AVATAR, ComponentKind.BADGE -> h / 2
        ComponentKind.FAB, ComponentKind.FAB_EXTENDED -> 16f
        ComponentKind.CHIP_ASSIST, ComponentKind.CHIP_FILTER, ComponentKind.CHIP_INPUT, ComponentKind.CHIP_SUGGESTION -> 8f
        ComponentKind.CARD_FILLED, ComponentKind.CARD_ELEVATED, ComponentKind.CARD_OUTLINED, ComponentKind.IMAGE_PLACEHOLDER,
        ComponentKind.VIDEO_PLAYER, ComponentKind.MAP -> 12f
        ComponentKind.DIALOG, ComponentKind.DATE_PICKER, ComponentKind.TIME_PICKER, ComponentKind.NOTIFICATION -> 28f
        ComponentKind.MENU, ComponentKind.SNACKBAR, ComponentKind.TOOLTIP, ComponentKind.TEXT_FIELD_OUTLINED, ComponentKind.TEXT_AREA -> 4f
        else -> 0f
    }

    /** Inserts a component centred in the target frame's visible part (or the view). */
    fun addComponent(kind: ComponentKind) {
        val frame = targetFrame()
        val viewCenter = toCanvas(Offset(viewSize.width / 2, viewSize.height / 2))
        var w = kind.width
        val h = kind.height
        val (cx, cy) = if (frame != null) {
            // Full-width bars snap to the frame's width.
            if (w >= 360f && kind.category.name in setOf("NAVIGATION", "SYSTEM", "CONTAINMENT") && kind != ComponentKind.NAVIGATION_DRAWER
                && kind != ComponentKind.SIDE_SHEET) w = frame.w
            val c = Offset(viewCenter.x.coerceIn(frame.x + w / 2, max(frame.x + w / 2, frame.x + frame.w - w / 2)),
                viewCenter.y.coerceIn(frame.y + h / 2, max(frame.y + h / 2, frame.y + frame.h - h / 2)))
            c.x to c.y
        } else viewCenter.x to viewCenter.y
        var node = componentNode(kind, cx - w / 2, cy - h / 2, w, h, frame?.id)
        if (frame != null) node = when (kind) {
            ComponentKind.FAB, ComponentKind.FAB_EXTENDED, ComponentKind.SNACKBAR -> {
                val bottom = frame.y + frame.h - bottomDockedHeight(frame) - 16f
                val x = if (kind == ComponentKind.SNACKBAR) frame.x + (frame.w - w) / 2 else frame.x + frame.w - 16f - w
                node.copy(x = x, y = bottom - h)
            }
            ComponentKind.DIALOG -> node.copy(x = frame.x + (frame.w - w) / 2, y = frame.y + (frame.h - h) / 2)
            ComponentKind.TOP_APP_BAR -> node.copy(x = frame.x, y = frame.y + statusBarHeight(frame))
            ComponentKind.NAVIGATION_BAR, ComponentKind.BOTTOM_APP_BAR -> node.copy(x = frame.x, y = frame.y + frame.h - h - navBarHeight(frame))
            ComponentKind.NAVIGATION_RAIL, ComponentKind.NAVIGATION_DRAWER ->
                node.copy(x = frame.x, y = frame.y + statusBarHeight(frame), h = frame.h - statusBarHeight(frame) - navBarHeight(frame))
            ComponentKind.BOTTOM_SHEET, ComponentKind.KEYBOARD -> node.copy(x = frame.x, y = frame.y + frame.h - h)
            else -> nextFreeSlot(frame, w, h)?.let { (x, y) -> node.copy(x = x, y = y) } ?: node
        }
        add(node)
        tool = Tool.SELECT
    }

    /** Height of everything docked to the frame's bottom edge (nav bars, bottom bars). */
    private fun bottomDockedHeight(frame: Node): Float {
        val bottom = frame.y + frame.h
        val docked = nodes.filter { it.parentId == frame.id && it.w >= frame.w - 1f && it.y + it.h >= bottom - navBarHeight(frame) - 1f }
        return docked.maxOfOrNull { bottom - it.y } ?: 0f
    }

    /** Below the lowest content in the frame, left-aligned with a 16 dp margin; null when it no longer fits. */
    private fun nextFreeSlot(frame: Node, w: Float, h: Float): Pair<Float, Float>? {
        val bottomLimit = frame.y + frame.h - bottomDockedHeight(frame)
        val content = nodes.filter { n ->
            n.parentId == frame.id && n.visible && n.y + n.h < bottomLimit - 1f &&
                n.component != ComponentKind.FAB.name && n.component != ComponentKind.FAB_EXTENDED.name
        }
        val top = content.maxOfOrNull { it.y + it.h } ?: (frame.y + statusBarHeight(frame))
        val gap = if (content.any { it.w >= frame.w - 1f && abs(it.y + it.h - top) < 0.5f }) 16f else 12f
        val y = top + gap
        if (y + h > bottomLimit - 8f) return null
        val x = if (w >= frame.w - 32f) frame.x + (frame.w - w) / 2 else frame.x + 16f
        return x to y
    }

    private fun statusBarHeight(frame: Node) = nodes.firstOrNull { it.parentId == frame.id && it.component == ComponentKind.STATUS_BAR.name }?.h ?: 0f
    private fun navBarHeight(frame: Node) = nodes.firstOrNull { it.parentId == frame.id && it.component == ComponentKind.SYSTEM_NAV.name }?.h ?: 0f

    fun addImage(path: String, pixelWidth: Int, pixelHeight: Int) {
        val frame = targetFrame()
        val maxW = (frame?.w ?: 400f) * 0.8f
        val ratio = pixelHeight.toFloat() / max(1, pixelWidth)
        val w = min(maxW, pixelWidth / 2f).coerceAtLeast(48f)
        val h = w * ratio
        val c = frame?.center ?: toCanvas(Offset(viewSize.width / 2, viewSize.height / 2))
        add(Node(type = NodeType.IMAGE, name = "Image", x = c.x - w / 2, y = c.y - h / 2, w = w, h = h,
            imagePath = path, fill = null, cornerRadius = 12f, parentId = frame?.id))
        tool = Tool.SELECT
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        checkpoint()
        nodes = nodes.filter { it.id != id && it.parentId != id }
        selectedId = null
    }

    fun duplicateSelected() {
        val node = selected ?: return
        checkpoint()
        if (node.isFrame) {
            val copy = node.copy(id = newId(), name = "${node.name} copy", x = node.x + node.w + 120f)
            val dx = copy.x - node.x
            val kids = nodes.filter { it.parentId == node.id }.map { it.copy(id = newId(), parentId = copy.id, x = it.x + dx) }
            nodes = nodes + copy + kids
            selectedId = copy.id
        } else {
            val copy = node.copy(id = newId(), name = node.name, x = node.x + 16f, y = node.y + 16f)
            val i = nodes.indexOfFirst { it.id == node.id }
            nodes = nodes.toMutableList().apply { add(i + 1, copy) }
            selectedId = copy.id
        }
    }

    /** Moves the selection in z-order among the layers that share its parent. */
    fun reorder(toFront: Boolean, allTheWay: Boolean) {
        val node = selected ?: return
        val siblings = nodes.filter { it.parentId == node.parentId }
        val pos = siblings.indexOfFirst { it.id == node.id }
        val targetPos = when {
            allTheWay && toFront -> siblings.lastIndex
            allTheWay -> 0
            toFront -> min(siblings.lastIndex, pos + 1)
            else -> max(0, pos - 1)
        }
        if (targetPos == pos) return
        checkpoint()
        val reordered = siblings.toMutableList().apply { removeAt(pos); add(targetPos, node) }
        // Write the reordered siblings back into the slots the siblings occupied.
        val slots = nodes.indices.filter { nodes[it].parentId == node.parentId }
        val result = nodes.toMutableList()
        slots.forEachIndexed { k, slot -> result[slot] = reordered[k] }
        nodes = result
    }

    fun align(mode: Align) {
        val node = selected ?: return
        val container = node.parentId?.let { pid -> nodes.firstOrNull { it.id == pid } }?.rect ?: return
        update(node.id) {
            when (mode) {
                Align.LEFT -> it.copy(x = container.left)
                Align.CENTER_H -> it.copy(x = container.center.x - it.w / 2)
                Align.RIGHT -> it.copy(x = container.right - it.w)
                Align.TOP -> it.copy(y = container.top)
                Align.CENTER_V -> it.copy(y = container.center.y - it.h / 2)
                Align.BOTTOM -> it.copy(y = container.bottom - it.h)
            }
        }
    }

    fun setVisible(id: String, visible: Boolean) = update(id) { it.copy(visible = visible) }
    fun setLocked(id: String, locked: Boolean) = update(id) { it.copy(locked = locked) }

    // MARK: Interactive move / resize

    private var origins: Map<String, Node> = emptyMap()
    private var resizeHandle = -1

    fun beginMove(id: String) {
        val node = nodes.firstOrNull { it.id == id } ?: return
        checkpoint()
        origins = (listOf(node) + if (node.isFrame) nodes.filter { it.parentId == node.id } else emptyList()).associateBy { it.id }
    }

    /** [total] is the drag distance so far in canvas units. */
    fun moveTo(id: String, total: Offset) {
        val origin = origins[id] ?: return
        var dx = total.x
        var dy = total.y
        guides = emptyList()
        if (snapping) {
            val proposed = origin.rect.translate(dx, dy)
            val (sx, sy, g) = snap(proposed, id)
            dx += sx; dy += sy
            guides = g
        }
        replace(origins.mapValues { (_, n) -> n.copy(x = n.x + dx, y = n.y + dy) })
    }

    fun endMove(id: String) {
        guides = emptyList()
        val node = nodes.firstOrNull { it.id == id } ?: return
        origins = emptyMap()
        if (!node.isFrame) {
            val parent = nodes.lastOrNull { it.isFrame && it.id != node.id && it.rect.contains(node.center) }?.id
            if (parent != node.parentId) replace(mapOf(node.id to node.copy(parentId = parent)))
        }
    }

    fun beginResize(id: String, handle: Int) {
        val node = nodes.firstOrNull { it.id == id } ?: return
        checkpoint()
        origins = mapOf(id to node)
        resizeHandle = handle
    }

    /** Handles: 0 TL, 1 T, 2 TR, 3 R, 4 BR, 5 B, 6 BL, 7 L. [keepRatio] for corner drags (S Pen button / images). */
    fun resizeTo(id: String, total: Offset, keepRatio: Boolean = false) {
        val o = origins[id] ?: return
        // Drag distance in the node's own (unrotated) axes.
        val d = if (o.rotation == 0f) total else rotateAround(total, Offset.Zero, -o.rotation)
        var left = o.x; var top = o.y; var right = o.x + o.w; var bottom = o.y + o.h
        when (resizeHandle) {
            0 -> { left += d.x; top += d.y }
            1 -> top += d.y
            2 -> { right += d.x; top += d.y }
            3 -> right += d.x
            4 -> { right += d.x; bottom += d.y }
            5 -> bottom += d.y
            6 -> { left += d.x; bottom += d.y }
            7 -> left += d.x
        }
        val minSize = if (o.type == NodeType.LINE) 1f else 4f
        if (right - left < minSize) { if (resizeHandle in listOf(0, 6, 7)) left = right - minSize else right = left + minSize }
        if (bottom - top < minSize) { if (resizeHandle in listOf(0, 1, 2)) top = bottom - minSize else bottom = top + minSize }
        if (keepRatio && resizeHandle % 2 == 0 && o.h > 0f) {
            val ratio = o.w / o.h
            val w = right - left
            val h = w / ratio
            if (resizeHandle == 0 || resizeHandle == 2) top = bottom - h else bottom = top + h
        }
        replace(mapOf(id to o.copy(x = left, y = top, w = right - left, h = bottom - top)))
    }

    fun endResize() {
        origins = emptyMap()
        resizeHandle = -1
    }

    /** Snaps [r]'s edges and centre to its parent frame and siblings. Returns (dx, dy, guides). */
    internal fun snap(r: Rect, movingId: String): Triple<Float, Float, List<Guide>> {
        val moving = nodes.firstOrNull { it.id == movingId } ?: return Triple(0f, 0f, emptyList())
        val movingIds = origins.keys
        val parent = moving.parentId?.let { pid -> nodes.firstOrNull { it.id == pid } }
            ?: if (!moving.isFrame) frameAt(r.center) else null
        val others = buildList {
            if (parent != null) add(parent.rect)
            nodes.filter { it.id !in movingIds && it.visible && it.id != parent?.id &&
                (if (moving.isFrame) it.isFrame else it.parentId == (parent?.id ?: moving.parentId)) }
                .forEach { add(it.rect) }
        }
        val threshold = 6f / zoom
        fun best(mine: List<Float>, theirs: List<Float>): Pair<Float, Float>? {
            var bestD: Float? = null
            var at = 0f
            for (m in mine) for (t in theirs) {
                val dd = t - m
                if (abs(dd) <= threshold && (bestD == null || abs(dd) < abs(bestD))) { bestD = dd; at = t }
            }
            return bestD?.let { it to at }
        }
        val xs = others.flatMap { listOf(it.left, it.center.x, it.right) }
        val ys = others.flatMap { listOf(it.top, it.center.y, it.bottom) }
        val bx = best(listOf(r.left, r.center.x, r.right), xs)
        val by = best(listOf(r.top, r.center.y, r.bottom), ys)
        val dx = bx?.first ?: 0f
        val dy = by?.first ?: 0f
        val moved = r.translate(dx, dy)
        val guides = buildList {
            bx?.let { (_, x) ->
                val span = others.filter { abs(it.left - x) < 0.01f || abs(it.center.x - x) < 0.01f || abs(it.right - x) < 0.01f }
                val top = (span.map { it.top } + moved.top).min()
                val bottom = (span.map { it.bottom } + moved.bottom).max()
                add(Guide(true, x, top, bottom))
            }
            by?.let { (_, y) ->
                val span = others.filter { abs(it.top - y) < 0.01f || abs(it.center.y - y) < 0.01f || abs(it.bottom - y) < 0.01f }
                val left = (span.map { it.left } + moved.left).min()
                val right = (span.map { it.right } + moved.right).max()
                add(Guide(false, y, left, right))
            }
        }
        return Triple(dx, dy, guides)
    }

    // MARK: Creating by drag

    fun startDraft(tool: Tool, at: Offset) {
        draft = when (tool) {
            Tool.RECT -> Node(type = NodeType.RECT, name = "Rectangle", x = at.x, y = at.y, w = 1f, h = 1f, fill = 0xFFD0BCFF, cornerRadius = 8f)
            Tool.ELLIPSE -> Node(type = NodeType.ELLIPSE, name = "Ellipse", x = at.x, y = at.y, w = 1f, h = 1f, fill = 0xFFFFD8E4)
            Tool.LINE -> Node(type = NodeType.LINE, name = "Line", x = at.x, y = at.y, w = 1f, h = 1f, fill = null, stroke = 0xFF1D1B20, strokeWidth = 2f)
            else -> null
        }
        draftStart = at
    }

    private var draftStart = Offset.Zero

    fun updateDraft(at: Offset, square: Boolean = false) {
        val d = draft ?: return
        var end = at
        if (square && d.type != NodeType.LINE) {
            val s = max(abs(at.x - draftStart.x), abs(at.y - draftStart.y))
            end = Offset(draftStart.x + s * sign(at.x - draftStart.x), draftStart.y + s * sign(at.y - draftStart.y))
        }
        val left = min(draftStart.x, end.x)
        val top = min(draftStart.y, end.y)
        val w = max(1f, abs(end.x - draftStart.x))
        val h = max(1f, abs(end.y - draftStart.y))
        draft = if (d.type == NodeType.LINE) {
            d.copy(x = left, y = top, w = w, h = h, points = listOf(
                if (draftStart.x <= end.x) 0f else 1f, if (draftStart.y <= end.y) 0f else 1f,
                if (draftStart.x <= end.x) 1f else 0f, if (draftStart.y <= end.y) 1f else 0f,
            ))
        } else d.copy(x = left, y = top, w = w, h = h)
    }

    private fun sign(v: Float) = if (v < 0) -1f else 1f

    /** Commits the draft; a tap (tiny draft) creates a default-size shape. */
    fun finishDraft(tapped: Boolean) {
        val d = draft ?: return
        draft = null
        val node = if (tapped || (d.w < 4f && d.h < 4f)) {
            when (d.type) {
                NodeType.LINE -> d.copy(w = 120f, h = 1f, points = listOf(0f, 0.5f, 1f, 0.5f))
                else -> d.copy(x = d.x - 50f, y = d.y - 50f, w = 100f, h = 100f)
            }
        } else d
        add(node)
        tool = Tool.SELECT
    }

    fun addText(at: Offset) {
        val node = Node(type = NodeType.TEXT, name = "Text", x = at.x, y = at.y - 12f, w = 200f, h = 28f, fill = null,
            text = "Text", fontSize = 20f, fontWeight = 400)
        add(node)
        tool = Tool.SELECT
        editingTextId = node.id
    }

    fun commitPen() {
        val pts = penDraft
        penDraft = emptyList()
        if (pts.size < 2) return
        val pad = penWidth / 2
        val left = pts.minOf { it.x } - pad
        val top = pts.minOf { it.y } - pad
        val w = max(1f, pts.maxOf { it.x } + pad - left)
        val h = max(1f, pts.maxOf { it.y } + pad - top)
        val normalized = pts.flatMap { listOf((it.x - left) / w, (it.y - top) / h) }
        add(Node(type = NodeType.PATH, name = "Drawing", x = left, y = top, w = w, h = h, fill = null,
            stroke = penColor, strokeWidth = penWidth, points = normalized), select = false)
    }

    /** Resizes a frame to another device, keeping its system bars at the screen edges. */
    fun changeDevice(id: String, preset: DevicePreset) {
        val frame = nodes.firstOrNull { it.id == id } ?: return
        checkpoint()
        val w = preset.width.toFloat()
        val h = preset.height.toFloat()
        nodes = nodes.map { n ->
            when {
                n.id == id -> n.copy(w = w, h = h, device = preset.id, cornerRadius = preset.cornerRadius,
                    name = if (Devices.byId(frame.device)?.name == frame.name) preset.name else n.name)
                n.parentId == id -> fitToFrame(n, frame, w, h)
                else -> n
            }
        }
    }

    /** Swaps a frame between portrait and landscape. */
    fun rotateFrame(id: String) {
        val frame = nodes.firstOrNull { it.id == id } ?: return
        checkpoint()
        nodes = nodes.map { n ->
            when {
                n.id == id -> n.copy(w = frame.h, h = frame.w)
                n.parentId == id -> fitToFrame(n, frame, frame.h, frame.w)
                else -> n
            }
        }
    }

    /** Full-width children (bars, sheets) stretch with the frame; bottom-docked ones stay at the bottom. */
    private fun fitToFrame(n: Node, frame: Node, w: Float, h: Float): Node {
        val fullWidth = abs(n.x - frame.x) < 0.5f && abs(n.w - frame.w) < 0.5f
        val docksBottom = abs(n.y + n.h - (frame.y + frame.h)) < 0.5f
        var c = n
        if (fullWidth) c = c.copy(w = w)
        if (docksBottom) c = c.copy(y = frame.y + h - n.h)
        return c
    }

    fun rename(id: String, newName: String) = update(id, "name") { it.copy(name = newName) }
}
