package com.clippercast.app.backup

import com.clippercast.app.tr
import com.clippercast.app.R

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.clippercast.app.data.Article
import com.clippercast.app.data.ArticleContent
import com.clippercast.app.data.QueueItem
import com.clippercast.app.data.Repository
import com.clippercast.app.data.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sao lưu và khôi phục toàn bộ thư viện thành một file ZIP (spec 2.4). Luôn miễn phí.
 * Cấu trúc: manifest.json, articles.json, images/<id>/<file>.
 */
object BackupManager {
    const val FORMAT_VERSION = 1

    data class Stats(val articles: Int, val skipped: Int)

    suspend fun export(context: Context, uri: Uri, includeImages: Boolean): Stats = withContext(Dispatchers.IO) {
        val repo = Repository.get(context)
        val all = repo.articles.all().filter { it.deletedAt == null }
        val queue = repo.queueIds()
        context.contentResolver.openOutputStream(uri)!!.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("""{"app":"ClipperCast","version":$FORMAT_VERSION,"exportedAt":${System.currentTimeMillis()},"count":${all.size}}""".toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("articles.json"))
                val w = JsonWriter(OutputStreamWriter(zip, Charsets.UTF_8))
                w.beginArray()
                for (a in all) {
                    w.beginObject()
                    w.name("id").value(a.id)
                    w.name("url").value(a.url)
                    w.name("normalizedUrl").value(a.normalizedUrl)
                    w.name("title").value(a.title)
                    w.name("author").value(a.author)
                    w.name("siteName").value(a.siteName)
                    w.name("publishedAt").value(a.publishedAt)
                    w.name("savedAt").value(a.savedAt)
                    w.name("lang").value(a.lang)
                    w.name("wordCount").value(a.wordCount.toLong())
                    w.name("excerpt").value(a.excerpt)
                    w.name("thumb").value(a.thumb?.let { File(it).name })
                    w.name("status").value(a.status.toLong())
                    w.name("readState").value(a.readState.toLong())
                    w.name("archived").value(a.archived)
                    w.name("favorite").value(a.favorite)
                    w.name("readProgress").value(a.readProgress.toDouble())
                    w.name("listenSentence").value(a.listenSentence.toLong())
                    w.name("tags").value(a.tags)
                    w.name("isNote").value(a.isNote)
                    w.name("inQueue").value(queue.indexOf(a.id).toLong())
                    var html = repo.content(a.id)
                    if (!includeImages && html != null) html = Jsoup.parseBodyFragment(html).also { it.select("img").remove() }.body().html()
                    w.name("html").value(html)
                    w.endObject()
                }
                w.endArray()
                w.flush()
                zip.closeEntry()

                if (includeImages) {
                    for (a in all) {
                        File(context.filesDir, "articles/${a.id}").listFiles()?.filter { it.isFile }?.forEach { f ->
                            zip.putNextEntry(ZipEntry("images/${a.id}/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
        Stats(all.size, 0)
    }

    suspend fun import(context: Context, uri: Uri): Stats = withContext(Dispatchers.IO) {
        val repo = Repository.get(context)
        val idMap = HashMap<Long, Long>()
        var added = 0
        var skipped = 0
        var sawArticles = false
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "articles.json" -> {
                            sawArticles = true
                            val r = JsonReader(zip.reader(Charsets.UTF_8))
                            r.beginArray()
                            while (r.hasNext()) {
                                val o = readObject(r)
                                val norm = o["normalizedUrl"] as? String ?: continue
                                if (!norm.startsWith("note:") && repo.articles.findByUrl(norm) != null) { skipped++; continue }
                                val a = Article(
                                    url = o["url"] as? String ?: "",
                                    normalizedUrl = norm,
                                    title = o["title"] as? String ?: "",
                                    author = o["author"] as? String,
                                    siteName = o["siteName"] as? String ?: "",
                                    publishedAt = o["publishedAt"] as? String,
                                    savedAt = (o["savedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    lang = o["lang"] as? String,
                                    wordCount = (o["wordCount"] as? Number)?.toInt() ?: 0,
                                    excerpt = o["excerpt"] as? String,
                                    thumb = o["thumb"] as? String, // tên file, sửa thành đường dẫn đầy đủ bên dưới
                                    status = (o["status"] as? Number)?.toInt() ?: 0,
                                    readState = (o["readState"] as? Number)?.toInt() ?: 0,
                                    archived = o["archived"] as? Boolean ?: false,
                                    favorite = o["favorite"] as? Boolean ?: false,
                                    readProgress = (o["readProgress"] as? Number)?.toFloat() ?: 0f,
                                    listenSentence = (o["listenSentence"] as? Number)?.toInt() ?: 0,
                                    tags = o["tags"] as? String ?: "",
                                    isNote = o["isNote"] as? Boolean ?: false,
                                )
                                val newId = repo.articles.insert(a)
                                val oldId = (o["id"] as? Number)?.toLong() ?: -1
                                idMap[oldId] = newId
                                val thumbName = a.thumb
                                repo.update(a.copy(id = newId, thumb = thumbName?.let { File(context.filesDir, "articles/$newId/$it").absolutePath }))
                                (o["html"] as? String)?.let { html ->
                                    repo.db.contents().upsert(ArticleContent(newId, html, TextUtils.fold(a.title + " " + Jsoup.parse(html).text())))
                                }
                                val q = (o["inQueue"] as? Number)?.toInt() ?: -1
                                if (q >= 0) repo.db.queue().put(QueueItem(newId, 10_000.0 + q))
                                added++
                            }
                            r.endArray()
                        }
                        e.name.startsWith("images/") && !e.isDirectory -> {
                            val parts = e.name.split('/')
                            val newId = parts.getOrNull(1)?.toLongOrNull()?.let { idMap[it] }
                            val name = parts.getOrNull(2)
                            if (newId != null && name != null && !name.contains("..")) {
                                val dir = File(context.filesDir, "articles/$newId").apply { mkdirs() }
                                File(dir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        if (!sawArticles) throw IllegalArgumentException(tr(R.string.backup_corrupt))
        // Bài có ảnh thumbnail nhưng không có file ảnh (bản sao lưu không kèm ảnh): bỏ thumbnail
        idMap.values.forEach { id ->
            repo.get(id)?.let { a -> if (a.thumb != null && !File(a.thumb).exists()) repo.update(a.copy(thumb = null)) }
        }
        Stats(added, skipped)
    }

    private fun readObject(r: JsonReader): Map<String, Any?> {
        val m = HashMap<String, Any?>()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            m[name] = when (r.peek()) {
                JsonToken.NULL -> { r.nextNull(); null }
                JsonToken.BOOLEAN -> r.nextBoolean()
                JsonToken.NUMBER -> r.nextString().let { s -> s.toLongOrNull() ?: s.toDoubleOrNull() }
                JsonToken.STRING -> r.nextString()
                else -> { r.skipValue(); null }
            }
        }
        r.endObject()
        return m
    }
}
