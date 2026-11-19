package com.clippercast.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Insert
    suspend fun insert(a: Article): Long

    @Update
    suspend fun update(a: Article)

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun get(id: Long): Article?

    @Query("SELECT * FROM articles WHERE id = :id")
    fun observe(id: Long): Flow<Article?>

    @Query("SELECT * FROM articles WHERE normalizedUrl = :u AND deletedAt IS NULL LIMIT 1")
    suspend fun findByUrl(u: String): Article?

    @Query("SELECT * FROM articles WHERE normalizedUrl = :u AND deletedAt IS NULL AND id != :exceptId LIMIT 1")
    suspend fun findDuplicate(u: String, exceptId: Long): Article?

    @Query(
        """SELECT * FROM articles WHERE deletedAt IS NULL AND archived = 0
        AND (:favOnly = 0 OR favorite = 1)
        AND (:problemOnly = 0 OR status IN (0,1,3,4))
        ORDER BY
          CASE WHEN :sort = 0 THEN savedAt END DESC,
          CASE WHEN :sort = 1 THEN savedAt END ASC,
          CASE WHEN :sort = 2 THEN wordCount END ASC,
          CASE WHEN :sort = 3 THEN wordCount END DESC,
          CASE WHEN :sort = 4 THEN siteName END ASC,
          savedAt DESC"""
    )
    fun inbox(sort: Int, favOnly: Boolean, problemOnly: Boolean): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE deletedAt IS NULL AND archived = 1 AND (:favOnly = 0 OR favorite = 1) ORDER BY savedAt DESC")
    fun archive(favOnly: Boolean): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun trash(): Flow<List<Article>>

    @Query("SELECT COUNT(*) FROM articles WHERE deletedAt IS NULL AND archived = 0")
    fun activeCount(): Flow<Int>

    @Query("SELECT * FROM articles WHERE status IN (0,4) AND deletedAt IS NULL")
    suspend fun retryable(): List<Article>

    @Query("SELECT * FROM articles")
    suspend fun all(): List<Article>

    @Query("SELECT * FROM articles WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashOlderThan(before: Long): List<Article>

    @Query("SELECT * FROM articles WHERE archived = 1 AND favorite = 0 AND deletedAt IS NULL AND savedAt < :before")
    suspend fun archivedOlderThan(before: Long): List<Article>

    @Query("SELECT * FROM articles WHERE archived = 1 AND deletedAt IS NULL")
    suspend fun archivedAll(): List<Article>

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM articles")
    fun totalSize(): Flow<Long>

    @Query(
        """SELECT a.* FROM articles a JOIN content_fts f ON a.id = f.rowid
        WHERE content_fts MATCH :match AND a.deletedAt IS NULL ORDER BY a.savedAt DESC LIMIT 200"""
    )
    fun search(match: String): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE deletedAt IS NULL AND (title LIKE :like OR siteName LIKE :like) ORDER BY savedAt DESC LIMIT 200")
    fun searchTitle(like: String): Flow<List<Article>>
}

@Dao
interface ContentDao {
    @Upsert
    suspend fun upsert(c: ArticleContent)

    @Query("SELECT * FROM article_content WHERE articleId = :id")
    suspend fun get(id: Long): ArticleContent?

    @Query("DELETE FROM article_content WHERE articleId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface QueueDao {
    @Query("SELECT a.* FROM articles a JOIN queue q ON a.id = q.articleId WHERE a.deletedAt IS NULL ORDER BY q.position ASC")
    fun observeQueue(): Flow<List<Article>>

    @Query("SELECT q.articleId FROM queue q JOIN articles a ON a.id = q.articleId WHERE a.deletedAt IS NULL ORDER BY q.position ASC")
    suspend fun ids(): List<Long>

    @Query("SELECT MIN(position) FROM queue")
    suspend fun minPos(): Double?

    @Query("SELECT MAX(position) FROM queue")
    suspend fun maxPos(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(q: QueueItem)

    @Query("DELETE FROM queue WHERE articleId = :id")
    suspend fun remove(id: Long)

    @Query("SELECT COUNT(*) FROM queue WHERE articleId = :id")
    suspend fun contains(id: Long): Int
}
