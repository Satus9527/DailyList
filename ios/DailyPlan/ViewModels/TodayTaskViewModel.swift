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

    /// 通知调度器（F3，M2）：完成即取消、取消完成恢复、设/改提醒排程均经此。
    private let scheduler: ReminderScheduler

    init(context: NSManagedObjectContext, scheduler: ReminderScheduler) {
        self.context = context
        self.repository = LocalTaskRepository(context: context)
        self.scheduler = scheduler
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
            // F3 接线点：新增时若已带提醒（未来时刻），自动排程（当前输入框未设提醒，为预留接线）
            if let ra = task.remindAt, ra > Date() {
                scheduler.schedule(for: task)
            }
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
                // AC-26：取消完成时若仍有未触发提醒，恢复其排程（提前/到点/重复）
                if let ra = updated.remindAt, ra > Date() {
                    scheduler.schedule(for: updated)
                }
            } else {
                // 标记完成（AC-14）
                try repository.markDone(task.id, at: Date())
                // AC-9（R-E7）：完成即取消该任务所有未触发后续提醒（避免已完成还响）
                scheduler.cancel(for: task.id)
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
            // 删除待办时一并取消其提醒（避免残留通知指向已删任务）
            scheduler.cancel(for: task.id)
            reload()
        } catch {
            errorMessage = "删除失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F3 提醒接线点（供 F4 编辑/改期 UI 调用）
    /// 设置或改期提醒（AC-28 / R-E11）：schedule 内含 cancel，按新 T 幂等重建，不重复通知。
    /// 若 remindAt 为空或已过去，则取消该任务全部提醒。
    func applyReminderSetting(for task: TaskDTO) {
        if let ra = task.remindAt, ra > Date() {
            scheduler.schedule(for: task)
        } else {
            scheduler.cancel(for: task.id)
        }
    }

    // MARK: - F3 提醒设置（UI 调用，M2-D，Task #36）
    /// 为单条待办设置/修改提醒：先更新领域模型（remindAt / leadMinutes / repeatCount），
    /// 再 repository.update 持久化；随后据新值排程。
    /// - 编辑 remindAt 时由 `scheduler.schedule` 内部先 `cancel` 再登记（幂等，改期不重复，AC-28 / R-E11）。
    /// - remindAt=nil（关闭提醒）或时间已过期 → `scheduler.cancel` 移除该任务全部 pending。
    func saveReminder(taskId: UUID, remindAt: Date?, leadMinutes: Int, repeatCount: Int) {
        guard let idx = tasks.firstIndex(where: { $0.id == taskId }) else { return }
        var task = tasks[idx]
        task.remindAt = remindAt
        task.leadMinutes = leadMinutes
        task.repeatCount = repeatCount
        task.updatedAt = Date()
        do {
            try repository.update(task)
            if let ra = remindAt, ra > Date() {
                scheduler.schedule(for: task)
            } else {
                scheduler.cancel(for: task.id)
            }
            reload()
        } catch {
            errorMessage = "保存提醒失败：\(error.localizedDescription)"
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
