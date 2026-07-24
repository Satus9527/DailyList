// LocalTaskRepository.kt
// TaskRepository 的 Room 实现（规格 §5 / §7）。所有写操作单事务提交（规格 §1.7，F6）。

package com.dailyplan.app.data.repository

import com.dailyplan.app.data.local.AppDatabase
import com.dailyplan.app.data.local.dao.IdOrder
import com.dailyplan.app.data.local.mapper.toDomain
import com.dailyplan.app.data.local.mapper.toEntity
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.todayDateString
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
}
