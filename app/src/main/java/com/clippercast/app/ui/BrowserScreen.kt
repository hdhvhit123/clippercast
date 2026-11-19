package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clippercast.app.ClipperApp
import com.clippercast.app.clip.ArticleFinalizer
import com.clippercast.app.clip.Http
import com.clippercast.app.clip.UrlNormalizer
import com.clippercast.app.clip.WebViewExtractor
import com.clippercast.app.data.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trình duyệt trong app (spec 2.1.2, tầng 3): người dùng đăng nhập hoặc vượt bước kiểm tra
 * robot, rồi bấm "Lưu trang này" để bóc tách đúng trang đang hiển thị.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    var article by remember { mutableStateOf<Article?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(id) { article = app.repo.get(id) }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, tr(R.string.close)) } },
            title = { Text(title.ifBlank { tr(R.string.opening_page) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
            actions = {
                Button(enabled = !saving && web != null, onClick = {
                    val w = web ?: return@Button
                    val a = article ?: return@Button
                    saving = true
                    w.evaluateJavascript(WebViewExtractor.script(context)) { res ->
                        val raw = WebViewExtractor.parseResult(res)
                        scope.launch {
                            if (raw == null || raw.contentHtml.isNullOrBlank()) {
                                saving = false
                                Toast.makeText(context, tr(R.string.no_article_on_page), Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val fixed = raw.copy(finalUrl = raw.finalUrl.ifBlank { a.url })
                            val fin = withContext(Dispatchers.IO) { ArticleFinalizer(context).finalize(a.id, fixed, true) }
                            val t = fixed.title?.takeIf { it.isNotBlank() } ?: a.title
                            app.repo.saveContent(a.id, fin.html, t, fin.text)
                            app.repo.update(a.copy(
                                title = t, author = fixed.byline, siteName = fixed.siteName ?: UrlNormalizer.prettySite(fixed.finalUrl),
                                lang = fin.lang, wordCount = fin.wordCount, excerpt = fin.text.take(200), thumb = fin.thumb,
                                status = fin.status, error = null, sizeBytes = fin.sizeBytes,
                            ))
                            Toast.makeText(context, tr(R.string.page_saved), Toast.LENGTH_SHORT).show()
                            onDone()
                        }
                    }
                }, modifier = Modifier.padding(end = 8.dp)) { Text(if (saving) tr(R.string.saving) else tr(R.string.save_this_page)) }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            val a = article
            if (a != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Http.UA
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { title = view.title.orEmpty() }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    loadUrl(a.url)
                    web = this
                }
            })
        }
    }
}
