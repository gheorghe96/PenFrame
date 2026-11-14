package com.gheorghe.penframe.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.Project
import com.gheorghe.penframe.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Projects are JSON files in app storage; images live next to them in `images/`. */
class ProjectRepository(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.json")

    suspend fun list(): List<Project> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString<Project>(f.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun load(id: String): Project? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<Project>(file(id).readText()) }.getOrNull()
    }

    suspend fun save(project: Project) = withContext(Dispatchers.IO) { saveBlocking(project) }

    /** Atomic write: a crash mid-save never leaves a half-written project. */
    fun saveBlocking(project: Project) {
        val tmp = File(dir, "${project.id}.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        if (!tmp.renameTo(file(project.id))) {
            file(project.id).delete()
            tmp.renameTo(file(project.id))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val project = load(id)
        file(id).delete()
        // Remove images only this project used.
        val used = project?.nodes?.mapNotNull { it.imagePath }.orEmpty().toSet()
        val stillUsed = list().flatMap { p -> p.nodes.mapNotNull { it.imagePath } }.toSet()
        (used - stillUsed).forEach { File(it).delete() }
    }

    suspend fun duplicate(id: String): Project? {
        val p = load(id) ?: return null
        val idMap = HashMap<String, String>()
        val nodes: List<Node> = p.nodes.map { n -> n.copy(id = newId().also { idMap[n.id] = it }) }
            .map { n -> n.copy(parentId = n.parentId?.let { idMap[it] }) }
        val copy = p.copy(id = newId(), name = "${p.name} copy", nodes = nodes,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        save(copy)
        return copy
    }

    /** Copies a picked image into app storage. Returns (path, width, height). */
    suspend fun importImage(context: Context, uri: Uri): Triple<String, Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(imagesDir, "${newId()}.img")
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            if (bounds.outWidth <= 0) { target.delete(); null } else Triple(target.path, bounds.outWidth, bounds.outHeight)
        }.getOrNull()
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
