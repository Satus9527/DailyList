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
import com.dailyplan.app.data.local.TaskTagCrossRef
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

    // S5 跨日归类（规格 §3.3 / 对齐 iOS Task.displayDay）：按展示日取数，不改动 date 存储。
    // remind_at 经 Converters 存为毫秒 INTEGER；date(remind_at/1000,'unixepoch','localtime') 得到本地日 yyyy-MM-dd。
    // 展示日规则（与 domain displayDay 一致）：
    //   跨 0 点任务（remind_at 所属日 ≠ date）仅并入 remind_at 所属日（:day）；
    //   普通任务（remind_at 为空，或 remind_at 所属日 = date）按 date 并入。
    // 故归属某 :day 的条件为：
    //   (date = :day 且「不属于跨 0 点任务」)  —— 普通任务归 date 当日；
    //   OR (remind_at 不为空 且 remind_at 所属日 ≠ date 且 remind_at 所属日 = :day) —— 跨 0 点任务仅归触发日。
    @Query("""
        SELECT * FROM task
        WHERE (date = :day
               AND NOT (remind_at IS NOT NULL
                        AND date(remind_at / 1000, 'unixepoch', 'localtime') <> date))
           OR (remind_at IS NOT NULL
               AND date(remind_at / 1000, 'unixepoch', 'localtime') <> date
               AND date(remind_at / 1000, 'unixepoch', 'localtime') = :day)
        ORDER BY is_done ASC, sort_order ASC
    """)
    suspend fun tasksByDisplayDay(day: String): List<TaskEntity>

    // MARK: - M5 F4 筛选（规格 §3.3）

    /**
     * 组合筛选基础查询：按展示日 + 分类 + 优先级。
     * - 分类：传 null = 不限；传「其他」预设 id（:otherId）时额外包含 category_id IS NULL 的任务（§6）。
     * - 优先级：传 null = 不限。
     * 标签 AND 语义与 untaggedOnly 由 Repository 层用 TaskTagCrossRef 内存判定（见 LocalTaskRepository）。
     */
    @Query("""
        SELECT * FROM task
        WHERE date = :day
          AND (:cat IS NULL OR category_id = :cat
               OR (:cat = :otherId AND category_id IS NULL))
          AND (:prio IS NULL OR priority = :prio)
        ORDER BY is_done ASC, sort_order ASC
    """)
    suspend fun tasksByDateCategoryPriority(
        day: String,
        cat: String?,
        otherId: String,
        prio: String?
    ): List<TaskEntity>

    /** 全部 Task↔Tag 关联行（规格 §2.4，供内存映射 taskId → tagIds 集合） */
    @Query("SELECT task_id, tag_id FROM task_tag")
    suspend fun allCrossRefs(): List<TaskTagCrossRef>

    /** 读取某任务标签（JOIN task_tag，规格 §3.3） */
    @Query("""
        SELECT tg.* FROM tag tg
        JOIN task_tag tt ON tt.tag_id = tg.id
        WHERE tt.task_id = :taskId
        ORDER BY tg.name ASC
    """)
    suspend fun tagsForTask(taskId: String): List<com.dailyplan.app.data.local.TagEntity>

    /** 标签联想补全：name 已归一（小写/半角），前缀须先归一（规格 §3.3） */
    @Query("""
        SELECT * FROM tag
        WHERE name LIKE :normalizedPrefix || '%'
        ORDER BY name ASC
        LIMIT :limit
    """)
    suspend fun suggestTags(normalizedPrefix: String, limit: Int): List<com.dailyplan.app.data.local.TagEntity>

    // —— M5 setTags：@Transaction 内先删旧关联，再批量插入（规格 §3.3）——
    @Transaction
    suspend fun setTags(taskId: String, tagIds: List<String>) {
        deleteCrossRefs(taskId)
        if (tagIds.isNotEmpty()) {
            insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId, it) })
        }
    }

    @Query("DELETE FROM task_tag WHERE task_id = :taskId")
    suspend fun deleteCrossRefs(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<TaskTagCrossRef>)
}

/** 重排参数载体（id + 新 sortOrder） */
data class IdOrder(val id: String, val sortOrder: Int)
