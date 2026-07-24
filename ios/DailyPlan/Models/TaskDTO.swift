// TaskDTO.swift
// 纯领域模型（与 Core Data 解耦），作为 Repository 返回类型。
// 由 NSManagedObject 映射而来，UI / ViewModel 只认这个类型（架构 §7.2：领域层不依赖具体存储）。

import Foundation

struct TaskDTO: Identifiable, Equatable {
    let id: UUID
    var title: String
    var date: String              // "yyyy-MM-dd"，本地时区当日（规格 §1.2）
    var categoryId: UUID?
    var priority: Priority
    var isDone: Bool
    var doneAt: Date?
    var remindAt: Date?
    var leadMinutes: Int          // 默认 10（P0-2）
    var repeatCount: Int          // 默认 3（P0-2），0=关闭重复
    var sortOrder: Int
    var source: TaskSource
    var updatedAt: Date
    var syncState: SyncState

    /// 构造一条新待办的最小便捷方法（F1 文字记录默认参数）。
    /// - 默认：date=今天、categoryId=「其他」预设、priority=中、source=.text、
    ///   leadMinutes=10、repeatCount=3、syncState=.local、updatedAt=now。
    static func makeNew(
        title: String,
        date: String = DateFormatter.todayDateString(),
        categoryId: UUID? = CategorySeed.otherId,
        priority: Priority = .default,
        source: TaskSource = .text,
        sortOrder: Int = 0
    ) -> TaskDTO {
        TaskDTO(
            id: UUID(),
            title: title,
            date: date,
            categoryId: categoryId,
            priority: priority,
            isDone: false,
            doneAt: nil,
            remindAt: nil,
            leadMinutes: 10,
            repeatCount: 3,
            sortOrder: sortOrder,
            source: source,
            updatedAt: Date(),
            syncState: .local
        )
    }
}

// MARK: - M4 S5 展示日（跨 0 点重归类，仅展示层，不改动 date 存储）

extension TaskDTO {
    /// 展示日：跨 0 点时取 remindAt 所属日，否则取 date（规格 §3.2）。
    var displayDay: String {
        if let ra = remindAt {
            let raDay = DateFormatter.todayDateString(ra)
            if raDay != date { return raDay }   // 跨 0 点：归触发日
        }
        return date                              // 普通：归创建日
    }

    /// 是否跨 0 点：remindAt 所属日 ≠ date。
    var isCrossDay: Bool {
        guard let ra = remindAt else { return false }
        return DateFormatter.todayDateString(ra) != date
    }
}

// MARK: - 日期工具（本地时区 yyyy-MM-dd）

extension DateFormatter {
    /// 设备本地时区的当日 "yyyy-MM-dd"（规格 §1.2）。
    static func todayDateString(_ date: Date = Date()) -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = TimeZone.current   // 设备本地时区（规格 §1.1）
        df.calendar = Calendar(identifier: .gregorian)
        return df.string(from: date)
    }
}
