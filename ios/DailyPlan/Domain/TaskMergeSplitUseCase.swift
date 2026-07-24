// TaskMergeSplitUseCase.swift
// 长按合并/拆分用例（F2 待确认项 5 / AC-5，规格 §7）。本 M3 实现「基础版」：
// 冻结 merge/split 接口，复用 M1 TaskRepository（update/add/delete）落库，重启后保持。
//
// 约束：不新增 Task 字段；合并后 title 以「、」连接（与 P0-4「、」不切分语义一致）；
// 拆分断点前的标点归属前段（同 §5.3）。来源 source 保持不变（保留 .voice/.text 追溯）。

import Foundation

protocol TaskMergeSplitUseCase {
    /// 合并相邻多条为一条：title 以「、」连接；保留最早 sortOrder；逐条删除被并者、更新主条。
    func merge(_ tasks: [TaskDTO]) throws
    /// 在第 index 个字符处断开为两条；前段带走原 id 与 sortOrder，后段为新条（sortOrder+1）。
    func split(_ task: TaskDTO, at index: Int) throws
}

final class NativeTaskMergeSplitUseCase: TaskMergeSplitUseCase {

    private let repository: TaskRepository

    init(repository: TaskRepository) {
        self.repository = repository
    }

    func merge(_ tasks: [TaskDTO]) throws {
        guard tasks.count >= 2 else { return }
        let sorted = tasks.sorted { $0.sortOrder < $1.sortOrder }
        let primary = sorted.first!
        // 「、」连接（遵循 P0-4：顿号不触发切分，作为合并连接符）
        let mergedTitle = sorted.map { $0.title }.joined(separator: "、")
        var merged = primary
        merged.title = mergedTitle
        merged.updatedAt = Date()
        // 删除其余被并条目，主条原地更新（id/source/date 不变，保留追溯）
        for t in sorted.dropFirst() { try repository.delete(t.id) }
        try repository.update(merged)
    }

    func split(_ task: TaskDTO, at index: Int) throws {
        guard index > 0, index < task.title.count else { return }
        let title = task.title
        let i = title.index(title.startIndex, offsetBy: index)
        var front = String(title[..<i])
        var back = String(title[i...])
        // 断开点若为拆分标点，该标点归属前段并丢弃（不进后段 title，同 §5.3）
        if let last = front.last, ASRSplitConfig.loadFromBundle()?.splitPunctuation.contains(String(last)) == true {
            front.removeLast()
        }
        front = front.trimmingCharacters(in: .whitespacesAndNewlines)
        back = back.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !front.isEmpty, !back.isEmpty else { return }

        var first = task
        first.title = front
        first.updatedAt = Date()
        try repository.update(first)

        // 后段为新条，sortOrder 紧邻其后，source 继承原来源
        var second = TaskDTO.makeNew(title: back, sortOrder: task.sortOrder + 1)
        second.source = task.source
        try repository.add(second)
    }
}
