// TaskRepository.swift
// Repository 协议（平台无关，规格 §5.1）。实现见 LocalTaskRepository（Core Data）。

import Foundation

protocol TaskRepository {
    /// 当天的全部待办，按「进行中在上、已完成置底；同日按 sortOrder 升序」排序（规格 §5.1）
    func todayTasks() throws -> [TaskDTO]

    /// 新增一条（id/updatedAt 由调用方给定或由仓库补全）
    func add(_ task: TaskDTO) throws

    /// 全量更新一条（含 isDone/title/categoryId/priority/remindAt/leadMinutes/repeatCount/sortOrder 等）
    func update(_ task: TaskDTO) throws

    /// 标记完成：isDone=true, doneAt=at, updatedAt=now（规格 §5.1）
    func markDone(_ id: UUID, at date: Date) throws

    /// 删除一条（级联删除其 Tag 关联）
    func delete(_ id: UUID) throws

    /// 重排：按 ids 给定顺序，将每条 sortOrder 设为该下标；仅影响传入的当日待办（规格 §5.1）
    func reorder(ids: [UUID]) throws

    /// 扫描未完成且有 remindAt，且 remindAt ∈ [now, until] 的待办（支撑 F3 重启/跨日补偿，规格 §5.3）
    /// - Parameter until: 扫描上界（绝对时刻）。now 取调用时刻。
    func tasksWithPendingReminders(until date: Date) throws -> [TaskDTO]
}
