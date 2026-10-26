@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import studio.lumen.reader.MainViewModel

@Composable
fun ChatScreen(viewModel: MainViewModel, service: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val article by viewModel.currentArticle.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val bilingual by viewModel.bilingual.collectAsState()
    val isDeepSeek = service == "deepseek"
    val title = if (isDeepSeek) "DeepSeek · Lumen 提问" else "ChatGPT · Lumen 提问"
    val url = if (isDeepSeek) "https://chat.deepseek.com/" else "https://chatgpt.com/"
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onBack() }

    fun copyContext() {
        val articleTitle = article?.title ?: bilingual?.title ?: ""
        val articleUrl = article?.url ?: bilingual?.url ?: ""
        val excerpt = selected?.text ?: bilingual?.paragraphs?.firstOrNull { it.text.isNotBlank() }?.text ?: ""
        val text = buildString {
            if (articleTitle.isNotBlank()) append("我正在阅读：").append(articleTitle).append('\n')
            if (articleUrl.isNotBlank()) append("来源：").append(articleUrl).append('\n')
            if (excerpt.isNotBlank()) append("\n选中/当前段落：\n").append(excerpt).append('\n')
            append("\n请结合语境帮助我理解以下问题：\n")
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lumen context", text))
        Toast.makeText(context, "已复制选文和出处，请在网页输入框粘贴后提问", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { copyContext() }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制选文提问")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "浏览器")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                Toast.makeText(ctx, "网页连接失败，可点右上角用系统浏览器打开", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            update = { if (it.url != url) it.loadUrl(url) }
        )
    }
}
