// LocalTaskRepository.swift
// TaskRepository 的 Core Data 实现（规格 §5 / §6）。
// 所有写操作单事务提交（规格 §1.7），保证杀进程不丢（F6）。

import CoreData
import Foundation

struct LocalTaskRepository: TaskRepository {
    private let context: NSManagedObjectContext

    init(context: NSManagedObjectContext) {
        self.context = context
    }

    // MARK: - 查询

    func todayTasks() throws -> [TaskDTO] {
        let today = DateFormatter.todayDateString()
        return try context.performAndWait {
            let req = Task.fetchRequest()
            req.predicate = NSPredicate(format: "date == %@", today)
            // 进行中在上、已完成置底；同组按 sortOrder 升序（规格 §5.1）
            req.sortDescriptors = [
                NSSortDescriptor(key: "isDone", ascending: true),
                NSSortDescriptor(key: "sortOrder", ascending: true)
            ]
            let results = try context.fetch(req)
            return results.map { ($0 as! Task).toDTO() }
        }
    }

    func tasksWithPendingReminders(until date: Date) throws -> [TaskDTO] {
        let now = Date()
        return try context.performAndWait {
            let req = Task.fetchRequest()
            // 未完成 + 有 remindAt + remindAt ∈ [now, until]，按 remindAt 升序（规格 §5.3）
            req.predicate = NSPredicate(
                format: "isDone == NO AND remindAt != nil AND remindAt >= %@ AND remindAt <= %@",
                now as NSDate, date as NSDate
            )
            req.sortDescriptors = [NSSortDescriptor(key: "remindAt", ascending: true)]
            let results = try context.fetch(req)
            return results.map { ($0 as! Task).toDTO() }
        }
    }

    // MARK: - M4 S5 展示日取数（只读，不新增字段）

    func tasksForDisplayDay(_ day: String) throws -> [TaskDTO] {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = TimeZone.current   // 设备本地时区（规格 §1.1）
        guard let dayDate = df.date(from: day) else { return [] }
        let start = Calendar.current.startOfDay(for: dayDate)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? start
        return try context.performAndWait {
            let req = Task.fetchRequest()
            // date == day（普通任务）或 remindAt 落在 day 当天（含跨 0 点项：date != day 但 remindAt 日 == day）
            req.predicate = NSPredicate(
                format: "date == %@ OR (remindAt != nil AND remindAt >= %@ AND remindAt < %@)",
                day, start as NSDate, end as NSDate
            )
            req.sortDescriptors = [
                NSSortDescriptor(key: "isDone", ascending: true),
                NSSortDescriptor(key: "sortOrder", ascending: true)
            ]
            let results = try context.fetch(req)
            return results.map { ($0 as! Task).toDTO() }
        }
    }

    // MARK: - 写操作（单事务提交）

    func add(_ task: TaskDTO) throws {
        try context.performAndWait {
            let obj = Task(context: context, dto: task)
            context.insert(obj)
            if context.hasChanges { try context.save() }   // 单事务提交
        }
    }

    func update(_ task: TaskDTO) throws {
        try context.performAndWait {
            let req = Task.fetchRequest()
            req.predicate = NSPredicate(format: "id == %@", task.id as NSUUID)
            req.fetchLimit = 1
            guard let existing = try context.fetch(req).first as? Task else { return }
            // 全量覆盖（规格 §5.1）
            existing.title = task.title
            existing.date = task.date
            existing.categoryId = task.categoryId
            existing.priority = task.priority.rawValue
            existing.isDone = task.isDone
            existing.doneAt = task.doneAt
            existing.remindAt = task.remindAt
            existing.leadMinutes = Int32(task.leadMinutes)
            existing.repeatCount = Int32(task.repeatCount)
            existing.sortOrder = Int32(task.sortOrder)
            existing.source = task.source.rawValue
            existing.updatedAt = Date()   // 写操作更新 updatedAt（规格 §1.6）
            existing.syncState = task.syncState.rawValue
            if context.hasChanges { try context.save() }
        }
    }

    func markDone(_ id: UUID, at date: Date) throws {
        try context.performAndWait {
            let req = Task.fetchRequest()
            req.predicate = NSPredicate(format: "id == %@", id as NSUUID)
            req.fetchLimit = 1
            guard let existing = try context.fetch(req).first as? Task else { return }
            existing.isDone = true
            existing.doneAt = date
            existing.updatedAt = Date()
            if context.hasChanges { try context.save() }
        }
    }

    func delete(_ id: UUID) throws {
        try context.performAndWait {
            let req = Task.fetchRequest()
            req.predicate = NSPredicate(format: "id == %@", id as NSUUID)
            req.fetchLimit = 1
            guard let existing = try context.fetch(req).first as? Task else { return }
            context.delete(existing)   // 关系 Cascade 级联删 Tag 关联（规格 §6.3）
            if context.hasChanges { try context.save() }
        }
    }

    func reorder(ids: [UUID]) throws {
        try context.performAndWait {
            for (index, id) in ids.enumerated() {
                let req = Task.fetchRequest()
                req.predicate = NSPredicate(format: "id == %@", id as NSUUID)
                req.fetchLimit = 1
                if let existing = try context.fetch(req).first as? Task {
                    existing.sortOrder = Int32(index)
                }
            }
            if context.hasChanges { try context.save() }   // 单事务提交整组重排
        }
    }
}

// Task.fetchRequest() 便捷（NSManagedObject 默认提供，但显式声明便于类型推断）
extension Task {
    @nonobjc class func fetchRequest() -> NSFetchRequest<NSFetchRequestResult> {
        NSFetchRequest<NSFetchRequestResult>(entityName: "Task")
    }
}
