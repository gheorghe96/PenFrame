package com.gheorghe.penframe.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

fun newId(): String = UUID.randomUUID().toString().take(12)

/** What a layer is. Everything on the canvas is a [Node]; devices are [FRAME]s. */
@Serializable
enum class NodeType { FRAME, RECT, ELLIPSE, LINE, TEXT, IMAGE, PATH, COMPONENT }

@Serializable
enum class TextAlignment { START, CENTER, END }

@Serializable
data class ShadowSpec(
    val x: Float = 0f,
    val y: Float = 4f,
    val blur: Float = 12f,
    val color: Long = 0x40000000,
)

/**
 * One layer. Positions are absolute canvas coordinates in dp, so a 412×915 device frame is
 * exactly 412×915 units. Children of a frame keep absolute coordinates plus [parentId]; moving
 * a frame moves its children with it.
 */
@Serializable
data class Node(
    val id: String = newId(),
    val type: NodeType,
    val name: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
    val parentId: String? = null,
    val fill: Long? = 0xFFE6E0EC,
    val stroke: Long? = null,
    val strokeWidth: Float = 1f,
    val cornerRadius: Float = 0f,
    val opacity: Float = 1f,
    val shadow: ShadowSpec? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    // Text
    val text: String = "",
    val fontSize: Float = 16f,
    val fontWeight: Int = 400,
    val textColor: Long = 0xFF1D1B20,
    val textAlign: TextAlignment = TextAlignment.START,
    val lineHeight: Float = 1.25f,
    // Image
    val imagePath: String? = null,
    // Component instance
    val component: String? = null,
    val props: Map<String, String> = emptyMap(),
    // Frame
    val device: String? = null,
    /** PATH: x,y pairs normalised to 0..1 of the node's box, so resizing scales the drawing. */
    val points: List<Float> = emptyList(),
) {
    val rect: Rect get() = Rect(x, y, x + w, y + h)
    val center: Offset get() = Offset(x + w / 2f, y + h / 2f)
    val isFrame: Boolean get() = type == NodeType.FRAME

    fun prop(key: String): String = props[key] ?: ComponentKind.of(component)?.defaults?.get(key).orEmpty()

    /** Hit test that honours rotation (the point is rotated into the node's frame). */
    fun contains(p: Offset, slop: Float = 0f): Boolean {
        val local = if (rotation == 0f) p else rotateAround(p, center, -rotation)
        val r = rect.inflate(slop)
        if (type == NodeType.LINE && h < 2f) return local.x in r.left..r.right && kotlin.math.abs(local.y - (y + h / 2)) <= slop + 6f
        return r.contains(local)
    }
}

fun rotateAround(p: Offset, c: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val dx = p.x - c.x
    val dy = p.y - c.y
    return Offset(
        (c.x + dx * cos(rad) - dy * sin(rad)).toFloat(),
        (c.y + dx * sin(rad) + dy * cos(rad)).toFloat(),
    )
}

@Serializable
data class Viewport(val offsetX: Float = 80f, val offsetY: Float = 120f, val zoom: Float = 0.5f)

@Serializable
data class Project(
    val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val nodes: List<Node> = emptyList(),
    val viewport: Viewport = Viewport(),
    val schema: Int = 1,
)

// MARK: Colors

fun Long.toColor(): Color = Color(this.toInt())
fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun Long.toHex(): String {
    val a = (this ushr 24) and 0xFF
    val rgb = String.format("%06X", this and 0xFFFFFF)
    return if (a == 0xFFL) "#$rgb" else "#" + String.format("%02X", a) + rgb
}

fun parseHex(text: String): Long? {
    val s = text.trim().removePrefix("#")
    val v = s.toLongOrNull(16) ?: return null
    return when (s.length) {
        6 -> 0xFF000000L or v
        8 -> v
        3 -> {
            val r = (v shr 8) and 0xF
            val g = (v shr 4) and 0xF
            val b = v and 0xF
            0xFF000000L or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
        }
        else -> null
    }
}

fun Rect.union(other: Rect): Rect = Rect(
    kotlin.math.min(left, other.left), kotlin.math.min(top, other.top),
    kotlin.math.max(right, other.right), kotlin.math.max(bottom, other.bottom),
)
