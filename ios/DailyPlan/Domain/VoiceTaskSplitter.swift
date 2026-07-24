// VoiceTaskSplitter.swift
// 领域层自动拆分（F2，M3 Task #48，规格 §5）：消费 shared/asr_split_config.json，按标点切分并落库。
//
// 核心约束（P0-4）：splitPunctuation / splitPauseThresholdMs / includeEnumerationComma / includeNewline
// 全部来自 ASRSplitConfig，**禁止在代码中硬编码任何拆分常量**（含 "。！？；"、1200）。
// 领域层不依赖任何平台 API（不 import Speech / AVFoundation），仅依赖 ASRSplitConfig 与 TaskRepository。

import Foundation

final class VoiceTaskSplitter {

    private let config: ASRSplitConfig
    private let repository: TaskRepository
    private let punctuationSet: Set<String>

    init(config: ASRSplitConfig, repository: TaskRepository) {
        self.config = config
        self.repository = repository
        self.punctuationSet = Set(config.splitPunctuation)   // 仅取自 JSON
    }

    /// 平台层在「句末标点 / 长停顿边界 / onFinal / 用户手动落一条」时调用。
    /// 按配置切分（去尾标点、不含「、」与换行），每段非空 → repository.add(source=.voice)。
    func commitFinalSegment(_ text: String) {
        for seg in splitIntoSegments(text) where !seg.isEmpty {
            // title ≤ 500 字（R-X5 / AC-29）：超长截断（与 F1 一致）
            let title = seg.count > 500 ? String(seg.prefix(500)) : seg
            let task = TaskDTO.makeNew(title: title, source: .voice)   // 落库来源=语音，其余默认（今天/其他/中）
            try? repository.add(task)   // M1 协议；落库即触发 UI 刷新（@FetchRequest）
        }
    }

    // MARK: - 拆分核心（双端同语义，规格 §5.3）

    /// 按配置标点集切分；段尾标点丢弃；「、」与换行视 include* 决定是否并入当前条（不切）。
    private func splitIntoSegments(_ text: String) -> [String] {
        var segs: [String] = []
        var buf = ""
        for ch in text {
            let s = String(ch)
            if punctuationSet.contains(s) {
                // 命中拆分标点：把标点前内容作为一个段，标点丢弃（不进 title）
                let t = buf.trimmingCharacters(in: .whitespacesAndNewlines)
                if !t.isEmpty { segs.append(t) }
                buf = ""
            } else if s == "、" && !config.includeEnumerationComma {
                buf.append(s)                 // 顿号保留，并入当前条，不切
            } else if (s == "\n" || s == "\r") && !config.includeNewline {
                buf.append(s)                 // 换行保留，并入当前条，不切
            } else {
                buf.append(ch)
            }
        }
        // 尾段（无句末标点）也成条
        let tail = buf.trimmingCharacters(in: .whitespacesAndNewlines)
        if !tail.isEmpty { segs.append(tail) }
        return segs
    }
}
