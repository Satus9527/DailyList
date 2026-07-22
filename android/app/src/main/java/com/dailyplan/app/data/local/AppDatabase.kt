// AppDatabase.kt
// Room 数据库（规格 §7.4）。version = 1，v1 允许 destructive 迁移（规格 §9）。
// 损坏兜底在 DatabaseProvider 中处理（规格 §10）。

package com.dailyplan.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dailyplan.app.data.local.dao.CategoryDao
import com.dailyplan.app.data.local.dao.TagDao
import com.dailyplan.app.data.local.dao.TaskDao

@Database(
    entities = [
        TaskEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DB_NAME = "dailyplan.db"
    }
}
