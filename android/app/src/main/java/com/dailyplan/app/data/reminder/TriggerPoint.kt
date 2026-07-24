// TriggerPoint.kt
// 触发点数据结构（规格 §2.3，双端一致语义）。由 ReminderScheduler 在内存推导，不落独立表（规格 §0/§6）。

package com.dailyplan.app.data.reminder

import java.util.Date
import java.util.UUID

/** 触发点类型（规格 §2.1） */
enum class TriggerKind { LEAD, AT, REPEAT, SNOOZE }

/**
 * 单个提醒触发点。
 * @param taskId   归属任务
 * @param kind     类型（LEAD 提前 / AT 到点 / REPEAT 重复 / SNOOZE 推迟）
 * @param repeatIndex 仅 REPEAT 使用（1..repeatCount）
 * @param fireAt   绝对触发时刻（设备本地时区，规格 §1.1）
 */
data class TriggerPoint(
    val taskId: UUID,
    val kind: TriggerKind,
    val repeatIndex: Int,
    val fireAt: Date
) {
    /** 通知标识（规格 §2.4）：taskId + 类型后缀，用于调试/去重追溯 */
    val identifier: String
        get() = when (kind) {
            TriggerKind.LEAD -> "${taskId}__lead"
            TriggerKind.AT -> "${taskId}__at"
            TriggerKind.REPEAT -> "${taskId}__rep$repeatIndex"
            TriggerKind.SNOOZE -> "${taskId}__snooze"
        }

    /** WorkManager 用 taskId 作唯一 tag，便于 cancelAllWorkByTag 批量取消（规格 §2.4 / §4.1） */
    val tag: String get() = taskId.toString()
}
