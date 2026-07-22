// CategorySeed.swift
// 预设分类固定 UUID（规格 §3.2，禁止随机）。首次启动写入种子数据。

import Foundation

enum CategorySeed {
    /// 工作
    static let workId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
    /// 生活
    static let lifeId = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
    /// 学习
    static let studyId = UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
    /// 其他（默认归类，规格 §3.2）
    static let otherId = UUID(uuidString: "00000000-0000-0000-0000-000000000004")!

    /// 全部预设：[id, name, isPreset]
    static let presets: [(UUID, String, Bool)] = [
        (workId, "工作", true),
        (lifeId, "生活", true),
        (studyId, "学习", true),
        (otherId, "其他", true)
    ]
}
