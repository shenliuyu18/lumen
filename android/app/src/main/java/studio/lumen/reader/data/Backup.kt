package studio.lumen.reader.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class ImportedBackup(
    val vocabulary: List<Vocab>,
    val saved: List<SavedItem>,
    val history: List<HistoryItem>
)

object BackupManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(store: Store): String {
        val saved = buildJsonArray {
            store.saved.forEach { item ->
                add(flatten(item.article, "savedAt" to item.savedAt))
            }
        }
        val history = buildJsonArray {
            store.history.forEach { item ->
                add(flatten(item.article, "readAt" to item.readAt))
            }
        }
        val data = buildJsonObject {
            put("settings", json.encodeToJsonElement(Settings.serializer(), store.settings))
            put("vocabulary", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Vocab.serializer()), store.vocabulary))
            put("saved", saved)
            put("history", history)
            put("readingSeconds", store.readingSeconds)
            put("version", store.version)
        }
        val root = buildJsonObject {
            put("format", "lumen-backup")
            put("version", 1)
            put("data", data)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun import(text: String): ImportedBackup {
        val root = json.parseToJsonElement(text).jsonObject
        val format = root["format"]?.jsonPrimitive?.content
        val version = root["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        if (format != "lumen-backup" || version != 1) error("不是有效的 Lumen 备份")
        val data = root["data"]?.jsonObject ?: error("备份缺少 data")
        val vocabulary = data["vocabulary"]?.jsonArray?.mapNotNull {
            runCatching { json.decodeFromJsonElement(Vocab.serializer(), it) }.getOrNull()
        } ?: error("备份缺少词句本")
        val saved = data["saved"]?.jsonArray?.mapNotNull {
            runCatching {
                val obj = it.jsonObject
                SavedItem(json.decodeFromJsonElement(Article.serializer(), obj), obj["savedAt"]?.jsonPrimitive?.longOrNull ?: 0)
            }.getOrNull()
        } ?: error("备份缺少收藏")
        val history = data["history"]?.jsonArray?.mapNotNull {
            runCatching {
                val obj = it.jsonObject
                HistoryItem(json.decodeFromJsonElement(Article.serializer(), obj), obj["readAt"]?.jsonPrimitive?.longOrNull ?: 0)
            }.getOrNull()
        } ?: error("备份缺少足迹")
        return ImportedBackup(vocabulary, saved, history)
    }

    fun vocabularyCsv(vocabulary: List<Vocab>): String {
        val header = listOf("英文", "释义", "原文语境", "来源", "下次复习").joinToString(",")
        val rows = vocabulary.map { card ->
            listOf(
                Ranker.csvCell(card.text),
                Ranker.csvCell(card.meaning),
                Ranker.csvCell(card.context),
                Ranker.csvCell(card.url),
                Ranker.csvCell(java.time.Instant.ofEpochMilli(card.dueAt).toString())
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\r\n") + "\r\n"
    }

    private fun flatten(article: Article, extra: Pair<String, Long>): JsonObject {
        val base = json.encodeToJsonElement(Article.serializer(), article).jsonObject.toMutableMap()
        base[extra.first] = kotlinx.serialization.json.JsonPrimitive(extra.second)
        return JsonObject(base)
    }
}
