// TaskFilter.swift
// M5 F4 筛选条件值类型（规格 §3.1）。双端一致：分类/优先级/标签单维 + 组合（AND）。
// 标签筛选为 AND 语义（任务须同时拥有全部指定标签）；untaggedOnly 与 tagIds 互斥，优先于 tagIds。

import Foundation

struct TaskFilter: Equatable {
    /// 分类：nil = 不限；筛「其他」预设 id 时含 categoryId == nil 的任务（§6）
    var categoryId: UUID? = nil
    /// 优先级：nil = 不限
    var priority: Priority? = nil
    /// 标签 id 集合：空 = 不限；非空 = AND 语义
    var tagIds: Set<UUID> = []
    /// 仅返回无任何标签的任务（与 tagIds 互斥，优先于 tagIds）
    var untaggedOnly: Bool = false

    /// 空条件 = 全部（不过滤，规格 §6 / AC-30④）
    var isEmpty: Bool {
        categoryId == nil && priority == nil && tagIds.isEmpty && !untaggedOnly
    }

    // MARK: - 内存过滤（§3.4，ViewModel 推荐主路径，复用 M4 已加载展示日集合）
    /// 判定单个任务是否命中当前筛选。标签命中需传入该任务的标签 id 集合（TaskDTO 不含标签，经 repository.tags(forTaskId:) 取得）。
    func matches(_ task: TaskDTO, tagIds: Set<UUID>) -> Bool {
        if let cat = categoryId {
            // 未归类（categoryId == nil）在逻辑上等同「其他」，筛「其他」须含 nil（§3.3 / §6）
            let taskCat = task.categoryId ?? CategorySeed.otherId
            if taskCat != cat { return false }
        }
        if let p = priority, task.priority != p { return false }
        if untaggedOnly {
            if !tagIds.isEmpty { return false }
        } else if !self.tagIds.isEmpty {
            if !self.tagIds.isSubset(of: tagIds) { return false }   // AND：须同时拥有全部指定标签
        }
        return true
    }
}
