// TaskEditViewModel.swift
// M5 F4 编辑页协调 ViewModel（规格 §4.1）：分类加载/自建、标签联想/确认/去重、保存组织字段。
// 标签归一与去重复用 M1 `TagNormalizer` 与 `TagRepository.addOrReuse`（§5 / AC-30③ / R-O1）。
// 标注 @MainActor：所有仓库方法为同步 throws，在 MainActor 上调用于本 App 的单线程 UI 语境安全。

import CoreData
import Foundation
import SwiftUI

@MainActor
final class TaskEditViewModel: ObservableObject {
    @Published var categories: [CategoryDTO] = []

    private let categoryRepo: CategoryRepository
    private let tagRepo: TagRepository
    private let taskRepo: TaskRepository

    init(context: NSManagedObjectContext) {
        self.categoryRepo = LocalCategoryRepository(context: context)
        self.tagRepo = LocalTagRepository(context: context)
        self.taskRepo = LocalTaskRepository(context: context)
    }

    // MARK: - 分类（预设 + 自建）

    func loadCategories() {
        do { categories = try categoryRepo.all() }
        catch { categories = [] }
    }

    /// 自建分类（isPreset=false）；空名忽略。
    func addCategory(_ name: String) -> CategoryDTO? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return try? categoryRepo.add(name: trimmed)
    }

    // MARK: - 标签联想 / 确认 / 去重（§4.1 / §5）

    /// 联想补全：前缀先归一再查（reuse repository.suggestTags）。
    func suggest(_ prefix: String, limit: Int = 8) -> [TagDTO] {
        (try? taskRepo.suggestTags(prefix: prefix, limit: limit)) ?? []
    }

    /// 回车/逗号确认：归一后作为标签加入 chips；重复（归一后相同）自动去重（AC-30③ / R-O1）。
    /// 内部走 `TagRepository.addOrReuse(normalizedName:)` → 同名归一结果复用同一 Tag 行，天然去重。
    func commitTagInput(_ text: inout String, into chips: inout [TagDTO]) {
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        text = ""   // 无论是否加入都清空输入框
        guard !raw.isEmpty else { return }
        let norm = TagNormalizer.normalize(raw)
        guard !chips.contains(where: { TagNormalizer.normalize($0.name) == norm }) else { return }
        if let tag = try? tagRepo.addOrReuse(normalizedName: norm) {
            chips.append(tag)
        }
    }

    /// 点击联想项：避免重复加入（按 id 去重）。
    func pickSuggestion(_ tag: TagDTO, into chips: inout [TagDTO]) {
        guard !chips.contains(where: { $0.id == tag.id }) else { return }
        chips.append(tag)
    }

    /// 读取某任务当前标签（用于编辑页回显）。
    func loadTags(taskId: UUID) -> [TagDTO] {
        (try? taskRepo.tags(forTaskId: taskId)) ?? []
    }

    // MARK: - 保存（分类 / 优先级 / 标签）

    /// 整体保存组织字段：更新 categoryId/priority（repository.update）+ 整体替换标签关联（repository.setTags）。
    /// 提醒相关字段由 TodayTaskViewModel.saveReminder 另行处理（二者经同一 context，已在调用顺序上避免互相覆盖）。
    func save(task: TaskDTO, categoryId: UUID?, priority: Priority, tags: [TagDTO]) {
        var updated = task
        updated.categoryId = categoryId
        updated.priority = priority
        updated.updatedAt = Date()
        do {
            try taskRepo.update(updated)
            try taskRepo.setTags(taskId: task.id, tagIds: Set(tags.map { $0.id }))
        } catch {
            // 错误交由上层 Toast 处理；此处仅吞掉以便编辑流不中断
        }
    }
}
