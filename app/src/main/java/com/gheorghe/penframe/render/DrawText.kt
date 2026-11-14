package com.gheorghe.penframe.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max

/** Canvas units are dp, and the canvas transform already scales them, so text is laid out at density 1. */
private val UnitDensity = Density(1f, 1f)

/** Shared drawing helpers handed to every renderer. */
class RenderContext(val measurer: TextMeasurer) {
    fun layout(
        text: String,
        size: Float,
        color: Color,
        weight: Int = 400,
        maxWidth: Float = Float.POSITIVE_INFINITY,
        maxLines: Int = 1,
        align: TextAlign = TextAlign.Start,
        lineHeight: Float = 1.25f,
    ): TextLayoutResult {
        val w = if (maxWidth.isFinite()) max(1, ceil(maxWidth).toInt()) else Constraints.Infinity
        return measurer.measure(
            AnnotatedString(text),
            style = TextStyle(
                fontSize = size.sp,
                fontWeight = FontWeight(weight.coerceIn(100, 900)),
                color = color,
                textAlign = align,
                lineHeight = lineHeight.em,
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            constraints = if (w == Constraints.Infinity) Constraints() else Constraints(maxWidth = w),
            density = UnitDensity,
        )
    }
}

/** Text with its top-left at (x, y). Returns the laid-out width. */
fun DrawScope.text(
    ctx: RenderContext, s: String, x: Float, y: Float, size: Float, color: Color,
    weight: Int = 400, maxWidth: Float = Float.POSITIVE_INFINITY,
): Float {
    if (s.isEmpty()) return 0f
    val layout = ctx.layout(s, size, color, weight, maxWidth)
    drawText(layout, topLeft = Offset(x, y))
    return layout.size.width.toFloat()
}

/** Single-line text vertically centred on [cy], starting at [x]. */
fun DrawScope.textMid(
    ctx: RenderContext, s: String, x: Float, cy: Float, size: Float, color: Color,
    weight: Int = 400, maxWidth: Float = Float.POSITIVE_INFINITY,
): Float {
    if (s.isEmpty()) return 0f
    val layout = ctx.layout(s, size, color, weight, maxWidth)
    drawText(layout, topLeft = Offset(x, cy - layout.size.height / 2f))
    return layout.size.width.toFloat()
}

/** Single-line text centred on (cx, cy). */
fun DrawScope.textCenter(
    ctx: RenderContext, s: String, cx: Float, cy: Float, size: Float, color: Color,
    weight: Int = 400, maxWidth: Float = Float.POSITIVE_INFINITY,
) {
    if (s.isEmpty()) return
    val layout = ctx.layout(s, size, color, weight, maxWidth)
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

fun RenderContext.width(s: String, size: Float, weight: Int = 400): Float =
    if (s.isEmpty()) 0f else layout(s, size, Color.Black, weight).size.width.toFloat()
