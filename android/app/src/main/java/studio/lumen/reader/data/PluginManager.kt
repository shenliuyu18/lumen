package studio.lumen.reader.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context, dataDir: File) {
    val directory = File(dataDir, "plugins").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var installed: List<Plugin> = loadInstalled()
    private val engine: String by lazy {
        context.assets.open("ads-engine.js").bufferedReader().use { it.readText() }
    }

    private fun loadInstalled(): List<Plugin> = runCatching {
        directory.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".install-") }?.mapNotNull { dir ->
            runCatching {
                PluginCodec.parseJson(File(dir, "plugin.json").readText())
            }.getOrNull()
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun all(): List<Plugin> = installed.sortedBy { it.name.lowercase() }
    private fun enabled(plugin: Plugin, disabled: Set<String>) = plugin.id !in disabled

    fun infos(disabledPlugins: List<String>): List<PluginInfo> {
        val disabled = disabledPlugins.toSet()
        return all().map { plugin ->
            PluginInfo(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                type = plugin.type,
                description = plugin.description,
                author = plugin.author,
                license = plugin.license,
                enabled = enabled(plugin, disabled),
                sourceCount = plugin.sources.size,
                ruleCount = ruleCount(plugin.rules)
            )
        }
    }

    fun sources(disabledPlugins: List<String>): List<Source> {
        val disabled = disabledPlugins.toSet()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Source>()
        all().filter { it.type == "crawler" && enabled(it, disabled) }.forEach { plugin ->
            plugin.sources.forEach { source ->
                if (source.id.isNotBlank() && seen.add(source.id)) result += source
            }
        }
        return result
    }

    fun newsroomSpec(disabledPlugins: List<String>): NewsroomSpec? {
        val disabled = disabledPlugins.toSet()
        return all().firstOrNull { it.type == "crawler" && enabled(it, disabled) }?.newsroom
    }

    /** 已启用去广告插件的域名屏蔽表，供 WebView 请求拦截使用。 */
    fun blockHosts(disabledPlugins: List<String>): Set<String> {
        val disabled = disabledPlugins.toSet()
        return all()
            .filter { it.type == "adblock" && enabled(it, disabled) && it.rules != null }
            .flatMap { plugin -> plugin.rules!!.global.blockHosts }
            .map { it.trim().lowercase().removePrefix("www.") }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun adScript(disabledPlugins: List<String>): String {
        val disabled = disabledPlugins.toSet()
        var found = false
        val selectors = mutableListOf<String>()
        val patterns = mutableListOf<String>()
        val domains = mutableMapOf<String, AdRuleSet>()
        all().filter { it.type == "adblock" && enabled(it, disabled) && it.rules != null }.forEach { plugin ->
            found = true
            val rules = plugin.rules!!
            selectors += rules.global.selectors
            patterns += rules.global.textPatterns
            rules.domains.forEach { (domain, set) ->
                val current = domains[domain] ?: AdRuleSet()
                domains[domain] = AdRuleSet(
                    (current.selectors + set.selectors).distinct(),
                    (current.textPatterns + set.textPatterns).distinct()
                )
            }
        }
        if (!found) return ""
        val merged = AdRules(AdRuleSet(selectors.distinct(), patterns.distinct()), domains)
        return "window.__LUMEN_AD_RULES__ = ${json.encodeToString(AdRules.serializer(), merged)};\n$engine"
    }

    fun install(uri: Uri): PluginInfo {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取插件包")
        if (bytes.isEmpty()) error("插件包为空")
        if (bytes.size > MAX_PLUGIN_BYTES) error("插件包过大")
        val plugin = if (PluginCodec.isZip(bytes)) installZip(bytes) else installJs(bytes)
        installed = loadInstalled()
        return infos(emptyList()).firstOrNull { it.id == plugin.id }
            ?: error("插件安装后未能加载")
    }

    private fun installZip(bytes: ByteArray): Plugin {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var total = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (name.endsWith("/")) continue
                if (!safeName(name)) continue
                val data = zip.readBytes()
                total += data.size
                if (total > MAX_PLUGIN_BYTES) error("插件包解压后过大")
                entries[name] = data
            }
        }
        val manifestName = entries.keys.firstOrNull {
            it == "plugin.json" || (it.endsWith("/plugin.json") && it.count { c -> c == '/' } == 1)
        } ?: error("插件包缺少 plugin.json")
        val base = manifestName.substringBeforeLast('/', "")
        val plugin = PluginCodec.parseJson((entries[manifestName] ?: error("插件包损坏")).decodeToString())
        writePlugin(plugin, entries, base)
        return plugin
    }

    private fun installJs(bytes: ByteArray): Plugin {
        val plugin = PluginCodec.parseJs(bytes)
        writePlugin(plugin, mapOf("plugin.json" to json.encodeToString(Plugin.serializer(), plugin).encodeToByteArray()), "")
        return plugin
    }

    private fun writePlugin(plugin: Plugin, entries: Map<String, ByteArray>, base: String) {
        val temp = File(directory, ".install-${System.nanoTime()}").apply { mkdirs() }
        try {
            entries.forEach { (name, data) ->
                val relative = if (base.isBlank()) name else name.removePrefix("$base/")
                if (relative.isBlank()) return@forEach
                if (!safeName(relative)) return@forEach
                val out = File(temp, relative)
                if (!out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) return@forEach
                out.parentFile?.mkdirs()
                out.writeBytes(data)
            }
            val target = File(directory, plugin.id)
            target.deleteRecursively()
            if (!temp.renameTo(target)) {
                temp.copyRecursively(target, overwrite = true)
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    fun remove(id: String) {
        if (!PluginCodec.validId(id)) error("无效插件 ID")
        File(directory, id).deleteRecursively()
        installed = loadInstalled()
    }

    private fun safeName(name: String): Boolean =
        name.isNotBlank() && !name.startsWith("/") && !name.contains("..") && !name.contains(":")

    private fun ruleCount(rules: AdRules?): Int {
        if (rules == null) return 0
        return rules.global.selectors.size + rules.global.textPatterns.size +
            rules.domains.values.sumOf { it.selectors.size + it.textPatterns.size }
    }

    private companion object {
        const val MAX_PLUGIN_BYTES = 20_000_000
    }
}
