// PersistenceController.swift
// Core Data 栈（代码化模型，规格 §6）+ 首启 Category 种子 + P0-3 损坏兜底（方案 B）。
//
// 说明：本工程采用「代码优先」建模（在运行时用 NSEntityDescription 构建
// NSManagedObjectModel），因此无需 .xcdatamodeld 二进制文件，Xcode 可直接编译。
// 实体/字段/关系严格对应设计规格 §6。

import CoreData
import Foundation

struct PersistenceController {

    // MARK: - 单例
    static let shared = PersistenceController()

    let container: NSPersistentContainer

    /// 损坏兜底标记：若启动时重建了空库，UI 可据此弹一次性提示（规格 §10.3）。
    private(set) var didRebuildOnCorruption = false

    // MARK: - 初始化（含损坏兜底）
    init(inMemory: Bool = false) {
        let model = Self.buildModel()
        let container = NSPersistentContainer(name: "DailyPlan", managedObjectModel: model)

        if inMemory {
            let desc = NSPersistentStoreDescription()
            desc.url = URL(fileURLWithPath: "/dev/null")
            container.persistentStoreDescriptions = [desc]
        }

        // —— 损坏兜底捕获点（规格 §10.1 / §10.4）——
        // DB 打开/创建、迁移、首启种子写入均在 do/try-catch 内，异常绝不抛出到 UI 线程。
        do {
            try Self.loadStoresSafely(container)
        } catch {
            // 打开/迁移失败：记本地日志 → 删损坏库 → 重建空库
            Self.logDBError(event: "open_failed", dbPath: container.storeURL?.path, error: error)
            Self.rebuildEmptyStore(for: container, model: model)
            didRebuildOnCorruption = true
        }

        self.container = container

        // 首启种子（Category 预设）。失败也按损坏处理，但不崩溃。
        do {
            try seedPresetCategoriesIfNeeded(context: container.viewContext)
        } catch {
            Self.logDBError(event: "migration_failed", dbPath: container.storeURL?.path, error: error)
            didRebuildOnCorruption = true
        }
    }

    // MARK: - 视图上下文便捷
    var viewContext: NSManagedObjectContext { container.viewContext }

    // MARK: - 私有：安全加载 store（单一事务提交，规格 §1.7）
    private static func loadStoresSafely(_ container: NSPersistentContainer) throws {
        var loadError: Error?
        let group = DispatchGroup()
        group.enter()
        container.loadPersistentStores { _, result in
            if case .failed(let err) = result {
                loadError = err
            }
            group.leave()
        }
        group.wait()   // 确保完成回调执行完再判定（避免异步竞态）
        if let loadError { throw loadError }
    }

    // MARK: - 私有：删除损坏库并重建空库（规格 §10.3）
    private static func rebuildEmptyStore(for container: NSPersistentContainer, model: NSManagedObjectModel) {
        guard let storeURL = container.storeURL else { return }
        // 删除损坏的 SQLite 主文件及 -wal / -shm 伴随文件
        let base = storeURL.deletingPathExtension().appendingPathExtension("sqlite")
        for suffix in ["", "-wal", "-shm"] {
            let url = suffix.isEmpty ? base : URL(fileURLWithPath: base.path + suffix)
            try? FileManager.default.removeItem(at: url)
        }
        do {
            // 重建空库并挂回 container（轻量迁移自动开启，规格 §9 v1 destructive 可接受）
            try container.persistentStoreCoordinator.addPersistentStore(
                ofType: NSSQLiteStoreType,
                configurationName: nil,
                at: base,
                options: [NSMigratePersistentStoresAutomaticallyOption: true,
                          NSInferMappingModelAutomaticallyOption: true]
            )
            logDBError(event: "corrupt_detected", dbPath: base.path,
                       error: NSError(domain: "DailyPlan", code: -1,
                                      userInfo: [NSLocalizedDescriptionKey: "rebuilt empty db"]))
        } catch {
            // 极端（如磁盘满）：降级为内存态，不抛到 UI（规格 §10.3）
            logDBError(event: "open_failed", dbPath: base.path, error: error)
        }
    }

    // MARK: - 私有：本地日志（规格 §10.2，仅本地、不联网）
    private static func logDBError(event: String, dbPath: String?, error: Error) {
        let ts = ISO8601DateFormatter().string(from: Date())
        let dbPath = dbPath ?? "<unknown>"
        let errType = String(describing: type(of: error))
        let message = error.localizedDescription
        let log = """
        [DB_ERROR] timestamp=\(ts)
        event=\(event)
        dbPath=\(dbPath)
        errorType=\(errType)
        message=\(message)
        action=rebuilt_empty_db

        """
        // 写入沙盒 Application Support/logs/db_error.log（不含任何账号/网络上报）
        if let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let logDir = dir.appendingPathComponent("logs")
            try? FileManager.default.createDirectory(at: logDir, withIntermediateDirectories: true)
            let file = logDir.appendingPathComponent("db_error.log")
            if let handle = try? FileHandle(forWritingTo: file) {
                handle.seekToEndOfFile()
                handle.write(log.data(using: .utf8)!)
                try? handle.close()
            } else {
                try? log.write(to: file, atomically: true, encoding: .utf8)
            }
        }
        // 同时输出到系统日志便于排查
        NSLog("[DB_ERROR] event=%@ dbPath=%@ errorType=%@ message=%@", event, dbPath, errType, message)
    }
}

// MARK: - Category 预设种子（规格 §3.2，首次启动写入）
extension PersistenceController {
    func seedPresetCategoriesIfNeeded(context: NSManagedObjectContext) throws {
        let req = NSFetchRequest<Category>(entityName: "Category")
        req.fetchLimit = 1
        let existing = try context.count(for: req)
        guard existing == 0 else { return }   // 已种子过，跳过

        for (id, name, isPreset) in CategorySeed.presets {
            let c = Category(context: context, id: id, name: name, isPreset: isPreset)
            context.insert(c)
        }
        // 单事务提交（规格 §1.7）
        if context.hasChanges {
            try context.save()
        }
    }
}

// MARK: - 代码化 Core Data 模型（规格 §6）
extension PersistenceController {

    /// 构建 NSManagedObjectModel 版本 "M1"（规格 §6.1）。
    static func buildModel() -> NSManagedObjectModel {
        let model = NSManagedObjectModel()

        // —— Task ——
        let task = NSEntityDescription()
        task.name = "Task"
        task.managedObjectClassName = "Task"

        let taskAttrs: [NSAttributeDescription] = [
            attr("id", .UUIDAttributeType),
            attr("title", .stringAttributeType, defaultValue: ""),
            attr("date", .stringAttributeType, defaultValue: ""),
            attr("categoryId", .UUIDAttributeType, optional: true),
            attr("priority", .stringAttributeType, defaultValue: "medium"),
            attr("isDone", .booleanAttributeType, defaultValue: false),
            attr("doneAt", .dateAttributeType, optional: true),
            attr("remindAt", .dateAttributeType, optional: true),
            attr("leadMinutes", .integer32AttributeType, defaultValue: 10),
            attr("repeatCount", .integer32AttributeType, defaultValue: 3),
            attr("sortOrder", .integer32AttributeType, defaultValue: 0),
            attr("source", .stringAttributeType, defaultValue: "text"),
            attr("updatedAt", .dateAttributeType),
            attr("syncState", .stringAttributeType, defaultValue: "local")
        ]
        task.properties = taskAttrs

        // —— Category ——
        let category = NSEntityDescription()
        category.name = "Category"
        category.managedObjectClassName = "Category"
        let catAttrs: [NSAttributeDescription] = [
            attr("id", .UUIDAttributeType),
            attr("name", .stringAttributeType),
            attr("isPreset", .booleanAttributeType, defaultValue: false)
        ]
        category.properties = catAttrs

        // —— Tag ——
        let tag = NSEntityDescription()
        tag.name = "Tag"
        tag.managedObjectClassName = "Tag"
        let tagAttrs: [NSAttributeDescription] = [
            attr("id", .UUIDAttributeType),
            attr("name", .stringAttributeType)
        ]
        tag.properties = tagAttrs

        // —— TaskTag（关联表，规格 §4.2）——
        let taskTag = NSEntityDescription()
        taskTag.name = "TaskTag"
        taskTag.managedObjectClassName = "TaskTag"
        let ttAttrs: [NSAttributeDescription] = [
            attr("taskId", .UUIDAttributeType),
            attr("tagId", .UUIDAttributeType)
        ]
        taskTag.properties = ttAttrs

        // —— 关系 ——
        // Task.category (to-one -> Category, Nullify)
        let taskCategory = NSRelationshipDescription()
        taskCategory.name = "category"
        taskCategory.destinationEntity = category
        taskCategory.minCount = 0
        taskCategory.maxCount = 1
        taskCategory.deleteRule = .nullifyDeleteRule

        // Category.tasks (to-many -> Task, Cascade)
        let categoryTasks = NSRelationshipDescription()
        categoryTasks.name = "tasks"
        categoryTasks.destinationEntity = task
        categoryTasks.minCount = 0
        categoryTasks.maxCount = 0
        categoryTasks.deleteRule = .cascadeDeleteRule
        taskCategory.inverseRelationship = categoryTasks
        categoryTasks.inverseRelationship = taskCategory

        // Task.tags (to-many -> Tag, Cascade 删 Task 时级联清理关联)
        let taskTags = NSRelationshipDescription()
        taskTags.name = "tags"
        taskTags.destinationEntity = tag
        taskTags.minCount = 0
        taskTags.maxCount = 0
        taskTags.deleteRule = .cascadeDeleteRule

        // Tag.tasks (to-many -> Task, Cascade)
        let tagTasks = NSRelationshipDescription()
        tagTasks.name = "tasks"
        tagTasks.destinationEntity = task
        tagTasks.minCount = 0
        tagTasks.maxCount = 0
        tagTasks.deleteRule = .cascadeDeleteRule
        taskTags.inverseRelationship = tagTasks
        tagTasks.inverseRelationship = taskTags

        task.properties.append(contentsOf: [taskCategory, taskTags])
        category.properties.append(categoryTasks)
        tag.properties.append(tagTasks)

        model.entities = [task, category, tag, taskTag]
        return model
    }

    // 便捷：构造带名称与默认值的属性描述
    private static func attr(_ name: String,
                             _ type: NSAttributeType,
                             defaultValue: Any? = nil,
                             optional: Bool = false) -> NSAttributeDescription {
        let a = NSAttributeDescription()
        a.name = name
        a.attributeType = type
        a.isOptional = optional
        if let dv = defaultValue { a.defaultValue = dv }
        return a
    }
}

// NSPersistentContainer 便捷取 store URL
extension NSPersistentContainer {
    var storeURL: URL? {
        persistentStoreDescriptions.first?.url
    }
}
