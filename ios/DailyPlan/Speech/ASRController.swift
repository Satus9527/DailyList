// ASRController.swift
// iOS 语音层（F2，M3 Task #48）：ASRController 协议 + NativeASRController 实现 + 长停顿监测。
// 仅负责「持续听 → 产出 partial/final 文本流」与权限/可用性；切分、落库不在本层（见 Domain/VoiceTaskSplitter）。
//
// 关键约束（设计规格_M3语音层.md §2/§3）：
// - 离线优先（requiresOnDeviceRecognition=true）→ 失败联网回退（P0-1）。
// - 长停顿阈值取自 ASRSplitConfig.splitPauseThresholdMs，**禁止硬编码 1200**（P0-4）。
// - 失败降级（权限拒/无网无离线包/识别失败）→ 通过 onDegrade 回调通知上层关闭语音 + Toast（§6）。

import AVFoundation
import Foundation
import Speech

// MARK: - 权限状态（双端一致枚举，规格 §2.1）

enum PermissionState: Equatable {
    case notDetermined   // 未请求过
    case granted         // 已授权
    case denied          // 用户拒绝（麦克风 / 语音识别）
    case restricted      // 系统限制（如家长控制）
    case unavailable     // 设备不支持 / 语言包缺失到完全不可用
}

// MARK: - 降级原因（仅用于上层展示 Toast / 埋点调用点，不上传账号）

enum VoiceDegradeReason: Equatable {
    case permissionDenied   // 授权被拒
    case offlineUnavailable // 离线包缺失且无网可回退
    case asrFailed          // 识别失败（联网回退后仍失败）
    case audioSession       // 音频会话异常
}

// MARK: - 平台无关 ASR 抽象（规格 §2.1，async/await 风格）

protocol ASRController {
    /// 当前是否可用（语言包/能力层判断；不触发权限弹窗）
    var isAvailable: Bool { get }

    /// 请求授权（麦克风 + 语音识别）。首次调用弹系统授权；返回最终状态。
    func requestPermission() async -> PermissionState

    /// 开始持续听。
    /// - onPartial: 流式中间结果（边说边显，实时刷新输入框），**仅用于展示，不作落库依据**。
    /// - onFinal:   一个确定句段边界（句末标点 / 长停顿 / 端侧识别结束）产出的最终文本，调用方据此落库。
    /// 实现内部维护 AVAudioEngine / SpeechRecognizer 持续喂缓冲；停止需调 stop()。
    func start(onPartial: @escaping (String) -> Void,
               onFinal: @escaping (String) -> Void) throws

    /// 停止听写并释放音频会话。停止后若仍有一段未达句末标点的尾句，实现再回调一次 onFinal（尾句）再结束。
    func stop()
}

// MARK: - iOS 原生实现（SFSpeechRecognizer + AVAudioEngine）

final class NativeASRController: ASRController {

    // 系统框架对象
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let speechRecognizer: SFSpeechRecognizer?

    // 拆分配置（来自 shared/asr_split_config.json 单例，禁止硬编码）
    private let splitConfig: ASRSplitConfig

    // 回调句柄
    private var onPartial: ((String) -> Void)?
    private var onFinal: ((String) -> Void)?

    /// 降级回调（协议外扩展，专供 UI 层关闭语音按钮 + Toast，规格 §6）
    var onDegrade: ((VoiceDegradeReason) -> Void)?

    // 长停顿监测（阈值取自配置，禁止硬编码）
    private var silenceMonitor: SilentPauseMonitor?

    // 增量去重状态：SFSpeechRecognizer 的 final 结果是累积全文，
    // 用 segments 计数只提交「新增片段」，避免尾句/停顿导致重复落库（R-E2）。
    private let stateQueue = DispatchQueue(label: "voice.asr.state")
    private var processedSegmentCount = 0
    private var pendingText = ""          // 自上次 final 以来累积的待落库文本
    private var latestPartialText: String?

    // 离线→联网回退标记（P0-1）
    private var didFallbackToOnline = false

    // 生成令牌：每次新建/回退/停止任务自增，旧任务迟到的回调（取消错误等）据此失效，避免误回退/误降级
    private var generation = 0

    init(config: ASRSplitConfig) {
        self.splitConfig = config
        self.speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    }

    // 当前是否可用：设备能力 + 中文语言可用性；不触发授权弹窗
    var isAvailable: Bool {
        speechRecognizer?.isAvailable ?? false
    }

    // MARK: - 权限请求

    func requestPermission() async -> PermissionState {
        // 1) 语音识别授权
        let speechAuth = await withCheckedContinuation { (cont: CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>) in
            SFSpeechRecognizer.requestAuthorization { status in cont.resume(returning: status) }
        }
        guard speechAuth == .authorized else {
            return mapSpeechAuth(speechAuth)
        }
        // 2) 麦克风授权（AVAudioSession 录音权限；iOS 17+ 用 AVAudioApplication，否则旧 API）
        let micAuth: Bool
        if #available(iOS 17.0, *) {
            let status = await AVAudioApplication.requestRecordPermission()
            micAuth = (status == .granted)
        } else {
            micAuth = await withCheckedContinuation { (cont: CheckedContinuation<Bool, Never>) in
                AVAudioSession.sharedInstance().requestRecordPermission { granted in cont.resume(returning: granted) }
            }
        }
        guard micAuth else { return .denied }
        return .granted
    }

    private func mapSpeechAuth(_ status: SFSpeechRecognizerAuthorizationStatus) -> PermissionState {
        switch status {
        case .authorized:           return .granted
        case .denied:               return .denied
        case .restricted:           return .restricted
        case .notDetermined:        return .notDetermined
        @unknown default:           return .unavailable
        }
    }

    // MARK: - 开始持续听

    func start(onPartial: @escaping (String) -> Void,
               onFinal: @escaping (String) -> Void) throws {
        self.onPartial = onPartial
        self.onFinal = onFinal

        // 启动前必须已授权（规格 §2.3）：未授权直接抛错，由上层降级，不静默无反应。
        guard SFSpeechRecognizer.authorizationStatus() == .authorized,
              AVAudioSession.sharedInstance().recordPermission == .granted else {
            throw NSError(domain: "NativeASRController", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "语音/麦克风未授权"])
        }

        // 重置增量状态
        stateQueue.sync {
            processedSegmentCount = 0
            pendingText = ""
            latestPartialText = nil
        }
        didFallbackToOnline = false
        generation += 1
        let myGen = generation

        // 音频会话配置
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            onDegrade?(.audioSession)
            throw error
        }

        // 识别请求：持续听 + 流式 partial + 听写提示
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true        // 流式中间结果
        req.taskHint = .dictation                      // 听写场景
        req.requiresOnDeviceRecognition = true        // 离线优先（P0-1）
        self.request = req

        let inputNode = audioEngine.inputNode
        let fmt = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: fmt) { [weak self] buffer, _ in
            self?.request?.append(buffer)
            self?.silenceMonitor?.feed(buffer)        // 驱动长停顿检测
        }
        audioEngine.prepare()
        try audioEngine.start()

        // 长停顿监测：阈值取自配置（禁止硬编码 1200，P0-4）
        let monitor = SilentPauseMonitor(thresholdMs: splitConfig.splitPauseThresholdMs) { [weak self] in
            self?.flushFinalBoundary()                // 长停顿 > 阈值 → 把当前缓冲作为 final 边界吐出
        }
        self.silenceMonitor = monitor
        monitor.start()

        // 启动识别任务（统一回调处理；myGen 限定仅当前代任务回调有效）
        task = speechRecognizer?.recognitionTask(with: req) { [weak self, myGen] result, error in
            guard let self, self.generation == myGen else { return }
            self.handleRecognitionResult(result: result, error: error)
        }
    }

    // MARK: - 识别结果统一处理（增量去重 + 离线回退）

    private func handleRecognitionResult(result: SFSpeechRecognitionResult?, error: Error?) {
        if let result {
            let full = result.bestTranscription.formattedString
            // 实时展示完整中间文本（仅展示，不落库）
            let onPartial = self.onPartial
            DispatchQueue.main.async { onPartial?(full) }

            // 增量去重：只处理「新增的 segments」，避免 final 累积全文导致重复落库
            let segs = result.bestTranscription.segments
            var newText = ""
            stateQueue.sync {
                if segs.count > processedSegmentCount {
                    let slice = segs[processedSegmentCount..<segs.count]
                    newText = slice.map { $0.substring }.joined()
                    processedSegmentCount = segs.count
                }
            }

            if result.isFinal {
                // 端侧识别结束：把「pending + 本段」作为一次 final 边界交给领域层落库
                let finalChunk = (pendingText + newText)
                stateQueue.sync {
                    pendingText = ""
                    latestPartialText = nil
                    processedSegmentCount = 0
                }
                let onFinal = self.onFinal
                DispatchQueue.main.async { onFinal?(finalChunk) }
            } else if !newText.isEmpty {
                // 非终态：累积到 pending，供长停顿/手动「落一条」/尾句成条使用
                stateQueue.sync {
                    pendingText += newText
                    latestPartialText = pendingText
                }
            }
        }

        if let error {
            handleRecognitionError(error)
        }
    }

    // MARK: - 失败降级链路（P0-1 + §6）

    private func handleRecognitionError(_ error: Error) {
        let ns = error as NSError
        // 离线优先失败时，先联网回退一次（requiresOnDeviceRecognition=false 重建请求）
        if !didFallbackToOnline {
            didFallbackToOnline = true
            // 埋点调用点（仅标注、不上传账号）：voice_error(reason: offline→online_fallback)
            retryWithOnlineRecognition()
            return
        }
        // 联网仍失败或不可恢复 → 降级（关语音 + Toast，规格 §6.2）
        // 本地日志标记：VOICE_DEGRADED，仅本地，不上传账号
        onDegrade?(.asrFailed)
        _ = ns   // 保留 error 供上层调试
    }

    private func retryWithOnlineRecognition() {
        guard let recognizer = speechRecognizer else {
            onDegrade?(.offlineUnavailable)
            return
        }
        generation += 1
        let myGen = generation
        // 回退时重置增量状态：新识别任务片段从 0 计，避免旧计数导致新片段被丢弃
        stateQueue.sync {
            processedSegmentCount = 0
            pendingText = ""
            latestPartialText = nil
        }
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        req.taskHint = .dictation
        req.requiresOnDeviceRecognition = false        // 联网回退（P0-1）
        self.request = req
        // 复用仍在运行的 audioEngine 输入缓冲，继续喂新请求
        self.task = recognizer.recognitionTask(with: req) { [weak self, myGen] result, error in
            guard let self, self.generation == myGen else { return }
            self.handleRecognitionResult(result: result, error: error)
        }
    }

    // MARK: - 长停顿边界：把当前 pending 作为一次 final 吐出，并清空避免重复落库

    private func flushFinalBoundary() {
        let buffered = stateQueue.sync { () -> String? in
            let t = pendingText
            pendingText = ""
            latestPartialText = nil
            return t.isEmpty ? nil : t
        }
        guard let buffered, !buffered.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let onFinal = self.onFinal
        DispatchQueue.main.async { onFinal?(buffered) }
    }

    // MARK: - 对外：当前缓冲文本（供「停止并保存」/手动「落一条」）

    /// 当前已识别但未落库的缓冲文本（主线程读取，经 stateQueue 保护）
    var bufferedText: String {
        stateQueue.sync { pendingText }
    }

    /// 清空缓冲（手动「落一条」提交后调用，避免重复）
    func clearBuffer() {
        stateQueue.sync { pendingText = ""; latestPartialText = nil }
    }

    // MARK: - 停止

    func stop() {
        // 取出残留尾句（自上次 final 以来未落库的缓冲），stop 后须以一次 onFinal 收尾（R-X4 空则不回调）
        let tail = stateQueue.sync { () -> String? in
            let t = pendingText
            pendingText = ""
            latestPartialText = nil
            return t.isEmpty ? nil : t
        }
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        task = nil
        request = nil
        silenceMonitor?.stop()
        silenceMonitor = nil
        generation += 1   // 失效后续迟到回调（避免取消错误误触发回退/降级）
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        // 尾句收尾：把剩余缓冲作为一次 final 落库（规格 §2.3：stop 后尾句单独成条）
        if let tail {
            let onFinal = self.onFinal
            onFinal?(tail)
        }
    }
}
