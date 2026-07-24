// ReminderContract.kt
// 提醒通知层共享常量（渠道、Action、Extra、扫描上界）。供 Scheduler / Worker / Helper / Receiver 共用，
// 避免各文件重复定义导致漂移（规格 §4.1 companion 常量集中此处）。

package com.dailyplan.app.data.reminder

object ReminderContract {
    /** 通知渠道 ID（规格 §4.3，重要性 HIGH + 声音/振动） */
    const val CHANNEL_ID = "channel_reminder"

    /** 通知 Action：标记完成（广播） */
    const val ACTION_COMPLETE = "com.dailyplan.app.action.REMINDER_COMPLETE"

    /** 通知 Action：推迟 10 分钟（广播） */
    const val ACTION_SNOOZE = "com.dailyplan.app.action.REMINDER_SNOOZE"

    /** Work / 广播间传递的 taskId（String） */
    const val EXTRA_TASK_ID = "extra_task_id"

    /** Work 输入数据：触发点类型（TriggerKind.name） */
    const val KEY_KIND = "kind"

    /** Work 输入数据：重复序号（仅 REPEAT 使用） */
    const val KEY_REPEAT_INDEX = "repeatIndex"

    /** 扫描上界：未来 7 天（规格 §1.5 / §5，双端一致） */
    const val REMINDER_SCAN_HORIZON_DAYS = 7
}
