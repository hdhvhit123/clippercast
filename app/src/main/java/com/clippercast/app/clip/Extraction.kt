package com.clippercast.app.clip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.TextUtils
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Kết quả bóc tách thô (trước khi làm sạch và tải ảnh). */
data class RawArticle(
    val finalUrl: String,
    val title: String?,
    val byline: String?,
    val contentHtml: String?,
    val text: String?,
    val lang: String?,
    val siteName: String?,
    val published: String?,
    val canonical: String?,
    val excerpt: String?,
)

class HttpStatusException(val code: Int) : IOException("HTTP $code")
class NotArticleException(val reason: String, val title: String?) : IOException(reason)

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/130.0.0.0 Mobile Safari/537.36"
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    const val MAX_PAGE_BYTES = 5L * 1024 * 1024
}

/** Tầng 1: tải HTML bằng OkHttp rồi chạy Readability ngay trên máy (spec 2.1.2). */
object FastExtractor {
    fun fetchAndExtract(url: String): RawArticle {
        val req = Request.Builder().url(url)
            .header("User-Agent", Http.UA)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val finalUrl = resp.request.url.toString()
            val type = resp.header("Content-Type").orEmpty().lowercase()
            if (type.contains("pdf")) throw NotArticleException("Đây là file PDF, đã lưu dưới dạng link.", null)
            if (type.isNotEmpty() && !type.contains("html") && !type.contains("xml") && !type.contains("text"))
                throw NotArticleException("Link này không phải trang bài viết.", null)
            val body = resp.body ?: throw IOException("Trang rỗng")
            val len = body.contentLength()
            if (len > Http.MAX_PAGE_BYTES) throw NotArticleException("Trang quá lớn để lưu.", null)
            val bytes = body.byteStream().use { it.readNBytesCompat(Http.MAX_PAGE_BYTES.toInt()) }
            val charset = body.contentType()?.charset() ?: Charsets.UTF_8
            val html = String(bytes, charset)
            return extractFromHtml(finalUrl, html)
        }
    }

    fun extractFromHtml(url: String, html: String): RawArticle {
        val doc = Jsoup.parse(html, url)
        val meta = Meta.from(doc)
        val r = Readability4JExtended(url, html).parse()
        return RawArticle(
            finalUrl = url,
            title = r.title?.takeIf { it.isNotBlank() } ?: meta.title,
            byline = r.byline ?: meta.author,
            contentHtml = r.content,
            text = r.textContent,
            lang = meta.lang,
            siteName = meta.siteName,
            published = meta.published,
            canonical = meta.canonical,
            excerpt = r.excerpt ?: meta.description,
        )
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

data class Meta(
    val title: String?, val siteName: String?, val author: String?, val published: String?,
    val canonical: String?, val lang: String?, val description: String?,
) {
    companion object {
        fun from(doc: Document): Meta {
            fun m(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
                doc.selectFirst("meta[property=$k], meta[name=$k]")?.attr("content")?.takeIf { it.isNotBlank() }
            }
            return Meta(
                title = m("og:title", "twitter:title") ?: doc.title().takeIf { it.isNotBlank() },
                siteName = m("og:site_name", "application-name"),
                author = m("author", "article:author"),
                published = m("article:published_time", "datePublished", "pubdate"),
                canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() },
                lang = doc.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() }?.substringBefore('-')?.lowercase(),
                description = m("og:description", "description"),
            )
        }
    }
}

/** Làm sạch HTML, tải ảnh về máy và tính các thông tin cuối cùng. */
class ArticleFinalizer(private val context: Context) {

    data class Result(
        val html: String, val text: String, val wordCount: Int, val thumb: String?,
        val sizeBytes: Long, val status: Int, val lang: String,
    )

    private val safelist: Safelist = Safelist.relaxed()
        .addTags("figure", "figcaption", "hr", "mark", "del", "s", "ins", "section", "article", "abbr", "time")
        .addAttributes(":all", "dir", "lang")
        .addAttributes("img", "alt", "title")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("th", "colspan", "rowspan")
        .removeTags("span")

    fun dirFor(id: Long) = File(context.filesDir, "articles/$id").apply { mkdirs() }

    fun finalize(id: Long, raw: RawArticle, loadImages: Boolean): Result {
        val pre = Jsoup.parseBodyFragment(raw.contentHtml.orEmpty(), raw.finalUrl)
        // Ảnh tải lười (lazy-load): đưa data-src vào src trước khi làm sạch
        pre.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isBlank() || src.startsWith("data:")) {
                listOf("data-src", "data-lazy-src", "data-original", "data-url").firstOrNull { img.hasAttr(it) }
                    ?.let { img.attr("src", img.absUrl(it)) }
            }
            if (img.attr("src").isBlank()) {
                img.attr("srcset").split(",").lastOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                    ?.let { img.attr("src", it) }
            }
        }
        pre.select("picture source, noscript, script, style, iframe, form, button, input, svg, video, audio").remove()
        val clean = Jsoup.clean(pre.body().html(), raw.finalUrl, safelist)
        val doc = Jsoup.parseBodyFragment(clean)
        // Bỏ tiêu đề lặp lại ở đầu bài
        doc.body().selectFirst("h1")?.let { h1 ->
            if (raw.title != null && TextUtils.fold(h1.text()) == TextUtils.fold(raw.title)) h1.remove()
        }
        // Bỏ đoạn rỗng
        doc.select("p, div").filter { it.text().isBlank() && it.select("img").isEmpty() }.forEach { it.remove() }

        val dir = dirFor(id)
        var thumb: String? = null
        var index = 0
        for (img in doc.select("img")) {
            val src = img.absUrl("src").ifBlank { img.attr("src") }
            if (!loadImages || index >= 40 || !src.startsWith("http")) { img.remove(); continue }
            val name = "img_$index.webp"
            val ok = runCatching { downloadImage(src, File(dir, name)) }.getOrDefault(null)
            if (ok == null) { img.remove(); continue }
            if (ok.first < 80 || ok.second < 40) { File(dir, name).delete(); img.remove(); continue }
            img.attr("src", name)
            img.removeAttr("srcset")
            if (thumb == null && ok.first >= 200) thumb = File(dir, name).absolutePath
            index++
        }
        // Nội dung nhúng không bóc tách được: ô giữ chỗ có link (spec 2.2)
        doc.select("a").forEach { a -> a.attr("href", a.absUrl("href")) }

        val text = doc.body().text()
        val words = TextUtils.wordCount(text)
        val html = doc.body().html()
        val lang = raw.lang ?: TextUtils.guessLang(text)
        val size = dir.walk().filter { it.isFile }.sumOf { it.length() } + html.length * 2L
        val status = if (looksPaywalled(text, words)) ClipStatus.PARTIAL else ClipStatus.READY
        return Result(html, text, words, thumb, size, status, lang)
    }

    private fun looksPaywalled(text: String, words: Int): Boolean {
        if (words < 150) return true
        val tail = TextUtils.fold(text.takeLast(600))
        val markers = listOf("subscribe to continue", "subscribe to read", "members only", "sign in to read",
            "create a free account", "dang ky de doc", "dang nhap de doc", "noi dung danh cho thanh vien",
            "continue reading with", "this article is for subscribers")
        return markers.any { tail.contains(it) }
    }

    /** Tải ảnh, thu nhỏ về tối đa 1080 px và lưu WebP. Trả về (rộng, cao). */
    private fun downloadImage(url: String, out: File): Pair<Int, Int>? {
        val req = Request.Builder().url(url).header("User-Agent", Http.UA).build()
        val bytes = Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val len = r.body?.contentLength() ?: -1
            if (len > 15L * 1024 * 1024) return null
            r.body?.bytes() ?: return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scaled = if (bmp.width > 1080) Bitmap.createScaledBitmap(bmp, 1080, bmp.height * 1080 / bmp.width, true) else bmp
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        out.outputStream().use { scaled.compress(fmt, 80, it) }
        val res = bounds.outWidth to bounds.outHeight
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return res
    }
}
