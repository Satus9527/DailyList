// Category+CoreData.swift
// Category 实体的 NSManagedObject 子类（规格 §3）。

import CoreData

@objc(Category)
public class Category: NSManagedObject {
    @NSManaged public var id: UUID
    @NSManaged public var name: String
    @NSManaged public var isPreset: Bool

    @NSManaged public var tasks: Set<Task>

    convenience init(context: NSManagedObjectContext, id: UUID, name: String, isPreset: Bool) {
        self.init(context: context)
        self.id = id
        self.name = name
        self.isPreset = isPreset
    }

    func toDTO() -> CategoryDTO {
        CategoryDTO(id: id, name: name, isPreset: isPreset)
    }
}

/// Category 领域模型
struct CategoryDTO: Identifiable, Equatable {
    let id: UUID
    let name: String
    let isPreset: Bool
}
