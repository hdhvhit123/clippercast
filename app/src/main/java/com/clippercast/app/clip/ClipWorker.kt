package com.clippercast.app.clip

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.Repository
import com.clippercast.app.data.SettingsRepo
import com.clippercast.app.data.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Tải và bóc tách bài chạy nền (spec 2.1.2). */
class ClipWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val repo = Repository.get(applicationContext)
        val settings = SettingsRepo(applicationContext).current()
        val article = repo.get(id) ?: return Result.success()
        if (article.deletedAt != null) return Result.success()
        repo.update(article.copy(status = ClipStatus.LOADING, error = null))

        if (UrlNormalizer.isLinkOnlyHost(article.url)) {
            val title = runCatching { withContext(Dispatchers.IO) { FastExtractor.fetchAndExtract(article.url).title } }.getOrNull()
            repo.update(article.copy(status = ClipStatus.LINK_ONLY, title = title ?: article.title,
                error = "Video và mạng xã hội được lưu dưới dạng link."))
            return Result.success()
        }

        // Tầng 1: tải nhanh
        var raw: RawArticle? = null
        var failure: Throwable? = null
        try {
            raw = withContext(Dispatchers.IO) { FastExtractor.fetchAndExtract(article.url) }
        } catch (e: NotArticleException) {
            repo.update(article.copy(status = ClipStatus.LINK_ONLY, error = e.reason))
            return Result.success()
        } catch (e: UnknownHostException) {
            // Không có mạng: giữ trạng thái chờ, thử lại sau
            repo.update(article.copy(status = ClipStatus.PENDING, error = "Đã lưu link. Sẽ tự tải khi có mạng."))
            return if (runAttemptCount < 5) Result.retry() else Result.success()
        } catch (e: Throwable) {
            failure = e
        }

        // Tầng 2: dựng lại bằng WebView khi nội dung quá ngắn hoặc tầng 1 lỗi
        val words1 = raw?.text?.let { TextUtils.wordCount(it) } ?: 0
        if (raw == null || words1 < 200) {
            val viaWeb = runCatching { WebViewExtractor.extract(applicationContext, raw?.finalUrl ?: article.url) }.getOrNull()
            val words2 = viaWeb?.text?.let { TextUtils.wordCount(it) } ?: 0
            if (viaWeb != null && words2 > words1) raw = viaWeb.copy(finalUrl = viaWeb.finalUrl.ifBlank { article.url })
        }

        val current = repo.get(id) ?: return Result.success()
        if (raw == null || raw.contentHtml.isNullOrBlank()) {
            val reason = reasonFor(failure)
            repo.update(current.copy(status = ClipStatus.FAILED, error = reason))
            if (settings.notifyOnSave) Notifications.failed(applicationContext, id, current.title, reason)
            return Result.success()
        }

        // Phát hiện bài trùng qua URL chuẩn (canonical)
        val canonical = raw.canonical?.let { UrlNormalizer.normalize(it) }
        if (canonical != null && canonical != current.normalizedUrl) {
            repo.articles.findDuplicate(canonical, id)?.let { dup ->
                repo.update(dup.copy(savedAt = System.currentTimeMillis(), archived = false))
                repo.deleteForever(id)
                return Result.success()
            }
        }

        val loadImages = when (settings.images) {
            2 -> false
            1 -> !isMetered()
            else -> true
        }
        val fin = withContext(Dispatchers.IO) { ArticleFinalizer(applicationContext).finalize(id, raw, loadImages) }
        val title = raw.title?.trim()?.takeIf { it.isNotBlank() } ?: current.title
        repo.saveContent(id, fin.html, title, fin.text)
        val updated = current.copy(
            title = title,
            author = raw.byline?.trim()?.take(120),
            siteName = raw.siteName?.trim()?.takeIf { it.isNotBlank() && it.length < 40 } ?: UrlNormalizer.prettySite(raw.finalUrl),
            publishedAt = raw.published,
            normalizedUrl = canonical ?: current.normalizedUrl,
            lang = fin.lang,
            wordCount = fin.wordCount,
            excerpt = (raw.excerpt ?: fin.text.take(200)).take(300),
            thumb = fin.thumb,
            status = fin.status,
            error = if (fin.status == ClipStatus.PARTIAL) "Trang này có thể cần đăng nhập để xem toàn bộ." else null,
            sizeBytes = fin.sizeBytes,
        )
        repo.update(updated)
        if (settings.notifyOnSave) Notifications.clipped(applicationContext, id, title, fin.status == ClipStatus.PARTIAL)
        return Result.success()
    }

    private fun isMetered(): Boolean {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered ?: true
    }

    private fun reasonFor(e: Throwable?): String = when (e) {
        is HttpStatusException -> when (e.code) {
            401, 402, 403 -> "Trang này cần đăng nhập hoặc chặn truy cập tự động. Hãy mở trang trong app để đăng nhập, hoặc dán văn bản."
            404, 410 -> "Không tìm thấy trang (lỗi ${e.code})."
            429 -> "Trang đang giới hạn truy cập. Hãy thử lại sau."
            in 500..599 -> "Máy chủ của trang đang gặp lỗi (${e.code}). Hãy thử lại sau."
            else -> "Trang trả về lỗi ${e.code}."
        }
        is SocketTimeoutException, is InterruptedIOException -> "Trang phản hồi quá chậm."
        else -> "Không tìm thấy nội dung bài viết trên trang này. Bạn có thể xem bản gốc hoặc dán văn bản."
    }

    companion object {
        const val KEY_ID = "id"

        suspend fun enqueue(context: Context, id: Long) {
            val wifiOnly = SettingsRepo(context).current().wifiOnly
            val req = OneTimeWorkRequestBuilder<ClipWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("clip")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("clip-$id", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
