package com.gheorghe.penframe.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.gheorghe.penframe.model.Node
import java.io.File
import kotlin.math.roundToInt

object Exporter {
    /** Renders one frame (without bezel) at [scale]× its dp size. */
    fun renderFrame(nodes: List<Node>, frame: Node, ctx: RenderContext, scale: Float): Bitmap {
        val w = (frame.w * scale).roundToInt().coerceIn(1, 8192)
        val h = (frame.h * scale).roundToInt().coerceIn(1, 8192)
        val image = ImageBitmap(w, h)
        val index = SceneIndex(nodes)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            scale(scale, scale, Offset.Zero) {
                translate(-frame.x, -frame.y) {
                    drawFrame(frame.copy(cornerRadius = 0f), index.children[frame.id].orEmpty(), ctx, bezel = false)
                }
            }
        }
        return image.asAndroidBitmap()
    }

    /** Writes a PNG into the shareable cache folder and returns it. */
    fun exportFrame(context: Context, nodes: List<Node>, frame: Node, ctx: RenderContext, scale: Float): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = frame.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "screen" }
        val file = File(dir, "$safe@${scale.roundToInt()}x.png")
        file.outputStream().use { renderFrame(nodes, frame, ctx, scale).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
