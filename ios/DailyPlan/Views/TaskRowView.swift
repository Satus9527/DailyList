// TaskRowView.swift
// 单条待办行：勾选完成（F5）、行内编辑 title、删除、拖拽手柄（F5）。已完成置底 + 删除线（R-S1）。

import SwiftUI

struct TaskRowView: View {
    let task: TaskDTO
    @Binding var isEditing: UUID?
    let onToggleDone: (TaskDTO) -> Void
    let onCommitEdit: (TaskDTO, String) -> Void
    let onDelete: (TaskDTO) -> Void
    /// F3 提醒设置入口（M2-D，Task #36）：点击铃铛打开提醒面板
    let onSetReminder: (TaskDTO) -> Void

    // —— M4 D3：整行点按跳转（错过的提醒区用，打开提醒设置面板）——
    var onRowTap: ((TaskDTO) -> Void)? = nil
    // M4 D3：标记「提醒未达」胶囊
    var missedBadge: Bool = false
    // M4 R-4：长按合并/拆分（AC-5）
    var onMergeUp: ((TaskDTO) -> Void)? = nil
    var onSplit: ((TaskDTO) -> Void)? = nil

    @State private var editText: String = ""

    var body: some View {
        HStack(spacing: 12) {
            // 完成勾选
            Image(systemName: task.isDone ? "checkmark.circle.fill" : "circle")
                .foregroundColor(task.isDone ? .green : .gray)
                .onTapGesture { onToggleDone(task) }

            // 标题：编辑态 / 展示态
            if isEditing == task.id {
                TextField("编辑待办", text: $editText, onCommit: commit)
                    .textFieldStyle(.roundedBorder)
            } else {
                Text(task.title)
                    .strikethrough(task.isDone)
                    .foregroundColor(task.isDone ? .secondary : .primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .onTapGesture {
                        // M4 D3：错过的提醒区整行点按 → 跳转（打开提醒设置）；否则进入行内编辑
                        if let onRowTap { onRowTap(task) }
                        else { beginEdit() }
                    }
            }

            // F3 提醒入口：铃铛 + 下次提醒时间（无提醒显示「无提醒」）
            Button(action: { onSetReminder(task) }) {
                HStack(spacing: 4) {
                    Image(systemName: task.remindAt == nil ? "bell.slash" : "bell")
                        .foregroundColor(task.remindAt == nil ? .secondary : .accentColor)
                    if let ra = task.reminderShortText {
                        Text(ra)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    } else {
                        Text("无提醒")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(.borderless)

            // M4 D3：「提醒未达」胶囊（橙红，白字，带无障碍标签）
            if missedBadge {
                Text("提醒未达")
                    .font(.caption2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.orange))
                    .foregroundColor(.white)
                    .accessibilityLabel("提醒未达")
            }

            // 删除
            Button(action: { onDelete(task) }) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        // M4 R-4：长按弹出「合并到上一条 / 从此处拆分」（调用 M3 TaskMergeSplitUseCase）
        .contextMenu {
            if let onMergeUp {
                Button(action: { onMergeUp(task) }) {
                    Label("合并到上一条", systemImage: "arrow.up.to.line")
                }
            }
            if let onSplit {
                Button(action: { onSplit(task) }) {
                    Label("从此处拆分", systemImage: "scissors")
                }
            }
        }
    }

    private func beginEdit() {
        if !task.isDone {
            editText = task.title
            isEditing = task.id
        }
    }

    private func commit() {
        onCommitEdit(task, editText)
        isEditing = nil
    }
}
