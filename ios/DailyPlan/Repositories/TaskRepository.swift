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

    /// 展示日取数（M4 S5，规格 §3.3）：返回「date == day」或「remindAt 落在 day 当天」的全部任务（含跨 0 点项）。
    /// 不新增 Task 字段；调用方再按 `TaskDTO.displayDay` 二次过滤归并到「当日列表」。
    /// - Parameter day: 本地时区 "yyyy-MM-dd" 展示日。
    func tasksForDisplayDay(_ day: String) throws -> [TaskDTO]

    // MARK: - M5 F4 筛选 / 标签读写（规格 §3.2）

    /// 组合筛选：分类 + 优先级 + 标签（AND 语义）；作用于展示日 `date`（默认当日）。
    /// 空条件（filter.isEmpty）= 返回该日全部（§6）。
    func filteredTasks(on date: String, filter: TaskFilter) throws -> [TaskDTO]

    /// 单维便捷：按分类（传「其他」预设 id 时含 categoryId == nil 的任务，见 §6）
    func tasksByCategory(_ categoryId: UUID, on date: String) throws -> [TaskDTO]
    /// 单维便捷：按优先级
    func tasksByPriority(_ priority: Priority, on date: String) throws -> [TaskDTO]
    /// 单维便捷：按标签（AND 多个）
    func tasksByTags(_ tagIds: Set<UUID>, on date: String) throws -> [TaskDTO]

    /// 标签联想补全：返回 name 以归一后前缀开头的已有标签（上限 limit，规格 §3.3 / §5）
    func suggestTags(prefix: String, limit: Int) throws -> [TagDTO]

    /// 读取某任务标签（§2.4 新增，复用 M1 TaskTagCrossRef 关联）
    func tags(forTaskId id: UUID) throws -> [TagDTO]
    /// 整体替换某任务标签关联（§2.4 新增；传入 tagIds 集合，先清空旧关联再建立新关联）
    func setTags(taskId: UUID, tagIds: Set<UUID>) throws
}
