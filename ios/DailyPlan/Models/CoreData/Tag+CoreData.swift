// Tag+CoreData.swift
// Tag 实体的 NSManagedObject 子类（规格 §4.1）。name 唯一，写入前由上层归一。

import CoreData

@objc(Tag)
public class Tag: NSManagedObject {
    @NSManaged public var id: UUID
    @NSManaged public var name: String

    @NSManaged public var tasks: Set<Task>

    convenience init(context: NSManagedObjectContext, id: UUID, name: String) {
        self.init(context: context)
        self.id = id
        self.name = name
    }

    func toDTO() -> TagDTO {
        TagDTO(id: id, name: name)
    }
}

/// Tag 领域模型
struct TagDTO: Identifiable, Equatable {
    let id: UUID
    let name: String
}
