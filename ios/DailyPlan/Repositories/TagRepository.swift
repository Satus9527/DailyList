// TagRepository.swift
// Tag 基础 CRUD（M1 最简，规格 §4）。写入/查询前对 name 做归一（§4.1）。

import CoreData
import Foundation

protocol TagRepository {
    func all() throws -> [TagDTO]
    /// 归一后写入；已存在同名归一结果则复用，不去重建（规格 §4.1 / AC-30）
    func addOrReuse(normalizedName name: String) throws -> TagDTO
}

struct LocalTagRepository: TagRepository {
    private let context: NSManagedObjectContext

    init(context: NSManagedObjectContext) { self.context = context }

    func all() throws -> [TagDTO] {
        try context.performAndWait {
            let req = Tag.fetchRequest()
            req.sortDescriptors = [NSSortDescriptor(key: "name", ascending: true)]
            let results = try context.fetch(req) as? [Tag] ?? []
            return results.map { $0.toDTO() }
        }
    }

    func addOrReuse(normalizedName name: String) throws -> TagDTO {
        let norm = TagNormalizer.normalize(name)
        return try context.performAndWait {
            let req = Tag.fetchRequest()
            req.predicate = NSPredicate(format: "name == %@", norm)
            req.fetchLimit = 1
            if let existing = try context.fetch(req).first as? Tag {
                return existing.toDTO()
            }
            let t = Tag(context: context, id: UUID(), name: norm)
            context.insert(t)
            if context.hasChanges { try context.save() }
            return t.toDTO()
        }
    }
}

// MARK: - 标签归一（忽略大小写、首尾空格、全角半角，规格 §4.1 / AC-30）
enum TagNormalizer {
    /// 小写 + 去首尾空格 + 全角转半角
    static func normalize(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let halfWidth = trimmed.applyingTransform(.fullwidthToHalfwidth, reverse: false) ?? trimmed
        return halfWidth.lowercased()
    }
}

extension Tag {
    @nonobjc class func fetchRequest() -> NSFetchRequest<NSFetchRequestResult> {
        NSFetchRequest<NSFetchRequestResult>(entityName: "Tag")
    }
}
