package com.clippercast.app.reader

import com.clippercast.app.tr
import com.clippercast.app.R

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.clippercast.app.ClipperApp
import com.clippercast.app.backup.MarkdownExporter
import com.clippercast.app.data.AppSettings
import com.clippercast.app.data.Article
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.ReadState
import com.clippercast.app.data.ReaderTheme
import com.clippercast.app.player.Playback
import com.clippercast.app.ui.AddDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

fun effectiveTheme(s: AppSettings, systemDark: Boolean): ReaderTheme = when {
    !s.followSystem -> s.theme
    systemDark -> if (s.theme.dark) s.theme else ReaderTheme.NAVY
    else -> if (s.theme.dark) ReaderTheme.LIGHT else s.theme
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(id: Long, autoplay: Boolean, onBack: () -> Unit, openPlayer: () -> Unit, openBrowser: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val article by repo.observe(id).collectAsState(initial = null)
    val appSettings by app.settings.flow.collectAsState(initial = null)
    val play by Playback.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    var chrome by remember { mutableStateOf(true) }
    var marked by remember { mutableStateOf<SentenceMarker.Marked?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var autoplayDone by remember { mutableStateOf(false) }
    val isThisPlaying = play.article?.id == id
    val a = article
    val s = appSettings

    // Nạp nội dung khi bài sẵn sàng
    LaunchedEffect(a?.status, s?.readCaptions) {
        if (a == null || s == null || !ClipStatus.isReadable(a.status) || marked != null) return@LaunchedEffect
        val html = repo.content(id) ?: return@LaunchedEffect
        marked = withContext(Dispatchers.Default) { SentenceMarker.mark(html, s.readCaptions, a.lang) }
        if (a.readState == ReadState.UNREAD) repo.update(a.copy(readState = ReadState.READING))
    }
    // "Nghe ngay": chờ bài tải xong rồi phát
    LaunchedEffect(a?.status, autoplay) {
        if (autoplay && !autoplayDone && a != null && ClipStatus.isReadable(a.status)) {
            autoplayDone = true
            Playback.play(id)
        }
    }

    // Chế độ toàn màn hình: ẩn thanh hệ thống khi ẩn thanh công cụ (spec 4)
    val view = LocalView.current
    DisposableEffect(chrome) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chrome) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    DisposableEffect(s?.keepScreenOn) {
        view.keepScreenOn = s?.keepScreenOn == true
        onDispose { view.keepScreenOn = false }
    }

    val theme = s?.let { effectiveTheme(it, systemDark) } ?: ReaderTheme.LIGHT
    // Áp dụng kiểu chữ ngay, không cần tải lại trang
    LaunchedEffect(s, theme, pageReady) {
        if (s != null && pageReady) webView?.evaluateJavascript("applyStyle(${ReaderHtml.styleJson(s, theme)})", null)
    }
    // Highlight câu đang đọc
    LaunchedEffect(play.sentenceIndex, isThisPlaying, play.isPlaying, pageReady) {
        if (!pageReady) return@LaunchedEffect
        val i = if (isThisPlaying) play.sentenceIndex else -1
        webView?.evaluateJavascript("highlight($i, ${play.isPlaying})", null)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val art = a ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = runCatching {
                val md = MarkdownExporter.toMarkdown(art, repo.content(art.id).orEmpty())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, if (ok) tr(R.string.md_exported) else tr(R.string.md_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(theme.bg))) {
        when {
            a == null || s == null -> Unit
            !ClipStatus.isReadable(a.status) -> NotReady(
                a, onRetry = { scope.launch { repo.retry(id) } }, onBrowser = { openBrowser(id) },
                onPaste = { showPaste = true },
                onOriginal = { if (a.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url))) },
                modifier = Modifier.statusBarsPadding().padding(top = 56.dp),
            )
            marked == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val m = marked!!
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(theme.bg.toInt())
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = true
                            settings.blockNetworkLoads = true
                            settings.builtInZoomControls = false
                            settings.textZoom = 100
                            addJavascriptInterface(object {
                                @JavascriptInterface fun onScroll(p: Float) {
                                    scope.launch { repo.get(id)?.let { cur -> if (kotlin.math.abs(cur.readProgress - p) > 0.01f) repo.update(cur.copy(readProgress = p)) } }
                                }
                                @JavascriptInterface fun onTap() { scope.launch { chrome = !chrome } }
                                @JavascriptInterface fun onSentenceTap(i: Int) { scope.launch { Playback.seekTo(i) } }
                                @JavascriptInterface fun isPlaying(): Boolean = Playback.state.value.let { it.isPlaying && it.article?.id == id }
                            }, "Android")
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url
                                    if (u.scheme == "http" || u.scheme == "https") {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                                    }
                                    return true
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val sch = request.url.scheme
                                    // Trình đọc không bao giờ tải gì từ mạng
                                    return if (sch == "http" || sch == "https")
                                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    view.evaluateJavascript("applyStyle(${ReaderHtml.styleJson(s, theme)})", null)
                                    val st = Playback.state.value
                                    if (st.article?.id == id) view.evaluateJavascript("scrollToSentence(${st.sentenceIndex})", null)
                                    else if (a.readProgress > 0.01f) view.evaluateJavascript("scrollToProgress(${a.readProgress})", null)
                                    pageReady = true
                                }
                            }
                            val base = "file://" + File(ctx.filesDir, "articles/$id").absolutePath + "/"
                            loadDataWithBaseURL(base, ReaderHtml.build(ctx, a, m.html, s, theme), "text/html", "utf-8", null)
                            webView = this
                        }
                    },
                )
            }
        }

        // Thanh công cụ trên
        AnimatedVisibility(chrome || a == null || !ClipStatus.isReadable(a.status), enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) }
                    Spacer(Modifier.weight(1f))
                    if (a != null && ClipStatus.isReadable(a.status)) {
                        IconButton(onClick = { showDisplay = true }) { Icon(Icons.Outlined.FormatSize, tr(R.string.display_options)) }
                        IconButton(onClick = { scope.launch { repo.setFavorite(id, !a.favorite) } }) {
                            Icon(if (a.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (a.favorite) tr(R.string.unfavorite) else tr(R.string.favorite))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                repo.setArchived(id, !a.archived)
                                Toast.makeText(context, if (a.archived) tr(R.string.moved_to_inbox) else tr(R.string.archived), Toast.LENGTH_SHORT).show()
                                if (!a.archived) onBack()
                            }
                        }) { Icon(if (a.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (a.archived) tr(R.string.move_to_inbox) else tr(R.string.archive)) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr(R.string.more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (a != null && a.url.isNotBlank()) {
                            DropdownMenuItem(text = { Text(tr(R.string.view_original)) }, onClick = {
                                menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)))
                            })
                            DropdownMenuItem(text = { Text(tr(R.string.share_link)) }, onClick = {
                                menu = false
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${a.title}\n${a.url}")
                                }, tr(R.string.share_link)))
                            })
                            DropdownMenuItem(text = { Text(tr(R.string.open_to_sign_in_again)) }, onClick = { menu = false; openBrowser(id) })
                        }
                        if (a != null && ClipStatus.isReadable(a.status)) {
                            DropdownMenuItem(text = { Text(tr(R.string.export_md)) }, onClick = {
                                menu = false; exportLauncher.launch(MarkdownExporter.fileName(a))
                            })
                        }
                        if (a != null && a.url.isNotBlank()) {
                            DropdownMenuItem(text = { Text(tr(R.string.report_extraction)) }, onClick = {
                                menu = false
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, tr(R.string.report_subject))
                                    putExtra(Intent.EXTRA_TEXT, tr(R.string.report_body, a.url))
                                }, tr(R.string.send_report)))
                            })
                        }
                        DropdownMenuItem(text = { Text(tr(R.string.delete)) }, onClick = {
                            menu = false; scope.launch { repo.moveToTrash(id); onBack() }
                        })
                    }
                }
            }
        }

        // Thanh nghe ở dưới
        if (a != null && ClipStatus.isReadable(a.status) && (chrome || isThisPlaying)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isThisPlaying) {
                        IconButton(onClick = { Playback.seekSentence(-1) }) { Icon(Icons.Outlined.SkipPrevious, tr(R.string.prev_sentence)) }
                        IconButton(onClick = { Playback.toggle() }) {
                            Icon(if (play.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (play.isPlaying) tr(R.string.pause) else tr(R.string.play))
                        }
                        IconButton(onClick = { Playback.seekSentence(1) }) { Icon(Icons.Outlined.SkipNext, tr(R.string.next_sentence)) }
                        Text(
                            play.error ?: (tr(R.string.sentence_progress, play.sentenceIndex + 1, play.sentenceCount) + " · ${"%.1f".format(play.speed)}x"),
                            Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (play.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = openPlayer) { Text(tr(R.string.player)) }
                    } else {
                        FilledTonalButton(onClick = { Playback.play(id, 0) }) {
                            Icon(Icons.Outlined.Headphones, null); Spacer(Modifier.width(6.dp)); Text(tr(R.string.listen_from_start))
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            webView?.evaluateJavascript("firstVisibleSentence()") { r -> r?.toIntOrNull()?.let { Playback.play(id, it) } }
                        }) { Text(tr(R.string.listen_from_here)) }
                    }
                }
            }
        }
    }

    if (showDisplay && s != null) DisplaySheet(s, onDismiss = { showDisplay = false })
    if (showPaste) AddDialog(onDismiss = { showPaste = false }, onSaved = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }, initialMode = 1)
}

@Composable
private fun NotReady(a: Article, onRetry: () -> Unit, onBrowser: () -> Unit, onPaste: () -> Unit, onOriginal: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text(a.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        when (a.status) {
            ClipStatus.PENDING, ClipStatus.LOADING -> {
                CircularProgressIndicator()
                Text(a.error ?: tr(R.string.downloading_article), textAlign = TextAlign.Center)
            }
            else -> {
                Text(a.error ?: tr(R.string.no_content_yet), textAlign = TextAlign.Center,
                    color = if (a.status == ClipStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.status == ClipStatus.FAILED) Button(onClick = onRetry) { Text(tr(R.string.retry)) }
                if (a.url.isNotBlank()) OutlinedButton(onClick = onBrowser) { Text(tr(R.string.open_to_sign_in)) }
                OutlinedButton(onClick = onPaste) { Text(tr(R.string.paste_text_manually)) }
                if (a.url.isNotBlank()) TextButton(onClick = onOriginal) { Text(tr(R.string.view_original)) }
            }
        }
    }
    LaunchedEffect(Unit) { delay(0) }
}
