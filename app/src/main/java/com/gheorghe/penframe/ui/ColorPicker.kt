package com.gheorghe.penframe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gheorghe.penframe.model.parseHex
import com.gheorghe.penframe.model.toArgbLong
import com.gheorghe.penframe.model.toColor
import com.gheorghe.penframe.model.toHex

private val Swatches: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF5F5F5, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF616161, 0xFF424242, 0xFF212121, 0xFF000000,
    0xFF6750A4, 0xFF7C4DFF, 0xFF3F51B5, 0xFF1E88E5, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
    0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFFF44336, 0xFFE91E63,
    0xFF9C27B0, 0xFF795548, 0xFF607D8B, 0xFFEADDFF, 0xFFD0BCFF, 0xFFFFD8E4, 0xFFC4EED0, 0xFFFFDDB3,
    0xFFFEF7FF, 0xFFF3EDF7, 0xFFE6E0E9, 0xFF1D1B20, 0xFF49454F, 0xFF79747E, 0xFFCAC4D0, 0x00000000,
)

/** A color button: shows the color (checkerboard-free "none" slash when null) and opens the picker. */
@Composable
fun ColorSwatch(color: Long?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        if (color == null) {
            Canvas(Modifier.size(36.dp)) { drawLine(Color(0xFFE53935), Offset(4f, size.height - 4f), Offset(size.width - 4f, 4f), 3f) }
        } else Box(Modifier.size(36.dp).background(color.toColor()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Long?, allowNone: Boolean, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val start = (initial ?: 0xFF6750A4).toColor()
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (start.red * 255).toInt(), (start.green * 255).toInt(), (start.blue * 255).toInt()), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(start.alpha) }
    fun current(): Long {
        val rgb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))
        return rgb.toLong() and 0xFFFFFFFFL
    }
    var hex by remember { mutableStateOf((initial ?: current()).toHex()) }
    fun setFrom(argb: Long) {
        val c = argb.toColor()
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), out)
        hue = out[0]; sat = out[1]; value = out[2]; alpha = c.alpha
        hex = argb.toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
                        Box(Modifier.size(48.dp).background(current().toColor()))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        hex, { text -> hex = text; parseHex(text)?.let { setFrom(it); hex = text } },
                        label = { Text("Hex") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Swatches.forEach { s ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { setFrom(s) }) {
                            Box(Modifier.size(28.dp).background(s.toColor()))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Label("Hue")
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                    Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(hue, { hue = it; hex = current().toHex() }, valueRange = 0f..360f)
                Label("Saturation")
                Slider(sat, { sat = it; hex = current().toHex() })
                Label("Brightness")
                Slider(value, { value = it; hex = current().toHex() })
                Label("Opacity ${(alpha * 100).toInt()}%")
                Slider(alpha, { alpha = it; hex = current().toHex() })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("Apply") } },
        dismissButton = {
            Row {
                if (allowNone) TextButton(onClick = { onPick(null) }) { Text("None") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Suppress("unused")
private fun Color.argb(): Long = toArgbLong()
