package com.gheorghe.penframe.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.AlignVerticalBottom
import androidx.compose.material.icons.rounded.AlignVerticalCenter
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gheorghe.penframe.editor.Align
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.model.ComponentKind
import com.gheorghe.penframe.model.Devices
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.model.PropSpec
import com.gheorghe.penframe.model.PropType
import com.gheorghe.penframe.model.ShadowSpec
import com.gheorghe.penframe.model.TextAlignment
import com.gheorghe.penframe.model.toHex
import com.gheorghe.penframe.render.IconPaths
import com.gheorghe.penframe.render.drawIcon
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InspectorPanel(state: EditorState, onImage: () -> Unit, modifier: Modifier) {
    val node = state.selected
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (node == null) CanvasSettings(state)
        else NodeInspector(state, node)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    content()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasSettings(state: EditorState) {
    Text("Nothing selected", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tap a layer to edit it. With the S Pen: the pen draws and edits, fingers pan, two fingers zoom, and holding the pen button while dragging pans. Hover the pen to preview what it will pick.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Section("Pen") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var picking by remember { mutableStateOf(false) }
            ColorSwatch(state.penColor) { picking = true }
            if (picking) ColorPickerDialog(state.penColor, false, { picking = false }) { c -> c?.let { state.penColor = it }; picking = false }
            Spacer(Modifier.width(12.dp))
            Text("Width ${state.penWidth.roundToInt()}", Modifier.width(72.dp))
            Slider(state.penWidth, { state.penWidth = it }, valueRange = 1f..24f, modifier = Modifier.weight(1f))
        }
    }
    Section("Canvas") {
        ToggleRow("Snap to edges & centers", state.snapping) { state.snapping = it }
        ToggleRow("Show device bezels", state.showBezels) { state.showBezels = it }
        ToggleRow("Fingers pan when using S Pen", state.fingersPanWithPen) { state.fingersPanWithPen = it }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun NodeInspector(state: EditorState, node: Node) {
    val id = node.id
    fun edit(key: String? = null, f: (Node) -> Node) = state.update(id, key, f)

    // Name + actions
    NameField(node.name) { edit("name") { n -> n.copy(name = it) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionIcon(Icons.Rounded.ContentCopy, "Duplicate") { state.duplicateSelected() }
        ActionIcon(Icons.Rounded.FlipToFront, "Bring to front") { state.reorder(toFront = true, allTheWay = true) }
        ActionIcon(Icons.Rounded.KeyboardArrowUp, "Bring forward") { state.reorder(toFront = true, allTheWay = false) }
        ActionIcon(Icons.Rounded.KeyboardArrowDown, "Send backward") { state.reorder(toFront = false, allTheWay = false) }
        ActionIcon(Icons.Rounded.FlipToBack, "Send to back") { state.reorder(toFront = false, allTheWay = true) }
        ActionIcon(Icons.Rounded.Delete, "Delete") { state.deleteSelected() }
    }
    if (node.parentId != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ActionIcon(Icons.Rounded.AlignHorizontalLeft, "Align left") { state.align(Align.LEFT) }
            ActionIcon(Icons.Rounded.AlignHorizontalCenter, "Align center") { state.align(Align.CENTER_H) }
            ActionIcon(Icons.Rounded.AlignHorizontalRight, "Align right") { state.align(Align.RIGHT) }
            ActionIcon(Icons.Rounded.AlignVerticalTop, "Align top") { state.align(Align.TOP) }
            ActionIcon(Icons.Rounded.AlignVerticalCenter, "Align middle") { state.align(Align.CENTER_V) }
            ActionIcon(Icons.Rounded.AlignVerticalBottom, "Align bottom") { state.align(Align.BOTTOM) }
        }
    }

    // Frame: position relative to the frame for children reads better.
    val parent = node.parentId?.let { pid -> state.nodes.firstOrNull { it.id == pid } }
    val ox = parent?.x ?: 0f
    val oy = parent?.y ?: 0f
    Section(if (parent != null) "Layout (in ${parent.name})" else "Layout") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("X", node.x - ox, Modifier.weight(1f)) { v -> edit("x") { it.copy(x = v + ox) }; moveChildren(state, node, dx = v + ox - node.x, dy = 0f) }
            NumberField("Y", node.y - oy, Modifier.weight(1f)) { v -> edit("y") { it.copy(y = v + oy) }; moveChildren(state, node, dx = 0f, dy = v + oy - node.y) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("W", node.w, Modifier.weight(1f)) { v -> if (v >= 1f) edit("w") { it.copy(w = v) } }
            NumberField("H", node.h, Modifier.weight(1f)) { v -> if (v >= 1f) edit("h") { it.copy(h = v) } }
        }
        if (!node.isFrame) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField("Rotation°", node.rotation, Modifier.weight(1f)) { v -> edit("rot") { it.copy(rotation = ((v % 360) + 360) % 360) } }
                NumberField("Radius", node.cornerRadius, Modifier.weight(1f)) { v -> edit("radius") { it.copy(cornerRadius = v.coerceAtLeast(0f)) } }
            }
        }
    }

    if (node.isFrame) FrameSection(state, node)

    ComponentKind.of(node.component)?.let { kind ->
        if (kind.specs.isNotEmpty()) Section(kind.title) {
            kind.specs.forEach { spec -> PropEditor(spec, node.prop(spec.key)) { v -> edit("prop-${spec.key}") { it.copy(props = it.props + (spec.key to v)) } } }
        }
    }

    if (node.type == NodeType.TEXT) TextSection(node) { key, f -> edit(key, f) }

    Section("Appearance") {
        val fillLabel = when (node.type) {
            NodeType.COMPONENT -> if (node.component in listOf(ComponentKind.STATUS_BAR.name, ComponentKind.SYSTEM_NAV.name)) "Background" else "Main color"
            NodeType.TEXT -> "Background"
            NodeType.FRAME -> "Background"
            else -> "Fill"
        }
        if (node.type != NodeType.PATH && node.type != NodeType.LINE && node.type != NodeType.IMAGE) {
            ColorRow(fillLabel, node.fill, allowNone = true) { c -> edit("fill") { it.copy(fill = c) } }
            if (node.type in listOf(NodeType.RECT, NodeType.ELLIPSE, NodeType.FRAME) && node.fill != null) {
                val gradient = node.props["gradientTo"]?.toLongOrNull()
                ToggleRow("Gradient", gradient != null) { on ->
                    edit { it.copy(props = if (on) it.props + ("gradientTo" to "4294967295") else it.props - "gradientTo") }
                }
                if (gradient != null) {
                    ColorRow("Gradient end", gradient, allowNone = false) { c -> edit("grad") { it.copy(props = it.props + ("gradientTo" to (c ?: 0xFFFFFFFF).toString())) } }
                    val angle = node.props["gradientAngle"]?.toFloatOrNull() ?: 90f
                    LabeledSlider("Angle ${angle.roundToInt()}°", angle, 0f..360f) { v -> edit("gradAngle") { it.copy(props = it.props + ("gradientAngle" to v.roundToInt().toString())) } }
                }
            }
        }
        ColorRow(if (node.type == NodeType.PATH || node.type == NodeType.LINE) "Stroke" else "Border", node.stroke, allowNone = node.type != NodeType.PATH) { c ->
            edit("stroke") { it.copy(stroke = c) }
        }
        if (node.stroke != null || node.type == NodeType.PATH || node.type == NodeType.LINE) {
            LabeledSlider("Width ${"%.1f".format(node.strokeWidth)}", node.strokeWidth, 0f..32f) { v -> edit("strokeW") { it.copy(strokeWidth = v) } }
        }
        if (node.type == NodeType.LINE) ToggleRow("Arrow head", node.props["arrow"] == "true") { on -> edit { it.copy(props = it.props + ("arrow" to on.toString())) } }
        LabeledSlider("Opacity ${(node.opacity * 100).roundToInt()}%", node.opacity, 0f..1f) { v -> edit("opacity") { it.copy(opacity = v) } }
    }

    if (node.type != NodeType.PATH && node.type != NodeType.LINE) Section("Shadow") {
        ToggleRow("Drop shadow", node.shadow != null) { on -> edit { it.copy(shadow = if (on) ShadowSpec() else null) } }
        node.shadow?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("X", s.x, Modifier.weight(1f)) { v -> edit("sx") { it.copy(shadow = s.copy(x = v)) } }
                NumberField("Y", s.y, Modifier.weight(1f)) { v -> edit("sy") { it.copy(shadow = s.copy(y = v)) } }
                NumberField("Blur", s.blur, Modifier.weight(1f)) { v -> edit("sb") { it.copy(shadow = s.copy(blur = v.coerceAtLeast(0f))) } }
            }
            ColorRow("Shadow color", s.color, allowNone = false) { c -> edit("sc") { it.copy(shadow = s.copy(color = c ?: 0x40000000)) } }
        }
    }
}

private fun moveChildren(state: EditorState, node: Node, dx: Float, dy: Float) {
    if (!node.isFrame || (dx == 0f && dy == 0f)) return
    state.nodes.filter { it.parentId == node.id }.forEach { child ->
        state.update(child.id, "move-${node.id}") { it.copy(x = it.x + dx, y = it.y + dy) }
    }
}

@Composable
private fun FrameSection(state: EditorState, node: Node) {
    Section("Device") {
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(Devices.byId(node.device)?.name ?: "Custom size", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                Devices.all.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("${preset.name}  ${preset.width}×${preset.height}") },
                        onClick = { menu = false; state.changeDevice(node.id, preset) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.rotateFrame(node.id) }) {
                Icon(Icons.Rounded.ScreenRotation, null); Spacer(Modifier.width(8.dp)); Text("Rotate")
            }
            Spacer(Modifier.weight(1f))
            NumberField("Screen radius", node.cornerRadius, Modifier.width(140.dp)) { v -> state.update(node.id, "radius") { it.copy(cornerRadius = v.coerceAtLeast(0f)) } }
        }
        ToggleRow("Device bezel", node.props["bezel"] == "true") { on -> state.update(node.id) { it.copy(props = it.props + ("bezel" to on.toString())) } }
    }
}

@Composable
private fun TextSection(node: Node, edit: (String?, (Node) -> Node) -> Unit) {
    Section("Text") {
        OutlinedTextField(
            node.text, { v -> edit("text") { it.copy(text = v, name = v.lineSequence().first().take(32).ifBlank { "Text" }) } },
            modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Content") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField("Size", node.fontSize, Modifier.weight(1f)) { v -> if (v >= 1f) edit("fs") { it.copy(fontSize = v) } }
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(weightName(node.fontWeight)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    (100..900 step 100).forEach { w ->
                        DropdownMenuItem(text = { Text(weightName(w)) }, onClick = { menu = false; edit(null) { it.copy(fontWeight = w) } })
                    }
                }
            }
        }
        ColorRow("Text color", node.textColor, allowNone = false) { c -> edit("tc") { it.copy(textColor = c ?: 0xFF1D1B20) } }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val options = listOf(
                TextAlignment.START to Icons.AutoMirrored.Rounded.FormatAlignLeft,
                TextAlignment.CENTER to Icons.Rounded.FormatAlignCenter,
                TextAlignment.END to Icons.AutoMirrored.Rounded.FormatAlignRight,
            )
            options.forEachIndexed { i, (align, icon) ->
                SegmentedButton(node.textAlign == align, { edit(null) { it.copy(textAlign = align) } }, SegmentedButtonDefaults.itemShape(i, 3)) {
                    Icon(icon, align.name)
                }
            }
        }
        LabeledSlider("Line height ${"%.2f".format(node.lineHeight)}", node.lineHeight, 0.8f..2.5f) { v -> edit("lh") { it.copy(lineHeight = v) } }
    }
}

private fun weightName(w: Int) = when (w) {
    100 -> "Thin"; 200 -> "Extra light"; 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"
    600 -> "Semibold"; 700 -> "Bold"; 800 -> "Extra bold"; else -> "Black"
}

@Composable
private fun PropEditor(spec: PropSpec, value: String, onChange: (String) -> Unit) {
    when (spec.type) {
        PropType.TEXT -> OutlinedTextField(
            value, onChange, label = { Text(spec.label) },
            singleLine = spec.key !in setOf("body", "text", "value"),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        PropType.BOOL -> ToggleRow(spec.label, value == "true") { onChange(it.toString()) }
        PropType.CHOICE -> {
            Text(spec.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.padding(bottom = 8.dp)) {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.replaceFirstChar { it.uppercase() }, Modifier.weight(1f)); Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    spec.choices.forEach { c -> DropdownMenuItem(text = { Text(c.replaceFirstChar { it.uppercase() }) }, onClick = { menu = false; onChange(c) }) }
                }
            }
        }
        PropType.NUMBER -> {
            val v = value.toFloatOrNull() ?: spec.range.start
            val integral = spec.range.endInclusive <= 60f
            LabeledSlider("${spec.label}: ${if (integral) v.roundToInt() else v.roundToInt()}", v, spec.range,
                steps = if (integral) (spec.range.endInclusive - spec.range.start).toInt() - 1 else 0) {
                onChange(it.roundToInt().toString())
            }
        }
        PropType.ICON -> {
            var picking by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(spec.label, Modifier.weight(1f))
                val tint = MaterialTheme.colorScheme.onSurface
                if (value != "none" && value.isNotEmpty()) Canvas(Modifier.size(24.dp)) { drawIcon(value, Offset.Zero, size.width, tint) }
                Spacer(Modifier.width(8.dp))
                Text(value.replace('_', ' '), color = MaterialTheme.colorScheme.primary)
            }
            if (picking) IconPickerDialog(onDismiss = { picking = false }) { onChange(it); picking = false }
        }
    }
}

@Composable
private fun IconPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon") },
        text = {
            LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.height(380.dp)) {
                items(listOf("none") + IconPaths.names) { name ->
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(name) }, contentAlignment = Alignment.Center) {
                        if (name == "none") Text("None", style = MaterialTheme.typography.labelSmall)
                        else {
                            val tint = MaterialTheme.colorScheme.onSurface
                            Canvas(Modifier.size(26.dp)) { drawIcon(name, Offset.Zero, size.width, tint) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ColorRow(label: String, color: Long?, allowNone: Boolean, onChange: (Long?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(color?.toHex() ?: "None", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        ColorSwatch(color) { picking = true }
    }
    if (picking) ColorPickerDialog(color, allowNone, onDismiss = { picking = false }) { onChange(it); picking = false }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, steps = steps.coerceAtLeast(0))
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label) }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text("Layer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

/** Numeric field that keeps the user's partial input while typing and follows outside changes otherwise. */
@Composable
fun NumberField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var text by remember { mutableStateOf(fmtNum(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused && text.toFloatOrNull()?.let { abs(it - value) > 0.01f } != false) text = fmtNum(value)
    }
    OutlinedTextField(
        text,
        { t -> text = t; t.replace(',', '.').toFloatOrNull()?.let(onChange) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

private fun fmtNum(v: Float) = if (abs(v - v.roundToInt()) < 0.01f) v.roundToInt().toString() else "%.1f".format(v)

