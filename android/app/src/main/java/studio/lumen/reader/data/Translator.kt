package studio.lumen.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class Translator(private val secrets: SecretStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun cacheKey(provider: String, text: String, context: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$provider\u0000$text\u0000$context".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun translate(text: String, context: String, settings: TranslationSettings): Translation =
        withContext(Dispatchers.IO) {
            when (settings.provider) {
                "microsoft-web" -> microsoftWeb(text)
                "microsoft" -> microsoft(text, settings, secrets.get("microsoft"))
                "deepl" -> deepl(text, context, settings, secrets.get("deepl"))
                "ai" -> aiModel(text, context, settings, secrets.get("ai"))
                "mymemory" -> myMemory(text)
                else -> error("请选择翻译服务")
            }
        }

    /** OpenAI 兼容的大模型翻译：地址、模型名与密钥都由用户配置。 */
    private fun aiModel(text: String, context: String, settings: TranslationSettings, key: String?): Translation {
        if (key.isNullOrBlank()) error("请在设置中填写大模型服务的 API 密钥")
        val base = settings.aiBaseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val modelName = settings.aiModel.ifBlank { "gpt-4o-mini" }
        val system = "你是严谨的英译中译者。只输出中文译文，不要解释、不要加引号或列表符号；保留专有名词、数字与代码原样。"
        val user = if (context.isBlank()) {
            text
        } else {
            "上下文（仅供参考，不要翻译它）：\n$context\n\n需要翻译的段落：\n$text"
        }
        val payload = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val request = Request.Builder()
            .url("$base/chat/completions")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${key.trim()}")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: error("空响应")
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                error(message ?: httpError(response.code))
            }
            val content = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            if (content.isNullOrBlank()) error("大模型服务未返回译文")
            return Translation(content.trim(), "大模型翻译", System.currentTimeMillis())
        }
    }

    private fun microsoftWeb(text: String): Translation {
        val request = Request.Builder()
            .url(MicrosoftWeb.Endpoint)
            .post(MicrosoftWeb.requestBody(text).toRequestBody("application/json".toMediaType()))
            .header("User-Agent", MicrosoftWeb.UserAgent)
            .header("Origin", "https://www.microsoft.com")
            .header("Referer", "https://www.microsoft.com/zh-cn/translator/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(httpError(response.code))
            return Translation(
                MicrosoftWeb.parse(response.body?.string() ?: error("空响应")),
                "Microsoft Translator 官方网页",
                System.currentTimeMillis()
            )
        }
    }

    private fun microsoft(text: String, settings: TranslationSettings, key: String?): Translation {
        if (key.isNullOrBlank()) error("请在设置中填写 Microsoft Translator API 密钥")
        val body = buildJsonArray { add(buildJsonObject { put("Text", text) }) }.toString()
        val builder = Request.Builder()
            .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=zh-Hans")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Ocp-Apim-Subscription-Key", key)
        if (settings.region.isNotBlank()) builder.header("Ocp-Apim-Subscription-Region", settings.region)
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error(httpError(response.code))
            val root = json.parseToJsonElement(response.body?.string() ?: error("空响应")).jsonArray
            val translated = root.firstOrNull()?.jsonObject?.get("translations")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: error("翻译服务未返回有效译文")
            return Translation(translated, "Microsoft Translator", System.currentTimeMillis())
        }
    }

    private fun deepl(text: String, context: String, settings: TranslationSettings, key: String?): Translation {
        if (key.isNullOrBlank()) error("请在设置中填写 DeepL API 密钥")
        val host = if (settings.deeplPlan == "pro") "api.deepl.com" else "api-free.deepl.com"
        val body = buildJsonObject {
            put("text", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(text)) })
            put("source_lang", "EN")
            put("target_lang", "ZH-HANS")
            put("preserve_formatting", true)
            if (settings.useContext && context.isNotBlank()) put("context", context)
        }.toString()
        val request = Request.Builder()
            .url("https://$host/v2/translate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "DeepL-Auth-Key $key")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(httpError(response.code))
            val root = json.parseToJsonElement(response.body?.string() ?: error("空响应")).jsonObject
            val translated = root["translations"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")
                ?.jsonPrimitive?.content ?: error("翻译服务未返回有效译文")
            return Translation(translated, "DeepL", System.currentTimeMillis())
        }
    }

    private fun myMemory(text: String): Translation {
        val chunks = splitForMyMemory(text)
        val translated = chunks.map { chunk ->
            val url = "https://api.mymemory.translated.net/get?q=${URLEncoder.encode(chunk, "UTF-8")}&langpair=en%7Czh-CN"
            val request = Request.Builder().url(url).header("User-Agent", "LumenReader/1.0 (Android)").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(httpError(response.code))
                val root = json.parseToJsonElement(response.body?.string() ?: error("空响应")).jsonObject
                val status = root["responseStatus"]?.jsonPrimitive?.content?.toIntOrNull() ?: 200
                if (status != 200 || root["quotaFinished"]?.jsonPrimitive?.content == "true") {
                    error(root["responseDetails"]?.jsonPrimitive?.content ?: "备用翻译服务达到配额")
                }
                val value = root["responseData"]?.jsonObject?.get("translatedText")?.jsonPrimitive?.content
                    ?: error("翻译服务未返回有效译文")
                org.jsoup.Jsoup.parse(value).text()
            }
        }
        return Translation(translated.joinToString(" "), "MyMemory · 备用", System.currentTimeMillis())
    }

    private fun splitForMyMemory(text: String): List<String> {
        if (text.toByteArray().size <= 450) return listOf(text)
        val chunks = mutableListOf<String>()
        var rest = text
        while (rest.toByteArray().size > 450) {
            var cut = rest.length
            while (cut > 1 && rest.substring(0, cut).toByteArray().size > 450) cut--
            val candidate = rest.substring(0, cut)
            val boundary = maxOf(candidate.lastIndexOf(". "), candidate.lastIndexOf("? "), candidate.lastIndexOf("! "), candidate.lastIndexOf(' '))
            if (boundary > 80) cut = boundary + 1
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) chunks += rest
        return chunks
    }

    suspend fun dictionary(word: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(word, "UTF-8")}")
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val root = json.parseToJsonElement(response.body?.string() ?: "[]").jsonArray
                val result = mutableListOf<String>()
                for (entry in root) {
                    val meanings = entry.jsonObject["meanings"]?.jsonArray ?: continue
                    for (meaning in meanings.take(3)) {
                        val part = meaning.jsonObject["partOfSpeech"]?.jsonPrimitive?.content ?: ""
                        val definitions = meaning.jsonObject["definitions"]?.jsonArray ?: continue
                        definitions.take(2).forEach {
                            val definition = it.jsonObject["definition"]?.jsonPrimitive?.content ?: ""
                            if (definition.isNotBlank()) result += "($part) $definition"
                        }
                    }
                }
                result
            }
        }.getOrDefault(emptyList())
    }

    private fun httpError(code: Int): String = when (code) {
        401 -> "密钥无效或认证失败"
        403 -> "无权访问，请检查 API 计划与密钥"
        429 -> "请求过于频繁或达到配额"
        456 -> "DeepL 字符配额已用尽"
        else -> "翻译服务 HTTP $code"
    }
}

// Edge 浏览器内置翻译使用的公开接口：无需密钥，但要求浏览器风格的请求头。
object MicrosoftWeb {
    const val Endpoint = "https://edge.microsoft.com/translate/translatetext?api-version=3.0&from=en&to=zh-Hans"
    const val UserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"

    private val json = Json { ignoreUnknownKeys = true }

    fun requestBody(text: String): String =
        buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(text)) }.toString()

    fun parse(payload: String): String {
        val root = runCatching { json.parseToJsonElement(payload).jsonArray }.getOrNull()
            ?: error("微软网页翻译返回了无法解析的内容")
        val text = root.firstOrNull()?.jsonObject?.get("translations")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
        if (text.isNullOrBlank()) error("微软网页翻译未返回有效译文")
        return text
    }
}
