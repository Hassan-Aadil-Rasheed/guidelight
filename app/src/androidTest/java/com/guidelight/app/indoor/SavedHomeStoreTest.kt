package com.guidelight.app.indoor

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavedHomeStoreTest {
    @Test fun mapAndRoomReferencesSurviveRecreationAndFailedSavesKeepThePreviousMap() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "map-test-${UUID.randomUUID()}")
        try {
            val store = SavedHomeStore(directory)
            val draft = store.newRecording()
            SQLiteDatabase.openOrCreateDatabase(draft, null).use { db ->
                db.execSQL("CREATE TABLE Node(id INTEGER PRIMARY KEY, stamp REAL, map_id INTEGER, weight INTEGER, pose BLOB)")
                val pose=java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                    floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f).forEach { putFloat(it) }
                }.array()
                repeat(12) { db.execSQL("INSERT INTO Node VALUES (?, ?, 0, 0, ?)", arrayOf(it + 1, 1000.0 + it,pose)) }
            }
            val saved = store.commit(draft, listOf(SavedRoomObservation("kitchen", 1000.0), SavedRoomObservation("hall", 1005.0), SavedRoomObservation("bedroom", 1010.0)))
            val reopened = SavedHomeStore(directory)
            assertThat(reopened.current()).isEqualTo(saved)
            assertThat(reopened.rooms()).containsExactly(SavedRoomView(1,"kitchen"), SavedRoomView(6,"hall"), SavedRoomView(11,"bedroom"))
            val bad = store.newRecording().apply { writeText("not a map") }
            assertThat(runCatching { store.commit(bad) }.isFailure).isTrue()
            assertThat(reopened.current()).isEqualTo(saved)
        } finally {
            check(directory.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            directory.deleteRecursively()
        }
    }
}
