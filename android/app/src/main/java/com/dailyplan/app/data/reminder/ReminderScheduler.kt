// ReminderScheduler.kt
// 通知层协议（架构 §7.2 / 规格 §4.1）。实现见 WorkManagerReminderScheduler。
// 仅负责据 Task 字段推导触发点并调度/取消，不存储业务数据（架构 §7.1 通知层边界）。

package com.dailyplan.app.data.reminder

import com.dailyplan.app.domain.model.Task
import java.util.UUID

interface ReminderScheduler {
    /** 据 task 的 remindAt / leadMinutes / repeatCount 生成触发点并登记（规格 §2.2 / §4.1） */
    fun schedule(task: Task)

    /** 取消该 task 全部未触发提醒（tag = taskId，规格 §2.4 / §4.1） */
    fun cancel(taskId: UUID)

    /** 启动/前台/重启补偿：扫描未来 7 天未完成任务重建（规格 §4.4 / §5） */
    fun rescheduleAllPending()

    /** 推迟 10 分钟（清旧 snooze 后建新，规格 §2.4 / §4.1 snooze） */
    fun snooze(taskId: UUID)
}
