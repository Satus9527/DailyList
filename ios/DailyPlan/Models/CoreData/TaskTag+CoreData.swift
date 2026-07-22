// TaskTag+CoreData.swift
// Task↔Tag 关联实体（规格 §4.2）。复合主键 (taskId, tagId)，级联删除由关系 Delete Rule 处理。

import CoreData

@objc(TaskTag)
public class TaskTag: NSManagedObject {
    @NSManaged public var taskId: UUID
    @NSManaged public var tagId: UUID

    convenience init(context: NSManagedObjectContext, taskId: UUID, tagId: UUID) {
        self.init(context: context)
        self.taskId = taskId
        self.tagId = tagId
    }
}
