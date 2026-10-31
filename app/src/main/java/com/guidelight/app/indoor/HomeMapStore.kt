package com.guidelight.app.indoor

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object HomeMapSerializer : Serializer<HomeMap> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue = HomeMap()
    override suspend fun readFrom(input: InputStream): HomeMap = try {
        json.decodeFromString<HomeMap>(input.readBytes().decodeToString()).validate()
    } catch (error: SerializationException) {
        throw CorruptionException("Home map cannot be read; the existing file was kept.", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Home map is invalid; the existing file was kept.", error)
    }
    override suspend fun writeTo(t: HomeMap, output: OutputStream) {
        output.write(json.encodeToString(HomeMap.serializer(), t.validate()).encodeToByteArray())
    }
}

// Exactly one DataStore instance per application/file. Never silently replace a corrupt home map.
val Context.homeMapStore by dataStore(fileName = "home-map.json", serializer = HomeMapSerializer)
