package com.clippercast.app.clip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Tầng 2: dựng trang bằng WebView ẩn (chạy JavaScript của trang) rồi chạy Readability.js gốc
 * ngay trong trang. Dùng chung cookie với trình duyệt trong app, nên trang đã đăng nhập ở đó
 * cũng bóc tách được (spec 2.1.2).
 */
object WebViewExtractor {
    @Volatile private var readabilitySrc: String? = null

    fun script(context: Context): String {
        val src = readabilitySrc ?: context.assets.open("js/Readability.js").bufferedReader().use { it.readText() }
            .also { readabilitySrc = it }
        return src + """
;(function(){
  try {
    var lang = (document.documentElement.lang || '').split('-')[0].toLowerCase();
    var canon = document.querySelector('link[rel=canonical]');
    var site = document.querySelector('meta[property="og:site_name"]');
    var pub = document.querySelector('meta[property="article:published_time"]');
    var r = new Readability(document.cloneNode(true)).parse();
    if (!r) return JSON.stringify({error: 'empty'});
    return JSON.stringify({title: r.title, byline: r.byline, content: r.content, text: r.textContent,
      excerpt: r.excerpt, siteName: (site && site.content) || r.siteName, lang: lang || r.lang,
      published: (pub && pub.content) || r.publishedTime, canonical: canon && canon.href, url: location.href});
  } catch (e) { return JSON.stringify({error: String(e)}); }
})();
"""
    }

    fun parseResult(raw: String?): RawArticle? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val str = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return null
        val o = runCatching { JSONObject(str) }.getOrNull() ?: return null
        if (o.has("error")) return null
        fun s(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        return RawArticle(
            finalUrl = s("url") ?: "",
            title = s("title"), byline = s("byline"), contentHtml = s("content"), text = s("text"),
            lang = s("lang"), siteName = s("siteName"), published = s("published"),
            canonical = s("canonical"), excerpt = s("excerpt"),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(context: Context, url: String, timeoutMs: Long = 25_000): RawArticle? =
        withContext(Dispatchers.Main) {
            val js = withContext(Dispatchers.IO) { script(context) }
            val web = WebView(context.applicationContext)
            try {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine<RawArticle?> { cont ->
                        val handler = Handler(Looper.getMainLooper())
                        var done = false
                        fun finish(v: RawArticle?) { if (!done) { done = true; if (cont.isActive) cont.resume(v) } }
                        web.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = Http.UA
                            loadsImagesAutomatically = false
                            mediaPlaybackRequiresUserGesture = true
                        }
                        web.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                                handler.removeCallbacksAndMessages(null)
                            }
                            override fun onPageFinished(view: WebView, u: String?) {
                                handler.removeCallbacksAndMessages(null)
                                // Đợi JS của trang dựng nội dung xong
                                handler.postDelayed({
                                    view.evaluateJavascript(js) { res -> finish(parseResult(res)) }
                                }, 2500)
                            }
                        }
                        web.loadUrl(url)
                        cont.invokeOnCancellation { handler.removeCallbacksAndMessages(null) }
                    }
                }
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }
}
