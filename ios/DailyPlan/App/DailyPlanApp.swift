// DailyPlanApp.swift
// App 入口。注入 PersistenceController，启动时若因损坏重建了空库则弹一次性提示（规格 §10.3）。

import SwiftUI

@main
struct DailyPlanApp: App {
    /// 共享 Core Data 栈（含损坏兜底，规格 §10）
    let persistenceController = PersistenceController.shared

    /// 通知调度器（F3，M2）：持有 repository，负责排程/取消/补偿与通知 Action 处理。
    private let reminderScheduler: NativeReminderScheduler

    @State private var showCorruptionNotice = false
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // 若启动时发生损坏并重建了空库，弹出一次性提示（不阻塞记录流）
        if persistenceController.didRebuildOnCorruption {
            _showCorruptionNotice = State(initialValue: true)
        }

        // —— F3 通知层接线（M2）——
        // 与 ViewModel 共享同一 viewContext 构建 repository。
        let repo = LocalTaskRepository(context: persistenceController.viewContext)
        reminderScheduler = NativeReminderScheduler(repository: repo)
        reminderScheduler.registerCategory()             // 注册 UNNotificationCategory（标记完成 / 推迟 10 分钟）
        reminderScheduler.requestAuthorizationIfNeeded()  // 首次启动请求授权；未授权降级、不崩溃
        reminderScheduler.setAsDelegate()                 // 设置 center.delegate 处理 Action 回调
        reminderScheduler.rescheduleAllPending()          // 冷/热启动补偿：重建未来 7 天 pending（规格 §5）
    }

    var body: some Scene {
        WindowGroup {
            TodayView(scheduler: reminderScheduler)
                .environment(\.managedObjectContext, persistenceController.viewContext)
                .alert("本地数据可能已损坏", isPresented: $showCorruptionNotice) {
                    Button("知道了", role: .cancel) { }
                } message: {
                    Text("已重置为空，历史记录可能丢失。你可以继续正常记录今日待办。")
                }
                // 进入前台补偿（杀进程/重启后重新排程，规格 §3.3）
                .onChange(of: scenePhase) { _, newPhase in
                    if newPhase == .active {
                        reminderScheduler.rescheduleAllPending()
                    }
                }
        }
    }
}
