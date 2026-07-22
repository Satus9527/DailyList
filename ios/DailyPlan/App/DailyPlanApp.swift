// DailyPlanApp.swift
// App 入口。注入 PersistenceController，启动时若因损坏重建了空库则弹一次性提示（规格 §10.3）。

import SwiftUI

@main
struct DailyPlanApp: App {
    /// 共享 Core Data 栈（含损坏兜底，规格 §10）
    let persistenceController = PersistenceController.shared

    @State private var showCorruptionNotice = false

    init() {
        // 若启动时发生损坏并重建了空库，弹出一次性提示（不阻塞记录流）
        if persistenceController.didRebuildOnCorruption {
            _showCorruptionNotice = State(initialValue: true)
        }
    }

    var body: some Scene {
        WindowGroup {
            TodayView()
                .environment(\.managedObjectContext, persistenceController.viewContext)
                .alert("本地数据可能已损坏", isPresented: $showCorruptionNotice) {
                    Button("知道了", role: .cancel) { }
                } message: {
                    Text("已重置为空，历史记录可能丢失。你可以继续正常记录今日待办。")
                }
        }
    }
}
