package studio.lumen.reader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.Json
import studio.lumen.reader.data.ExtractedArticle
import studio.lumen.reader.data.ExtractionEnvelope

/**
 * 离屏抽取器：不显示原站页面，只在后台加载文章 URL、注入 Readability 脚本并取回正文。
 * 阅读模式因此可以「点文章直接进阅读界面」，原站仍由用户主动打开。
 */
@SuppressLint("ViewConstructor")
class OffscreenExtractor(
    context: Context,
    private val scripts: Pair<String, String>,
    private val adblockScript: () -> String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val web = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
    }

    fun load(url: String, timeoutMillis: Long = 45_000, onResult: (Result<ExtractedArticle>) -> Unit) {
        var delivered = false
        val tasks = mutableListOf<Runnable>()
        fun schedule(delayMillis: Long, action: () -> Unit) {
            // 离屏 WebView 永远不会 attach，View.postDelayed 的队列因此不会执行，只能用主线程 Handler。
            val task = Runnable { action() }
            tasks += task
            mainHandler.postDelayed(task, delayMillis)
        }
        fun deliver(result: Result<ExtractedArticle>) {
            if (delivered) return
            delivered = true
            tasks.forEach { mainHandler.removeCallbacks(it) }
            onResult(result)
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, address: String?) {
                val ad = adblockScript()
                if (ad.isNotBlank()) view?.evaluateJavascript(ad, null)
                schedule(500) { extract(view, ::deliver) }
            }
        }
        schedule(timeoutMillis) { deliver(Result.failure(IllegalStateException("原站加载超时，可点「原站」直接查看"))) }
        runCatching { web.loadUrl(url) }
            .onFailure { deliver(Result.failure(IllegalStateException(it.message ?: "无法打开原站"))) }
    }

    private fun extract(view: WebView?, deliver: (Result<ExtractedArticle>) -> Unit) {
        val target = view ?: return deliver(Result.failure(IllegalStateException("阅读视图不可用")))
        val code = scripts.first + System.lineSeparator() + ";" + scripts.second
        target.evaluateJavascript(code) { raw ->
            deliver(
                runCatching {
                    val envelope = json.decodeFromString(ExtractionEnvelope.serializer(), raw)
                    val data = envelope.data
                    if (!envelope.ok || data == null) {
                        error(envelope.error?.takeIf { it.isNotBlank() } ?: "未找到可提取正文")
                    }
                    if (data.paragraphs.none { it.text.isNotBlank() }) error("页面没有可提取的正文")
                    data
                }
            )
        }
    }

    fun destroy() {
        runCatching {
            web.stopLoading()
            web.destroy()
        }
    }
}
