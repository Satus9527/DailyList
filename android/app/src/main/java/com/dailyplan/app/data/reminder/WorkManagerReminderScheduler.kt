// WorkManagerReminderScheduler.kt
// ReminderScheduler 的 Android 实现（规格 §4.1）。基于 WorkManager 为每个触发点创建 OneTimeWorkRequest，
// 以 setInitialDelay 到绝对触发时刻；tag = taskId 便于批量取消。异常在方法内捕获，绝不致 App 崩溃（P0-3 精神）。

package com.dailyplan.app.data.reminder

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.domain.model.Task
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    private val context: Context,
    private val workManager: WorkManager,
    private val repository: TaskRepository
) : ReminderScheduler {

    /**
     * 触发点生成（平台无关算法 Kotlin 表达，规格 §2.2）。
     * - L>0 → 提前点 T-L
     * - 总是 → 到点 T
     * - R>0 → 重复点 T+i×10 分钟（固定 10 分钟间隔，P0-2）
     * L=0 不生成提前点；R=0 不生成重复点（AC-11）。
     */
    private fun buildTriggerPoints(task: Task): List<TriggerPoint> {
        val t = task.remindAt ?: return emptyList()   // 未设提醒 → 无触发点
        val pts = mutableListOf<TriggerPoint>()
        if (task.leadMinutes > 0) {
            pts += TriggerPoint(
                task.id, TriggerKind.LEAD, 0,
                Date(t.time - task.leadMinutes * 60_000L)
            )
        }
        pts += TriggerPoint(task.id, TriggerKind.AT, 0, t)
        if (task.repeatCount > 0) {
            for (i in 1..task.repeatCount) {
                pts += TriggerPoint(
                    task.id, TriggerKind.REPEAT, i,
                    Date(t.time + i * 10 * 60_000L)
                )
            }
        }
        return pts.sortedBy { it.fireAt.time }
    }

    override fun schedule(task: Task) {
        if (task.remindAt == null) return
        // 先清旧（同 taskId tag 全部移除），再登记 → 幂等，改期/取消完成均安全（规格 §4.1 / §6.4）
        cancel(task.id)
        val now = System.currentTimeMillis()
        for (pt in buildTriggerPoints(task)) {
            // 守卫：跳过已过期触发点（fireAt 已 <= now）。补偿重排（rescheduleAllPending）复用同一 schedule，
            // 故窗口 (T-lead, T) 内重新打开时，已过 T-lead 不会被重排成立即触发的 Work，仅排未来点（T/重复点）。
            if (pt.fireAt.time <= now) continue
            // 延迟从当前时刻算到绝对触发时刻（此处 delay 恒 > 0，因过期点已在上方跳过）
            val delay = (pt.fireAt.time - now).coerceAtLeast(0L)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)   // 绝对延迟，规避相对倒计时漂移
                .addTag(pt.tag)                                   // tag = taskId，便于 cancelAllWorkByTag
                .setInputData(
                    workDataOf(
                        ReminderContract.EXTRA_TASK_ID to pt.taskId.toString(),
                        ReminderContract.KEY_KIND to pt.kind.name,
                        ReminderContract.KEY_REPEAT_INDEX to pt.repeatIndex
                    )
                )
                .build()
            workManager.enqueue(req)
        }
        // 埋点调用点（仅标注，不写上报）：reminder_set
    }

    override fun cancel(taskId: UUID) {
        // 取消该任务全部未触发 Work（含 lead/at/rep/snooze，统一 tag = taskId）
        runCatching { workManager.cancelAllWorkByTag(taskId.toString()) }
    }

    override fun snooze(taskId: UUID) {
        // 清旧 snooze（同 tag）后建新 __snooze，保证唯一有效实例（规格 §2.4）
        cancel(taskId)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(10 * 60_000L, TimeUnit.MILLISECONDS)   // 推迟 10 分钟
            .addTag(taskId.toString())
            .setInputData(
                workDataOf(
                    ReminderContract.EXTRA_TASK_ID to taskId.toString(),
                    ReminderContract.KEY_KIND to TriggerKind.SNOOZE.name,
                    ReminderContract.KEY_REPEAT_INDEX to 0
                )
            )
            .build()
        runCatching { workManager.enqueue(req) }
        // 埋点调用点（仅标注）：reminder_dismiss（推迟）
    }

    override fun rescheduleAllPending() {
        val horizon = Date(
            System.currentTimeMillis()
                + ReminderContract.REMINDER_SCAN_HORIZON_DAYS * 24L * 3600_000L
        )
        // 扫描未来 7 天未完成且有提醒的待办，逐条重建（补偿 Doze / 重启 / 系统清理丢失，规格 §4.4 / §5）
        runBlocking {
            runCatching { repository.tasksWithPendingReminders(horizon) }
                .onSuccess { tasks ->
                    // schedule 内含 cancel（按 taskId tag），天然幂等去重，不会重复排
                    tasks.forEach { schedule(it) }
                }
        }
    }
}
