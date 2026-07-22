// Task+CoreData.swift
// Task 实体的 NSManagedObject 子类（规格 §6.2 / §6.3）。
// 字段类型严格对应设计规格；枚举以 String(rawValue) 持久化。

import CoreData

@objc(Task)
public class Task: NSManagedObject {
    @NSManaged public var id: UUID
    @NSManaged public var title: String
    @NSManaged public var date: String            // "yyyy-MM-dd"
    @NSManaged public var categoryId: UUID?
    @NSManaged public var priority: String        // Priority.rawValue
    @NSManaged public var isDone: Bool
    @NSManaged public var doneAt: Date?
    @NSManaged public var remindAt: Date?
    @NSManaged public var leadMinutes: Int32
    @NSManaged public var repeatCount: Int32
    @NSManaged public var sortOrder: Int32
    @NSManaged public var source: String          // TaskSource.rawValue
    @NSManaged public var updatedAt: Date
    @NSManaged public var syncState: String       // SyncState.rawValue

    // 关系（规格 §6.3）
    @NSManaged public var category: Category?
    @NSManaged public var tags: Set<Tag>

    // MARK: - 便捷构造（插入一条新对象）
    convenience init(context: NSManagedObjectContext, dto: TaskDTO) {
        self.init(context: context)
        self.id = dto.id
        self.title = dto.title
        self.date = dto.date
        self.categoryId = dto.categoryId
        self.priority = dto.priority.rawValue
        self.isDone = dto.isDone
        self.doneAt = dto.doneAt
        self.remindAt = dto.remindAt
        self.leadMinutes = Int32(dto.leadMinutes)
        self.repeatCount = Int32(dto.repeatCount)
        self.sortOrder = Int32(dto.sortOrder)
        self.source = dto.source.rawValue
        self.updatedAt = dto.updatedAt
        self.syncState = dto.syncState.rawValue
    }

    /// 映射为领域模型 TaskDTO（供 Repository 返回）。
    func toDTO() -> TaskDTO {
        TaskDTO(
            id: id,
            title: title,
            date: date,
            categoryId: categoryId,
            priority: Priority(rawValue: priority) ?? .default,
            isDone: isDone,
            doneAt: doneAt,
            remindAt: remindAt,
            leadMinutes: Int(leadMinutes),
            repeatCount: Int(repeatCount),
            sortOrder: Int(sortOrder),
            source: TaskSource(rawValue: source) ?? .text,
            updatedAt: updatedAt,
            syncState: SyncState(rawValue: syncState) ?? .local
        )
    }
}
