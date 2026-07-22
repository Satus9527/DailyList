// Task.kt
// 纯领域模型（与 Room 解耦），作为 Repository 返回类型。
// 由 TaskEntity 映射而来，UI / ViewModel 只认这个类型（架构 §7.2）。

package com.dailyplan.app.domain.model

import com.dailyplan.app.util.CategorySeed
import java.util.Date
import java.util.UUID

data class Task(
    val id: UUID,
    val title: String,
    val date: String,              // "yyyy-MM-dd"，本地时区当日（规格 §1.2）
    val categoryId: UUID?,
    val priority: Priority,
    val isDone: Boolean,
    val doneAt: Date?,
    val remindAt: Date?,
    val leadMinutes: Int,          // 默认 10（P0-2）
    val repeatCount: Int,          // 默认 3（P0-2），0=关闭重复
    val sortOrder: Int,
    val source: TaskSource,
    val updatedAt: Date,
    val syncState: SyncState
) {
    companion object {
        /**
         * 构造一条新待办（F1 文字记录默认参数）。
         * 默认：date=今天、categoryId=「其他」预设、priority=中、source=.text、
         * leadMinutes=10、repeatCount=3、syncState=.local、updatedAt=now。
         */
        fun makeNew(
            title: String,
            date: String = todayDateString(),
            categoryId: UUID? = CategorySeed.OTHER_ID,
            priority: Priority = Priority.MEDIUM,
            source: TaskSource = TaskSource.TEXT,
            sortOrder: Int = 0
        ): Task = Task(
            id = UUID.randomUUID(),
            title = title,
            date = date,
            categoryId = categoryId,
            priority = priority,
            isDone = false,
            doneAt = null,
            remindAt = null,
            leadMinutes = 10,
            repeatCount = 3,
            sortOrder = sortOrder,
            source = source,
            updatedAt = Date(),
            syncState = SyncState.LOCAL
        )
    }
}

/** 设备本地时区的当日 "yyyy-MM-dd"（规格 §1.2） */
fun todayDateString(date: Date = Date()): String {
    val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    df.timeZone = java.util.TimeZone.getDefault()   // 设备本地时区（规格 §1.1）
    return df.format(date)
}
