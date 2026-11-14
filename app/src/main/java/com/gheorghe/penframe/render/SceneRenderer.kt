package com.gheorghe.penframe.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import com.gheorghe.penframe.model.Node
import kotlin.math.min

/** Roots in z-order, and each frame's children in z-order. */
class SceneIndex(nodes: List<Node>) {
    val byId: Map<String, Node> = nodes.associateBy { it.id }
    val roots: List<Node> = nodes.filter { it.parentId == null || byId[it.parentId] == null }
    val children: Map<String, List<Node>> = nodes.filter { it.parentId != null && byId[it.parentId] != null }.groupBy { it.parentId!! }

    /** Everything in paint order: each root followed by its children. */
    val paintOrder: List<Node> = buildList {
        for (root in roots) {
            add(root)
            children[root.id]?.let { addAll(it) }
        }
    }
}

/**
 * Draws the scene in canvas coordinates. Frames (device screens) clip their children;
 * [devices] adds a bezel around frames that have one switched on.
 */
fun DrawScope.drawScene(index: SceneIndex, ctx: RenderContext, devices: Boolean = true, only: String? = null) {
    for (root in index.roots) {
        if (only != null && root.id != only) continue
        if (!root.visible) continue
        if (root.isFrame) drawFrame(root, index.children[root.id].orEmpty(), ctx, devices)
        else drawNode(root, ctx)
    }
}

fun DrawScope.drawFrame(frame: Node, children: List<Node>, ctx: RenderContext, bezel: Boolean) {
    if (bezel && frame.props["bezel"] == "true") {
        val pad = 10f
        val r = frame.cornerRadius + pad
        drawRoundRect(Color(0xFF16161A), Offset(frame.x - pad, frame.y - pad), Size(frame.w + pad * 2, frame.h + pad * 2), CornerRadius(r, r))
        drawRoundRect(Color(0xFF3A3A42), Offset(frame.x - pad, frame.y - pad), Size(frame.w + pad * 2, frame.h + pad * 2), CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    }
    drawNode(frame, ctx)
    val clip = frame.shapePath()
    translate(frame.x, frame.y) {
        clipPath(clip) {
            translate(-frame.x, -frame.y) {
                for (child in children) drawNode(child, ctx)
            }
        }
    }
    if (bezel && frame.props["bezel"] == "true" && frame.props["camera"] != "false" && frame.w < 900) {
        drawCircle(Color(0xFF0B0B0D), min(6f, frame.w / 60f), Offset(frame.x + frame.w / 2, frame.y + 14f))
    }
}
