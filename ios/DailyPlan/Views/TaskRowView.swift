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
                    .onTapGesture { beginEdit() }
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

            // 删除
            Button(action: { onDelete(task) }) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
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
