package com.clippercast.app.data

import com.clippercast.app.tr
import com.clippercast.app.R

import android.content.Context
import com.clippercast.app.clip.ClipWorker
import com.clippercast.app.clip.UrlNormalizer
import kotlinx.coroutines.flow.Flow
import org.jsoup.nodes.Entities
import java.io.File

class Repository(private val context: Context) {
    val db = AppDatabase.get(context)
    val articles = db.articles()
    private val contents = db.contents()
    private val queueDao = db.queue()

    data class SaveResult(val id: Long, val duplicate: Boolean, val isNote: Boolean)

    /** Lưu nội dung được chia sẻ: link thì lưu ngay rồi tải nền, văn bản không có link thì lưu thành ghi chú. */
    suspend fun saveShared(text: String?, subject: String?): SaveResult? {
        val url = UrlNormalizer.extractUrl(text)
        if (url == null) {
            if (text.isNullOrBlank()) return null
            return SaveResult(addNote(subject, text), duplicate = false, isNote = true)
        }
        return saveUrl(url, subject?.takeIf { it.isNotBlank() && !it.startsWith("http") })
    }

    suspend fun saveUrl(rawUrl: String, title: String? = null): SaveResult {
        val normalized = UrlNormalizer.normalize(rawUrl)
        articles.findByUrl(normalized)?.let { old ->
            // Bài trùng: đưa lên đầu danh sách
            articles.update(old.copy(savedAt = System.currentTimeMillis(), archived = false))
            return SaveResult(old.id, duplicate = true, isNote = false)
        }
        val id = articles.insert(
            Article(
                url = normalized,
                normalizedUrl = normalized,
                title = title ?: normalized,
                siteName = UrlNormalizer.prettySite(normalized),
                status = ClipStatus.PENDING,
            )
        )
        ClipWorker.enqueue(context, id)
        return SaveResult(id, duplicate = false, isNote = false)
    }

    suspend fun addNote(title: String?, text: String): Long {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val t = title?.takeIf { it.isNotBlank() } ?: firstLine.take(80).ifBlank { tr(R.string.note) }
        val html = text.split(Regex("\\n\\s*\\n|\\n")).filter { it.isNotBlank() }
            .joinToString("") { "<p>${Entities.escape(it.trim())}</p>" }
        val words = TextUtils.wordCount(text)
        val id = articles.insert(
            Article(
                url = "", normalizedUrl = "note:${System.nanoTime()}", title = t, siteName = tr(R.string.note),
                lang = TextUtils.guessLang(text), wordCount = words, excerpt = text.take(200),
                status = ClipStatus.READY, isNote = true, sizeBytes = html.length * 2L,
            )
        )
        contents.upsert(ArticleContent(id, html, TextUtils.fold("$t $text")))
        return id
    }

    suspend fun content(id: Long): String? = contents.get(id)?.html

    suspend fun saveContent(id: Long, html: String, title: String, text: String) {
        contents.upsert(ArticleContent(id, html, TextUtils.fold("$title $text")))
    }

    fun observe(id: Long): Flow<Article?> = articles.observe(id)
    suspend fun get(id: Long) = articles.get(id)
    suspend fun update(a: Article) = articles.update(a)

    suspend fun setArchived(id: Long, archived: Boolean) = articles.get(id)?.let {
        articles.update(it.copy(archived = archived, readState = if (archived) ReadState.READ else it.readState))
    }

    suspend fun setFavorite(id: Long, fav: Boolean) = articles.get(id)?.let { articles.update(it.copy(favorite = fav)) }

    suspend fun moveToTrash(id: Long) = articles.get(id)?.let {
        articles.update(it.copy(deletedAt = System.currentTimeMillis()))
        queueDao.remove(id)
    }

    suspend fun restore(id: Long) = articles.get(id)?.let { articles.update(it.copy(deletedAt = null)) }

    suspend fun deleteForever(id: Long) {
        File(context.filesDir, "articles/$id").deleteRecursively()
        contents.delete(id)
        queueDao.remove(id)
        articles.deleteRow(id)
    }

    suspend fun emptyTrash() = articles.trashOlderThan(Long.MAX_VALUE).forEach { deleteForever(it.id) }

    suspend fun retry(id: Long) = articles.get(id)?.let {
        articles.update(it.copy(status = ClipStatus.PENDING, error = null))
        ClipWorker.enqueue(context, id)
    }

    suspend fun retryAll() = articles.retryable().forEach { retry(it.id) }

    // --- Hàng chờ nghe ---
    fun queue(): Flow<List<Article>> = queueDao.observeQueue()
    suspend fun queueIds(): List<Long> = queueDao.ids()
    suspend fun inQueue(id: Long) = queueDao.contains(id) > 0
    suspend fun enqueueEnd(id: Long) = queueDao.put(QueueItem(id, (queueDao.maxPos() ?: 0.0) + 1.0))
    suspend fun enqueueFront(id: Long) = queueDao.put(QueueItem(id, (queueDao.minPos() ?: 0.0) - 1.0))
    suspend fun dequeue(id: Long) = queueDao.remove(id)
    suspend fun moveInQueue(id: Long, delta: Int) {
        val ids = queueDao.ids().toMutableList()
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids.removeAt(i); ids.add(j, id)
        ids.forEachIndexed { idx, aid -> queueDao.put(QueueItem(aid, idx.toDouble())) }
    }
    /** Đặt bài ngay sau bài đang phát (nếu có). */
    suspend fun playNext(id: Long, currentId: Long?) {
        val ids = queueDao.ids().toMutableList()
        ids.remove(id)
        val at = currentId?.let { ids.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
        ids.add(at, id)
        ids.forEachIndexed { idx, aid -> queueDao.put(QueueItem(aid, idx.toDouble())) }
    }

    // --- Dọn dẹp dung lượng (spec 7) ---
    suspend fun housekeeping(autoDeleteArchivedDays: Int) {
        val now = System.currentTimeMillis()
        articles.trashOlderThan(now - 30L * 24 * 3600 * 1000).forEach { deleteForever(it.id) }
        if (autoDeleteArchivedDays > 0) {
            articles.archivedOlderThan(now - autoDeleteArchivedDays * 24L * 3600 * 1000).forEach { moveToTrash(it.id) }
        }
    }

    /** Xóa ảnh của bài đã lưu trữ, giữ lại chữ. Trả về số byte giải phóng. */
    suspend fun clearArchivedImages(): Long {
        var freed = 0L
        for (a in articles.archivedAll()) {
            val dir = File(context.filesDir, "articles/${a.id}")
            val files = dir.listFiles()?.filter { it.name.startsWith("img_") } ?: continue
            if (files.isEmpty()) continue
            val bytes = files.sumOf { it.length() }
            freed += bytes
            files.forEach { it.delete() }
            content(a.id)?.let { html ->
                val doc = org.jsoup.Jsoup.parseBodyFragment(html)
                doc.select("img").remove()
                contents.upsert(ArticleContent(a.id, doc.body().html(), contents.get(a.id)?.searchText ?: ""))
            }
            articles.update(a.copy(thumb = null, sizeBytes = (a.sizeBytes - bytes).coerceAtLeast(0)))
        }
        return freed
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
