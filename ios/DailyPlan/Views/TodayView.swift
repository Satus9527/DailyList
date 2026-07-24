// TodayView.swift
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度、当日列表（进行中在上、已完成置底、可拖拽）、
// 底部文字输入框 + 添加按钮。数据从 Core Data 加载（F6 持久化）。

import SwiftUI
import Combine

struct TodayView: View {
    @Environment(\.managedObjectContext) private var viewContext
    @StateObject private var vm: TodayTaskViewModel

    @State private var editingId: UUID?
    /// F3 提醒设置面板所针对的待办（nil 表示未打开）
    @State private var reminderTask: TaskDTO?
    /// M4 R-4 设置页（AC-22）：齿轮入口弹出
    @State private var showSettings = false
    /// M4 R-4 拆分位置输入（alert）
    @State private var splitTarget: TaskDTO?
    @State private var splitIndexText: String = ""
    /// 进入前台（含从系统设置返回）刷新权限状态（D4 横幅，规格 §2.2）
    @Environment(\.scenePhase) private var scenePhase

    // —— F2 录音态视觉反馈（红点/计时/波形）——
    @State private var voiceElapsed = 0        // 录音计时（秒），仅听写中累加
    /// 1 秒心跳计时器（Combine），驱动计时显示；仅在 isVoiceActive 时 +1
    private let voiceTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    /// 计时格式化 mm:ss
    private var formattedVoiceTime: String {
        String(format: "%02d:%02d", voiceElapsed / 60, voiceElapsed % 60)
    }

    /// scheduler 由 DailyPlanApp 注入（F3，M2）；config 为 ASR 拆分配置（F2，M3）；context 沿用共享栈。
    init(scheduler: ReminderScheduler, config: ASRSplitConfig) {
        _vm = StateObject(
            wrappedValue: TodayTaskViewModel(
                context: PersistenceController.shared.viewContext,
                scheduler: scheduler,
                config: config
            )
        )
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // 进度 X / Y（R-U3 / AC-15）
                HStack {
                    Text("今日完成 \(vm.doneCount) / 共 \(vm.totalCount)")
                        .font(.headline)
                    Spacer()
                    // F2 语音按钮：进行中显示停止，否则显示麦克风（不可用则置灰）
                    Button(action: { vm.toggleVoice() }) {
                        Image(systemName: vm.isVoiceActive ? "waveform.circle.fill" : "mic.circle")
                            .font(.title2)
                            .foregroundColor(vm.voiceAvailable ? .accentColor : .gray)
                    }
                    .disabled(!vm.voiceAvailable && !vm.isVoiceActive)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)

                if let msg = vm.errorMessage {
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(.orange)
                        .padding(.horizontal)
                }

                // F2 语音实时中间文本 + 录音态视觉反馈（红点/计时/波形）+ 手动「落一条」/「存为文字」（仅听写中显示）
                if vm.isVoiceActive {
                    VStack(alignment: .leading, spacing: 6) {
                        // 录音态指示：红点脉冲 + 计时 + 波形（视觉反馈，不阻塞文字输入）
                        HStack(spacing: 8) {
                            VoicePulsingDot()                       // 红点脉冲提示正在聆听
                            Text(formattedVoiceTime)               // 计时 mm:ss
                                .font(.caption.monospacedDigit())
                                .foregroundColor(.secondary)
                            Spacer()
                            VoiceWaveform()                        // 简易波形动画（视觉反馈）
                        }
                        Text(vm.voicePartialText.isEmpty ? "聆听中…" : vm.voicePartialText)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(3)
                        HStack {
                            Button("落一条") { vm.commitManualSegment() }   // 手动优先（R-E2）
                            Button("存为文字") { vm.saveBufferedAsText() }  // 降级兜底：保存当前文本
                            Spacer()
                        }
                        .font(.caption)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 4)
                }

                // M4 D4：首页常驻横幅「提醒可能不送达」（仅通知未授权时显示，规格 §2）
                if vm.showNotificationBanner {
                    HStack(spacing: 8) {
                        Image(systemName: "bell.slash.fill")
                            .foregroundColor(.orange)
                        Text("提醒可能不送达，去开启通知")
                            .font(.subheadline)
                            .foregroundColor(.primary)
                        Spacer()
                        Button(action: { vm.bannerDismissedThisSession = true }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.secondary)
                        }
                        .buttonStyle(.borderless)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal)
                    .onTapGesture { vm.openAppSettings() }   // 深链系统设置（规格 §2.3）
                }

                // —— M5 F4 筛选栏（规格 §4.3：D4 横幅之下、列表之上）——
                FilterBar(filter: $vm.filter, categories: vm.categories, allTags: vm.allTags)
                    .padding(.horizontal)

                // 当日列表（M4 S5：错过的提醒 / 进行中 / 已完成 三个区块；进行中可拖拽，AC-16）
                // M5 F4：三区块统一叠加当前筛选（vm.filter，空条件=全部，§6 / AC-30④）
                List {
                    // —— M4 D3 错过的提醒区（AC-10）——
                    if !vm.filteredMissedTasks.isEmpty {
                        Section {
                            ForEach(vm.filteredMissedTasks) { task in
                                TaskRowView(
                                    task: task,
                                    isEditing: $editingId,
                                    onToggleDone: { vm.toggleDone($0) },
                                    onCommitEdit: { vm.editTitle($0, to: $1) },
                                    onDelete: { vm.delete($0) },
                                    onSetReminder: { reminderTask = $0 },
                                    onRowTap: { reminderTask = $0 },   // 点按跳转：打开提醒设置
                                    missedBadge: true
                                )
                            }
                        } header: {
                            HStack {
                                Text("错过的提醒")
                                Spacer()
                                Text("\(vm.filteredMissedTasks.count)")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }

                    // —— 进行中（含跨 0 点归触发日的任务）——
                    Section {
                        ForEach(vm.filteredInProgressTasks) { task in
                            TaskRowView(
                                task: task,
                                isEditing: $editingId,
                                onToggleDone: { vm.toggleDone($0) },
                                onCommitEdit: { vm.editTitle($0, to: $1) },
                                onDelete: { vm.delete($0) },
                                onSetReminder: { reminderTask = $0 },
                                onMergeUp: { vm.mergeWithPrevious($0) },   // M4 R-4 合并到上一条
                                onSplit: { requestSplit($0) }              // M4 R-4 从此处拆分
                            )
                        }
                        .onMove { from, to in
                            // 拖拽仅影响「进行中」展示区块（规格 §5.1 reorder / M4 S5）
                            vm.reorderInProgress(from: from, to: to)
                        }
                    } header: { Text("进行中") }

                    // —— 已完成（置底）——
                    if !vm.filteredDoneTasks.isEmpty {
                        Section {
                            ForEach(vm.filteredDoneTasks) { task in
                                TaskRowView(
                                    task: task,
                                    isEditing: $editingId,
                                    onToggleDone: { vm.toggleDone($0) },
                                    onCommitEdit: { vm.editTitle($0, to: $1) },
                                    onDelete: { vm.delete($0) },
                                    onSetReminder: { reminderTask = $0 }
                                )
                            }
                        } header: { Text("已完成") }
                    }
                }
                .listStyle(.plain)

                // F1 文字输入（回车 / 点按钮新增）
                HStack {
                    TextField("添加今日待办…", text: $vm.inputText, onCommit: { vm.addFromInput() })
                        .textFieldStyle(.roundedBorder)
                    Button(action: { vm.addFromInput() }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                    }
                }
                .padding()
            }
            .navigationTitle("今日计划")
            // 齿轮入口：M4 R-4 设置页（AC-22）
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showSettings = true }) {
                        Image(systemName: "gearshape")
                    }
                }
            }
            // 进入/退出录音态：计时归零（红点/波形由子视图自行 onAppear 启停）
            .onChange(of: vm.isVoiceActive) { _, active in
                voiceElapsed = 0
            }
            // 录音中每秒累加计时
            .onReceive(voiceTimer) { _ in
                if vm.isVoiceActive { voiceElapsed += 1 }
            }
            // M4 D4：进入前台/回到本页刷新权限状态（含从系统设置返回）
            .onAppear { vm.refreshPermissions() }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active { vm.refreshPermissions() }
            }
            // F2 降级/提示 Toast：不阻塞文字记录流（R-X1）
            .overlay(alignment: .bottom) {
                if let toast = vm.voiceToast {
                    Text(toast)
                        .font(.caption)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 80)
                        .transition(.opacity)
                        .onTapGesture { vm.voiceToast = nil }
                }
            }
            // M4 R-4 设置页（AC-22）：挂在内部 VStack 上，避免与 NavigationView 的 reminderTask .sheet 同视图冲突
            .sheet(isPresented: $showSettings) {
                SettingsView(vm: vm)
            }
        }
        // F3 提醒设置面板（M2-D，Task #36）：保存经 VM 持久化并排程
        .sheet(item: $reminderTask) { task in
            ReminderSettingView(
                task: task,
                onDismiss: { reminderTask = nil },
                onSave: { ra, lm, rc in
                    vm.saveReminder(taskId: task.id, remindAt: ra, leadMinutes: lm, repeatCount: rc)
                }
            )
        }
        // M4 R-4 设置页（AC-22）由内部 VStack 的 .sheet 承载（见下方 VStack 链），此处不再重复
        // M4 R-4 拆分位置输入（alert）：默认按首个句末标点断开，可手动填字符序号
        .alert("拆分位置", isPresented: Binding(
            get: { splitTarget != nil },
            set: { if !$0 { splitTarget = nil } }
        )) {
            TextField("从第几个字断开", text: $splitIndexText)
                .keyboardType(.numberPad)
            Button("拆分") {
                if let t = splitTarget, let i = Int(splitIndexText) {
                    vm.split(t, at: i)   // 复用 M3 拆分用例，落库后即时刷新
                }
                splitTarget = nil
            }
            Button("取消", role: .cancel) { splitTarget = nil }
        } message: {
            Text("默认按首个句末标点断开；可手动填入字符序号。")
        }
    }

    // M4 R-4：计算拆分默认断点（首个拆分标点之后），弹出输入 alert（规格 §4.2）
    private func requestSplit(_ task: TaskDTO) {
        let puncts = ASRSplitConfig.loadFromBundle()?.splitPunctuation ?? []
        var idx = max(1, task.title.count / 2)
        for p in puncts {
            if let r = task.title.range(of: p) {
                let pos = task.title.distance(from: task.title.startIndex, to: r.lowerBound)
                idx = pos + 1   // 标点归属前段
                break
            }
        }
        splitTarget = task
        splitIndexText = String(idx)
    }
}

// MARK: - F2 语音录音态视觉反馈（红点脉冲 / 波形，规格 §3.4 录音态提示）

/// 红点脉冲：录音中呼吸式缩放 + 透明度变化，提示正在聆听。仅视觉，不依赖真实音量。
private struct VoicePulsingDot: View {
    @State private var pulsing = false
    var body: some View {
        Circle()
            .fill(Color.red)
            .frame(width: 10, height: 10)
            .scaleEffect(pulsing ? 1.4 : 1.0)
            .opacity(pulsing ? 0.5 : 1.0)
            .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: pulsing)
            .onAppear { pulsing = true }
    }
}

/// 简易波形：多根竖条循环起伏，增强录音态反馈（非真实振幅，仅视觉）。
private struct VoiceWaveform: View {
    @State private var phase: Bool = false
    private let bars: [CGFloat] = [6, 12, 8, 14, 6]   // 各竖条基准高度
    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<bars.count, id: \.self) { i in
                Capsule()
                    .fill(Color.accentColor.opacity(0.7))
                    .frame(width: 3, height: barHeight(for: i))
            }
        }
        .frame(height: 18)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) {
                phase = true
            }
        }
    }
    private func barHeight(for i: Int) -> CGFloat {
        let base = bars[i % bars.count]
        return phase ? base : max(4, base * 0.5)
    }
}
