// ReminderNotificationHelper.kt
// 通知渠道创建 + 提醒通知展示（规格 §4.3）。含点击跳转 App、标记完成 / 推迟 10 分钟两个 Action。
// DND 绕过仅在已获 ACCESS_NOTIFICATION_POLICY 授权时开启；未授权仅栏显、不响铃（规格 §4.5 兜底）。

package com.dailyplan.app.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dailyplan.app.MainActivity
import com.dailyplan.app.R
import com.dailyplan.app.domain.model.Task

class ReminderNotificationHelper(private val context: Context) {

    /** 创建通知渠道 channel_reminder（HIGH + 声音/振动）。在 Application 启动时调用一次。 */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return   // O 以下无渠道概念
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ReminderContract.CHANNEL_ID,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH   // 重要 + 声音/振动
        ).apply {
            description = "每日计划待办提醒（重要）"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        mgr.createNotificationChannel(channel)
    }

    /**
     * 展示一条提醒通知。
     * @param kind 触发点类型（LEAD/AT/REPEAT/SNOOZE），仅用于埋点/文案扩展
     */
    fun showReminder(task: Task, kind: String, repeatIndex: Int = 0) {
        // 通知被禁检测（R-S4 / AC-20）：用户关了通知则不发，靠 App 内列表兜底
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        // 点击通知 → 跳转 App 首页，并带 taskId（供 UI 定位/高亮）
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderContract.EXTRA_TASK_ID, task.id.toString())
        }
        val contentPi = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ReminderContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("待办提醒")
            .setContentText(task.title)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .addAction(makeCompleteAction(task))
            .addAction(makeSnoozeAction(task))

        // DND 绕过（规格 §4.5）：仅当已获授权才 setBypassDnd；
        // 未授权时仅栏显、不响铃（系统 DND 静音），App 内列表兜底。
        if (DndPolicyHelper.isBypassDndGranted(context)) {
            builder.setBypassDnd(true)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(task.id.hashCode(), builder.build())
    }

    /** 「标记完成」Action → 广播 ReminderActionReceiver（ACTION_COMPLETE） */
    private fun makeCompleteAction(task: Task): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderContract.ACTION_COMPLETE
            putExtra(ReminderContract.EXTRA_TASK_ID, task.id.toString())
        }
        val pi = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() xor 0x1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "标记完成",
            pi
        ).build()
    }

    /** 「推迟10分钟」Action → 广播 ReminderActionReceiver（ACTION_SNOOZE） */
    private fun makeSnoozeAction(task: Task): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderContract.ACTION_SNOOZE
            putExtra(ReminderContract.EXTRA_TASK_ID, task.id.toString())
        }
        val pi = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() xor 0x2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_recent_history,
            "推迟10分钟",
            pi
        ).build()
    }
}
