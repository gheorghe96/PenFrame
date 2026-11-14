package com.gheorghe.penframe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gheorghe.penframe.data.ProjectsViewModel
import com.gheorghe.penframe.model.DeviceCategory
import com.gheorghe.penframe.model.Devices
import com.gheorghe.penframe.model.Project
import com.gheorghe.penframe.model.union
import com.gheorghe.penframe.render.RenderContext
import com.gheorghe.penframe.render.SceneIndex
import com.gheorghe.penframe.render.drawScene
import java.text.DateFormat
import java.util.Date
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(onOpen: (String) -> Unit, vm: ProjectsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("PenFrame") },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New project") },
            )
        },
    ) { padding ->
        val projects = vm.projects
        when {
            projects == null -> {}
            projects.isEmpty() -> EmptyState(Modifier.padding(padding)) { creating = true }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpen(project.id) },
                        onRename = { renaming = project },
                        onDuplicate = { vm.duplicate(project.id) },
                        onDelete = { deleting = project },
                    )
                }
            }
        }
    }

    if (creating) NewProjectDialog(onDismiss = { creating = false }) { name, devices ->
        creating = false
        vm.create(name, devices, onOpen)
    }
    renaming?.let { p ->
        NameDialog("Rename project", p.name, onDismiss = { renaming = null }) { vm.rename(p, it); renaming = null }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("The project and its imported images are removed from this device. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.delete(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Draw, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Design your next app", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Place real device screens on a canvas, drop in buttons, bars, lists and more, and shape every detail with your S Pen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCreate) { Text("Create a project") }
    }
}

@Composable
private fun ProjectCard(project: Project, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        ProjectThumbnail(project, Modifier.fillMaxWidth().aspectRatio(1.4f))
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                val frames = project.nodes.count { it.isFrame }
                Text(
                    "$frames screen${if (frames == 1) "" else "s"} · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.updatedAt)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** The whole design scaled to fit, drawn by the same renderer as the editor. */
@Composable
fun ProjectThumbnail(project: Project, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val ctx = remember(measurer) { RenderContext(measurer) }
    val index = remember(project.nodes) { SceneIndex(project.nodes) }
    Canvas(modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color(0xFFEDEBF0))) {
        val bounds = index.roots.filter { it.visible }.map { it.rect }.reduceOrNull { a, b -> a.union(b) } ?: return@Canvas
        val pad = 16.dp.toPx()
        val s = min((size.width - pad * 2) / bounds.width, (size.height - pad * 2) / bounds.height)
        withTransform({
            translate(size.width / 2 - bounds.center.x * s, size.height / 2 - bounds.center.y * s)
            scale(s, s, Offset.Zero)
        }) {
            drawScene(index, ctx)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, List<com.gheorghe.penframe.model.DevicePreset>) -> Unit) {
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf(Devices.byId("pixel-9")!!) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Start with these screens", style = MaterialTheme.typography.labelLarge)
                for ((category, presets) in Devices.grouped) {
                    if (category == DeviceCategory.LAYOUT) continue
                    Text(category.title, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            val on = chosen.contains(preset)
                            FilterChip(
                                selected = on,
                                onClick = { if (on) chosen.remove(preset) else chosen.add(preset) },
                                label = { Text(preset.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, chosen.toList()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(value.trim().ifEmpty { initial }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
