// ReminderScheduler.swift
// iOS 通知层（F3 本地提醒，M2 Task #34）。
// 基于 UNUserNotificationCenter 实现「提前 / 到点 / 重复」触发点的排程、取消、推迟与启动/前台补偿。
// 不落独立 Reminder 表（规格 §6）；触发点完全由本层在内存 + 系统通知中心管理。
// 异常一律捕获记录、绝不崩溃（P0-3 精神）。

import Foundation
import UserNotifications

// MARK: - 触发点类型（与规格 §2.3 / §2.4 双端一致语义）

enum TriggerKind: String {
    case lead      // 提前（T - leadMinutes）
    case at        // 到点（T）
    case repeatN   // 重复第 i 次（T + i×10min）
    case snooze    // 推迟（T + 10min，单次）
}

/// 单个触发点：内存推导，不持久化（v1.1 才落独立表，规格 §0）。
struct TriggerPoint {
    let taskId: UUID
    let kind: TriggerKind
    let repeatIndex: Int      // 仅 repeatN 使用，其余为 0
    let fireAt: Date          // 绝对本地时刻（设备本地时区，规格 §1）

    /// 通知请求标识：以 taskId 为前缀（规格 §2.4），便于完成时批量取消。
    var identifier: String {
        switch kind {
        case .lead:    return "\(taskId.uuidString)__lead"
        case .at:      return "\(taskId.uuidString)__at"
        case .repeatN: return "\(taskId.uuidString)__rep\(repeatIndex)"
        case .snooze:  return "\(taskId.uuidString)__snooze"
        }
    }
}

// MARK: - 通知层协议（架构 §7.2，M2 补全 snooze）

protocol ReminderScheduler {
    /// 按 remindAt / leadMinutes / repeatCount 生成触发点并登记本地通知（规格 §2）。
    func schedule(for task: TaskDTO)
    /// 移除该 task 全部前缀的 pending requests（含 __lead/__at/__rep{i}/__snooze）。
    func cancel(for taskId: UUID)
    /// 启动/前台补偿：扫描未来 7 天未完成任务，幂等重建 pending（规格 §5）。
    func rescheduleAllPending()
    /// 推迟 10 分钟（单个 __snooze 实例，幂等覆盖）。
    func snooze(_ taskId: UUID)
}

// MARK: - iOS 实现

final class NativeReminderScheduler: ReminderScheduler {

    // 通知分类与 Action 标识（与规格 §3.2 一致）
    static let categoryIdentifier = "REMINDER_CATEGORY"
    static let actionComplete = "action_complete"   // 标记完成
    static let actionSnooze   = "action_snooze"     // 推迟 10 分钟

    /// 扫描上界（天），与 Android 一致（规格 §5 / REMINDER_SCAN_HORIZON_DAYS = 7）。
    static let scanHorizonDays: TimeInterval = 7

    // 重复次数（repeatCount）UI 可选上限：双端统一为 5（S1 口径对齐）。
    // 来源：Android `ReminderSettingSheet.repeatOptions = listOf(1,2,3,5)`，
    //       iOS `ReminderSettingView.repeatOptions = [1,2,3,5]`。
    // 说明：设计规格 §3.1 旧注 `REPEAT_MAX=3` 为 stale 口径；
    //       cancel 已改为动态枚举，覆盖任意 repeatCount（含 5），规格口径待架构统一，
    //       本文件不改动规格文档。`cancel` 不再硬编码上界，见下方 `cancelRequests(for:)`。

    private let center = UNUserNotificationCenter.current()
    private let repository: TaskRepository

    init(repository: TaskRepository) {
        self.repository = repository
    }

    // MARK: - 触发点生成（平台无关算法的 Swift 表达，规格 §2.2）
    // 规则：leadMinutes>0 → 提前点；总是 → 到点；repeatCount>0 → 重复 i=1..R（每次 +10min）。
    private func buildTriggerPoints(_ task: TaskDTO) -> [TriggerPoint] {
        guard let t = task.remindAt else { return [] }   // 未设提醒 → 无触发点
        var pts: [TriggerPoint] = []
        if task.leadMinutes > 0 {
            pts.append(.init(taskId: task.id, kind: .lead, repeatIndex: 0,
                             fireAt: t.addingTimeInterval(-Double(task.leadMinutes) * 60)))
        }
        pts.append(.init(taskId: task.id, kind: .at, repeatIndex: 0, fireAt: t))
        if task.repeatCount > 0 {
            for i in 1...task.repeatCount {
                pts.append(.init(taskId: task.id, kind: .repeatN, repeatIndex: i,
                                 fireAt: t.addingTimeInterval(Double(i) * 10 * 60)))
            }
        }
        return pts.sorted { $0.fireAt < $1.fireAt }   // 按触发时刻升序
    }

    /// 绝对本地时间组件：避开时区漂移（规格 §1 / §5），UNCalendarNotificationTrigger 用绝对时刻。
    private func dateComponents(_ date: Date) -> DateComponents {
        Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
    }

    // MARK: - schedule（排程）
    func schedule(for task: TaskDTO) {
        // 未授权则降级：仅记录、不登记，App 不崩溃（规格 §3.1 / P0-3）
        guard isAuthorizedOrDetermined() else {
            Self.log("[Reminder] 通知未授权，跳过排程 taskId=\(task.id)")
            return
        }
        let base = makeContent(task)
        let pts = buildTriggerPoints(task)   // 已按 task.repeatCount 动态生成 __rep{1..repeatCount}
        // 先动态移除该任务全部旧 pending（含历史更大 repeatCount 残留点，如 __rep{4,5}），
        // 移除完成后再登记新点，避免异步 cancel 与新登记竞态导致新点被误删（改期幂等不重复）。
        cancelRequests(for: task.id) { [weak self] in
            guard let self else { return }
            for pt in pts {
                // 跳过已过去的触发点，避免登记即触发历史点（补偿场景尤为常见）
                guard pt.fireAt > Date() else { continue }
                var c = base
                c.userInfo = ["taskId": task.id.uuidString,
                              "kind": pt.kind.rawValue,
                              "repeatIndex": pt.repeatIndex]
                let trigger = UNCalendarNotificationTrigger(dateMatching: self.dateComponents(pt.fireAt), repeats: false)
                let req = UNNotificationRequest(identifier: pt.identifier, content: c, trigger: trigger)
                self.center.add(req) { error in
                    if let error {
                        Self.log("[Reminder] 添加请求失败 id=\(pt.identifier) error=\(error.localizedDescription)")
                    }
                }
            }
            // 埋点调用点（仅标注，不写上报，规格 §6.5）：reminder_set
        }
    }

    /// 授权状态判定：authorized/provisional/未决定 均允许尝试登记；denied 则降级。
    private func isAuthorizedOrDetermined() -> Bool {
        switch center.authorizationStatus {
        case .authorized, .provisional, .notDetermined:
            return true
        case .denied, .ephemeral:
            return false
        @unknown default:
            return false
        }
    }

    private func makeContent(_ task: TaskDTO) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = "待办提醒"
        c.body = task.title
        c.categoryIdentifier = Self.categoryIdentifier   // 绑定带 Action 的分类
        c.sound = .default
        return c
    }

    // MARK: - cancel（取消）

    /// 动态全量取消（修复 M2 验收缺陷 D1 / 违反 AC-9）。
    /// 枚举通知中心内以 `taskId.uuidString` 为前缀的全部 pending 标识
    /// （覆盖 `__lead` / `__at` / `__rep{i}` 任意 i / `__snooze`），一次性移除。
    /// 无论 UI 设定的 repeatCount 多大（当前双端统一上限 = 5）都能彻底清除，
    /// 避免任务完成后残留重复点仍触发（AC-9）。
    ///
    /// 实现：通过 `getPendingNotificationRequests` 取出全部请求标识，按前缀过滤后
    /// `removePendingNotificationRequests(withIdentifiers:)`。前缀为完整 36 位 UUID，
    /// 后接 `__` 分隔符，故不与其它 taskId 的标识产生前缀误匹配。
    func cancel(for taskId: UUID) {
        cancelRequests(for: taskId)
    }

    /// 动态取消的内部实现：枚举并移除该 taskId 前缀下的全部 pending 标识，完成后回调 completion。
    /// 供 `cancel(for:)` 与 `schedule(for:)`（先清旧再登记，避免竞态）复用。
    private func cancelRequests(for taskId: UUID, completion: (() -> Void)? = nil) {
        let prefix = taskId.uuidString
        center.getPendingNotificationRequests { [weak self] requests in
            guard let self else { completion?(); return }
            let ids = requests
                .map { $0.identifier }
                .filter { $0.hasPrefix(prefix) }
            if !ids.isEmpty {
                self.center.removePendingNotificationRequests(withIdentifiers: ids)
            }
            completion?()
        }
    }

    // MARK: - 推迟 10 分钟（__snooze 幂等覆盖，规格 §2.4 / §3.1）
    func snooze(_ taskId: UUID) {
        let id = "\(taskId.uuidString)__snooze"
        center.removePendingNotificationRequests(withIdentifiers: [id])   // 清旧 snooze，避免叠加
        let fire = Date().addingTimeInterval(10 * 60)
        let content = makeSnoozeContent(taskId: taskId)
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents(fire), repeats: false)
        let req = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
        center.add(req) { error in
            if let error {
                Self.log("[Reminder] snooze 添加失败 id=\(id) error=\(error.localizedDescription)")
            }
        }
    }

    private func makeSnoozeContent(taskId: UUID) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = "待办提醒（已推迟）"
        c.body = "10 分钟后再提醒一次"
        c.categoryIdentifier = Self.categoryIdentifier
        c.sound = .default
        c.userInfo = ["taskId": taskId.uuidString,
                      "kind": TriggerKind.snooze.rawValue,
                      "repeatIndex": 0]
        return c
    }

    // MARK: - 标记完成（联动 cancel，规格 §2.5 主路径 / AC-9）
    /// 供通知 Action「标记完成」调用：完成即移除该任务全部未触发后续点。
    func completeTask(_ taskId: UUID) {
        try? repository.markDone(taskId, at: Date())
        cancel(for: taskId)
        // 埋点调用点：reminder_complete
    }

    // MARK: - rescheduleAllPending（启动/前台补偿，规格 §5）
    func rescheduleAllPending() {
        let horizon = Date().addingTimeInterval(Self.scanHorizonDays * 24 * 3600)   // now + 7天
        let tasks: [TaskDTO]
        do {
            // tasksWithPendingReminders 已过滤：未完成 + 有 remindAt + remindAt ∈ [now, until]
            tasks = try repository.tasksWithPendingReminders(until: horizon)
        } catch {
            Self.log("[Reminder] 扫描 pending 失败 error=\(error.localizedDescription)")
            return
        }
        for t in tasks {
            schedule(for: t)   // schedule 内含 cancel，天然幂等重建（补偿杀进程/重启/系统清理丢失）
        }
        Self.log("[Reminder] rescheduleAllPending 重建 \(tasks.count) 条 pending")
    }

    // MARK: - 注册分类（Action：标记完成 / 推迟 10 分钟，规格 §3.2）
    func registerCategory() {
        let complete = UNNotificationAction(identifier: Self.actionComplete,
                                            title: "标记完成",
                                            options: [.authenticationRequired])
        let snooze = UNNotificationAction(identifier: Self.actionSnooze,
                                         title: "推迟10分钟",
                                         options: [])
        let category = UNNotificationCategory(identifier: Self.categoryIdentifier,
                                              actions: [complete, snooze],
                                              intentIdentifiers: [],
                                              options: [])
        center.setNotificationCategories([category])
    }

    // MARK: - 授权请求（未授权降级，不崩溃）
    /// 首次启动（notDetermined）请求授权；已 denied 仅记录，提醒降级为 App 内列表兜底（规格 §3.4）。
    func requestAuthorizationIfNeeded() {
        let status = center.authorizationStatus
        guard status == .notDetermined else {
            if status == .denied {
                Self.log("[Reminder] 通知授权被拒，提醒降级（仅 App 内列表兜底，R-S4）")
            }
            return
        }
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error {
                Self.log("[Reminder] 请求授权出错 error=\(error.localizedDescription)")
            }
            Self.log("[Reminder] 通知授权 granted=\(granted)")
            // 授权结果供 R-S4 首页「提醒可能不送达」提示使用（F-perm，M2 仅打点）
        }
    }

    /// 设置自身为 UNUserNotificationCenter 的 delegate，以处理通知 Action 回调。
    func setAsDelegate() {
        center.delegate = self
    }

    // 轻量本地日志（不联网，与 P0-3 精神一致：异常记录、不崩溃）。
    private static func log(_ message: String) {
        NSLog("%@", message)
    }
}

// MARK: - UNUserNotificationCenterDelegate（处理 Action 回调，规格 §3.2）

extension NativeReminderScheduler: UNUserNotificationCenterDelegate {

    /// 前台也展示通知（横幅 + 声音），避免 App 打开时重复提醒被吞（AC-8）。
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {
        completion([.banner, .sound, .badge])
    }

    /// 用户点击通知 Action 的回调：标记完成 → completeTask；推迟 → snooze。
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completion: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        guard let taskIdStr = info["taskId"] as? String,
              let taskId = UUID(uuidString: taskIdStr) else {
            completion(); return   // 无 taskId 则忽略，不崩溃
        }
        switch response.actionIdentifier {
        case Self.actionComplete:
            completeTask(taskId)        // markDone + cancel（AC-9）
        case Self.actionSnooze:
            snooze(taskId)              // 额外排一个 +10min 点（幂等）
        default:
            break                       // 普通点开：不处理，由 App 内列表兜底
        }
        completion()
    }
}
