// Enums.swift
// 双端一致的枚举原始值定义（规格 §2.2）。
// 持久化一律以 String rawValue 落库，禁止用整数 ordinal（规格 §1.4）。

import Foundation

/// 优先级：高 / 中（默认）/ 低
enum Priority: String, Codable, CaseIterable, Identifiable {
    case high = "high"
    case medium = "medium"
    case low = "low"

    var id: String { rawValue }

    /// 默认优先级（规格 §2.1 默认 "medium"）
    static let `default`: Priority = .medium

    /// 用于 UI 展示的中文名
    var displayName: String {
        switch self {
        case .high: return "高"
        case .medium: return "中"
        case .low: return "低"
        }
    }
}

/// 待办来源：语音 / 文字 / 模板 / 补生成
enum TaskSource: String, Codable {
    case voice = "voice"       // 语音（F2）
    case text = "text"         // 文字（F1）
    case template = "template" // 模板（v1.1 F8）
    case makeup = "makeup"     // 补生成（v1.1 F8）
}

/// 同步状态预埋（v1.1 消费，M1 不消费，规格 §1.6）
enum SyncState: String, Codable {
    case local = "local"   // 默认，仅本地
    case dirty = "dirty"   // 已变更待同步
    case synced = "synced" // 已同步
}
