// TodayTaskViewModel.swift
// 今日待办 ViewModel：聚合 F1（文字记录）/ F5（完成/编辑/删除/拖拽）/ F6（持久化）与 X/Y 进度。
// UI 经 ViewModel 调 Repository，领域层不依赖具体存储（架构 §7.2）。

import Combine
import CoreData
import Foundation
import SwiftUI

final class TodayTaskViewModel: ObservableObject {
    // MARK: 发布状态
    @Published var tasks: [TaskDTO] = []
    @Published var inputText: String = ""
    @Published var errorMessage: String?

    /// 进度 X / Y（规格 R-U3 / AC-15）：X=已完成数，Y=当日总数
    var doneCount: Int { tasks.filter { $0.isDone }.count }
    var totalCount: Int { tasks.count }

    private let repository: TaskRepository
    private let context: NSManagedObjectContext

    init(context: NSManagedObjectContext) {
        self.context = context
        self.repository = LocalTaskRepository(context: context)
        reload()
    }

    // MARK: - F6 加载（从库而非内存，规格 AC-17）
    func reload() {
        do {
            tasks = try repository.todayTasks()
        } catch {
            errorMessage = "加载待办失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F1 文字记录
    /// 将输入框文本加入当日列表。去空白；≤500 字，超出截断并提示（AC-29 / R-X5）。
    func addFromInput() {
        let raw = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else { return }   // 空内容不生成（AC-1 / R-X4）

        let title: String
        if raw.count > 500 {
            // 超出截断至 500 字并提示（AC-29）
            title = String(raw.prefix(500))
            errorMessage = "内容超过 500 字，已截断。"
        } else {
            title = raw
        }

        let maxOrder = (tasks.map { $0.sortOrder }.max() ?? -1) + 1
        let task = TaskDTO.makeNew(title: title, sortOrder: maxOrder)
        do {
            try repository.add(task)
            inputText = ""        // 清空输入框（AC-1）
            reload()
        } catch {
            errorMessage = "添加失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 完成
    func toggleDone(_ task: TaskDTO) {
        do {
            if task.isDone {
                // 取消完成（AC-26）：isDone=false, doneAt=nil，回到进行中
                var updated = task
                updated.isDone = false
                updated.doneAt = nil
                try repository.update(updated)
            } else {
                // 标记完成（AC-14）
                try repository.markDone(task.id, at: Date())
            }
            reload()
        } catch {
            errorMessage = "操作失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 行内编辑 title
    func editTitle(_ task: TaskDTO, to newTitle: String) {
        let trimmed = newTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let title = trimmed.count > 500 ? String(trimmed.prefix(500)) : trimmed
        var updated = task
        updated.title = title
        do {
            try repository.update(updated)
            reload()
        } catch {
            errorMessage = "编辑失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 删除
    func delete(_ task: TaskDTO) {
        do {
            try repository.delete(task.id)
            reload()
        } catch {
            errorMessage = "删除失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 拖拽重排（AC-16）
    func reorder(from source: IndexSet, to destination: Int) {
        var reordered = tasks
        reordered.move(fromOffsets: source, toOffset: destination)
        let ids = reordered.map { $0.id }
        do {
            try repository.reorder(ids: ids)
            reload()
        } catch {
            errorMessage = "排序失败：\(error.localizedDescription)"
        }
    }
}
