// ASRSplitConfig.swift
// 双端共享 ASR 拆分配置解析（规格 §8）。M1 仅定义结构、F2 才消费。
// 重要：禁止在代码中硬编码拆分标点集或阈值，必须从 shared/asr_split_config.json 解析（P0-4）。

import Foundation

struct ASRSplitConfig: Codable {
    let configVersion: Int
    let splitPunctuation: [String]
    let splitPauseThresholdMs: Int
    let includeEnumerationComma: Bool
    let includeNewline: Bool
    // note 字段可选，便于兼容
    let note: String?

    /// 从 App 包内资源加载（拷贝自 /workspace/shared/asr_split_config.json 的同源副本）。
    /// 返回 nil 表示配置文件缺失——调用方应降级为规格默认集，但不得硬编码常量。
    static func loadFromBundle() -> ASRSplitConfig? {
        guard let url = Bundle.main.url(forResource: "asr_split_config", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(ASRSplitConfig.self, from: data)
    }
}
