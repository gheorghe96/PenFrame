package com.gheorghe.penframe.render

import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.util.LruCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.model.TextAlignment
import com.gheorghe.penframe.model.toColor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Decoded images, downsampled to at most 2048 px. Keyed by file path. */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = HashSet<String>()

    fun get(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        if (path in missing) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) { missing += path; return null }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: run { missing += path; return null }
        return bitmap.asImageBitmap().also { cache.put(path, it) }
    }
}

fun Node.shapePath(): Path = Path().apply {
    when (type) {
        NodeType.ELLIPSE -> addOval(Rect(0f, 0f, w, h))
        else -> addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(min(cornerRadius, min(w, h) / 2))))
    }
}

/** Gradient fill: props gradientTo (ARGB) and gradientAngle (degrees, 0 = left→right). */
private fun Node.fillBrush(): Brush? {
    val from = fill?.toColor() ?: return null
    val to = props["gradientTo"]?.toLongOrNull()?.toColor() ?: return null
    val a = Math.toRadians((props["gradientAngle"]?.toFloatOrNull() ?: 90f).toDouble())
    val dx = cos(a).toFloat() * w / 2
    val dy = sin(a).toFloat() * h / 2
    return Brush.linearGradient(listOf(from, to), Offset(w / 2 - dx, h / 2 - dy), Offset(w / 2 + dx, h / 2 + dy))
}

private fun DrawScope.drawShadow(node: Node) {
    val shadow = node.shadow ?: return
    val shape = node.shapePath()
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = shadow.color.toColor().toArgb()
        if (shadow.blur > 0.5f) fp.maskFilter = BlurMaskFilter(shadow.blur, BlurMaskFilter.Blur.NORMAL)
        canvas.save()
        canvas.translate(shadow.x, shadow.y)
        canvas.drawPath(shape, paint)
        canvas.restore()
    }
}

/** Draws one node (not its children) in canvas coordinates. */
fun DrawScope.drawNode(node: Node, ctx: RenderContext) {
    if (!node.visible) return
    withTransform({
        if (node.rotation != 0f) rotate(node.rotation, pivot = node.center)
        translate(node.x, node.y)
    }) {
        val layered = node.opacity < 0.999f
        if (layered) drawContext.canvas.saveLayer(
            Rect(-node.w, -node.h, node.w * 2, node.h * 2).inflate(64f),
            Paint().apply { alpha = node.opacity },
        )
        drawNodeContent(node, ctx)
        if (layered) drawContext.canvas.restore()
    }
}

private fun DrawScope.drawNodeContent(node: Node, ctx: RenderContext) {
    val w = node.w
    val h = node.h
    when (node.type) {
        NodeType.FRAME, NodeType.RECT, NodeType.ELLIPSE -> {
            drawShadow(node)
            val path = node.shapePath()
            val brush = node.fillBrush()
            when {
                brush != null -> drawPath(path, brush)
                node.fill != null -> drawPath(path, node.fill.toColor())
            }
            node.stroke?.let { if (node.strokeWidth > 0f) drawShapeStroke(node, it.toColor()) }
        }
        NodeType.TEXT -> {
            node.fill?.let { bg ->
                drawShadow(node)
                drawPath(node.shapePath(), bg.toColor())
            }
            val layout = ctx.layout(
                node.text.ifEmpty { " " }, node.fontSize, node.textColor.toColor(), node.fontWeight,
                maxWidth = w, maxLines = Int.MAX_VALUE,
                align = when (node.textAlign) {
                    TextAlignment.START -> TextAlign.Start
                    TextAlignment.CENTER -> TextAlign.Center
                    TextAlignment.END -> TextAlign.End
                },
                lineHeight = node.lineHeight,
            )
            drawText(layout, topLeft = Offset.Zero)
        }
        NodeType.LINE -> {
            val pts = node.points.takeIf { it.size >= 4 } ?: listOf(0f, 0.5f, 1f, 0.5f)
            val c = (node.stroke ?: node.fill ?: 0xFF1D1B20).toColor()
            drawLine(c, Offset(pts[0] * w, pts[1] * h), Offset(pts[2] * w, pts[3] * h), max(0.5f, node.strokeWidth), StrokeCap.Round)
            if (node.props["arrow"] == "true") drawArrowHead(Offset(pts[0] * w, pts[1] * h), Offset(pts[2] * w, pts[3] * h), c, node.strokeWidth)
        }
        NodeType.PATH -> {
            val c = (node.stroke ?: 0xFF1D1B20).toColor()
            drawPath(penPath(node.points, w, h), c, style = Stroke(node.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        NodeType.IMAGE -> {
            drawShadow(node)
            val bitmap = node.imagePath?.let { ImageCache.get(it) }
            clipPath(node.shapePath()) {
                if (bitmap == null) {
                    drawRect(Color(0xFFE6E0E9), size = Size(w, h))
                    drawIcon("image", Offset(w / 2 - 20, h / 2 - 20), 40f, Color(0xFF79747E))
                } else {
                    // Fill the box like ContentScale.Crop.
                    val scale = max(w / bitmap.width, h / bitmap.height)
                    val sw = w / scale
                    val sh = h / scale
                    val sx = ((bitmap.width - sw) / 2).roundToInt().coerceAtLeast(0)
                    val sy = ((bitmap.height - sh) / 2).roundToInt().coerceAtLeast(0)
                    drawImage(
                        bitmap,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(sw.roundToInt().coerceIn(1, bitmap.width), sh.roundToInt().coerceIn(1, bitmap.height)),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())),
                    )
                }
            }
            node.stroke?.let { if (node.strokeWidth > 0f) drawShapeStroke(node, it.toColor()) }
        }
        NodeType.COMPONENT -> {
            drawShadow(node)
            drawComponent(node, ctx)
            node.stroke?.let { if (node.strokeWidth > 0f) drawShapeStroke(node, it.toColor()) }
        }
    }
}

private fun DrawScope.drawShapeStroke(node: Node, color: Color) {
    val sw = node.strokeWidth
    val inset = sw / 2
    if (node.type == NodeType.ELLIPSE) {
        drawOval(color, Offset(inset, inset), Size(node.w - sw, node.h - sw), style = Stroke(sw))
    } else {
        val r = max(0f, min(node.cornerRadius, min(node.w, node.h) / 2) - inset)
        drawRoundRect(color, Offset(inset, inset), Size(node.w - sw, node.h - sw), CornerRadius(r), style = Stroke(sw))
    }
}

private fun DrawScope.drawArrowHead(from: Offset, to: Offset, c: Color, width: Float) {
    val angle = kotlin.math.atan2(to.y - from.y, to.x - from.x)
    val len = 6f + width * 3
    for (side in listOf(-1, 1)) {
        val a = angle + Math.PI.toFloat() + side * 0.5f
        drawLine(c, to, Offset(to.x + len * cos(a), to.y + len * sin(a)), max(0.5f, width), StrokeCap.Round)
    }
}

/** Smooth path through normalised points (x,y pairs), scaled to the node box. */
fun penPath(points: List<Float>, w: Float, h: Float): Path {
    val path = Path()
    if (points.size < 2) return path
    val n = points.size / 2
    fun px(i: Int) = points[i * 2] * w
    fun py(i: Int) = points[i * 2 + 1] * h
    path.moveTo(px(0), py(0))
    if (n == 1) { path.lineTo(px(0) + 0.01f, py(0)); return path }
    for (i in 1 until n - 1) {
        val mx = (px(i) + px(i + 1)) / 2
        val my = (py(i) + py(i + 1)) / 2
        path.quadraticTo(px(i), py(i), mx, my)
    }
    path.lineTo(px(n - 1), py(n - 1))
    return path
}
