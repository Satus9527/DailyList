// TaskMappers.kt
// TaskEntity ↔ 领域 Task 互转（规格 §7.3：DAO 仅暴露存储原语，Repository 负责映射）。

package com.dailyplan.app.data.local.mapper

import com.dailyplan.app.data.local.TaskEntity
import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.SyncState
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskSource
import java.util.UUID

fun TaskEntity.toDomain(): Task = Task(
    id = UUID.fromString(id),
    title = title,
    date = date,
    categoryId = categoryId?.let { UUID.fromString(it) },
    priority = Priority.from(priority),
    isDone = isDone,
    doneAt = doneAt,
    remindAt = remindAt,
    leadMinutes = leadMinutes,
    repeatCount = repeatCount,
    sortOrder = sortOrder,
    source = TaskSource.values().firstOrNull { it.raw == source } ?: TaskSource.TEXT,
    updatedAt = updatedAt,
    syncState = SyncState.values().firstOrNull { it.raw == syncState } ?: SyncState.LOCAL
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id.toString(),
    title = title,
    date = date,
    categoryId = categoryId?.toString(),
    priority = priority.raw,
    isDone = isDone,
    doneAt = doneAt,
    remindAt = remindAt,
    leadMinutes = leadMinutes,
    repeatCount = repeatCount,
    sortOrder = sortOrder,
    source = source.raw,
    updatedAt = updatedAt,
    syncState = syncState.raw
)
