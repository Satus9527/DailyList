// TaskRowView.swift
// 单条待办行：勾选完成（F5）、行内编辑 title、删除、拖拽手柄（F5）。已完成置底 + 删除线（R-S1）。

import SwiftUI

struct TaskRowView: View {
    let task: TaskDTO
    @Binding var isEditing: UUID?
    let onToggleDone: (TaskDTO) -> Void
    let onCommitEdit: (TaskDTO, String) -> Void
    let onDelete: (TaskDTO) -> Void

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
