// TodayView.swift
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度、当日列表（进行中在上、已完成置底、可拖拽）、
// 底部文字输入框 + 添加按钮。数据从 Core Data 加载（F6 持久化）。

import SwiftUI

struct TodayView: View {
    @Environment(\.managedObjectContext) private var viewContext
    @StateObject private var vm: TodayTaskViewModel

    @State private var editingId: UUID?
    /// F3 提醒设置面板所针对的待办（nil 表示未打开）
    @State private var reminderTask: TaskDTO?

    /// scheduler 由 DailyPlanApp 注入（F3，M2）；context 沿用 PersistenceController 共享栈。
    init(scheduler: ReminderScheduler) {
        _vm = StateObject(
            wrappedValue: TodayTaskViewModel(
                context: PersistenceController.shared.viewContext,
                scheduler: scheduler
            )
        )
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // 进度 X / Y（R-U3 / AC-15）
                HStack {
                    Text("今日完成 \(vm.doneCount) / 共 \(vm.totalCount)")
                        .font(.headline)
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.vertical, 8)

                if let msg = vm.errorMessage {
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(.orange)
                        .padding(.horizontal)
                }

                // 当日列表（可拖拽重排，AC-16）
                List {
                    ForEach(vm.tasks) { task in
                        TaskRowView(
                            task: task,
                            isEditing: $editingId,
                            onToggleDone: { vm.toggleDone($0) },
                            onCommitEdit: { vm.editTitle($0, to: $1) },
                            onDelete: { vm.delete($0) },
                            onSetReminder: { reminderTask = $0 }
                        )
                    }
                    .onMove { from, to in
                        // 拖拽仅影响当日待办（规格 §5.1 reorder）
                        vm.reorder(from: from, to: to)
                    }
                }
                .listStyle(.plain)

                // F1 文字输入（回车 / 点按钮新增）
                HStack {
                    TextField("添加今日待办…", text: $vm.inputText, onCommit: { vm.addFromInput() })
                        .textFieldStyle(.roundedBorder)
                    Button(action: { vm.addFromInput() }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                    }
                }
                .padding()
            }
            .navigationTitle("今日计划")
        }
        // F3 提醒设置面板（M2-D，Task #36）：保存经 VM 持久化并排程
        .sheet(item: $reminderTask) { task in
            ReminderSettingView(
                task: task,
                onDismiss: { reminderTask = nil },
                onSave: { ra, lm, rc in
                    vm.saveReminder(taskId: task.id, remindAt: ra, leadMinutes: lm, repeatCount: rc)
                }
            )
        }
    }
}
