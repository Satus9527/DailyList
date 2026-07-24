// SilentPauseMonitor.swift
// 长停顿监测（F2 自动拆分 · P0-4）：依据 ASRSplitConfig.splitPauseThresholdMs 检测静音时长。
//
// 约束：阈值仅来自配置，**禁止硬编码 1200**（规格 §5.4 规则 4）。领域层不直接计时。
// 实现：从 AVAudioPCMBuffer 计算 RMS，低于静音阈值持续超过 thresholdMs 即触发 onPauseExceeded。

import AVFoundation
import Foundation

final class SilentPauseMonitor {

    /// 静音判定阈值（RMS，信号级启发值，非拆分参数，不在 P0-4 禁止范围）
    private let silenceRmsThreshold: Float = 0.01

    private let thresholdMs: Int
    private let onPauseExceeded: () -> Void

    private var isMonitoring = false
    private var lastSpeechTime = Date()
    private let queue = DispatchQueue(label: "voice.pausemonitor")

    init(thresholdMs: Int, onPauseExceeded: @escaping () -> Void) {
        self.thresholdMs = thresholdMs
        self.onPauseExceeded = onPauseExceeded
    }

    /// 每收到一帧音频缓冲时调用，驱动静音累计。
    func feed(_ buffer: AVAudioPCMBuffer) {
        guard isMonitoring else { return }
        let rms = Self.rms(buffer)
        let now = Date()
        if rms >= silenceRmsThreshold {
            // 有语音：刷新「最近有声音」时刻
            queue.sync { lastSpeechTime = now }
        } else {
            // 静音：检查持续时长是否超过阈值（阈值来自配置）
            let silentMs = now.timeIntervalSince(queue.sync { lastSpeechTime }) * 1000
            if silentMs >= Double(thresholdMs) {
                queue.sync { lastSpeechTime = now }   // 重置，避免连续重复触发
                onPauseExceeded()
            }
        }
    }

    func reset() {
        queue.sync { lastSpeechTime = Date() }
    }

    func start() {
        queue.sync { isMonitoring = true; lastSpeechTime = Date() }
    }

    func stop() {
        queue.sync { isMonitoring = false }
    }

    // 计算单声道 RMS（均方根）能量
    private static func rms(_ buffer: AVAudioPCMBuffer) -> Float {
        guard let channelData = buffer.floatChannelData else { return 0 }
        let frames = Int(buffer.frameLength)
        guard frames > 0 else { return 0 }
        let data = channelData[0]
        var sum: Float = 0
        for i in 0..<frames { sum += data[i] * data[i] }
        return sqrt(sum / Float(frames))
    }
}
