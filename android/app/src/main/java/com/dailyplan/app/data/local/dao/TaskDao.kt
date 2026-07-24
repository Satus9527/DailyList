// TaskDao.kt
// Task DAO（规格 §7.3，含全部 7 方法对应的存储原语）。

package com.dailyplan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dailyplan.app.data.local.TaskEntity
import java.util.Date

@Dao
interface TaskDao {
    // F1/F5/F6：当天列表，进行中在上、已完成置底，同组按 sortOrder 升序（规格 §5.1）
    @Query("""
        SELECT * FROM task
        WHERE date = :today
        ORDER BY is_done ASC, sort_order ASC
    """)
    suspend fun todayTasks(today: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    // F5：标记完成（规格 §5.1）
    @Query("""
        UPDATE task
        SET is_done = 1, done_at = :at, updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun markDone(id: String, at: Date, updatedAt: Date)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteById(id: String)

    // F3：按 id 读取单条（规格 §4.2 触发时查 isDone）
    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    // F5：拖拽重排（@Transaction 保证原子，规格 §5.1 / §7.3）
    @Transaction
    suspend fun reorder(orders: List<IdOrder>) {
        orders.forEach { updateSortOrder(it.id, it.sortOrder) }
    }

    @Query("UPDATE task SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    // F3 扫描：未完成 + 有 remindAt + remindAt ∈ [now, until]，按 remindAt 升序（规格 §5.3）
    @Query("""
        SELECT * FROM task
        WHERE is_done = 0
          AND remind_at IS NOT NULL
          AND remind_at >= :now
          AND remind_at <= :until
        ORDER BY remind_at ASC
    """)
    suspend fun tasksWithPendingReminders(now: Date, until: Date): List<TaskEntity>
}

/** 重排参数载体（id + 新 sortOrder） */
data class IdOrder(val id: String, val sortOrder: Int)
