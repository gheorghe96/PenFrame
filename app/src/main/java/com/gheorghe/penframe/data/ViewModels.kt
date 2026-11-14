package com.gheorghe.penframe.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gheorghe.penframe.editor.EditorState
import com.gheorghe.penframe.model.DevicePreset
import com.gheorghe.penframe.model.Project
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ProjectRepository(app)
    var projects by mutableStateOf<List<Project>?>(null)
        private set

    fun refresh() = viewModelScope.launch { projects = repo.list() }

    /** Creates a project with one frame per chosen device. Returns its id through [onCreated]. */
    fun create(name: String, devices: List<DevicePreset>, onCreated: (String) -> Unit) = viewModelScope.launch {
        val state = EditorState(Project(name = name.ifBlank { "Untitled" }))
        devices.forEach { state.addDevice(it) }
        state.select(null)
        val project = state.toProject()
        repo.save(project)
        refresh()
        onCreated(project.id)
    }

    fun rename(project: Project, name: String) = viewModelScope.launch {
        repo.save(project.copy(name = name, updatedAt = System.currentTimeMillis()))
        refresh()
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id); refresh() }
    fun duplicate(id: String) = viewModelScope.launch { repo.duplicate(id); refresh() }
}

class EditorViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    val repo = ProjectRepository(app)
    var state by mutableStateOf<EditorState?>(null)
        private set
    var missing by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val project = repo.load(projectId)
            if (project == null) { missing = true; return@launch }
            val s = EditorState(project)
            state = s
            autosave(s)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave(s: EditorState) {
        snapshotFlow { Triple(s.nodes, s.name, Triple(s.offset, s.zoom, 0)) }
            .drop(1)
            .debounce(600)
            .collect { repo.save(s.toProject()) }
    }

    fun saveNow() {
        state?.let { repo.saveBlocking(it.toProject()) }
    }

    override fun onCleared() {
        saveNow()
        super.onCleared()
    }
}
