// TaskEntity.kt
// Task 实体（规格 §7.2）。字段类型严格对应设计规格；枚举以 String(raw) 持久化。

package com.dailyplan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title", defaultValue = "") val title: String,
    @ColumnInfo(name = "date") val date: String,                 // yyyy-MM-dd
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "priority", defaultValue = "medium") val priority: String,
    @ColumnInfo(name = "is_done", defaultValue = "0") val isDone: Boolean = false,
    @ColumnInfo(name = "done_at") val doneAt: Date? = null,
    @ColumnInfo(name = "remind_at") val remindAt: Date? = null,
    @ColumnInfo(name = "lead_minutes", defaultValue = "10") val leadMinutes: Int = 10,
    @ColumnInfo(name = "repeat_count", defaultValue = "3") val repeatCount: Int = 3,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "source", defaultValue = "text") val source: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Date,
    @ColumnInfo(name = "sync_state", defaultValue = "local") val syncState: String = "local"
)
