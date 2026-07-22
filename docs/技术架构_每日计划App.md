# 每日计划 App · 技术架构草案（阶段三·架构产出）

> 文档状态：架构草案 v1，作为 PRD 评审后研发实现的依据
> 角色：架构师（Arch）产出
> 适用范围：v1 核心（iOS + Android 原生、纯本地零登录、语音 + 文字、本地提醒），含 v1.1 扩展点规划
> 参考文档：`每日计划App_需求规划.md`、`团队角色说明书.md`

---

## 一、总览与设计原则

### 1.1 总体目标
构建一款**极低门槛的当日待办记录工具**：用户 30 秒内通过语音或文字完成当日记录，关键待办准时本地提醒，数据纯本地持久化、零登录。

### 1.2 设计原则
1. **原生优先**：iOS / Android 均用平台推荐的原生 UI 与系统能力，规避跨平台封装在 ASR / 通知上的能力损耗与延迟。
2. **分层与边界清晰**：UI / 领域 / 数据 / 语音 / 通知五层解耦，领域层不依赖具体存储与平台 API，便于单测与后续替换（如云同步、第三方 ASR）。
3. **失败兜底优先（Graceful Degradation）**：语音不可用 → 自动降级文字输入；通知受限 → 明确免打扰策略与 App 内兜底；任何单点失败不阻断核心记录流。
4. **纯本地、零登录 + ASR 联网回退**：v1 不引入任何需要账号/网络的**写路径**（无云端持久化、无上传待办），降低合规与隐私风险；但语音识别（ASR）允许在设备端不可用时**联网回退**（音频可能发往系统厂商识别服务），属识别链路而非数据写路径。隐私与降级细节见 §4.3。
5. **接口先行**：ASR、通知、存储均定义抽象接口，v1.1 可热插拔（第三方 ASR、云同步）而不改上层逻辑。

### 1.3 架构分层（双端共用同一心智模型）

```
┌─────────────────────────────────────────────┐
│  UI 层 (SwiftUI / Jetpack Compose)           │  视图 + 交互状态
├─────────────────────────────────────────────┤
│  领域层 (Domain / Use Cases / 业务状态)        │  纯 Dart/Swift/Kotlin 业务逻辑
├──────────────┬──────────────┬───────────────┤
│  语音层       │  通知层       │  数据层         │  抽象接口 + 平台适配实现
│  (ASR 适配)   │  (Local Push) │  (Repository)   │
├──────────────┴──────────────┴───────────────┤
│  平台能力层 (iOS Speech / UNUserNotification / │  系统框架封装
│             Android SpeechRecognizer / Room)  │
└─────────────────────────────────────────────┘
```

---

## 二、技术选型

### 2.1 iOS 技术栈

| 维度 | 推荐 | 理由 |
|------|------|------|
| UI 框架 | **SwiftUI（主）+ UIKit（必要处补充）** | SwiftUI 声明式开发快、与 Swift 并发（async/await）契合，适合 v1 轻量界面；个别系统能力（如 `SFSpeechRecognizer` 部分回调、`UNUserNotificationCenter` 交互）在必要时用 UIKit 桥接 |
| 语言 | **Swift 5.9+** | 原生 ASR（`Speech`）、本地通知（`UserNotifications`）均为 Swift 一等公民；`async/await` + `Task` 简化持续听写与流式处理 |
| 本地存储 | **Core Data（主）+ 可选 GRDB** | 见第三节对比，Core Data 与 SwiftUI `@FetchRequest` / `@Environment(\.managedObjectContext)` 深度集成，开发成本低；若团队偏好 SQL 显式控制可选 GRDB（SQLite 封装） |
| 语音 | **Speech 框架（SFSpeechRecognizer）** | 系统原生、支持中文、可离线（设备端识别，iOS 13+ 需 on-device 授权）、免费 |
| 通知 | **UserNotifications（UNUserNotificationCenter）** | 系统标准本地通知，支持触发器、自定义声音、通知分类与交互 |

### 2.2 Android 技术栈

| 维度 | 推荐 | 理由 |
|------|------|------|
| UI 框架 | **Jetpack Compose** | 声明式、与 Kotlin 协程/流天然契合，官方主推，迭代快 |
| 语言 | **Kotlin 1.9+** | 协程（持续听写流式处理）、空安全、与 AndroidX 生态一致 |
| 本地存储 | **Room（SQLite 封装）** | 编译期 SQL 校验、与 Flow 集成好、Google 官方主推；v1.1 云同步可通过 Room + 同步适配器平滑扩展 |
| 语音 | **android.speech.SpeechRecognizer** | 系统原生、调用 Google 语音识别（多数设备默认联网；部分设备/语言可离线语言包）；免费、无需额外 SDK |
| 通知 | **WorkManager（调度）+ NotificationManager / 通知渠道（展示）** | 见第四节，AlarmManager 在 Doze 下不可靠，WorkManager 为官方推荐的后台可靠调度 |

### 2.3 选型对比：本地存储

| 方案 | iOS | Android | 选型建议 |
|------|-----|---------|---------|
| Core Data / Room | ✅ 官方 | ✅ 官方 | **推荐**：与各自平台 UI 框架集成最佳，零额外依赖 |
| Realm | 第三方 | 第三方 | 跨端一致 API，但增加体积与学习成本；v1 双端各自原生方案更轻 |
| 纯 SQLite | ✅ | ✅ | 过于底层，开发成本高；经 Room/Core Data 封装即可 |
| 文件/JSON | ⚠️ | ⚠️ | 仅适合极小配置；待办结构化查询、提醒调度不适合 |

> **结论**：iOS 用 **Core Data**（或 GRDB 备选），Android 用 **Room**，二者均为 SQLite 之上官方封装，保证结构化查询、事务与迁移能力，为 v1.1 云同步预留变更追踪字段（见第九节）。

---

## 三、本地存储方案与持久化策略

### 3.1 持久化策略
- **主库**：单进程本地 SQLite（Core Data / Room），App 启动时打开，进程内常驻。
- **事务**：每条待办新增/完成/编辑均单事务提交，确保「重启/杀进程不丢」（需求 F6 Ubiquitous）。
- **迁移**：预留 `schema version`，v1.1 循环待办、统计新增表时走迁移而非重建，避免用户数据丢失。
- **损坏兜底**：
  - **v1（方案 B·仅不崩溃 + 记日志）**：启动时若 DB 打开失败，捕获异常并记日志，重建空库保证 App 可启动、**不崩溃**；**不强制恢复**用户数据（v1 零网络无备份源，强行恢复意义有限且风险高）。
  - **v1.1（升级方案 C·轻量本地快照）**：引入每日本地快照（每日凌晨导出一份轻量 JSON/SQL 快照至应用沙盒），损坏时可回滚至最近一次快照，降低数据丢失面。详见文末「已拍板决策记录 · P0-3」。
- **容量上限**：纯文本待办量级极小（单日 ~50 条、单条 < 1KB），SQLite 上限（TB 级）远未触及；不构成风险（详见风险章）。

### 3.2 存储与同步解耦（v1 不联网）
- Repository 层只暴露 `TaskRepository` 接口，v1 实现为 `LocalTaskRepository`（读写本地库）。
- 所有写操作带 `updatedAt` 与 `syncState`（默认 `local`），为 v1.1 账号云同步预留「增量变更集」能力，无需改上层。

---

## 四、语音 ASR 集成方案

### 4.1 iOS：Speech 框架（SFSpeechRecognizer）
- **接入方式**：
  - 权限：`NSSpeechRecognitionUsageDescription` + `Info.plist` 麦克风权限 `NSMicrophoneUsageDescription`；运行时请求 `SFSpeechRecognizer.requestAuthorization`。
  - 持续听：`SFSpeechAudioBufferRecognitionRequest` 配合 `AVAudioEngine` 持续喂音频缓冲，设 `shouldReportPartialResults = true` 拿流式中间结果；`taskHint = .dictation`。
  - 离线：设 `request.requiresOnDeviceRecognition = true`（iOS 13+，设备需已下载离线语言包，中文需用户预装），失败回退联网识别。
  - 自动拆分：监听 `finalResult`（句末停顿 / 标点）触发「落一条」；流式 partial 实时显示在输入框，final 提交为待办并清空缓冲继续听。
- **失败兜底（Unwanted 需求）**：
  - 识别授权被拒 / 识别失败 / 无网络且未装离线包 → 关闭语音按钮并 Toast 引导「改用文字输入」，记录流程不中断。
  - 提供「停止并保存当前已识别文本」作为文字条目。

### 4.2 Android：SpeechRecognizer
- **接入方式**：
  - 权限：`RECORD_AUDIO`，运行时 `ActivityCompat.requestPermissions`；`RecognizerIntent.EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`，`EXTRA_LANGUAGE = "zh-CN"`。
  - 持续听：`SpeechRecognizer` 单次识别结束（`RESULTS`）后立即重新 `startListening` 形成持续听循环；用 `EXTRA_PARTIAL_RESULTS = true` 拿流式中间结果。
  - 自动拆分：在 `onPartialResults`/`onResults` 中按停顿（SDK 自然句末）或检测到标点切分；`onEndOfSpeech` 视为一条完成的待办提交。
  - 离线：依赖设备已下载的离线语音识别语言包；未装时回退联网识别，再失败则降级。
- **失败兜底**：`onError`(`ERROR_NO_MATCH` / `ERROR_NETWORK` / `ERROR_INSUFFICIENT_PERMISSIONS`) → 提示并切文字输入；录音中 `AudioFocus` 被抢占（来电等）自动暂停恢复。

### 4.3 拆分策略（跨端共用算法，方案 C·温和）

- **统一拆分标点集（参数化、双端共享）**：`SPLIT_PUNCTUATION = { 。！？； }`。
  - **不含「、」**：顿号视为同一待办内的并列项，**不切分**（避免一条长清单被误拆成多条）。
  - **不含换行**：换行同样视为同一待办内的换行内容，**不切分**。
  - 标点集与阈值集中在**双端共享配置/规格文件**（如 `asr_split_config` 常量或共享生成代码），以规避 Swift / Kotlin 实现漂移（见风险「双端行为不一致」）。
- **长停顿阈值（统一）**：`SPLIT_PAUSE_THRESHOLD_MS = 1200`（>1.2s 视为句末停顿，触发落一条）。此前 800ms~1.2s 的浮动区间统一收紧为 1.2s，降低误切。
- **用户手动干预**：点「落一条」始终优先，可覆盖自动信号。
- **容错**：拆分后每条待办允许**长按合并 / 拆分**（需求待确认项 5），识别错误可一键修正。
- **隐私与降级路径（方案 C·允许联网识别）**：
  - ASR **优先设备端识别**（iOS `requiresOnDeviceRecognition=true`、Android 离线语言包）；设备端不可用（未装离线包 / 授权失败 / 识别失败）时**联网回退**。
  - 联网识别时**音频可能发往系统厂商识别服务**（iOS Apple 服务端 / Android Google 语音服务），非本 App 自有服务器；不附带账号与待办写回。
  - 联网仍失败 → 降级文字输入（关闭语音按钮 + Toast 引导），记录流不中断。
- 伪代码（领域层，平台无关，阈值/标点集取自共享配置）：
  ```kotlin
  // 取自双端共享配置，避免漂移
  val SPLIT_PUNCTUATION = setOf('。', '！', '？', '；')  // 不含 、 与换行
  const val SPLIT_PAUSE_THRESHOLD_MS = 1200L

  fun onFinalSegment(text: String) {
      val cleaned = text.trim().removeEndPunctuation(SPLIT_PUNCTUATION)
      if (cleaned.isNotEmpty()) taskRepository.add(Task(title = cleaned, date = today()))
      asrController.continueListening() // 继续下一句
  }
  ```

### 4.4 抽象接口（便于 v1.1 换第三方）
```swift
protocol ASRController {
    func requestPermission() async -> ASRPermissionState
    func startContinuousListening(onPartial: (String) -> Void, onFinal: (String) -> Void)
    func stop()
    var isAvailable: Bool { get }
}
```
v1 实现 `NativeASRController`；v1.1 可加 `XfyunASRController` / `WhisperASRController` 同协议替换。

---

## 五、提醒 / 通知架构

### 5.1 提醒模型（方案 A·默认推荐值 + 单条可调）
> **默认推荐策略**：单条提醒 = `提醒时间 T` + 提前 `leadMinutes`（默认 **10** 分钟，即 T-10）+ 到点（T）+ 未完成每 10 分钟重复 `repeatCount`（默认 **3** 次，即 T+10/T+20/T+30；`repeatCount=0` 表示不重复/关闭重复）。
> **单条可调**：每条 `Task` 可独立覆盖默认 `leadMinutes` / `repeatCount`，关闭或调整自身提醒；未显式设置时沿用默认推荐值。字段定义见 §6.2 `Task.leadMinutes` / `Task.repeatCount`。

### 5.2 iOS：UNUserNotificationCenter
- **调度**：为单条待办生成一组 `UNNotificationRequest`，用 `UNCalendarNotificationTrigger` 按绝对时间触发：
  - 提前：`T-10`；到点：`T`；重复：`T+10 / +20 / +30`（仅当到点未标记完成时仍需响）。
- **取消 / 改期**：以 `taskId` 为 `request.identifier` 前缀，标记完成时批量 `removePendingNotificationRequests` 取消剩余提醒（避免已完成还响）。
- **交互**：通知 `category` 带「标记完成 / 推迟 10 分钟」Action，点击直接回调完成，闭环体验。
- **免打扰**：iOS 勿扰 / 专注模式系统级拦截，App 无法绕过；需 PRD 文案引导用户在「专注模式」允许本 App 通知，并做 App 内「待完成」列表兜底展示。

### 5.3 Android：WorkManager + NotificationManager
- **调度**：`AlarmManager` 在 Doze 下会被推迟，不可靠；用 **WorkManager `OneTimeWorkRequest`**（设 `initialDelay` 到各触发点）保证后台可靠触发。`T-10 / T / T+10 / +20 / +30` 各起一个 Work，到点 Work 内检查任务完成态：
  - 未完成 → 发通知（通知渠道 `channel_reminder`，重要级别 `HIGH` + 声音/振动）；完成 → 不发。
  - 重复次数由 Work 内计数控制，最多 3 次。
- **取消 / 改期**：以 `taskId` 为 tag，`WorkManager.cancelAllWorkByTag(taskId)` 取消剩余。
- **免打扰（DND）**：`NotificationManager` 在系统 DND 下可能被静音；重要提醒用 `setBypassDnd(true)` 需 `ACCESS_NOTIFICATION_POLICY` 权限（需引导用户授予「绕过勿扰」）。否则仅通知栏展示、不响铃，App 内列表兜底。

### 5.4 通知可靠性要点
- 所有提醒触发点以**设备本地时间**计算，存储 `remindAt` 绝对时间戳，避免时区/重启漂移。
- 重启后重新排程：App 启动 / 网络恢复时通过 `TaskRepository.tasksWithPendingReminders(until:)` 扫描未完成且有提醒的待办（覆盖跨日场景），重建通知（补偿重启丢失的 pending）。
- 测试覆盖：勿扰、低电量、Doze、App 被杀等场景（见测试协作）。

---

## 六、数据模型

### 6.1 实体关系（ER 概览）

```
Category(1) ──< (N)Task >──(N) Tag        // 任务归属一个分类，可打多个标签
Task(1) ──< (N)Reminder                     // 单任务多个提醒触发点（提前/到点/重复）
RecurringTemplate(1) ──< (N)Task           // v1.1：周模板生成当日任务（可覆盖）
MakeupRecord(1) ──< (N)Task                // v1.1：漏做项改期补生成
```

### 6.2 关键字段表

**Task（待办）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID / String | 主键，双端通用，便于未来同步 |
| title | String | 内容 |
| date | Date(yyyy-MM-dd) | 所属日（默认今天） |
| categoryId | FK → Category | 分类，可空（默认「其他」） |
| priority | Enum(高/中/低) | 默认中 |
| tagIds | 关联表 | 多对多 |
| isDone | Bool | 完成态 |
| doneAt | Date? | 完成时间（统计用，v1.1） |
| remindAt | Date? | 主提醒时间 |
| leadMinutes | Int | 提前提醒分钟数，默认 10（T-leadMinutes 触发提前提醒）；单条可覆盖，见 §5.1 方案 A |
| repeatCount | Int | 到点未完成重复提醒次数，默认 3；0 表示不重复/关闭重复；单条可覆盖 |
| sortOrder | Int | 拖拽排序 |
| source | Enum(语音/文字/模板/补生成) | 来源追溯 |
| templateId | FK? | 来自哪个循环模板（v1.1） |
| updatedAt | Date | 变更时间（同步用） |
| syncState | Enum(local/dirty/synced) | 默认 local（v1.1 用） |

**Reminder（提醒触发点）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| taskId | FK → Task | 归属任务 |
| fireAt | Date | 触发绝对时间 |
| type | Enum(提前/到点/重复1/2/3) | 触发类型 |
| repeatIndex | Int | 第几次重复 |
| fired | Bool | 是否已触发 |
| cancelled | Bool | 完成/改期后置 true |

> **触发点生成说明**：`Reminder` 的具体触发点（提前 / 到点 / 重复 1..N）由 `Task.leadMinutes` 与 `Task.repeatCount` 推导——仅当 `leadMinutes > 0` 时生成「提前」触发点，重复触发点数量等于 `repeatCount`（为 0 则不生成重复触发点）。单条 `Task` 调整这两个字段后，`ReminderScheduler.schedule` 重新计算触发点。

**Category（分类）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| name | String | 预设：工作/生活/学习/其他 + 自建 |
| isPreset | Bool | 预设不可删 |

**Tag（标签）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| name | String | 唯一，输入联想补全 |

**RecurringTemplate（v1.1 循环模板）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| weekdayMask | Int(0b1111111) | 周几生效 |
| baseTitle/category/priority | — | 模板内容 |
| createdAt | Date | — |

**MakeupRecord（v1.1 补生成）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| fromTaskId | FK | 原漏做项 |
| moveToDate | Date | 改到哪天 |
| generatedTaskId | FK | 补生成的任务 |

---

## 七、模块划分与接口定义

### 7.1 各层职责与边界

| 层 | 职责 | 边界（不做什么） |
|----|------|------------------|
| **UI 层** | 展示当日列表、输入框、语音按钮、提醒设置面板、完成/拖拽/编辑；持有交互态 | 不含业务逻辑；不直接调系统 ASR/DB，经 ViewModel/UseCase |
| **领域层（Domain）** | 业务规则：拆分文本成任务、提醒计划计算、完成态流转、进度统计（X/Y）、v1.1 模板生成/补生成 | 不依赖 Core Data/Room/Speech 具体类；仅依赖抽象接口 |
| **数据层（Repository）** | `TaskRepository` / `CategoryRepository` / `TagRepository` 实现，封装本地库读写、迁移 | 不处理 UI；不决定何时弹通知（交给通知层） |
| **语音层（ASR）** | 实现 `ASRController`，封装 iOS Speech / Android SpeechRecognizer，产出 final/partial 文本流 | 不决定如何切分（切分规则在领域层），只传文本 |
| **通知层（Reminder）** | 实现 `ReminderScheduler`，据 Task+Reminder 计算触发点，调用平台通知 API 排程/取消/补偿 | 不存储业务数据；只根据领域层给的提醒计划调度 |

### 7.2 关键接口定义（平台无关，伪代码）

```swift
// 数据
protocol TaskRepository {
    func todayTasks() -> [Task]
    func add(_ task: Task)
    func update(_ task: Task)
    func markDone(_ id: UUID, at: Date)
    func delete(_ id: UUID)
    func reorder(ids: [UUID])
    // 支撑重启/跨日提醒补偿（原缺口 B）：返回未完成且含未触发提醒、
    // 触发点在 [now, until] 区间内的待办（含跨日场景）
    func tasksWithPendingReminders(until date: Date) -> [Task]
}

// 语音
protocol ASRController {
    func requestPermission() async -> PermissionState
    var isAvailable: Bool { get }
    func start(onPartial: (String)->Void, onFinal: (String)->Void)
    func stop()
}

// 通知
protocol ReminderScheduler {
    func schedule(for task: Task)   // 据 remindAt 生成 T-10/T/T+10.. 触发点
    func cancel(for taskId: UUID)
    func rescheduleAllPending()     // 启动/重启补偿
}
```

### 7.3 跨层数据流示例（新增一条语音待办）
```
语音层 onFinal("买菜。") 
  → 领域层 splitter 生成 Task(title="买菜", date=today) 
  → TaskRepository.add 
  → 若设了 remindAt → ReminderScheduler.schedule 
  → UI 层 @FetchRequest/Flow 自动刷新列表
```

---

## 八、技术风险与缓解

| 风险 | 影响 | 等级 | 缓解 |
|------|------|------|------|
| 原生 ASR 中文/方言识别率有限 | 语音体验打折、误识别 | 高 | 文字兜底（Unwanted）；v1.1 接第三方（讯飞/Whisper）；支持识别后编辑 |
| 自动拆分误切（一条变多/多变一） | 待办碎片化/丢失 | 中 | 长按合并/拆分；停顿阈值可配；用户手动『落一条』干预 |
| 系统通知受限（iOS 专注 / Android DND / Doze） | 提醒漏达 | 高 | 明确免打扰策略与文案；重要通知 `bypassDnd`（Android 需授权）；App 内「待完成」列表兜底；可靠性测试覆盖 |
| 后台调度被系统杀（Android 尤其） | 重复提醒不响 | 中 | 用 WorkManager 而非裸 AlarmManager；启动补偿重排未来提醒 |
| 本地存储损坏/上限 | 数据丢失 | 低 | SQLite 事务 + WAL；单日数据量极小远未触上限；v1 损坏仅不崩溃+记日志（方案 B），v1.1 引入本地每日快照回滚（方案 C） |
| 权限被拒（麦克风/通知） | 功能不可用 | 中 | 首次引导 + 设置入口；拒绝后降级文字输入、App 内提示开启 |
| 双端行为不一致（拆分/提醒逻辑） | 体验割裂 | 中 | 领域层规则双端共用同一算法与阈值，平台只做传输 |
| v1 范围蔓延（加云同步/账号） | 排期/复杂度失控 | 中 | 严格零网络仅限数据写路径（无云端持久化/上传待办）；ASR 联网识别属识别链路回退，已拍板允许（方案 C）；所有同步能力仅留扩展点，v1 不实现 |

---

## 九、纯本地无同步的取舍与云同步扩展点

### 9.1 取舍说明
- **收益**：零登录 = 无注册流失门槛（核心差异化）；无服务端 = 无运维/合规/隐私数据外泄风险；开发量最小、可最快验证日活与留存。
- **代价**：换机/卸载数据不保留（留存受损，但符合 v1「极简验证」定位）；无多端协同；无备份。
- **决策**：v1 接受该代价，专注跑通「记录→提醒→完成」闭环验证假设。

### 9.2 云同步扩展点（v1 不做，仅路径规划）
1. **抽象隔离**：`TaskRepository` 接口不变，v1.1 增加 `CloudTaskRepository` + `SyncEngine`，本地库作为 single source of truth，云端做增量合并。
2. **变更追踪**：已预留 `updatedAt` + `syncState`(local/dirty/synced) + `id`(UUID)，可生成「待同步变更集」。
3. **冲突解决**：以 `updatedAt` 最后写胜出 + 字段级合并（v1.1 设计）；支持账号体系（邮箱/手机/OAuth）但不影响 v1 数据模型。
4. **迁移路径**：用户首次登录 → 上传本地全量 → 后续增量双向同步；卸载重装可恢复。
5. **第三方 ASR（F9）**：`ASRController` 协议已抽象，v1.1 可加实现热插拔，不影响上层。

---

## 十、给研发的落地建议（非排期，仅顺序）
1. 先搭双端骨架 + 数据层（Task/Category/Tag + Repository），跑通 F1/F5/F6 文字流。
2. 接入通知层，完成 F3 提醒（含重启补偿）。
3. 接入语音层，完成 F2 持续听 + 自动拆分 + 降级兜底。
4. 最后 F4 分类/优先级/标签。
5. v1.1 在稳定闭环上叠 F7/F8/F9 与云同步扩展点。

---

> 本草案不含排期、第三方采购与上架账号决策（见需求规划第七节，需负责人拍板）。研发实现前以 PRD + 本架构评审通过为准。

---

## 十一、已拍板决策记录（负责人拍板 · 与 PRD §14 对齐）

> 本节汇总 4 个 P0 阻塞项的终拍方案，供研发实现与测试核对；其余未决项（如待确认项 5 长按合并/拆分、PRD §14 其余条目）仍以最新 PRD 为准。

### P0-1 语音 ASR 策略 → 选 C（允许联网识别）
- **方案**：移除「纯本地零网络」的 ASR 硬约束；ASR **优先设备端识别**，设备端不可用时**联网回退**（音频可能发往系统厂商识别服务，非本 App 自有服务器，不附带账号与待办写回）。
- **影响章节**：§1.2 设计原则 #4、§4.3 隐私与降级路径；§8 风险表「v1 范围蔓延」缓解口径。
- **隐私说明**：联网识别仅限识别链路，v1 仍无云端持久化/上传待办的写路径。

### P0-2 提醒默认策略 → 选 A（默认推荐值 + 单条可调）
- **方案**：默认推荐值 `leadMinutes = 10`、`repeatCount = 3`（`0` 表示不重复/关闭）；每条 `Task` 可独立覆盖这两个字段关闭或调整自身提醒。
- **数据模型新增字段**：`Task.leadMinutes`（Int，默认 10）、`Task.repeatCount`（Int，默认 3）。
- **接口缺口补充（原缺口 B）**：`TaskRepository` 新增 `tasksWithPendingReminders(until:)`（等价 `tasksUntil(date:)`），支撑 App 重启/跨日提醒补偿重排。
- **影响章节**：§5.1、§5.4、§6.2（Task / Reminder）、§7.2。

### P0-3 存储损坏兜底 → v1 选 B；v1.1 升级 C
- **v1（方案 B）**：启动时 DB 打开失败 → 捕获异常记日志、重建空库，**仅不崩溃 + 记日志，不强制恢复**用户数据（零网络无备份源）。
- **v1.1（方案 C）**：引入每日本地轻量快照，损坏时回滚至最近快照降低丢失面。
- **影响章节**：§3.1 持久化策略、§8 风险表「本地存储损坏」缓解口径。

### P0-4 语音拆分规则 → 选 C（温和）
- **统一拆分标点集**：`{ 。！？； }`，**不含「、」与换行**（顿号/换行视为同一待办内的并列内容，不切分）。
- **长停顿阈值统一**：`>1.2s`（原 800ms~1.2s 浮动区间收紧为 1.2s）。
- **双端共享/参数化**：标点集与阈值集中在**双端共享配置/规格**，规避 Swift / Kotlin 实现漂移。
- **影响章节**：§4.3 拆分策略（含共享配置伪代码）、§8 风险表「双端行为不一致」。

### 待进一步澄清的点（已记录，非本架构阻断项）
1. **共享配置落地形态**：§4.3 提出「双端共享配置/规格」，需研发确认具体形式（独立 `.json` + 双端解析 / 代码生成 / 复制常量），以保证 Swift/Kotlin 零漂移。
2. **跨日补偿与 `until` 边界**：`tasksWithPendingReminders(until:)` 的扫描上界（如未来 7 天）与跨日唤醒触发条件需与通知层排程策略最终对齐。
3. **损坏兜底日志内容**：v1 方案 B 的日志是否需含可上报的最小诊断信息（仍不出户、不联网），建议研发定稿时明确。
