// TaskRepository.kt
// Repository 接口（平台无关，规格 §5.2）。实现见 LocalTaskRepository（Room）。

package com.dailyplan.app.data.repository

import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskFilter
import com.dailyplan.app.data.local.TagEntity
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

    // —— M5 F4 组织能力（规格 §3.2，复用 M1 字段与 TaskTagCrossRef，不重定义实体）——

    /** 组合筛选（AND）；作用于展示日 date（默认当日）。空条件 = 全部（§6）。 */
    suspend fun filteredTasks(date: String, filter: TaskFilter): List<Task>

    /** 单维：按分类（「其他」预设 id 含 categoryId==null 任务，§6） */
    suspend fun tasksByCategory(categoryId: UUID, date: String): List<Task>

    /** 单维：按优先级 */
    suspend fun tasksByPriority(priority: Priority, date: String): List<Task>

    /** 单维：按标签（AND 多个） */
    suspend fun tasksByTags(tagIds: Set<UUID>, date: String): List<Task>

    /** 标签联想补全：返回 name 以归一后前缀开头的已有标签（上限 limit，规格 §3.3） */
    suspend fun suggestTags(prefix: String, limit: Int): List<TagEntity>

    /** 读取某任务标签（§2.4，经 TaskTagCrossRef 联表） */
    suspend fun tagsForTask(id: UUID): List<TagEntity>

    /** 整体替换某任务标签关联（§2.4；传入归一后的 Tag.id 集合） */
    suspend fun setTags(taskId: UUID, tagIds: Set<UUID>)

    /** 批量读取 taskId → 标签 id 集合 映射（内存筛选用，规格 §3.4） */
    suspend fun taskTagIds(): Map<UUID, Set<UUID>>
}
