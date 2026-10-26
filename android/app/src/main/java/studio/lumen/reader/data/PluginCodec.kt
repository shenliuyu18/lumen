package studio.lumen.reader.data

import kotlinx.serialization.json.Json

class PluginFormatException(message: String) : IllegalArgumentException(message)

object PluginCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
                (bytes[2] == 7.toByte() && bytes[3] == 8.toByte()))

    fun validId(id: String): Boolean = idPattern.matches(id)

    fun parseJson(text: String): Plugin {
        val plugin = try {
            json.decodeFromString<Plugin>(text.removePrefix("\uFEFF").trim())
        } catch (error: Exception) {
            throw PluginFormatException("plugin.json 不是有效的插件对象：${error.message}")
        }
        validate(plugin)
        return plugin
    }

    // JS 插件只允许声明式 JSON 对象，应用不会执行脚本内容。
    fun parseJs(bytes: ByteArray): Plugin {
        val text = bytes.decodeToString().removePrefix("\uFEFF").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw PluginFormatException("JS 插件需要导出 JSON 对象，例如 module.exports = {...}")
        }
        val plugin = try {
            json.decodeFromString<Plugin>(text.substring(start, end + 1))
        } catch (error: Exception) {
            throw PluginFormatException("JS 插件对象不是合法 JSON：${error.message}")
        }
        validate(plugin)
        return plugin
    }

    fun validate(plugin: Plugin) {
        if (plugin.format != 1 || plugin.id.isBlank() || plugin.name.isBlank()) {
            throw PluginFormatException("插件格式无效")
        }
        if (!validId(plugin.id)) throw PluginFormatException("插件 ID 只能包含字母、数字、点、短横线或下划线")
        if (plugin.type != "crawler" && plugin.type != "adblock") throw PluginFormatException("不支持的插件类型")
        if (plugin.type == "crawler" && plugin.sources.isEmpty()) throw PluginFormatException("爬虫插件没有来源")
        if (plugin.type == "adblock" && plugin.rules == null) throw PluginFormatException("去广告插件没有规则")
    }
}
