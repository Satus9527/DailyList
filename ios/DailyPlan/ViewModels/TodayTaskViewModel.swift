// TodayTaskViewModel.swift
// 今日待办 ViewModel：聚合 F1（文字记录）/ F5（完成/编辑/删除/拖拽）/ F6（持久化）与 X/Y 进度。
// UI 经 ViewModel 调 Repository，领域层不依赖具体存储（架构 §7.2）。

import AVFoundation
import Combine
import CoreData
import Foundation
import SwiftUI
import UIKit
import UserNotifications

final class TodayTaskViewModel: ObservableObject {
    // MARK: 发布状态
    @Published var tasks: [TaskDTO] = []
    @Published var inputText: String = ""
    @Published var errorMessage: String?

    // —— M5 F4 筛选（§3.1 / §4.2）：首页筛选栏状态，与各区块叠加生效，空条件=全部 ——
    @Published var filter: TaskFilter = .init()
    /// 全部分类（筛选栏 + 编辑页用）
    @Published var categories: [CategoryDTO] = []
    /// 全部标签（筛选栏标签多选用）
    @Published var allTags: [TagDTO] = []
    /// 任务 id → 标签 id 集合（按标签筛选的内存路径映射，§3.4，由 reload 刷新）
    private var taskTagIds: [UUID: Set<UUID>] = [:]

    // —— F2 语音（M3）发布状态 ——
    @Published var isVoiceActive = false       // 语音听写进行中
    @Published var voiceAvailable = false       // 语音按钮是否可用（授权/能力判断）
    @Published var voicePartialText = ""        // 实时中间文本（仅展示，不落库）
    @Published var voiceToast: String?          // 降级/提示 Toast 文案（如「改用文字输入」）

    // —— M4 D4：通知/麦克风权限状态（规格 §2）——
    @Published var notificationAuthStatus: UNAuthorizationStatus = .notDetermined
    @Published var micAuthStatus: AVAudioSession.RecordPermission = .undetermined
    /// 用户本次会话是否临时收起横幅（不持久化，避免掩盖风险，规格 §2.2）
    @Published var bannerDismissedThisSession = false
    /// D4 横幅可见：仅当通知未授权（含 provisional/denied/notDetermined，视为「可能不达」）时显示。
    var showNotificationBanner: Bool {
        notificationAuthStatus != .authorized && !bannerDismissedThisSession
    }

    // —— M4 R-4：语音输入开关（持久化 UserDefaults，默认开）——
    @Published var voiceInputEnabled: Bool

    /// 进度 X / Y（规格 R-U3 / AC-15）：X=已完成数，Y=当日总数
    var doneCount: Int { tasks.filter { $0.isDone }.count }
    var totalCount: Int { tasks.count }

    private let repository: TaskRepository
    private let context: NSManagedObjectContext

    // —— M5 F4：分类/标签读取（筛选栏与编辑页共用）——
    private let categoryRepo: CategoryRepository
    private let tagRepo: TagRepository

    /// 通知调度器（F3，M2）：完成即取消、取消完成恢复、设/改提醒排程均经此。
    private let scheduler: ReminderScheduler

    // —— F2 语音层（M3）——
    private let asr: NativeASRController
    private let splitter: VoiceTaskSplitter
    /// 合并/拆分用例（F2 待确认项 5，基础版，规格 §7）
    let mergeSplit: TaskMergeSplitUseCase
    /// 语音基础能力（授权 + 配置非空），与 voiceInputEnabled 共同决定按钮可用（R-4 设置页开关）
    private let voiceCapable: Bool

    /// 语音输入开关的持久化键（R-4 设置页）
    private let voiceInputKey = "dailyplan.voiceInputEnabled"

    init(context: NSManagedObjectContext, scheduler: ReminderScheduler, config: ASRSplitConfig) {
        self.context = context
        self.repository = LocalTaskRepository(context: context)
        self.categoryRepo = LocalCategoryRepository(context: context)
        self.tagRepo = LocalTagRepository(context: context)
        self.scheduler = scheduler
        // 配置缺失时降级为「语音不可用」，但绝不硬编码拆分常量（P0-4 精神）
        let effectiveConfig = config
        self.asr = NativeASRController(config: effectiveConfig)
        self.splitter = VoiceTaskSplitter(config: effectiveConfig, repository: repository)
        self.mergeSplit = NativeTaskMergeSplitUseCase(repository: repository)
        // 配置缺失（空标点集）视为语音不可用，避免无意义启动（P0-4：绝不硬编码常量）
        self.voiceCapable = asr.isAvailable && !effectiveConfig.splitPunctuation.isEmpty
        // 从 UserDefaults 读取语音输入开关（R-4 设置页；默认开）
        self.voiceInputEnabled = UserDefaults.standard.object(forKey: "dailyplan.voiceInputEnabled") as? Bool ?? true
        self.voiceAvailable = voiceCapable && self.voiceInputEnabled
        // 降级回调：关语音按钮 + Toast 引导文字（规格 §6）
        asr.onDegrade = { [weak self] reason in
            self?.handleDegrade(reason)
        }
        reload()
    }

    // MARK: - F6 加载（从库而非内存，规格 AC-17）
    // M4 S5：改用展示日取数 tasksForDisplayDay，把跨 0 点（remindAt 所属日 == 今日）任务并入当日列表，
    // 再按 displayDay 二次过滤（不改动 date 存储）。
    func reload() {
        let today = DateFormatter.todayDateString()
        do {
            let all = try repository.tasksForDisplayDay(today)
            tasks = all.filter { $0.displayDay == today }
        } catch {
            errorMessage = "加载待办失败：\(error.localizedDescription)"
        }
        loadCategories()
        loadAllTags()
        loadTaskTagIds()   // 刷新「任务→标签」映射，供按标签筛选（§3.4）
        refreshPermissions()   // 每次加载顺带刷新权限状态（D4 横幅）
    }

    // MARK: - M5 F4 筛选支持

    private func loadCategories() {
        do { categories = try categoryRepo.all() }
        catch { categories = [] }
    }

    private func loadAllTags() {
        do { allTags = try tagRepo.all() }
        catch { allTags = [] }
    }

    /// 刷新「任务 id → 标签 id 集合」映射（内存过滤路径，规格 §3.4）。
    private func loadTaskTagIds() {
        var map: [UUID: Set<UUID>] = [:]
        for t in tasks {
            if let tg = try? repository.tags(forTaskId: t.id) {
                map[t.id] = Set(tg.map { $0.id })
            } else {
                map[t.id] = []
            }
        }
        taskTagIds = map
    }

    /// 对给定列表套用当前筛选（空条件=原样返回，§6）。
    func filteredTasks(from list: [TaskDTO]) -> [TaskDTO] {
        guard !filter.isEmpty else { return list }
        return list.filter { filter.matches($0, tagIds: taskTagIds[$0.id] ?? []) }
    }

    /// 三区块筛选视图（筛选栏与各区块统一生效，§4.3）
    var filteredMissedTasks: [TaskDTO] { filteredTasks(from: missedTasks) }
    var filteredInProgressTasks: [TaskDTO] { filteredTasks(from: inProgressTasks) }
    var filteredDoneTasks: [TaskDTO] { filteredTasks(from: doneTasks) }

    // MARK: - M4 D3 分区（首页三区块，均由 tasks 派生）
    /// 错过的提醒（应响未响）：未完成 + remindAt 已过当前时刻（displayDay==今日由 tasks 保证）。
    var missedTasks: [TaskDTO] {
        let now = Date()
        tasks.filter { !$0.isDone && $0.remindAt != nil && $0.remindAt! < now }
    }
    /// 进行中（未完成且非错过项）。
    var inProgressTasks: [TaskDTO] {
        tasks.filter { !$0.isDone && !missedTasks.contains($0) }
    }
    /// 已完成（置底）。
    var doneTasks: [TaskDTO] {
        tasks.filter { $0.isDone }
    }

    // MARK: - M4 D4 权限检测与系统设置深链
    /// 刷新通知/麦克风授权状态（规格 §2.1）。通知经 getNotificationSettings 异步回填。
    func refreshPermissions() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                self?.notificationAuthStatus = settings.authorizationStatus
            }
        }
        micAuthStatus = AVAudioSession.sharedInstance().recordPermission
    }

    /// 深链到本 App 系统设置（通知/麦克风权限统一入口，规格 §2.3）。
    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - M4 R-4 语音输入开关
    /// 设置页切换语音输入开关并持久化（默认开；关闭后首页语音按钮禁用）。
    func setVoiceInputEnabled(_ on: Bool) {
        voiceInputEnabled = on
        UserDefaults.standard.set(on, forKey: voiceInputKey)
        voiceAvailable = voiceCapable && on
    }

    // MARK: - F1 文字记录
    /// 将输入框文本加入当日列表。去空白；≤500 字，超出截断并提示（AC-29 / R-X5）。
    func addFromInput() {
        let raw = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else { return }   // 空内容不生成（AC-1 / R-X4）

        let title: String
        if raw.count > 500 {
            // 超出截断至 500 字并提示（AC-29）
            title = String(raw.prefix(500))
            errorMessage = "内容超过 500 字，已截断。"
        } else {
            title = raw
        }

        let maxOrder = (tasks.map { $0.sortOrder }.max() ?? -1) + 1
        let task = TaskDTO.makeNew(title: title, sortOrder: maxOrder)
        do {
            try repository.add(task)
            inputText = ""        // 清空输入框（AC-1）
            // F3 接线点：新增时若已带提醒（未来时刻），自动排程（当前输入框未设提醒，为预留接线）
            if let ra = task.remindAt, ra > Date() {
                scheduler.schedule(for: task)
            }
            reload()
        } catch {
            errorMessage = "添加失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 完成
    func toggleDone(_ task: TaskDTO) {
        do {
            if task.isDone {
                // 取消完成（AC-26）：isDone=false, doneAt=nil，回到进行中
                var updated = task
                updated.isDone = false
                updated.doneAt = nil
                try repository.update(updated)
                // AC-26：取消完成时若仍有未触发提醒，恢复其排程（提前/到点/重复）
                if let ra = updated.remindAt, ra > Date() {
                    scheduler.schedule(for: updated)
                }
            } else {
                // 标记完成（AC-14）
                try repository.markDone(task.id, at: Date())
                // AC-9（R-E7）：完成即取消该任务所有未触发后续提醒（避免已完成还响）
                scheduler.cancel(for: task.id)
            }
            reload()
        } catch {
            errorMessage = "操作失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 行内编辑 title
    func editTitle(_ task: TaskDTO, to newTitle: String) {
        let trimmed = newTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let title = trimmed.count > 500 ? String(trimmed.prefix(500)) : trimmed
        var updated = task
        updated.title = title
        do {
            try repository.update(updated)
            reload()
        } catch {
            errorMessage = "编辑失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 删除
    func delete(_ task: TaskDTO) {
        do {
            try repository.delete(task.id)
            // 删除待办时一并取消其提醒（避免残留通知指向已删任务）
            scheduler.cancel(for: task.id)
            reload()
        } catch {
            errorMessage = "删除失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F3 提醒接线点（供 F4 编辑/改期 UI 调用）
    /// 设置或改期提醒（AC-28 / R-E11）：schedule 内含 cancel，按新 T 幂等重建，不重复通知。
    /// 若 remindAt 为空或已过去，则取消该任务全部提醒。
    func applyReminderSetting(for task: TaskDTO) {
        if let ra = task.remindAt, ra > Date() {
            scheduler.schedule(for: task)
        } else {
            scheduler.cancel(for: task.id)
        }
    }

    // MARK: - F3 提醒设置（UI 调用，M2-D，Task #36）
    /// 为单条待办设置/修改提醒：先更新领域模型（remindAt / leadMinutes / repeatCount），
    /// 再 repository.update 持久化；随后据新值排程。
    /// - 编辑 remindAt 时由 `scheduler.schedule` 内部先 `cancel` 再登记（幂等，改期不重复，AC-28 / R-E11）。
    /// - remindAt=nil（关闭提醒）或时间已过期 → `scheduler.cancel` 移除该任务全部 pending。
    func saveReminder(taskId: UUID, remindAt: Date?, leadMinutes: Int, repeatCount: Int) {
        // 先刷新：确保读到 F4 编辑页刚落库的最新 categoryId/priority（由 editVM.save 经同一 context 写入），
        // 避免用旧内存副本整体 update 时把组织字段覆盖回旧值。
        reload()
        guard let idx = tasks.firstIndex(where: { $0.id == taskId }) else { return }
        var task = tasks[idx]
        task.remindAt = remindAt
        task.leadMinutes = leadMinutes
        task.repeatCount = repeatCount
        task.updatedAt = Date()
        do {
            try repository.update(task)
            if let ra = remindAt, ra > Date() {
                scheduler.schedule(for: task)
            } else {
                scheduler.cancel(for: task.id)
            }
            reload()
        } catch {
            errorMessage = "保存提醒失败：\(error.localizedDescription)"
        }
    }

    // MARK: - F5 拖拽重排（AC-16，M4 S5 仅作用于「进行中」展示区）
    /// 仅对当前展示的「进行中」区块重排并持久化（跨 0 点项也参与当日展示排序；不改动 date）。
    func reorderInProgress(from source: IndexSet, to destination: Int) {
        var reordered = inProgressTasks
        reordered.move(fromOffsets: source, toOffset: destination)
        let ids = reordered.map { $0.id }
        do {
            try repository.reorder(ids: ids)
            reload()
        } catch {
            errorMessage = "排序失败：\(error.localizedDescription)"
        }
    }

    // MARK: - M4 R-4 合并到上一条（AC-5，调用 M3 TaskMergeSplitUseCase）
    /// 取「进行中」列表中紧邻当前条的前一条（同展示顺序）作为参数，与当前条合并。
    /// 首条不可合并到上一条（无前序则忽略）。
    func mergeWithPrevious(_ task: TaskDTO) {
        let inProg = inProgressTasks
        guard let idx = inProg.firstIndex(where: { $0.id == task.id }), idx > 0 else { return }
        let prev = inProg[idx - 1]
        merge([prev, task])   // 复用 M3 合并用例，落库后 reload 刷新（规格 §4.2）
    }

    // MARK: - F2 语音（M3）

    /// 麦克风按钮：已在进行则停止；否则先确认授权再开始持续听。
    /// 未授权 → 关闭按钮 + Toast 引导文字（规格 §6）。
    func toggleVoice() {
        if isVoiceActive {
            stopVoice()
        } else {
            Task { await beginVoiceIfPermitted() }
        }
    }

    @MainActor
    private func beginVoiceIfPermitted() async {
        let state = await asr.requestPermission()
        guard state == .granted else {
            voiceAvailable = false
            voiceToast = "语音不可用，请改用文字输入"   // R-X1：记录流不中断，文字录入仍可用
            return
        }
        voiceAvailable = asr.isAvailable
        startVoice()
    }

    /// 开始持续听：onPartial 实时展示，onFinal → 领域层按 JSON 配置切分并落库（source=.voice）。
    private func startVoice() {
        voiceToast = nil
        do {
            try asr.start(
                onPartial: { [weak self] text in
                    self?.voicePartialText = text          // 仅展示
                },
                onFinal: { [weak self] text in
                    guard let self else { return }
                    self.splitter.commitFinalSegment(text) // 标点切分 + 落库（P0-4）
                    self.voicePartialText = ""
                    self.reload()                          // 落库触发列表刷新
                }
            )
            isVoiceActive = true
        } catch {
            // 启动失败（如未授权/音频会话异常）→ 降级文字输入
            voiceAvailable = false
            voiceToast = "语音启动失败，请改用文字输入"
        }
    }

    /// 停止听写：stop() 内部会对尾句再回调一次 onFinal → splitter 落库，随后释放音频。
    func stopVoice() {
        asr.stop()
        isVoiceActive = false
        voicePartialText = ""
    }

    /// 用户手动「落一条」（始终优先，R-E2）：把当前缓冲按 JSON 配置切分落库，并清空缓冲避免重复。
    func commitManualSegment() {
        let buffered = asr.bufferedText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !buffered.isEmpty else { return }
        splitter.commitFinalSegment(buffered)
        asr.clearBuffer()
        voicePartialText = ""
        reload()
    }

    /// 停止并保存当前已识别文本为「文字条目」（降级兜底，规格 §6.2）：source=.text，避免丢失已说内容。
    func saveBufferedAsText() {
        let buffered = asr.bufferedText.trimmingCharacters(in: .whitespacesAndNewlines)
        asr.stop()
        isVoiceActive = false
        voicePartialText = ""
        guard !buffered.isEmpty else { return }   // 空则不落（R-X4）
        let title = buffered.count > 500 ? String(buffered.prefix(500)) : buffered
        let task = TaskDTO.makeNew(title: title, source: .text)   // 视为文字条目
        do {
            try repository.add(task)
            reload()
        } catch {
            voiceToast = "保存失败：\(error.localizedDescription)"
        }
    }

    /// 失败降级（规格 §6.2）：关语音按钮 + Toast 引导文字；记录流不中断。
    private func handleDegrade(_ reason: VoiceDegradeReason) {
        asr.stop()
        isVoiceActive = false
        voiceAvailable = false
        voicePartialText = ""
        voiceToast = "语音暂不可用，请改用文字输入"   // R-X1：文字记录仍可用
        // 本地日志标记 VOICE_DEGRADED（仅本地，不上传账号）
    }

    /// 合并相邻多条（F2 待确认项 5，基础版）
    func merge(_ tasks: [TaskDTO]) {
        do { try mergeSplit.merge(tasks); reload() }
        catch { errorMessage = "合并失败：\(error.localizedDescription)" }
    }

    /// 在第 index 处拆分一条（F2 待确认项 5，基础版）
    func split(_ task: TaskDTO, at index: Int) {
        do { try mergeSplit.split(task, at: index); reload() }
        catch { errorMessage = "拆分失败：\(error.localizedDescription)" }
    }
}
