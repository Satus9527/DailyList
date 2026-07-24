// SettingsView.swift
// M4 R-4 设置页（AC-22）：通知/麦克风权限状态 + 去系统设置深链 + 语音输入开关 + P0-1 隐私说明。
// 复用 TodayTaskViewModel 的权限状态与 openAppSettings / setVoiceInputEnabled（不新增数据层）。

import AVFoundation
import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var vm: TodayTaskViewModel

    var body: some View {
        NavigationView {
            Form {
                // —— 通知权限（AC-22）——
                Section(header: Text("通知权限")) {
                    HStack {
                        Circle()
                            .fill(vm.notificationAuthStatus == .authorized ? Color.green : Color.gray)
                            .frame(width: 10, height: 10)
                        Text(notificationText)
                            .font(.subheadline)
                        Spacer()
                    }
                    Button("去系统设置") { vm.openAppSettings() }
                }

                // —— 麦克风权限（AC-22）——
                Section(header: Text("麦克风权限")) {
                    HStack {
                        Circle()
                            .fill(micAuthorized ? Color.green : Color.gray)
                            .frame(width: 10, height: 10)
                        Text(micText)
                            .font(.subheadline)
                        Spacer()
                    }
                    Button("去系统设置") { vm.openAppSettings() }
                }

                // —— 语音输入开关（R-4 设置页，默认开）——
                Section(header: Text("语音输入")) {
                    Toggle("启用语音输入", isOn: Binding(
                        get: { vm.voiceInputEnabled },
                        set: { vm.setVoiceInputEnabled($0) }
                    ))
                    .disabled(!vm.voiceAvailable && !vm.voiceInputEnabled)
                    Text("关闭后首页麦克风按钮不可用，文字记录不受影响。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                // —— 默认提醒策略（只读展示，v1 仅展示，v1.1 可改）——
                Section(header: Text("默认提醒策略")) {
                    Text("提前 10 分钟 + 到点 + 每 10 分钟重复最多 3 次；单条可调。")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                // —— 隐私说明（P0-1，AC-22）——
                Section(header: Text("隐私说明")) {
                    Text(privacyText)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("设置")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("完成") { dismiss() }
                }
            }
            .onAppear { vm.refreshPermissions() }   // 进入设置页刷新最新权限状态
        }
    }

    // MARK: - 文案与状态推导

    private var notificationText: String {
        switch vm.notificationAuthStatus {
        case .authorized:  return "已授权"
        case .denied:      return "未授权（被拒绝）"
        case .provisional: return "provisional（可能不达）"
        case .ephemeral:   return "未授权（临时）"
        case .notDetermined: return "未决定（建议开启）"
        @unknown default:  return "未知"
        }
    }

    private var micAuthorized: Bool {
        vm.micAuthStatus == .granted
    }

    private var micText: String {
        switch vm.micAuthStatus {
        case .granted:    return "已授权"
        case .denied:     return "未授权（被拒绝）"
        case .undetermined: return "未决定（建议开启）"
        @unknown default: return "未知"
        }
    }

    /// P0-1 隐私说明（M3 §8.4 / M4 §4.1，双端同一文案）
    private let privacyText = """
    本 App 纯本地存储、零登录；语音经系统 ASR 在设备端或联网转写，弱网/离线场景可能将音频上传至系统厂商\
    （Apple/Google）完成识别，非上传至本 App 账号/服务端；无个人数据随账号出端。
    """
}
