// TaskMergeSplitUseCase.kt
// 长按合并/拆分领域用例（规格 M3 §7 / M4 R-4，AC-5）。复用 M1 TaskRepository 与 M2 ReminderScheduler，
// 不新增任何 Task 字段，合并/拆分结果经 repository.update/add/delete 落库（重启保持，AC-5）。
//
// 合并规则（M3 §7.2）：保留参与各条最早 date、最前 sortOrder；title 用「、」连接；
//   remindAt 取合并中首个有提醒者；isDone 取「全完成才完成」。
// 拆分规则（M3 §7.2）：在第 at 个字符处断开；断点为 splitPunctuation 中标点时，标点归属前段并丢弃（不进后段）；
//   两段 source 不变；前段带走原 id 与 remindAt，后段新 id、同 date、相邻 sortOrder（sortOrder+1）。
// 标点集取自 ASRSplitConfig（禁止硬编码，P0-4 防漂移）；config 为 null 时按普通字符断开。

package com.dailyplan.app.domain.voice

import com.dailyplan.app.data.reminder.ReminderScheduler
import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.util.ASRSplitConfig
import java.util.Date
import java.util.UUID

interface TaskMergeSplitUseCase {
    /** 合并相邻多条为一条（至少 2 条）。埋点调用点：todo_merge（仅标注） */
    suspend fun merge(tasks: List<Task>)

    /** 在 task.title 的第 at 个字符处断开为两条。埋点调用点：todo_split（仅标注） */
    suspend fun split(task: Task, at: Int)
}

class TaskMergeSplitUseCaseImpl(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val splitConfig: ASRSplitConfig? = null
) : TaskMergeSplitUseCase {

    // 拆分断点标点判断集（仅取自 config，禁止硬编码，P0-4）
    private val punctuationSet: Set<String> = splitConfig?.splitPunctuation?.toSet() ?: emptySet()

    override suspend fun merge(tasks: List<Task>) {
        if (tasks.size < 2) return   // 至少两条相邻才可合并

        // 按 date、sortOrder 升序确定「主条」（最早/最前），其余为被并条
        val ordered = tasks.sortedWith(compareBy({ it.date }, { it.sortOrder }))
        val primary = ordered.first()
        val earliestDate = ordered.minOf { it.date }
        val minSortOrder = ordered.minOf { it.sortOrder }
        val title = ordered.joinToString("、") { it.title.trim() }   // 「、」连接（P0-4 不切分语义）
        val remindAt = ordered.firstNotNullOfOrNull { it.remindAt }  // 首个有提醒者
        val allDone = ordered.all { it.isDone }                       // 全完成才完成

        // 被并条目：取消其提醒 + 删除
        for (t in ordered.drop(1)) {
            reminderScheduler.cancel(t.id)
            repository.delete(t.id)
        }

        // 主条更新为合并结果（保留主条 id，不写新 date——沿用计算出的最早 date）
        val merged = primary.copy(
            title = title,
            date = earliestDate,
            sortOrder = minSortOrder,
            remindAt = remindAt,
            isDone = allDone,
            doneAt = if (allDone) ordered.firstNotNullOfOrNull { it.doneAt } ?: Date() else null,
            updatedAt = Date()
        )
        repository.update(merged)
        // 提醒联动：合并后提醒随主条；无提醒则清掉残留
        if (remindAt != null) reminderScheduler.schedule(merged) else reminderScheduler.cancel(merged.id)
    }

    override suspend fun split(task: Task, at: Int) {
        val title = task.title
        if (at <= 0 || at >= title.length) return   // 越界忽略（R-X4 精神，不生成空条）

        // 断点若为 splitPunctuation 标点：标点归属前段并丢弃（不进后段 title）
        val (frontRaw, backRaw) = if (at < title.length && punctuationSet.contains(title[at].toString())) {
            title.take(at) to title.drop(at + 1)
        } else {
            title.take(at) to title.drop(at)
        }
        val front = frontRaw.trim()
        val back = backRaw.trim()
        if (front.isEmpty() || back.isEmpty()) return   // 空段忽略（R-X4）

        // 前段带走原条 id 与 remindAt；后段新 id、同 date、相邻 sortOrder（+1）
        val updatedFront = task.copy(title = front, updatedAt = Date())
        val newBack = task.copy(
            id = UUID.randomUUID(),
            title = back,
            sortOrder = task.sortOrder + 1,
            remindAt = null,   // 后段无提醒（M3 §7.2）
            updatedAt = Date()
        )
        repository.update(updatedFront)
        repository.add(newBack)
        // 提醒随前段；后段无提醒
        if (task.remindAt != null) reminderScheduler.schedule(updatedFront)
    }
}
