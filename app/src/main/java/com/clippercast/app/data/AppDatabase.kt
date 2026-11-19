package com.clippercast.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Article::class, ArticleContent::class, ContentFts::class, QueueItem::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articles(): ArticleDao
    abstract fun contents(): ContentDao
    abstract fun queue(): QueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "clippercast.db")
                .build().also { instance = it }
        }
    }
}
