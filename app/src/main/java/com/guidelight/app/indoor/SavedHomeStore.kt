package com.guidelight.app.indoor

import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class SavedRoomView(val node: Int, val room: String)
data class SavedRoomObservation(val room: String, val epochSeconds: Double)

/** A separate visual-map store. The marker-based home-map.json is never modified. */
class SavedHomeStore(private val directory: File) {
    private val pointer get() = AtomicFile(File(directory, "current-map.txt"))
    fun current(): File? {
        val name = runCatching { pointer.readFully().toString(Charsets.UTF_8).trim() }.getOrNull() ?: return null
        if (!name.matches(Regex("home-[a-f0-9-]{36}\\.db"))) return null
        return File(directory, name).takeIf { it.isFile && it.length() > 1024 }
    }
    fun newRecording(): File { directory.mkdirs(); return File(directory, "draft-${UUID.randomUUID()}.db") }
    fun rooms(file: File? = current()): List<SavedRoomView> {
        if (file == null) return emptyList()
        return runCatching {
            val rooms = Json.decodeFromString<List<SavedRoomView>>(File(file.path + ".rooms.json").readText())
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { database ->
                SavedRoomReferences.rebind(rooms,readNodes(database))
            }
        }.getOrDefault(emptyList())
    }
    private fun readNodes(database: SQLiteDatabase): List<SavedViewNode> = buildList {
        database.rawQuery("SELECT id,stamp,map_id,weight,pose FROM Node",null).use { rows ->
            while (rows.moveToNext()) {
                val bytes = rows.getBlob(4) ?: continue
                if (bytes.size != 48) continue
                val pose = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                add(SavedViewNode(rows.getInt(0),rows.getDouble(1),rows.getInt(2),rows.getInt(3),pose[3],pose[7],pose[11]))
            }
        }
    }
    fun validate(file: File): Int {
        require(file.isFile && file.length() > 1024) { "No visual map was saved." }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { check ->
                require(check.moveToFirst() && check.getString(0) == "ok") { "The saved map is incomplete." }
            }
            database.rawQuery("SELECT COUNT(*) FROM Node", null).use { nodes ->
                require(nodes.moveToFirst() && nodes.getInt(0) >= 10) { "Record more of your home before saving." }
                return nodes.getInt(0)
            }
        }
    }
    fun commit(file: File, observations: List<SavedRoomObservation> = emptyList()): File {
        validate(file)
        val views = mutableListOf<SavedRoomView>()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val recorded = readNodes(database)
            observations.forEach { observation ->
                    val anchor = recorded.minByOrNull { kotlin.math.abs(it.stamp-observation.epochSeconds) }
                    require(anchor != null && kotlin.math.abs(anchor.stamp - observation.epochSeconds) <= 15) {
                        "A view for ${observation.room} could not be attached. Keep recording and name it from another angle."
                    }
                    val node = SavedRoomReferences.resolve(anchor,recorded)
                    require(node != null) { "No retained view for ${observation.room}. Stop, look around, and mark this room again before saving." }
                    require(views.none { it.node == node && it.room != observation.room }) { "Two rooms matched the same recorded view. Record more views before labeling them." }
                    views += SavedRoomView(node, observation.room)
            }
        }
        directory.mkdirs()
        val saved = File(directory, "home-${UUID.randomUUID()}.db")
        File(saved.path + ".rooms.json").writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedRoomView.serializer()), views.distinct()))
        check(file.renameTo(saved)) { "Could not keep the saved map." }
        var output: java.io.FileOutputStream? = null
        try {
            output = pointer.startWrite()
            output.write(saved.name.toByteArray(Charsets.UTF_8))
            pointer.finishWrite(output)
        } catch (error: Exception) { output?.let(pointer::failWrite); throw error }
        return saved
    }
}
