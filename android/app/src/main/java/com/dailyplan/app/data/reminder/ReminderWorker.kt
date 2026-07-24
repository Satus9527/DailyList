// ReminderWorker.kt
// 单个触发点的执行体（规格 §4.2）。触发时先查 isDone：未完成才发通知，已完成则不发并结束。
// 这是「到点已完成、后续重复点不响」的 Android 端完整兜底（规格 §2.5）。

package com.dailyplan.app.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailyplan.app.DailyPlanApplication
import com.dailyplan.app.domain.model.Task
import java.util.UUID

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskIdStr = inputData.getString(ReminderContract.EXTRA_TASK_ID) ?: return Result.failure()
        val taskId = runCatching { UUID.fromString(taskIdStr) }.getOrElse { return Result.failure() }
        val kind = inputData.getString(ReminderContract.KEY_KIND) ?: return Result.failure()
        val repeatIndex = inputData.getInt(ReminderContract.KEY_REPEAT_INDEX, 0)

        // 手动 DI：经 Application 容器取得 Repository 与通知 Helper（无 Hilt）
        val app = applicationContext as DailyPlanApplication
        val repository = app.container.taskRepository
        val helper = app.container.reminderNotificationHelper

        // 读取最新任务，触发时查 isDone（抗竞态兜底；markDone 与排程未对齐时仍能阻止重复响）
        val task: Task = repository.get(taskId) ?: return Result.failure()
        if (task.isDone) {
            // 已完成：不发通知，直接结束
            return Result.success()
        }

        // 埋点调用点（仅标注，不写上报）：reminder_trigger / reminder_shown
        helper.showReminder(task, kind, repeatIndex)
        return Result.success()
    }
}
