// TaskRepository.kt
// Repository 接口（平台无关，规格 §5.2）。实现见 LocalTaskRepository（Room）。

package com.dailyplan.app.data.repository

import com.dailyplan.app.domain.model.Task
import java.util.Date
import java.util.UUID

interface TaskRepository {
    /** 当天全部待办，排序见 §5.1（进行中在上、已完成置底、同组 sortOrder 升序） */
    suspend fun todayTasks(): List<Task>

    /** 新增一条 */
    suspend fun add(task: Task)

    /** 全量更新一条 */
    suspend fun update(task: Task)

    /** 标记完成（规格 §5.1） */
    suspend fun markDone(id: UUID, at: Date)

    /** 删除一条（级联删 Tag 关联） */
    suspend fun delete(id: UUID)

    /** 按 id 读取单条（F3 触发时查 isDone 用，规格 §4.2；未找到返回 null） */
    suspend fun get(id: UUID): Task?

    /** 重排：ids 顺序即新 sortOrder（规格 §5.1） */
    suspend fun reorder(ids: List<UUID>)

    /** 扫描 remindAt ∈ [now, until] 且未完成的待办（规格 §5.3，支撑 F3） */
    suspend fun tasksWithPendingReminders(until: Date): List<Task>

    /**
     * 按「展示日」取当日全部待办（规格 §3.3 / S5，仅展示层重归类，不改动 date 存储）：
     * 返回 date == day 的任务，以及 remindAt 所属本地日 == day 但 date != day 的跨 0 点任务。
     * 排序沿用 M1（进行中在上、已完成置底、同组 sortOrder 升序）。
     */
    suspend fun tasksByDisplayDay(day: String): List<Task>
}
