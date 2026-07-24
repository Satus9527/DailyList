// LocalTaskRepository.kt
// TaskRepository 的 Room 实现（规格 §5 / §7）。所有写操作单事务提交（规格 §1.7，F6）。

package com.dailyplan.app.data.repository

import com.dailyplan.app.data.local.AppDatabase
import com.dailyplan.app.data.local.TagEntity
import com.dailyplan.app.data.local.dao.IdOrder
import com.dailyplan.app.data.local.mapper.toDomain
import com.dailyplan.app.data.local.mapper.toEntity
import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskFilter
import com.dailyplan.app.domain.model.matches
import com.dailyplan.app.domain.model.todayDateString
import com.dailyplan.app.util.CategorySeed
import java.util.Date
import java.util.UUID

class LocalTaskRepository(private val db: AppDatabase) : TaskRepository {

    private val dao get() = db.taskDao()

    override suspend fun todayTasks(): List<Task> {
        return dao.todayTasks(todayDateString()).map { it.toDomain() }
    }

    override suspend fun add(task: Task) {
        // 单事务提交（Room @Insert 自身原子；规格 §1.7）
        dao.insert(task.toEntity())
    }

    override suspend fun update(task: Task) {
        // 写操作更新 updatedAt（规格 §1.6）
        dao.update(task.copy(updatedAt = Date()).toEntity())
    }

    override suspend fun markDone(id: UUID, at: Date) {
        dao.markDone(id.toString(), at, Date())
    }

    override suspend fun delete(id: UUID) {
        // 关系 CASCADE 级联删 Tag 关联（规格 §4.2）
        dao.deleteById(id.toString())
    }

    override suspend fun get(id: UUID): Task? =
        dao.getById(id.toString())?.toDomain()   // 未找到返回 null（规格 §4.2）

    override suspend fun reorder(ids: List<UUID>) {
        // 单事务提交整组重排（@Transaction，规格 §7.3）
        val orders = ids.mapIndexed { index, id -> IdOrder(id.toString(), index) }
        dao.reorder(orders)
    }

    override suspend fun tasksWithPendingReminders(until: Date): List<Task> {
        val now = Date()
        return dao.tasksWithPendingReminders(now, until).map { it.toDomain() }
    }

    override suspend fun tasksByDisplayDay(day: String): List<Task> {
        // 按展示日取数（跨 0 点任务并入），含已完成（置底由 ORDER BY is_done 控制）
        return dao.tasksByDisplayDay(day).map { it.toDomain() }
    }

    // MARK: - M5 F4 筛选 / 标签读写（规格 §3.2 / §3.3）

    override suspend fun filteredTasks(date: String, filter: TaskFilter): List<Task> {
        val ents = dao.tasksByDateCategoryPriority(
            day = date,
            cat = filter.categoryId?.toString(),
            otherId = CategorySeed.OTHER_ID.toString(),
            prio = filter.priority?.raw
        )
        val map = taskTagIds()
        return ents.map { it.toDomain() }
            .filter { filter.matches(it, map[it.id] ?: emptySet()) }
    }

    override suspend fun tasksByCategory(categoryId: UUID, date: String): List<Task> =
        filteredTasks(date, TaskFilter(categoryId = categoryId))

    override suspend fun tasksByPriority(priority: Priority, date: String): List<Task> =
        filteredTasks(date, TaskFilter(priority = priority))

    override suspend fun tasksByTags(tagIds: Set<UUID>, date: String): List<Task> =
        filteredTasks(date, TaskFilter(tagIds = tagIds))

    override suspend fun suggestTags(prefix: String, limit: Int): List<TagEntity> {
        // 联想前缀先归一（与写入同口径，规格 §5.1）
        val norm = com.dailyplan.app.util.TagNormalizer.normalize(prefix)
        return dao.suggestTags(norm, limit)
    }

    override suspend fun tagsForTask(id: UUID): List<TagEntity> =
        dao.tagsForTask(id.toString())

    override suspend fun setTags(taskId: UUID, tagIds: Set<UUID>) {
        // tagIds 应为归一后的 Tag.id 集合（由 TagRepository.addOrReuse 得到），仅写关联表
        dao.setTags(taskId.toString(), tagIds.map { it.toString() })
    }

    override suspend fun taskTagIds(): Map<UUID, Set<UUID>> {
        // 一次性读取全量关联，归并为 taskId → tagIds 集合（内存过滤/展示用）
        return dao.allCrossRefs()
            .groupBy(
                keySelector = { UUID.fromString(it.taskId) },
                valueTransform = { UUID.fromString(it.tagId) }
            )
            .mapValues { it.value.toSet() }
    }
}
