package com.gheorghe.penframe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.model.ComponentCategory
import com.gheorghe.penframe.model.ComponentKind
import com.gheorghe.penframe.model.Devices
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.render.IconPaths
import com.gheorghe.penframe.render.RenderContext
import com.gheorghe.penframe.render.SceneIndex
import com.gheorghe.penframe.render.drawIcon
import com.gheorghe.penframe.render.drawNode
import kotlin.math.min

// MARK: Insert

@Composable
fun InsertPanel(state: EditorState, modifier: Modifier, onInserted: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val ctx = remember(measurer) { RenderContext(measurer) }
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 4.dp)) {
            listOf("Components", "Devices", "Icons").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3), icon = {}) { Text(label, maxLines = 1) }
            }
        }
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Search ${listOf("components", "devices", "icons")[tab]}") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        val q = query.trim().lowercase()
        when (tab) {
            0 -> {
                val matches = ComponentKind.entries.filter {
                    q.isEmpty() || it.title.lowercase().contains(q) || it.keywords.contains(q) || it.category.title.lowercase().contains(q)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (category in ComponentCategory.entries) {
                        val kinds = matches.filter { it.category == category }
                        if (kinds.isEmpty()) continue
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-$category") {
                            Text(category.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(kinds, key = { it.name }) { kind ->
                            OutlinedCard(Modifier.clickable { state.addComponent(kind); onInserted() }) {
                                ComponentPreview(state.componentNode(kind, 0f, 0f), ctx, Modifier.fillMaxWidth().height(76.dp))
                                Text(kind.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                for ((category, presets) in Devices.grouped) {
                    val list = presets.filter { q.isEmpty() || it.name.lowercase().contains(q) || category.title.lowercase().contains(q) }
                    if (list.isEmpty()) continue
                    item(key = "h-$category") {
                        Text(category.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                    }
                    items(list, key = { it.id }) { preset ->
                        Row(
                            Modifier.fillMaxWidth().clickable { state.addDevice(preset); onInserted() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(preset.name, Modifier.weight(1f))
                            Text("${preset.width} × ${preset.height}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> {
                val icons = IconPaths.names.filter { q.isEmpty() || it.contains(q) }
                LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), contentPadding = PaddingValues(12.dp)) {
                    items(icons, key = { it }) { name ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                state.addComponent(ComponentKind.ICON)
                                state.selectedId?.let { id -> state.update(id) { it.copy(props = it.props + ("icon" to name), name = name) } }
                                onInserted()
                            }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tint = MaterialTheme.colorScheme.onSurface
                            Canvas(Modifier.size(28.dp)) { drawIcon(name, Offset.Zero, size.width, tint) }
                            Text(name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A component drawn by the real renderer, scaled to fit the tile. */
@Composable
fun ComponentPreview(node: Node, ctx: RenderContext, modifier: Modifier) {
    Canvas(modifier.background(Color(0xFFF7F2FA))) {
        val pad = 10.dp.toPx()
        // At most life size (1 dp per unit); bigger components shrink to fit.
        val s = min(density, min((size.width - pad * 2) / node.w, (size.height - pad * 2) / node.h.coerceAtLeast(4f)))
        withTransform({
            translate(size.width / 2 - node.w * s / 2, size.height / 2 - node.h * s / 2)
            scale(s, s, Offset.Zero)
        }) { drawNode(node, ctx) }
    }
}

// MARK: Layers

@Composable
fun LayersPanel(state: EditorState, modifier: Modifier, onPicked: () -> Unit) {
    val index = SceneIndex(state.nodes)
    val rows = buildList {
        for (root in index.roots.asReversed()) {
            add(root to 0)
            if (root.isFrame) index.children[root.id]?.asReversed()?.forEach { add(it to 1) }
        }
    }
    if (rows.isEmpty()) {
        Text("No layers yet. Add a device from Insert → Devices.", Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(rows, key = { it.first.id }) { (node, depth) ->
            val selected = node.id == state.selectedId
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { state.select(node.id); onPicked() }
                    .padding(start = 12.dp + 20.dp * depth, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(layerIcon(node), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    node.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (node.isFrame) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (node.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                IconButton(onClick = { state.setLocked(node.id, !node.locked) }) {
                    Icon(if (node.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (node.locked) "Unlock" else "Lock",
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (node.locked) 1f else 0.4f))
                }
                IconButton(onClick = { state.setVisible(node.id, !node.visible) }) {
                    Icon(if (node.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (node.visible) "Hide" else "Show",
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (node.visible) 0.5f else 1f))
                }
            }
        }
    }
}

private fun layerIcon(node: Node): ImageVector = when (node.type) {
    NodeType.FRAME -> Icons.Rounded.Smartphone
    NodeType.RECT -> Icons.Rounded.CropSquare
    NodeType.ELLIPSE -> Icons.Rounded.Circle
    NodeType.LINE -> Icons.Rounded.Remove
    NodeType.TEXT -> Icons.Rounded.TextFields
    NodeType.IMAGE -> Icons.Rounded.Image
    NodeType.PATH -> Icons.Rounded.Draw
    NodeType.COMPONENT -> Icons.Rounded.Widgets
}
