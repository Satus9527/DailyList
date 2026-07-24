// BootReceiver.kt
// 重启后补偿（规格 §4.4 / §5）。WorkManager 自身已持久化 Work、重启后自动重排；
// 此处再扫库重建未来 7 天 pending，作为数据库驱动的二次修复（如跨日窗口、完成与排程未对齐时）。

package com.dailyplan.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import com.dailyplan.app.DailyPlanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as DailyPlanApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.rescheduleAllPending()
            } finally {
                pending.finish()
            }
        }
    }
}
