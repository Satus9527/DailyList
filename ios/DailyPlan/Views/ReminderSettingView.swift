// ReminderSettingView.swift
// F3 提醒设置面板（M2-D，Task #36）：为单条待办设置/修改提醒时间、提前量、重复次数。
// 默认沿用 P0-2（leadMinutes=10 / repeatCount=3）；可分别关闭（L=0 / R=0，AC-11）。
// 确认后经 ViewModel 持久化（repository.update）并调度（ReminderScheduler.schedule，内部先 cancel 再登记，幂等）。

import SwiftUI

struct ReminderSettingView: View {
    let task: TaskDTO
    /// 关闭面板（取消或保存后均调用）
    let onDismiss: () -> Void
    /// 保存：回传 remindAt / leadMinutes / repeatCount（remindAt=nil 表示「无提醒」）
    let onSave: (_ remindAt: Date?, _ leadMinutes: Int, _ repeatCount: Int) -> Void

    // 是否启用提醒：无 remindAt 即「无提醒」
    @State private var enabled: Bool
    // 提醒时间（沿用既有值；未设则默认今天 09:00）
    @State private var remindAt: Date
    // 提前提醒开关与分钟数
    @State private var leadEnabled: Bool
    @State private var leadMinutes: Int
    // 重复提醒开关与次数
    @State private var repeatEnabled: Bool
    @State private var repeatCount: Int

    // 预设提前档位（分钟）
    private let leadOptions = [5, 10, 15, 30]
    // 预设重复档位（次）。上限 5 与 Android `ReminderSettingSheet.repeatOptions = listOf(1,2,3,5)` 对齐（S1 口径统一）。
    // 设计规格 §3.1 旧注 REPEAT_MAX=3 为 stale 口径；cancel 已动态覆盖任意 repeatCount（见 ReminderScheduler.cancel）。
    private let repeatOptions = [1, 2, 3, 5]

    init(task: TaskDTO,
         onDismiss: @escaping () -> Void,
         onSave: @escaping (_ remindAt: Date?, _ leadMinutes: Int, _ repeatCount: Int) -> Void) {
        self.task = task
        self.onDismiss = onDismiss
        self.onSave = onSave
        let hasReminder = task.remindAt != nil
        _enabled = State(initialValue: hasReminder)
        _remindAt = State(initialValue: task.remindAt ?? Self.defaultRemindAt())
        _leadEnabled = State(initialValue: task.leadMinutes > 0)
        _leadMinutes = State(initialValue: task.leadMinutes > 0 ? task.leadMinutes : 10)
        _repeatEnabled = State(initialValue: task.repeatCount > 0)
        _repeatCount = State(initialValue: task.repeatCount > 0 ? task.repeatCount : 3)
    }

    /// 默认提醒时间：今天 09:00（本地时区），便于用户直接确认
    private static func defaultRemindAt() -> Date {
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        comps.hour = 9; comps.minute = 0; comps.second = 0
        return Calendar.current.date(from: comps) ?? Date()
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    Toggle("启用提醒", isOn: $enabled)
                }
                if enabled {
                    Section(header: Text("提醒时间")) {
                        DatePicker("提醒时间", selection: $remindAt,
                                   displayedComponents: [.date, .hourAndMinute])
                    }
                    Section(header: Text("提前提醒")) {
                        Toggle("提前提醒", isOn: $leadEnabled)
                        if leadEnabled {
                            Picker("提前量", selection: $leadMinutes) {
                                ForEach(leadOptions, id: \.self) { m in
                                    Text("\(m) 分").tag(m)
                                }
                            }
                            .pickerStyle(.segmented)
                        } else {
                            Text("关闭：到点才提醒（不提前，L=0）")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                    Section(header: Text("重复提醒")) {
                        Toggle("重复提醒", isOn: $repeatEnabled)
                        if repeatEnabled {
                            Picker("重复次数", selection: $repeatCount) {
                                ForEach(repeatOptions, id: \.self) { n in
                                    Text("\(n) 次").tag(n)
                                }
                            }
                            .pickerStyle(.segmented)
                            Text("每次间隔 10 分钟（每次 +10min，P0-2）")
                                .font(.caption).foregroundColor(.secondary)
                        } else {
                            Text("关闭：仅提醒一次（不重复，R=0，AC-11）")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                } else {
                    Section {
                        Text("未设置提醒：到点/提前/重复均不触发。")
                            .font(.caption).foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("提醒设置")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存", action: commit)
                }
            }
        }
    }

    private func commit() {
        let ra = enabled ? remindAt : nil
        let lm = leadEnabled ? leadMinutes : 0   // 关闭提前 → L=0（不生成 __lead）
        let rc = repeatEnabled ? repeatCount : 0 // 关闭重复 → R=0（不生成 __rep，AC-11）
        onSave(ra, lm, rc)
        onDismiss()
    }
}

// MARK: - 行内展示用：压缩的提醒时间（M/d HH:mm，设备本地时区）
extension TaskDTO {
    var reminderShortText: String? {
        guard let ra = remindAt else { return nil }
        let df = DateFormatter()
        df.dateFormat = "M/d HH:mm"
        df.timeZone = TimeZone.current
        return df.string(from: ra)
    }
}
