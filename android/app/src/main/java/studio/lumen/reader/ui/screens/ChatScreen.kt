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
import androidx.compose.ui.graphics.Color
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
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
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
                    // 站点对 WebView UA（含 "; wv"）会走兼容分支，登录页白屏且输入框字符乱序；
                    // 换成标准移动版 Chrome UA，行为与浏览器一致。
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    isFocusableInTouchMode = true
                    requestFocus()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            view?.evaluateJavascript(CARET_GUARD_SCRIPT, null)
                        }

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
            update = { if (it.url.isNullOrEmpty()) it.loadUrl(url) }
        )
    }
}

/**
 * 站点在 WebView 里每次输入后会把光标重置到开头，连续输入就变成倒序
 * （1 → 61 → 8761）。这里记录插入前的光标位置，输入后若光标被重置到 0，
 * 就把它放回「插入位置 + 内容长度」；组词、粘贴改写、中间插入都按同一规则处理。
 */
private val CARET_GUARD_SCRIPT = """
(function () {
  if (window.__lumenCaretGuard) return;
  window.__lumenCaretGuard = true;
  var pending = null;

  function isField(el) {
    return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
  }

  function place(el, pos) {
    if (pos <= 0) return;
    if (document.activeElement !== el) return;
    try { el.setSelectionRange(pos, pos); } catch (err) {}
  }

  document.addEventListener('beforeinput', function (event) {
    if (!isField(event.target) || event.isComposing) return;
    pending = {
      el: event.target,
      start: event.target.selectionStart,
      end: event.target.selectionEnd,
      data: event.data,
      type: event.inputType || ''
    };
  }, true);

  document.addEventListener('input', function (event) {
    var el = event.target;
    if (!isField(el) || event.isComposing) return;
    if (document.activeElement !== el) return;
    if (!pending || pending.el !== el) return;
    var p = pending;
    pending = null;
    var target = -1;
    if (p.data) {
      if (el.value.slice(p.start, p.start + p.data.length) !== p.data) return;
      target = p.start + p.data.length;
    } else if (p.type === 'deleteContentBackward' && p.start === p.end && p.start > 1) {
      target = p.start - 1;
    }
    if (target <= 0) return;
    // 只在光标被重置到开头时介入，正常输入不动。
    if (el.selectionStart === 0 && el.selectionEnd === 0) place(el, target);
    // 站点可能在 React 提交阶段稍后再重置一次，下一轮任务再校正。
    setTimeout(function () {
      if (el.selectionStart !== 0 || el.selectionEnd !== 0) return;
      if (p.data && el.value.slice(p.start, p.start + p.data.length) !== p.data) return;
      place(el, target);
    }, 0);
  }, true);
})();
""".trimIndent()
