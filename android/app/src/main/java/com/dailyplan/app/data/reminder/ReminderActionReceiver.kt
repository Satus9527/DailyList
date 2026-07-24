// ReminderActionReceiver.kt
// 通知 Action 广播接收器（规格 §4.3）：
// - ACTION_COMPLETE → markDone + cancel 后续（完成即取消，AC-9 / R-E7）
// - ACTION_SNOOZE   → scheduler.snooze（推迟 10 分钟）
// 使用 goAsync + 协程，确保 suspend 调用在广播回收前完成。

package com.dailyplan.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailyplan.app.DailyPlanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskIdStr = intent.getStringExtra(ReminderContract.EXTRA_TASK_ID) ?: return
        val taskId = runCatching { UUID.fromString(taskIdStr) }.getOrNull() ?: return

        val app = context.applicationContext as DailyPlanApplication
        val repository = app.container.taskRepository
        val scheduler = app.container.reminderScheduler

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderContract.ACTION_COMPLETE -> {
                        runCatching { repository.markDone(taskId, Date()) }
                        scheduler.cancel(taskId)   // 完成即取消后续（AC-9）
                        // 埋点调用点（仅标注）：reminder_complete
                    }
                    ReminderContract.ACTION_SNOOZE -> {
                        scheduler.snooze(taskId)   // 清旧建新，推迟 10 分钟
                        // 埋点调用点（仅标注）：reminder_dismiss
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
