package studio.lumen.reader.data

import android.content.Context
import kotlinx.serialization.json.Json

class Catalog(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun snapshot(): SnapshotSeed? = runCatching {
        context.assets.open("snapshot.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<SnapshotSeed>(it) }
    }.getOrNull()

    fun newsroom(): Newsroom? = runCatching {
        context.assets.open("newsroom-snapshot.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<Newsroom>(it) }
    }.getOrNull()

    fun readabilityScript(): String = runCatching {
        context.assets.open("readability.js").bufferedReader().use { it.readText() }
    }.getOrDefault("")

    fun extractionScript(): String = runCatching {
        context.assets.open("extract-android.js").bufferedReader().use { it.readText() }
    }.getOrDefault("")
}
