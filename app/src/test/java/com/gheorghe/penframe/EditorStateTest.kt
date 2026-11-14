package com.gheorghe.penframe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.gheorghe.penframe.data.ProjectRepository
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.editor.Tool
import com.gheorghe.penframe.model.ComponentKind
import com.gheorghe.penframe.model.Devices
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.NodeType
import com.gheorghe.penframe.model.Project
import com.gheorghe.penframe.model.parseHex
import com.gheorghe.penframe.model.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {
    private fun editorWithPhone(): EditorState {
        val s = EditorState(Project(name = "Test"))
        s.viewSize = Size(1000f, 2000f)
        s.addDevice(Devices.byId("pixel-9")!!)
        return s
    }

    private val EditorState.frame get() = nodes.first { it.isFrame }

    @Test fun addingADeviceCreatesAFrameWithSystemBars() {
        val s = editorWithPhone()
        val frame = s.frame
        assertEquals(412f, frame.w)
        assertEquals(923f, frame.h)
        val bars = s.nodes.filter { it.parentId == frame.id }.mapNotNull { it.component }
        assertEquals(listOf(ComponentKind.STATUS_BAR.name, ComponentKind.SYSTEM_NAV.name), bars)
        assertEquals(frame.id, s.selectedId)
    }

    @Test fun secondDeviceGoesToTheRight() {
        val s = editorWithPhone()
        s.addDevice(Devices.byId("galaxy-s22-ultra")!!)
        val frames = s.nodes.filter { it.isFrame }
        assertEquals(2, frames.size)
        assertTrue(frames[1].x >= frames[0].x + frames[0].w)
    }

    @Test fun componentsLandInsideTheTargetFrame() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.BUTTON_FILLED)
        val button = s.selected!!
        assertEquals(s.frame.id, button.parentId)
        assertTrue(s.frame.rect.contains(button.center))
        s.select(s.frame.id)
        s.addComponent(ComponentKind.NAVIGATION_BAR)
        val nav = s.selected!!
        assertEquals(s.frame.w, nav.w)
        assertEquals(s.frame.x, nav.x)
    }

    @Test fun hitTestingPrefersChildrenOverFrames() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.FAB)
        val fab = s.selected!!
        assertEquals(fab.id, s.nodeAt(fab.center)?.id)
        assertEquals(s.frame.id, s.nodeAt(Offset(s.frame.x + 5f, s.frame.y + 400f))?.id)
        assertNull(s.nodeAt(Offset(-500f, -500f)))
    }

    @Test fun rotatedNodesHitTestInTheirOwnAxes() {
        val n = Node(type = NodeType.RECT, name = "r", x = 0f, y = 0f, w = 200f, h = 20f, rotation = 90f)
        assertTrue(n.contains(Offset(100f, 90f)))   // rotated: now tall around the centre
        assertFalse(n.contains(Offset(10f, 10f)))
    }

    @Test fun movingSnapsToTheFrameCentre() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.BUTTON_FILLED)
        val b = s.selected!!
        val frame = s.frame
        s.beginMove(b.id)
        // Aim just off the frame's horizontal centre (snapping reaches 6 screen points).
        val target = frame.center.x - b.w / 2 + 2f / s.zoom - b.x
        s.moveTo(b.id, Offset(target, 0f))
        val moved = s.selected!!
        assertEquals(frame.center.x, moved.center.x, 0.01f)
        assertTrue(s.guides.any { it.vertical })
        s.endMove(b.id)
        assertTrue(s.guides.isEmpty())
    }

    @Test fun movingAFrameMovesItsChildren() {
        val s = editorWithPhone()
        val frame = s.frame
        val bar = s.nodes.first { it.parentId == frame.id }
        s.snapping = false
        s.beginMove(frame.id)
        s.moveTo(frame.id, Offset(100f, 50f))
        s.endMove(frame.id)
        assertEquals(bar.x + 100f, s.nodes.first { it.id == bar.id }.x, 0.01f)
        assertEquals(bar.y + 50f, s.nodes.first { it.id == bar.id }.y, 0.01f)
    }

    @Test fun undoAndRedoRestoreLayers() {
        val s = editorWithPhone()
        val before = s.nodes
        s.addComponent(ComponentKind.SWITCH)
        assertEquals(before.size + 1, s.nodes.size)
        s.undo()
        assertEquals(before, s.nodes)
        s.redo()
        assertEquals(before.size + 1, s.nodes.size)
    }

    @Test fun typingCoalescesIntoOneUndoStep() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.BUTTON_FILLED)
        val id = s.selectedId!!
        listOf("S", "Sa", "Sav", "Save").forEach { t -> s.update(id, "label") { it.copy(props = it.props + ("label" to t)) } }
        s.undo()
        assertEquals("Button", s.nodes.first { it.id == id }.prop("label"))
    }

    @Test fun deletingAFrameDeletesItsContent() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.CARD_ELEVATED)
        s.select(s.frame.id)
        s.deleteSelected()
        assertTrue(s.nodes.isEmpty())
    }

    @Test fun duplicatingAFrameCopiesItsContent() {
        val s = editorWithPhone()
        val count = s.nodes.size
        s.select(s.frame.id)
        s.duplicateSelected()
        assertEquals(count * 2, s.nodes.size)
        val copy = s.selected!!
        assertEquals(count - 1, s.nodes.count { it.parentId == copy.id })
    }

    @Test fun reorderMovesWithinSiblings() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.FAB)
        val fab = s.selectedId!!
        s.reorder(toFront = false, allTheWay = true)
        val siblings = s.nodes.filter { it.parentId == s.frame.id }
        assertEquals(fab, siblings.first().id)
    }

    @Test fun changingDeviceKeepsBarsAtTheEdges() {
        val s = editorWithPhone()
        s.changeDevice(s.frame.id, Devices.byId("galaxy-s22-ultra")!!)
        val frame = s.frame
        assertEquals(384f, frame.w)
        val nav = s.nodes.first { it.component == ComponentKind.SYSTEM_NAV.name }
        assertEquals(frame.y + frame.h, nav.y + nav.h, 0.01f)
        assertEquals(frame.w, nav.w)
    }

    @Test fun drawingARectangleByDrag() {
        val s = editorWithPhone()
        s.tool = Tool.RECT
        s.startDraft(Tool.RECT, Offset(10f, 20f))
        s.updateDraft(Offset(110f, 70f))
        s.finishDraft(tapped = false)
        val r = s.selected!!
        assertEquals(NodeType.RECT, r.type)
        assertEquals(100f, r.w)
        assertEquals(50f, r.h)
        assertEquals(s.frame.id, r.parentId)
        assertEquals(Tool.SELECT, s.tool)
    }

    @Test fun projectsSurviveSerialization() {
        val s = editorWithPhone()
        s.addComponent(ComponentKind.TEXT_FIELD_OUTLINED)
        val project = s.toProject()
        val json = ProjectRepository.json.encodeToString(Project.serializer(), project)
        val back = ProjectRepository.json.decodeFromString(Project.serializer(), json)
        assertEquals(project, back)
    }

    @Test fun hexColors() {
        assertEquals(0xFF6750A4, parseHex("#6750A4"))
        assertEquals(0x80FFFFFF, parseHex("80FFFFFF"))
        assertEquals(0xFFFFAA00, parseHex("#fa0"))
        assertEquals("#6750A4", 0xFF6750A4.toHex())
        assertNull(parseHex("nope"))
    }

    @Test fun catalogIsConsistent() {
        assertEquals(Devices.all.size, Devices.all.map { it.id }.toSet().size)
        for (kind in ComponentKind.entries) {
            for (spec in kind.specs) assertNotNull("${kind.name}.${spec.key}", kind.defaults[spec.key])
            assertTrue(kind.width > 0 && kind.height > 0)
        }
        assertTrue(ComponentKind.entries.size >= 60)
    }
}
