// TodayView.swift
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度、当日列表（进行中在上、已完成置底、可拖拽）、
// 底部文字输入框 + 添加按钮。数据从 Core Data 加载（F6 持久化）。

import SwiftUI

struct TodayView: View {
    @Environment(\.managedObjectContext) private var viewContext
    @StateObject private var vm: TodayTaskViewModel

    @State private var editingId: UUID?

    init() {
        // 在 View 初始化时无法访问 Environment，故用默认 context 的 fallback 构造；
        // 实际运行由 DailyPlanApp 注入 managedObjectContext。
        _vm = StateObject(wrappedValue: TodayTaskViewModel(context: PersistenceController.shared.viewContext))
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
                            onDelete: { vm.delete($0) }
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
    }
}
