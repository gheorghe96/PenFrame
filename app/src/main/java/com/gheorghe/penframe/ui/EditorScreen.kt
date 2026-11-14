package com.gheorghe.penframe.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.gheorghe.penframe.data.EditorViewModel
import com.gheorghe.penframe.editor.EditorCanvas
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.editor.Tool
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.render.Exporter
import com.gheorghe.penframe.render.RenderContext
import kotlinx.coroutines.launch

enum class Panel(val title: String, val icon: ImageVector) {
    DESIGN("Design", Icons.Rounded.Tune), LAYERS("Layers", Icons.Rounded.Layers), INSERT("Insert", Icons.Rounded.Widgets)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(projectId: String, onClose: () -> Unit) {
    val vm: EditorViewModel = viewModel(
        key = projectId,
        factory = viewModelFactory { initializer { EditorViewModel(this[APPLICATION_KEY]!!, projectId) } },
    )
    val state = vm.state
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveNow() }
    BackHandler { vm.saveNow(); onClose() }
    if (vm.missing) { LaunchedEffect(Unit) { onClose() }; return }
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    EditorContent(state, vm, onClose = { vm.saveNow(); onClose() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(state: EditorState, vm: EditorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val exportCtx = remember(measurer) { RenderContext(measurer) }
    var sheet by remember { mutableStateOf<Panel?>(null) }
    var widePanel by remember { mutableStateOf(Panel.DESIGN) }
    var presenting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            vm.repo.importImage(context, uri)?.let { (path, w, h) -> state.addImage(path, w, h) }
        }
    }
    val launchPicker = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    fun export(all: Boolean) {
        val frames = if (all) state.nodes.filter { it.isFrame && it.visible }
        else listOfNotNull(state.targetFrame())
        if (frames.isEmpty()) return
        val files = frames.map { Exporter.exportFrame(context, state.nodes, it, exportCtx, scale = 2f) }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.type = "image/png"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Export design"))
    }

    // New projects open framed on their screens.
    LaunchedEffect(state.viewSize) {
        if (state.viewSize.width > 0 && state.needsFit) {
            state.needsFit = false
            state.zoomToFit()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(state, wide, onBack = onClose, onRename = { renaming = true },
                onExport = ::export, onPresent = { presenting = true })
            Row(Modifier.weight(1f)) {
                if (wide) {
                    ToolRail(state, onImage = launchPicker)
                    VerticalDivider()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    EditorCanvas(state)
                }
                if (wide) {
                    VerticalDivider()
                    Column(Modifier.width(340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        PrimaryTabRow(selectedTabIndex = widePanel.ordinal) {
                            Panel.entries.forEach { p ->
                                Tab(selected = widePanel == p, onClick = { widePanel = p }, text = { Text(p.title) })
                            }
                        }
                        PanelContent(widePanel, state, launchPicker, onDone = {}, Modifier.weight(1f))
                    }
                }
            }
            if (!wide) BottomBar(state, onImage = launchPicker, onPanel = { sheet = it })
        }
    }

    sheet?.let { panel ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            PanelContent(panel, state, launchPicker, onDone = { sheet = null }, Modifier.fillMaxWidth().height(520.dp))
        }
    }
    if (renaming) NameDialog("Rename project", state.name, onDismiss = { renaming = false }) { state.name = it; renaming = false }
    state.editingTextId?.let { id -> TextEditDialog(state, id) }
    if (presenting) PresentDialog(state, onDismiss = { presenting = false })
}

@Composable
private fun PanelContent(panel: Panel, state: EditorState, onImage: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    when (panel) {
        Panel.DESIGN -> InspectorPanel(state, onImage, modifier)
        Panel.LAYERS -> LayersPanel(state, modifier, onPicked = onDone)
        Panel.INSERT -> InsertPanel(state, modifier, onInserted = onDone)
    }
}

@Composable
private fun TopBar(
    state: EditorState, wide: Boolean, onBack: () -> Unit, onRename: () -> Unit,
    onExport: (Boolean) -> Unit, onPresent: () -> Unit,
) {
    var zoomMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            // The name takes whatever room the actions leave, so it stays readable on phones.
            TextButton(onClick = onRename, modifier = Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
            }
            IconButton(onClick = state::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            IconButton(onClick = state::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            Box {
                TextButton(onClick = { zoomMenu = true }) { Text("${(state.zoom * 100).toInt()}%") }
                DropdownMenu(zoomMenu, onDismissRequest = { zoomMenu = false }) {
                    DropdownMenuItem(text = { Text("Zoom to fit") }, onClick = { zoomMenu = false; state.zoomToFit() })
                    DropdownMenuItem(text = { Text("Zoom to selection") }, enabled = state.selected != null,
                        onClick = { zoomMenu = false; state.zoomToFit(state.selected) })
                    listOf(0.25f, 0.5f, 1f, 2f, 4f).forEach { z ->
                        DropdownMenuItem(text = { Text("${(z * 100).toInt()}%") }, onClick = { zoomMenu = false; state.zoomTo(z) })
                    }
                }
            }
            if (wide) IconButton(onClick = onPresent) { Icon(Icons.Rounded.PlayArrow, "Preview") }
            IconButton(onClick = { onExport(false) }) { Icon(Icons.Rounded.IosShare, "Export screen") }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(more, onDismissRequest = { more = false }) {
                    if (!wide) DropdownMenuItem(text = { Text("Preview screens") }, onClick = { more = false; onPresent() })
                    DropdownMenuItem(text = { Text("Export all screens") }, onClick = { more = false; onExport(true) })
                    HorizontalDivider()
                    CheckItem("Snap to edges & centers", state.snapping) { state.snapping = it }
                    CheckItem("Show device bezels", state.showBezels) { state.showBezels = it }
                    CheckItem("Fingers pan when using S Pen", state.fingersPanWithPen) { state.fingersPanWithPen = it }
                }
            }
        }
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { androidx.compose.material3.Checkbox(checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

private data class ToolButton(val tool: Tool?, val icon: ImageVector, val label: String)

private val toolButtons = listOf(
    ToolButton(Tool.SELECT, Icons.Rounded.NearMe, "Select"),
    ToolButton(Tool.HAND, Icons.Rounded.PanTool, "Hand"),
    ToolButton(Tool.RECT, Icons.Rounded.CropSquare, "Rectangle"),
    ToolButton(Tool.ELLIPSE, Icons.Rounded.RadioButtonUnchecked, "Ellipse"),
    ToolButton(Tool.LINE, Icons.Rounded.Remove, "Line"),
    ToolButton(Tool.TEXT, Icons.Rounded.TextFields, "Text"),
    ToolButton(Tool.PEN, Icons.Rounded.Draw, "Pen"),
)

@Composable
private fun ToolRail(state: EditorState, onImage: () -> Unit) {
    Column(
        Modifier.width(64.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        toolButtons.forEach { b ->
            FilledIconToggleButton(checked = state.tool == b.tool, onCheckedChange = { state.tool = b.tool!! }) {
                Icon(b.icon, b.label)
            }
        }
        HorizontalDivider(Modifier.padding(8.dp))
        IconButton(onClick = onImage) { Icon(Icons.Rounded.Image, "Image") }
        IconButton(onClick = { state.zoomToFit() }) { Icon(Icons.Rounded.Smartphone, "Fit screens") }
    }
}

@Composable
private fun BottomBar(state: EditorState, onImage: () -> Unit, onPanel: (Panel) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolButtons.forEach { b ->
                    FilledIconToggleButton(checked = state.tool == b.tool, onCheckedChange = { state.tool = b.tool!! }) {
                        Icon(b.icon, b.label)
                    }
                }
                IconButton(onClick = onImage) { Icon(Icons.Rounded.Image, "Image") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onPanel(Panel.INSERT) }) { Icon(Icons.Rounded.AddBox, null); Spacer(Modifier.width(6.dp)); Text("Insert") }
                TextButton(onClick = { onPanel(Panel.LAYERS) }) { Icon(Icons.Rounded.Layers, null); Spacer(Modifier.width(6.dp)); Text("Layers") }
                TextButton(onClick = { onPanel(Panel.DESIGN) }, enabled = true) {
                    Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(6.dp))
                    Text(if (state.selected != null) "Design" else "Settings")
                }
            }
        }
    }
}

@Composable
private fun TextEditDialog(state: EditorState, id: String) {
    val node = state.nodes.firstOrNull { it.id == id }
    if (node == null || node.type != NodeType.TEXT) { state.editingTextId = null; return }
    // New text starts as "Text" with everything selected, so typing replaces it.
    var value by remember(id) { mutableStateOf(TextFieldValue(node.text, TextRange(0, node.text.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(id) { focus.requestFocus() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { state.editingTextId = null },
        title = { Text("Edit text") },
        text = {
            androidx.compose.material3.OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 2)
        },
        confirmButton = {
            TextButton(onClick = {
                val text = value.text
                state.update(id) { it.copy(text = text, name = text.lineSequence().first().take(32).ifBlank { "Text" }) }
                state.editingTextId = null
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = { state.editingTextId = null }) { Text("Cancel") } },
    )
}

