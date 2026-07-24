// ReminderSettingView.swift
// F3 提醒设置面板（M2-D，Task #36）：为单条待办设置/修改提醒时间、提前量、重复次数。
// M5（F4）扩展：在同一 sheet 内增加「分类 / 优先级 / 标签」三段，与既有提醒设置共存（规格 §4.1 / §4.3，不另起新页）。
// 默认沿用 P0-2（leadMinutes=10 / repeatCount=3）；可分别关闭（L=0 / R=0，AC-11）。
// 确认后经 ViewModel 持久化（repository.update + setTags）并调度（ReminderScheduler.schedule，内部先 cancel 再登记，幂等）。

import SwiftUI

struct ReminderSettingView: View {
    @Environment(\.managedObjectContext) private var viewContext
    let task: TaskDTO
    /// 关闭面板（取消或保存后均调用）
    let onDismiss: () -> Void
    /// 保存提醒：回传 remindAt / leadMinutes / repeatCount（remindAt=nil 表示「无提醒」）
    let onSave: (_ remindAt: Date?, _ leadMinutes: Int, _ repeatCount: Int) -> Void

    /// M5 F4 编辑协调 ViewModel（分类/标签联想/去重/保存），复用 TagNormalizer 与 TagRepository.addOrReuse
    @StateObject private var editVM: TaskEditViewModel

    // —— 提醒（M2）状态 ——
    @State private var enabled: Bool
    @State private var remindAt: Date
    @State private var leadEnabled: Bool
    @State private var leadMinutes: Int
    @State private var repeatEnabled: Bool
    @State private var repeatCount: Int

    // —— M5 F4 状态 ——
    @State private var selectedCategoryId: UUID
    @State private var selectedPriority: Priority
    @State private var tagInput: String = ""
    @State private var selectedTags: [TagDTO] = []
    @State private var suggestions: [TagDTO] = []
    @State private var showAddCategory = false
    @State private var newCategoryName: String = ""

    private let leadOptions = [5, 10, 15, 30]
    private let repeatOptions = [1, 2, 3, 5]

    init(task: TaskDTO,
         onDismiss: @escaping () -> Void,
         onSave: @escaping (_ remindAt: Date?, _ leadMinutes: Int, _ repeatCount: Int) -> Void) {
        self.task = task
        self.onDismiss = onDismiss
        self.onSave = onSave
        // 沿用 TodayView 既有约定：直接取共享栈 viewContext（与 TodayTaskViewModel 同一 context）
        _editVM = StateObject(wrappedValue: TaskEditViewModel(context: PersistenceController.shared.viewContext))

        let hasReminder = task.remindAt != nil
        _enabled = State(initialValue: hasReminder)
        _remindAt = State(initialValue: task.remindAt ?? Self.defaultRemindAt())
        _leadEnabled = State(initialValue: task.leadMinutes > 0)
        _leadMinutes = State(initialValue: task.leadMinutes > 0 ? task.leadMinutes : 10)
        _repeatEnabled = State(initialValue: task.repeatCount > 0)
        _repeatCount = State(initialValue: task.repeatCount > 0 ? task.repeatCount : 3)

        // F4 默认值：分类默认当前值或「其他」预设；优先级默认当前值（§6 / AC-30①②）
        _selectedCategoryId = State(initialValue: task.categoryId ?? CategorySeed.otherId)
        _selectedPriority = State(initialValue: task.priority)
    }

    private static func defaultRemindAt() -> Date {
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        comps.hour = 9; comps.minute = 0; comps.second = 0
        return Calendar.current.date(from: comps) ?? Date()
    }

    var body: some View {
        NavigationView {
            Form {
                // —— M5 F4 分类（§4.1）——
                Section(header: Text("分类")) {
                    Picker("分类", selection: $selectedCategoryId) {
                        ForEach(editVM.categories) { c in
                            Text(c.name).tag(c.id)
                        }
                    }
                    .pickerStyle(.menu)
                    Button(action: { showAddCategory = true }) {
                        Label("新建分类", systemImage: "plus")
                    }
                }

                // —— M5 F4 优先级（§4.1，默认「中」，不强制列表标识）——
                Section(header: Text("优先级")) {
                    Picker("优先级", selection: $selectedPriority) {
                        ForEach(Priority.allCases) { p in
                            Text(p.displayName).tag(p)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                // —— M5 F4 标签（§4.1 / §5）——
                Section(header: Text("标签")) {
                    if !selectedTags.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(selectedTags) { t in
                                    ChipView(text: t.name) {
                                        selectedTags.removeAll { $0.id == t.id }
                                    }
                                }
                            }
                        }
                    }
                    TextField("输入标签，回车或逗号确认", text: $tagInput, onCommit: commitTag)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: tagInput) { _, newValue in
                            // 逗号（中/英）确认（§4.1）
                            if newValue.contains(",") || newValue.contains("，") {
                                let cleaned = newValue
                                    .replacingOccurrences(of: ",", with: "")
                                    .replacingOccurrences(of: "，", with: "")
                                var input = cleaned
                                editVM.commitTagInput(&input, into: &selectedTags)
                                tagInput = ""
                                suggestions = []
                            } else {
                                suggestions = editVM.suggest(tagInput)
                            }
                        }
                    if !suggestions.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(suggestions) { s in
                                    Button(action: {
                                        editVM.pickSuggestion(s, into: &selectedTags)
                                        tagInput = ""
                                        suggestions = []
                                    }) {
                                        Text(s.name)
                                            .font(.caption)
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                    Text("非必填，可跳过；输入时联想补全，重复自动归并。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                // —— F3 提醒（M2）——
                Section {
                    Toggle("启用提醒", isOn: $enabled)
                }
                if enabled {
                    Section(header: Text("提醒时间")) {
                        DatePicker("提醒时间", selection: $remindAt,
                                   displayedComponents: [.date, .hourAndMinute])
                    }
                    Section(header: Text("提前提醒")) {
                        Toggle("提前提醒", isOn: $leadEnabled)
                        if leadEnabled {
                            Picker("提前量", selection: $leadMinutes) {
                                ForEach(leadOptions, id: \.self) { m in
                                    Text("\(m) 分").tag(m)
                                }
                            }
                            .pickerStyle(.segmented)
                        } else {
                            Text("关闭：到点才提醒（不提前，L=0）")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                    Section(header: Text("重复提醒")) {
                        Toggle("重复提醒", isOn: $repeatEnabled)
                        if repeatEnabled {
                            Picker("重复次数", selection: $repeatCount) {
                                ForEach(repeatOptions, id: \.self) { n in
                                    Text("\(n) 次").tag(n)
                                }
                            }
                            .pickerStyle(.segmented)
                            Text("每次间隔 10 分钟（每次 +10min，P0-2）")
                                .font(.caption).foregroundColor(.secondary)
                        } else {
                            Text("关闭：仅提醒一次（不重复，R=0，AC-11）")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                } else {
                    Section {
                        Text("未设置提醒：到点/提前/重复均不触发。")
                            .font(.caption).foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("任务设置")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存", action: commit)
                }
            }
            .onAppear {
                editVM.loadCategories()
                selectedTags = editVM.loadTags(taskId: task.id)   // 回显已有标签
            }
            .alert("新建分类", isPresented: $showAddCategory) {
                TextField("分类名称", text: $newCategoryName)
                Button("添加") {
                    if let c = editVM.addCategory(newCategoryName) {
                        selectedCategoryId = c.id
                    }
                    newCategoryName = ""
                }
                Button("取消", role: .cancel) { newCategoryName = "" }
            } message: {
                Text("输入新分类名称（自建分类可删除）")
            }
        }
    }

    // 回车确认标签（§4.1）
    private func commitTag() {
        var input = tagInput
        editVM.commitTagInput(&input, into: &selectedTags)
        tagInput = ""
        suggestions = []
    }

    private func commit() {
        let ra = enabled ? remindAt : nil
        let lm = leadEnabled ? leadMinutes : 0   // 关闭提前 → L=0
        let rc = repeatEnabled ? repeatCount : 0 // 关闭重复 → R=0（AC-11）

        // M5 F4：先保存组织字段（分类/优先级/标签），落库后经同一 context 可见
        editVM.save(task: task, categoryId: selectedCategoryId, priority: selectedPriority, tags: selectedTags)
        // F3：再保存提醒（TodayTaskViewModel.saveReminder 内部先 reload 再更新，避免互相覆盖）
        onSave(ra, lm, rc)
        onDismiss()
    }
}

// MARK: - 标签 chip（可删，§4.1）
private struct ChipView: View {
    let text: String
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(text)
                .font(.caption)
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
            }
            .buttonStyle(.borderless)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Capsule().fill(Color.accentColor.opacity(0.12)))
    }
}

// MARK: - 行内展示用：压缩的提醒时间（M/d HH:mm，设备本地时区）
extension TaskDTO {
    var reminderShortText: String? {
        guard let ra = remindAt else { return nil }
        let df = DateFormatter()
        df.dateFormat = "M/d HH:mm"
        df.timeZone = TimeZone.current
        return df.string(from: ra)
    }
}
