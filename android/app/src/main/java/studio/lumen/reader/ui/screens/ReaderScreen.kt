package studio.lumen.reader.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.ExtractionEnvelope
import studio.lumen.reader.data.SelectionInfo

private class SelectionBridge(private val handler: Handler, private val onSelection: (SelectionInfo) -> Unit) {
    @JavascriptInterface
    fun postSelection(text: String?, context: String?, url: String?) {
        val clean = text?.trim()?.take(1500) ?: return
        if (clean.isBlank()) return
        handler.post {
            onSelection(SelectionInfo(clean, context?.take(2000) ?: "", url ?: ""))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onBilingual: () -> Unit,
    onAsk: (String) -> Unit
) {
    val article by viewModel.currentArticle.collectAsState()
    val selection by viewModel.selected.collectAsState()
    val store by viewModel.store.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scripts = remember { viewModel.catalogScripts() }
    var extracting by remember { mutableStateOf(false) }
    var askDialog by remember { mutableStateOf(false) }

    val current = article
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("没有打开的文章", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }

    fun leave() {
        viewModel.finishReading()
        onBack()
    }

    BackHandler { leave() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var autoStarted by remember(current.url) { mutableStateOf(false) }
    val saved = store.saved.any { it.article.url == current.url }

    fun runExtraction(auto: Boolean) {
        if (extracting) return
        val view = webView ?: return
        extracting = true
        val code = scripts.first + System.lineSeparator() + ";" + scripts.second
        view.evaluateJavascript(code) { raw ->
            extracting = false
            runCatching {
                val envelope = json.decodeFromString(ExtractionEnvelope.serializer(), raw)
                if (!envelope.ok || envelope.data == null) error(envelope.error ?: "未找到可提取正文")
                viewModel.startBilingual(envelope.data)
                onBilingual()
            }.onFailure {
                if (!auto) Toast.makeText(context, it.message ?: "提取失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新原站")
                    }
                    IconButton(onClick = { openExternal(context, current.url) }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "系统浏览器")
                    }
                    IconButton(onClick = { askDialog = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(onClick = { runExtraction(false) }, enabled = !extracting) {
                    if (extracting) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (extracting) "提取中" else "双语精读")
                }
                IconButton(onClick = { viewModel.toggleSaved(current) }) {
                    Icon(
                        if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (saved) "取消收藏" else "收藏"
                    )
                }
                TextButton(onClick = { askDialog = true }) { Text("提问") }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.setSupportMultipleWindows(false)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.textZoom = (store.settings.fontSize * 100) / 18
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(SelectionBridge(mainHandler) { viewModel.setSelection(it) }, "LumenAndroid")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(SELECTION_SCRIPT, null)
                            val adScript = viewModel.adblockScript()
                            if (adScript.isNotBlank()) view?.evaluateJavascript(adScript, null)
                            if (!autoStarted && !url.isNullOrBlank() && !url.startsWith("about:") && viewModel.consumeAutoBilingual()) {
                                autoStarted = true
                                view?.postDelayed({ runExtraction(true) }, 1200)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            return handleExternalScheme(ctx, url)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.deny()
                        }
                    }
                    setDownloadListener(DownloadListener { url, _, _, _, _ ->
                        Toast.makeText(ctx, "下载已阻止：请在系统浏览器打开 $url", Toast.LENGTH_LONG).show()
                    })
                    loadUrl(current.url)
                    webView = this
                }
            },
            update = { view ->
                view.settings.textZoom = (store.settings.fontSize * 100) / 18
                if (view.url != current.url) view.loadUrl(current.url)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    if (selection != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.setSelection(null) }) {
            SelectionSheet(
                selection = selection!!,
                viewModel = viewModel,
                onDismiss = { viewModel.setSelection(null) }
            )
        }
    }

    if (askDialog) {
        AlertDialog(
            onDismissRequest = { askDialog = false },
            title = { Text("向 AI 提问") },
            text = { Text("选择要打开的网页版助手。当前选文会保留，可在提问页复制上下文。") },
            confirmButton = {
                Row {
                    TextButton(onClick = { askDialog = false; onAsk("chatgpt") }) { Text("ChatGPT") }
                    TextButton(onClick = { askDialog = false; onAsk("deepseek") }) { Text("DeepSeek") }
                }
            },
            dismissButton = {
                TextButton(onClick = { askDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SelectionSheet(selection: SelectionInfo, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var translation by remember(selection.text) { mutableStateOf<String?>(null) }
    var definitions by remember(selection.text) { mutableStateOf<List<String>>(emptyList()) }
    var meaning by remember(selection.text) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(selection.text, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching {
                        val result = viewModel.translateText(selection.text, selection.context)
                        translation = result.text
                        if (meaning.isBlank()) meaning = result.text
                    }.onFailure { error = it.message }.also { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "翻译中…" else "翻译") }
            OutlinedButton(onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching {
                        definitions = viewModel.dictionary(selection.text)
                        if (definitions.isEmpty()) error = "词典未收录该词"
                        else if (meaning.isBlank()) meaning = definitions.first()
                    }.onFailure { error = it.message }.also { busy = false }
                }
            }, enabled = !busy) { Text("查词") }
        }
        if (translation != null) {
            Spacer(Modifier.height(12.dp))
            Text(translation ?: "", style = MaterialTheme.typography.bodyLarge)
        }
        definitions.forEach {
            Spacer(Modifier.height(6.dp))
            Text("· $it", style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = meaning,
            onValueChange = { meaning = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("释义 / 笔记") },
            minLines = 2
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                viewModel.addVocab(selection.text, meaning, selection.context, selection.url)
                onDismiss()
            }) { Text("加入词句本") }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        if (selection.context.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("语境", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                selection.context,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun handleExternalScheme(context: android.content.Context, url: String): Boolean {
    return try {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme == "http" || scheme == "https") false
        else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }
    } catch (_: Exception) {
        true
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private const val SELECTION_SCRIPT = """(function(){
  if (window.__lumenAndroidSelection) return;
  window.__lumenAndroidSelection = true;
  function send(){
    var s = window.getSelection();
    var t = s && s.toString().trim();
    if (!t || t.length > 1500) return;
    var n = s.anchorNode && s.anchorNode.parentElement;
    if (n && n.closest('input,textarea,[contenteditable="true"]')) return;
    var c = n && n.closest('p,blockquote,li') ? n.closest('p,blockquote,li').textContent.trim().slice(0,2000) : '';
    if (window.LumenAndroid && window.LumenAndroid.postSelection) {
      window.LumenAndroid.postSelection(t, c, location.href);
    }
  }
  var timer;
  function later(){ clearTimeout(timer); timer = setTimeout(send, 250); }
  document.addEventListener('mouseup', function(){ setTimeout(send, 30); });
  document.addEventListener('keyup', function(e){ if (e.key === 'Shift') send(); });
  document.addEventListener('touchend', later, true);
  document.addEventListener('selectionchange', later);
})();"""
