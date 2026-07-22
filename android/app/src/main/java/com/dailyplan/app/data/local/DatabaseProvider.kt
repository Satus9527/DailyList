// DatabaseProvider.kt
// 数据库单例 + 损坏兜底（规格 §10.1 / §10.3 / §10.4）。
// 打开/迁移抛异常 → 记本地日志 → 删除损坏库 → 重建空库，保证 App 不崩溃。

package com.dailyplan.app.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile private var INSTANCE: AppDatabase? = null

    /** 获取（必要时创建）数据库。首次打开即触发损坏检测与重建（规格 §10）。 */
    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            var db = build(context)
            try {
                // 真正触发 SQLite 打开，捕获损坏/迁移失败
                db.openHelper.writableDatabase
            } catch (e: Exception) {
                // —— 损坏兜底：记日志 + 删库 + 重建空库（不崩溃）——
                DBErrorLogger.log(context, "open_failed", e)
                context.deleteDatabase(AppDatabase.DB_NAME)
                DBErrorLogger.log(context, "corrupt_detected",
                    RuntimeException("rebuilt empty db"))
                db = build(context)
                // 极端（如磁盘满）：仍捕获不向上抛（规格 §10.3 降级内存态）
                runCatching { db.openHelper.writableDatabase }
            }
            INSTANCE = db
            db
        }
    }

    private fun build(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DB_NAME
        )
            // v1 允许 destructive 迁移（规格 §9）
            .fallbackToDestructiveMigration()
            .build()
    }
}
