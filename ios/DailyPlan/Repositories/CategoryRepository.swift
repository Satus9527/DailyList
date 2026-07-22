// CategoryRepository.swift
// Category 基础 CRUD（M1 最简，规格 §3）。预设项不可删/不可改名。

import CoreData
import Foundation

protocol CategoryRepository {
    func all() throws -> [CategoryDTO]
    func add(name: String) throws -> CategoryDTO
    /// 删除自建分类；删除时其下任务回退到「其他」预设（规格 §3.2）。预设项不可删。
    func delete(_ id: UUID) throws
}

struct LocalCategoryRepository: CategoryRepository {
    private let context: NSManagedObjectContext

    init(context: NSManagedObjectContext) { self.context = context }

    func all() throws -> [CategoryDTO] {
        try context.performAndWait {
            let req = Category.fetchRequest()
            req.sortDescriptors = [NSSortDescriptor(key: "name", ascending: true)]
            let results = try context.fetch(req) as? [Category] ?? []
            return results.map { $0.toDTO() }
        }
    }

    func add(name: String) throws -> CategoryDTO {
        try context.performAndWait {
            let c = Category(context: context, id: UUID(), name: name, isPreset: false)
            context.insert(c)
            if context.hasChanges { try context.save() }
            return c.toDTO()
        }
    }

    func delete(_ id: UUID) throws {
        try context.performAndWait {
            let req = Category.fetchRequest()
            req.predicate = NSPredicate(format: "id == %@", id as NSUUID)
            req.fetchLimit = 1
            guard let c = try context.fetch(req).first as? Category else { return }
            guard !c.isPreset else { return }   // 预设不可删（规格 §3.2）

            // 其下任务回退到「其他」预设（Nullify + 上层赋值）
            let tasksReq = Task.fetchRequest()
            tasksReq.predicate = NSPredicate(format: "categoryId == %@", id as NSUUID)
            if let tasks = try context.fetch(tasksReq) as? [Task] {
                for t in tasks { t.categoryId = CategorySeed.otherId }
            }
            context.delete(c)
            if context.hasChanges { try context.save() }
        }
    }
}

extension Category {
    @nonobjc class func fetchRequest() -> NSFetchRequest<NSFetchRequestResult> {
        NSFetchRequest<NSFetchRequestResult>(entityName: "Category")
    }
}
