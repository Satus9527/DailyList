# 每日计划 App · iOS 端（M1）

> 平台：Swift + SwiftUI + Core Data（纯本地、零登录）。
> 实现范围：数据层 + F1 文字记录 + F5 完成/编辑 + F6 本地持久化（Stage 4 / Milestone M1）。

## 目录结构

```
ios/DailyPlan/
  App/
    DailyPlanApp.swift            # @main 入口，注入 Core Data 栈，损坏重建后弹一次性提示
    PersistenceController.swift   # Core Data 栈（代码化模型）+ 首启 Category 种子 + P0-3 损坏兜底
  Models/
    Enums.swift                   # Priority / TaskSource / SyncState（String rawValue 落库）
    TaskDTO.swift                 # 纯领域模型（与 Core Data 解耦），含 makeNew 便捷构造
    CoreData/
      Task+CoreData.swift         # Task NSManagedObject 子类 + toDTO 映射
      Category+CoreData.swift     # Category 子类 + CategoryDTO
      Tag+CoreData.swift          # Tag 子类 + TagDTO
      TaskTag+CoreData.swift      # Task↔Tag 关联实体
  Repositories/
    TaskRepository.swift          # Repository 协议（7 方法签名，规格 §5.1）
    LocalTaskRepository.swift     # Core Data 实现（7 方法语义 + 单事务提交）
    CategoryRepository.swift      # 基础 CRUD（预设不可删，删自建回退「其他」）
    TagRepository.swift           # 基础 CRUD（写入前归一，AC-30）
  ViewModels/
    TodayTaskViewModel.swift      # F1/F5/F6 聚合，X/Y 进度；M3 新增语音开关/降级/手动落一条
  Speech/
    ASRController.swift           # ASRController 协议 + PermissionState + NativeASRController（SFSpeechRecognizer+AVAudioEngine）
    SilentPauseMonitor.swift      # 长停顿监测（阈值取自配置，禁止硬编码）
  Domain/
    VoiceTaskSplitter.swift       # 领域层自动拆分，消费 asr_split_config.json（P0-4）
    TaskMergeSplitUseCase.swift   # 合并/拆分基础版（§7，冻结接口，复用 Repository）
  Views/
    TodayView.swift               # 首页「今日」：进度 / 列表 / 输入框
    TaskRowView.swift             # 单条：勾选完成 / 行内编辑 / 删除
  Resources/
    CategorySeed.swift            # 预设分类固定 UUID（规格 §3.2）
    ASRSplitConfig.swift          # 解析 shared/asr_split_config.json（规格 §8，F2 才消费）
    asr_split_config.json         # 同源副本（来自 /workspace/shared）
```

## 如何用 Xcode 打开与运行

1. 打开 Xcode → `File ▸ New ▸ Project ▸ iOS App`，Product Name 填 `DailyPlan`，
   Interface 选 `SwiftUI`，**Storage 选「None」**（本工程用代码化 Core Data 模型，不需要 .xcdatamodeld）。
2. 将 `ios/DailyPlan/` 下的所有 `.swift` 文件与 `Resources/asr_split_config.json`
   拖入新工程（勾选 Add to target: DailyPlan）。
3. 确认 `DailyPlanApp.swift` 的 `@main` 为入口；如有重名 `App.swift` 请删除模板自带的。
4. 选模拟器（iOS 17+）→ `Cmd + R` 运行。
   - 首次启动会自动写入 4 个预设分类（工作/生活/学习/其他）种子。
   - 数据存于 App 沙盒 SQLite，杀进程 / 重启后从库加载（F6）。

> 说明：本工程刻意采用「代码化（code-first）Core Data 模型」而非 `.xcdatamodeld` 二进制文件，
> 以便源码直接落在仓库、Xcode 免额外模型文件即可编译；实体/字段/关系严格对应设计规格 §6。

## M1 完成范围与未做项

### 已实现（M1）
- **数据层**：Task / Category / Tag / TaskTagCrossRef 实体按规格 §6 建模；枚举以 String rawValue 落库；
  `date` 以 `yyyy-MM-dd` 本地时区字符串存储；`title` ≤500 字（上层截断）；`syncState`/`updatedAt` 预埋。
- **TaskRepository 7 方法**：`todayTasks` / `add` / `update` / `markDone` / `delete` / `reorder(ids:)` /
  `tasksWithPendingReminders(until:)`，语义与 `LocalTaskRepository` 实现严格对应规格 §5（含排序与扫描语义 §5.3）。
- **CategoryRepository / TagRepository**：基础 CRUD；预设不可删、删自建回退「其他」；Tag 写入前归一（大小写/空格/全角半角）。
- **F1 文字记录**：输入框 + 回车/按钮添加；去空白、超 500 截断并提示；默认落今天、分类「其他」、优先级中、source=.text。
- **F5 完成与编辑**：勾选完成（写 doneAt/isDone）、取消完成（AC-26）、行内编辑 title、删除、上下移改 sortOrder；顶部 X/Y 进度。
- **F6 本地持久化**：所有写操作单事务提交；列表从 Core Data 加载；**损坏兜底（P0-3 方案 B）**——启动时
  DB 打开/迁移失败 → 捕获异常、`Application Support/logs/db_error.log` 记本地日志（不联网）→ 删损坏库 →
  重建空库并重跑种子 → App 不崩溃，弹一次性提示。
- **共享 ASR 配置**：`asr_split_config.json` 由 `ASRSplitConfig` 解析（M1 仅定义结构，F2 才消费，禁止硬编码）。

### 刻意未做（后续里程碑）
- **F2 语音输入**：原生 ASR（Speech 框架）、持续听、按停顿/标点拆分——M1 仅引用拆分配置结构，不实现业务逻辑。
- **F3 提醒排程**：`remindAt`/`leadMinutes`(默认10)/`repeatCount`(默认3) 字段已持久化，`tasksWithPendingReminders`
  接口已就绪，但 `ReminderScheduler` 通知排程与重启补偿未实现。
- **F4 分类/优先级/标签完整 UI**：字段/结构/种子/归一已实现，但分类选择、优先级切换、标签联想输入的完整界面暂缓。
- **v1.1 云同步**：`syncState`/`updatedAt` 字段预埋，但未消费，无账号/网络写路径。

## M2（F3 提醒）iOS 完成项与未做项

> 阶段：Stage 4 / Milestone M2（Task #34 M2-B，iOS 通知层）。依据 `设计规格_M2提醒排程.md`。

### 已实现（M2 · iOS 通知层）
- **通知层文件**：`DailyPlan/Notifications/ReminderScheduler.swift`，含 `TriggerKind` / `TriggerPoint` /
  `ReminderScheduler` 协议 + `NativeReminderScheduler`（基于 `UNUserNotificationCenter`）。
- **触发点生成规则（规格 §2）**：`leadMinutes>0` 生成 `__lead`（T−leadMinutes）；总是生成 `__at`（T）；
  `repeatCount>0` 生成 `__rep{i}`（T+i×10min，i=1..R）。默认随 `TaskDTO`（P0-2：10/3），单条可覆盖；
  未设 `remindAt` 不排程。每个点用 `UNCalendarNotificationTrigger`（绝对本地时间）登记，`identifier`
  以 `task.id.uuidString` 为前缀。
- **schedule / cancel / snooze**：`schedule` 先 `cancel` 再登记（幂等，改期不重复）；`cancel` 移除该
  task 全部前缀 pending（含 `__lead/__at/__snooze/__rep{1..3}`）；`snooze` 单实例 `__snooze`（T+10min，幂等覆盖）。
- **启动 / 前台补偿**：`rescheduleAllPending()` 扫 `now+7天`（`tasksWithPendingReminders`），对每个未完成任务
  幂等重建；在 App 冷/热启动（`DailyPlanApp.init`）与进入前台（`scenePhase == .active`）调用。
- **通知交互**：注册 `UNNotificationCategory`（「标记完成」`action_complete` / 「推迟10分钟」`action_snooze`）；
  `NativeReminderScheduler` 作为 `UNUserNotificationCenterDelegate` 处理 `didReceive`：点「标记完成」→
  `completeTask`（markDone + cancel）；「推迟」→ `snooze`。
- **完成即取消（AC-9 / R-E7）**：`TodayTaskViewModel.toggleDone` 标记完成时调 `scheduler.cancel`；通知 Action
  亦经 `completeTask` 联动取消；取消完成时若仍有未来提醒则 `schedule` 恢复（AC-26）。
- **授权降级（P0-3 精神）**：未授权（`denied`）时 `schedule` 跳过并记日志、不崩溃；首次启动 `notDetermined`
  时请求授权；授权请求/排程/补偿全程异常捕获、绝不崩溃。
- **不改 M1 数据层**：复用 `remindAt`/`leadMinutes`/`repeatCount` 与 `tasksWithPendingReminders`，未新增字段、
  未建独立 Reminder 表。ViewModel 预留 `applyReminderSetting(for:)` 供 F4 编辑/改期 UI 接线（AC-28）。
- **F3 提醒设置 UI（M2-D，Task #36）**：
  - 新增 `Views/ReminderSettingView.swift`：提醒设置面板（`.sheet` 呈现）。提供「启用提醒」开关；
    启用时可选 `remindAt`（DatePicker，含日期+时分，默认今天 09:00）、`leadMinutes`（分段选择器 5/10/15/30 分，
    默认 10；关闭开关即 L=0）、`repeatCount`（分段选择器 1/2/3/5 次，默认 3；关闭开关即 R=0，AC-11）。
  - `Views/TaskRowView.swift`：每条待办加铃铛入口（`onSetReminder`），并显示下次提醒时间（`M/d HH:mm`）；
    无提醒显示「无提醒」、铃铛置灰。
  - `Views/TodayView.swift`：持有 `reminderTask` 状态，以 `.sheet(item:)` 弹出 `ReminderSettingView`，
    保存回调调 `vm.saveReminder(...)`。
  - `ViewModels/TodayTaskViewModel.swift`：新增 `saveReminder(taskId:remindAt:leadMinutes:repeatCount:)`——
    先更新领域模型并 `repository.update` 持久化，再据新值排程；编辑 `remindAt` 由 `scheduler.schedule`
    内部先 cancel 再登记（幂等，AC-28 / R-E11）；`remindAt=nil` 或时间已过则 `scheduler.cancel`。
  - 完成/删除的自动取消已就绪（`toggleDone`/`delete` 均调 `scheduler.cancel`），与 UI 改动无冲突；500 字上限等 M1 规则不受影响。

### 刻意未做 / 待确认（M2 · iOS）
- **iOS 触发时刻查 isDone**：系统级 pending 通知无法在触发瞬间被 App 条件性抑制（规格 §2.5）。iOS 等价保证来自
  「完成即 cancel + 幂等 Action + App 内列表兜底」，属平台边界，不阻塞上线；与 Android 的 `doWork` 运行时查
  `isDone` 完整兜底存在能力差异，需架构/测试确认口径。
- **首页「错过的提醒」区（R-X3 / AC-10）与「提醒可能不送达」常驻提示（R-S4 / AC-20）**：仅留 `requestAuthorizationIfNeeded`
  打点，UI 区与提示文案尚未实现（属 F-perm / 首页增强，建议后续里程碑补齐）。
- **埋点上报（reminder_set / trigger / complete 等）**：仅留调用点注释，未接通上报逻辑（规格 §6.5）。
- **跨日后台常驻唤醒**：v1 不做（规格 §5）；超 7 天的远期提醒依赖用户再次打开 App 时补偿。

## M3（F2 语音）iOS 完成项与未做项

> 阶段：Stage 4 / Milestone M3（Task #48 M3-B，iOS 语音层）。依据 `设计规格_M3语音层.md`。

### 已实现（M3 · iOS 语音层）
- **`Speech/ASRController.swift`**：平台无关协议 `ASRController`（`requestPermission()` / `isAvailable` /
  `start(onPartial:onFinal:)` / `stop()`）+ `PermissionState` + `VoiceDegradeReason`；iOS 实现
  `NativeASRController` 基于 `SFSpeechRecognizer` + `AVAudioEngine` 持续听：`shouldReportPartialResults=true`、
  `taskHint=.dictation`、`requiresOnDeviceRecognition=true` **离线优先**。
- **离线优先 → 联网回退（P0-1）**：`requiresOnDeviceRecognition=true` 失败（离线包缺失）→ 重建
  `requiresOnDeviceRecognition=false` 请求联网识别一次；联网仍失败 → 降级。音频仅发往 Apple 系统 ASR，
  无账号/待办写回（§8 隐私）。
- **长停顿边界**：`Speech/SilentPauseMonitor.swift` 由音频缓冲 RMS 检测静音，阈值取自
  `config.splitPauseThresholdMs`（当前 1200），**禁止硬编码**（P0-4）；超阈值把当前缓冲作为一次 `onFinal` 边界
  交给领域层切分落库。
- **增量去重**：`onFinal` 仅提交 SFTranscription 的「新增 segments」，避免尾句/停顿导致重复落库（R-E2）。
- **`Domain/VoiceTaskSplitter.swift`（领域层，双端同源）**：解析消费 `shared/asr_split_config.json`
  （`splitPunctuation=[。！？；]`、`includeEnumerationComma=false`、`includeNewline=false`、`splitPauseThresholdMs=1200`）；
  按标点切分、去尾标点、**不含「、」与换行**（由配置决定，不切）；每段非空 → `TaskRepository.add(source=.voice)`；
  title 超 500 截断（R-X5）。**全程无拆分常量硬编码**。
- **失败降级（§6）**：授权拒 / 无网无离线包 / 识别失败 → `onDegrade` 回调关语音按钮 + Toast「语音暂不可用，请改用文字输入」
  （R-X1，文字录入仍可用）；提供「存为文字」把当前缓冲以 `source=.text` 落库（§6.2）。
- **手动「落一条」优先（R-E2）**：听写中按钮直接把当前缓冲交 `commitFinalSegment`，并清空避免重复。
- **接线（与 M1 并存）**：`DailyPlanApp` 启动时 `ASRSplitConfig.loadFromBundle()` 解析配置；`TodayTaskViewModel`
  持有 `NativeASRController` + `VoiceTaskSplitter`，`onFinal → splitter.commitFinalSegment → repository.add`，
  复用 `todayTasks()` 刷新；文字输入（F1）完全不受影响。

### F2 语音输入 UI（M3-D，Task #50 · `Views/TodayView.swift`）
- **麦克风按钮与文字输入并存**：顶部进度条右侧麦克风按钮（`mic.circle` / 录音中 `waveform.circle.fill`），
  与底部 `TextField` 文字输入互不干扰；能力不可用 / 已降级时按钮置灰（`!voiceAvailable && !isVoiceActive`）。
- **录音态视觉反馈**：听写中显示红点脉冲（`VoicePulsingDot` 呼吸式缩放+透明度）、`mm:ss` 计时（`Timer` 每秒累加）、
  简易波形动画（`VoiceWaveform` 竖条起伏），均为纯视觉、不阻塞文字流。
- **实时中间文本**：`onPartial` 经 `vm.voicePartialText` 实时显示于气泡（空时显示「聆听中…」），仅展示不落库。
- **自动落一条**：`onFinal → splitter.commitFinalSegment` 按 JSON 标点切分并 `source=.voice` 落库后 `reload()` 刷新列表。
- **手动「落一条」**：听写中按钮直接把当前缓冲交 `commitFinalSegment`（优先于自动信号，R-E2）。
- **「存为文字」停止并保存**：降级/中途场景把当前缓冲以 `source=.text` 落库（§6.2）；为空不落（R-X4）。
- **降级 UI**：`onDegrade` → 关麦克风按钮 + 底部 Toast「语音暂不可用，请改用文字输入」（轻点可关），文字录入仍可用（R-X1）。
- **合并/拆分基础版（§7）**：`Domain/TaskMergeSplitUseCase.swift` 冻结 `merge`/`split` 接口，复用
  `TaskRepository`（update/add/delete）落库，重启后保持；合并以「、」连接。
- **不新增字段**：落库复用 `source=.voice`（M1 已预留），未改动数据层。

### 需补充 / 未做（M3 · iOS）
- **`Info.plist` 两个权限文案（§3.3）**：本工程为 code-first，无仓库内 Info.plist；需在 Xcode 工程加入
  `NSSpeechRecognitionUsageDescription` 与 `NSMicrophoneUsageDescription`（文案含「语音经系统 ASR 在设备端或
  联网转写，弱网/离线可能上传音频至 Apple，可在设置关闭语音输入」）。
- **设置页语音开关 / 重授权入口（§8）**：仅预留 `voiceAvailable` 状态与 Toast 引导，设置页 UI 待 F-perm 阶段补齐。
- **埋点调用点（`voice_start/voice_partial/voice_split/voice_stop/voice_error`）**：仅留注释，未接通上报（同 M2 口径）。
- **长按合并/拆分 UI（AC-5）**：接口与基础版已实现，但列表长按手势入口未接（待确认项 5，建议 M3.1 补 UI）。
- **真机语音质量 / 端到端拆分**：需真机验证（同 M1/M2 验收跟进项）。

## 与规格的偏差 / 待确认点
- **Core Data 多对多建模**：iOS 采用原生 many-to-many 关系（`Task.tags`），Android Room 用显式 `task_tag` 关联表；
  两者语义等价，属双端存储差异的正常取舍（规格 §6 实体清单含 TaskTag，Room 侧已落表）。
- **拖拽手势**：M1 列表排序通过「上移/下移」按钮驱动 `reorder`（持久化已验证），完整手指拖拽手势建议在 F4 阶段增强。
- **模型形式**：采用 code-first 模型而非 `.xcdatamodeld`，便于源码直落仓库（见上「如何打开」）。

## Info.plist 配置说明（M3 缺陷 D2 修复）

已新增 `DailyPlan/Info.plist`（与 `DailyPlanApp.swift` 同层），补齐语音识别与麦克风权限文案，避免
`NativeASRController` 在真机请求 `SFSpeechRecognizer.requestAuthorization` / `AVAudioEngine` 录音时因缺键崩溃（上线阻断项）。

**已包含的权限键**

| 键 | 用途 |
| --- | --- |
| `NSMicrophoneUsageDescription` | 每日计划需要使用麦克风以进行语音记录待办 |
| `NSSpeechRecognitionUsageDescription` | 每日计划需要使用语音识别，将您的语音转为文字待办（可离线识别） |

**最小宿主键**：`CFBundleName` / `CFBundleDisplayName`(`DailyPlan`) / `CFBundleIdentifier`(`com.dailyplan.app`) /
`CFBundleVersion` / `CFBundleShortVersionString` / `CFBundlePackageType`(`APPL`) / `UILaunchScreen`(空字典)。

> ⚠️ **必须在 Xcode 中挂到 target**：仅把 `Info.plist` 放进仓库不够。需在 Xcode 工程的
> **Build Settings → Info.plist File** 指向 `DailyPlan/Info.plist`（如 `DailyPlan/Info.plist`），
> 使其被纳入对应 target 的 Info 配置；否则真机运行仍会因缺键崩溃。本沙箱无 Xcode，无法编译验证，请在本机确认。
> 未改动任何语音/识别逻辑，仅补配置。
