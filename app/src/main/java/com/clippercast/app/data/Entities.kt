package com.clippercast.app.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/** Trạng thái tải của một bài (spec 2.1.3). */
object ClipStatus {
    const val PENDING = 0        // Chờ tải
    const val LOADING = 1        // Đang tải
    const val READY = 2          // Sẵn sàng
    const val PARTIAL = 3        // Có thể chưa đầy đủ
    const val FAILED = 4         // Lỗi
    const val LINK_ONLY = 5      // Chỉ có link

    fun isReadable(s: Int) = s == READY || s == PARTIAL
}

object ReadState {
    const val UNREAD = 0
    const val READING = 1
    const val READ = 2
}

/**
 * Thông tin hiển thị trong danh sách. Nội dung HTML nằm ở bảng riêng [ArticleContent]
 * để truy vấn danh sách luôn nhẹ.
 */
@Entity(tableName = "articles", indices = [Index("normalizedUrl"), Index("savedAt")])
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val normalizedUrl: String,
    val title: String,
    val author: String? = null,
    val siteName: String,
    val publishedAt: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val lang: String? = null,
    val wordCount: Int = 0,
    val excerpt: String? = null,
    val thumb: String? = null,
    val status: Int = ClipStatus.PENDING,
    val error: String? = null,
    val readState: Int = ReadState.UNREAD,
    val archived: Boolean = false,
    val favorite: Boolean = false,
    val deletedAt: Long? = null,
    val readProgress: Float = 0f,
    val listenSentence: Int = 0,
    val tags: String = "",
    val sizeBytes: Long = 0,
    val isNote: Boolean = false,
)

@Entity(tableName = "article_content")
data class ArticleContent(
    @PrimaryKey val articleId: Long,
    val html: String,
    /** Tiêu đề + nội dung đã bỏ dấu, viết thường: dùng cho tìm kiếm không dấu. */
    val searchText: String,
)

@Fts4(contentEntity = ArticleContent::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "content_fts")
data class ContentFts(val searchText: String)

@Entity(tableName = "queue")
data class QueueItem(
    @PrimaryKey val articleId: Long,
    val position: Double,
)
