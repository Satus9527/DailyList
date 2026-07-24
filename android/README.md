# 每日计划 App · Android 端（M1）

> 平台：Kotlin + Jetpack Compose + Room（纯本地、零登录）。
> 实现范围：数据层 + F1 文字记录 + F5 完成/编辑 + F6 本地持久化（Stage 4 / Milestone M1）。

## 目录结构

```
android/
  settings.gradle.kts / build.gradle.kts / gradle.properties   # Gradle 工程
  app/
    build.gradle.kts             # AGP 8.2.2 / Kotlin 1.9.22 / Compose BOM / Room 2.6.1 / ksp
    proguard-rules.pro
    schemas/.gitkeep              # Room exportSchema 目录（规格 §9）
    src/main/
      AndroidManifest.xml
      assets/asr_split_config.json   # 同源副本（来自 /workspace/shared）
      res/values/{strings,themes}.xml
      java/com/dailyplan/app/
        DailyPlanApplication.kt   # 持 AppContainer，首启种子预设分类
        MainActivity.kt           # 承载 Compose 今日屏幕，提供 ViewModel
        domain/model/
          Enums.kt                # Priority / TaskSource / SyncState（String raw 落库）
          Task.kt                 # 纯领域模型 + makeNew + todayDateString
        data/local/
          AppDatabase.kt          # @Database(version=1, fallbackToDestructiveMigration)
          Converters.kt           # Date↔Long、UUID↔String
          TaskEntity / CategoryEntity / TagEntity / TaskTagCrossRef.kt
          dao/TaskDao.kt          # 含 7 方法对应 SQL（规格 §7.3）
          dao/CategoryDao.kt / TagDao.kt
          DatabaseProvider.kt     # 单例 + P0-3 损坏兜底（删库重建空库）
          DBErrorLogger.kt        # 本地日志（不联网）
          mapper/TaskMappers.kt   # TaskEntity ↔ 领域 Task
        data/repository/
          TaskRepository.kt       # 接口（7 方法，规格 §5.2）
          LocalTaskRepository.kt  # Room 实现（suspend，单事务提交）
          CategoryRepository.kt / TagRepository.kt
        ui/
          viewmodel/TodayViewModel.kt   # F1/F5/F6 + X/Y 进度
          screen/TodayScreen.kt / TaskItem.kt
          theme/Theme.kt
        di/
          AppContainer.kt         # 手动 DI：DB → Repository → ViewModel
          TodayViewModelFactory.kt
        util/
          CategorySeed.kt         # 预设分类固定 UUID（规格 §3.2）
          ASRSplitConfig.kt       # 解析 assets/asr_split_config.json（规格 §8）
```

## 如何用 Android Studio 打开与运行

1. 打开 Android Studio（Giraffe / Hedgehog 及以上）→ `File ▸ Open` 选择 `android/` 目录（识别为 Gradle 工程）。
2. 等待 Gradle 同步（首次会下载 AGP / Kotlin / Compose / Room 依赖，需联网）。
3. 连接模拟器或真机（minSdk 26 / Android 8.0+）→ 点击 Run。
   - 首次启动 `DailyPlanApplication` 注入 `AppContainer`，自动写入 4 个预设分类种子。
   - 数据存于 Room SQLite，杀进程 / 重启后从库加载（F6）。

> 未包含 Gradle Wrapper 脚本（`gradlew`）。如需离线构建，用 Android Studio 自带的 Gradle 或在根目录
> 执行一次 `gradle wrapper` 生成 wrapper；CI 亦可直连 Gradle 守护。

## M1 完成范围与未做项

### 已实现（M1）
- **数据层**：Task / Category / Tag / TaskTagCrossRef 实体按规格 §7 建模（Room @Entity/@Dao/@Query）；
  枚举以 String raw 落库；`date` 以 `yyyy-MM-dd` 本地时区存储；`title` ≤500（上层截断）；
  `syncState`/`updatedAt` 预埋；`Converters` 处理 Date/UUID。
- **TaskRepository 7 方法**：`todayTasks` / `add` / `update` / `markDone` / `delete` / `reorder` / 
  `tasksWithPendingReminders`，语义与 `LocalTaskRepository` 实现严格对应规格 §5（DAO SQL 见 §7.3，扫描语义 §5.3）。
- **CategoryRepository / TagRepository**：基础 CRUD；预设不可删、删自建回退「其他」；Tag 写入前归一。
- **F1 文字记录**：`OutlinedTextField` + 回车/「+」添加；去空白、超 500 截断并提示；默认落今天、分类「其他」、优先级中、source=TEXT。
- **F5 完成与编辑**：勾选完成（写 doneAt/isDone）、取消完成（AC-26）、行内编辑 title、删除、上下移改 sortOrder；顶部 X/Y 进度。
- **F6 本地持久化**：写操作经 Room 单事务提交；列表从 Room 加载；**损坏兜底（P0-3 方案 B）**——
  `DatabaseProvider` 打开/迁移抛异常 → `DBErrorLogger` 写 `filesDir/logs/db_error.log`（本地、不联网）→
  `context.deleteDatabase` 删损坏库 → 重建空库 → App 不崩溃。
- **共享 ASR 配置**：`assets/asr_split_config.json` 由 `ASRSplitConfig`（kotlinx.serialization）解析，M1 仅定义结构。

### 刻意未做（后续里程碑）
- **F2 语音输入**：原生 `SpeechRecognizer`、持续听、按停顿/标点拆分——M1 仅引用拆分配置结构，不实现业务逻辑。
- **F3 提醒排程**：`remindAt`/`leadMinutes`(默认10)/`repeatCount`(默认3) 字段已持久化，`tasksWithPendingReminders`
  接口已就绪，但 `ReminderScheduler`（WorkManager 排程/取消/重启补偿）未实现。
- **F4 分类/优先级/标签完整 UI**：字段/结构/种子/归一已实现，但分类选择、优先级切换、标签联想输入界面暂缓。
- **v1.1 云同步**：`syncState`/`updatedAt` 预埋，未消费，无账号/网络写路径。

## M2（F3 提醒）Android 完成项与未做项

> 阶段：M2（在 M1 数据层之上实现 Android 通知层）。依据 `设计规格_M2提醒排程.md` §4。

### 已实现（M2 · Android 通知层）
- **触发点生成**：`WorkManagerReminderScheduler.buildTriggerPoints` 按规格 §2.2 推导——`L>0→T-L`（提前）、`T`（到点）、`R>0→T+i×10分`（重复）。严格采用 P0-2 默认 10/3，单条经 `update` 覆盖；`L=0`/`R=0` 关闭对应点（AC-11）。
- **调度**：每个触发点一个 `OneTimeWorkRequest`，`setInitialDelay` 到绝对触发时刻，`tag = taskId.toString()`；`schedule` 先 `cancelAllWorkByTag` 再登记，幂等（改期/取消完成安全）。
- **触发时查 isDone（完整兜底）**：`ReminderWorker.doWork` 先 `repository.get(taskId)`，未完成才 `showReminder`，已完成直接结束（规格 §2.5 / §4.2），实现「到点已完成后续重复不响」。
- **通知渠道**：`channel_reminder`（IMPORTANCE_HIGH + 声音/振动），在 `DailyPlanApplication.onCreate` 创建。
- **通知交互**：`标记完成` / `推迟10分钟` 两个 Action → `ReminderActionReceiver` 广播：`complete→markDone+cancel`（AC-9），`snooze→snooze`（清旧建新，规格 §2.4）；点击通知跳转 App。
- **DND 处理**：`DndPolicyHelper` 检测 `ACCESS_NOTIFICATION_POLICY` 授权；仅授权时 `setBypassDnd(true)`，未授权仅栏显不响铃；首次未授权经 `maybeRequestDndPolicy` 引导至系统设置（SharedPreferences 去重）。`POST_NOTIFICATIONS` 在 `MainActivity` 运行时申请（API 33+）。
- **启动/重启补偿**：`rescheduleAllPending()` 扫 `tasksWithPendingReminders(now, now+7天)` 重建；由 `MainActivity.onResume`（前台）、`BootReceiver`（重启）触发，补偿 Doze/重启丢失。WorkManager 自身持久化已跨重启，此为二次修复。
- **接线**：`AppContainer` 注入 `WorkManagerReminderScheduler` 到 `TodayViewModel`；标记完成 `cancel`、取消完成 `schedule` 恢复、删除 `cancel`；`AndroidManifest` 加三权限 + 两 Receiver。
- **M1 复用**：未新增 Task 字段、未建独立 Reminder 表；新增 `TaskRepository.get(id)`（触发时查 isDone 用）。
- **异常兜底**：调度/取消/补偿均 `runCatching`，绝不致 App 崩溃（P0-3）。
- **F3 提醒设置 UI（M2-D，Task #36）**：
  - 新增 `ui/screen/ReminderSettingSheet.kt`：以 `ModalBottomSheet` 承载的提醒设置面板。提供「启用提醒」开关；
    启用时可选 `remindAt`（DatePickerDialog + TimePicker，默认今天 09:00）、`leadMinutes`（FilterChip 5/10/15/30 分，
    默认 10；关闭开关即 L=0）、`repeatCount`（FilterChip 1/2/3/5 次，默认 3；关闭开关即 R=0，AC-11）。
  - `ui/screen/TaskItem.kt`：每条待办加铃铛入口（`onSetReminder`），已设提醒时显示下次提醒时间（`MM/dd HH:mm`）且铃铛高亮。
  - `ui/screen/TodayScreen.kt`：持有 `reminderTask` 状态，以 `ModalBottomSheet` 弹出 `ReminderSettingSheet`，
    保存回调调 `viewModel.saveReminder(...)`。
  - `ui/viewmodel/TodayViewModel.kt`：新增 `saveReminder(taskId, remindAt, leadMinutes, repeatCount)`——
    先 `repository.update` 持久化新值，再据新值排程；编辑 `remindAt` 由 `reminderScheduler.schedule`
    内部先 `cancelAllWorkByTag` 再登记（幂等，AC-28 / R-E11）；`remindAt=null` 则 `cancel`。
  - 完成/删除的自动取消已就绪（`toggleDone`/`delete` 均调 `cancel`），与 UI 改动无冲突；500 字上限等 M1 规则不受影响。

### 刻意未做 / 需确认（M2）
- **iOS 端实现**：本任务仅 Android；iOS `NativeReminderScheduler` 不在范围内。
- **跨日强唤醒**：v1 不做后台常驻；超出 7 天窗口且多日不开 App 的远期提醒可能不达（规格 §5.3），由 App 内列表兜底。
- **需架构/测试确认**：
  1. `RescheduleAllPending` 用 `runBlocking` 一次性扫全窗口，数据量大时（理论上限低）可接受，建议真机验证前台补偿耗时。
  2. `ReminderWorker` 经 `DailyPlanApplication.container` 取依赖（手动 DI），需确认 WorkManager 在极低内存被杀重建时 `container` 懒加载时序安全。
  3. 通知 Action 的 `markDone` 经 Receiver 写入，未主动刷新 Compose 列表（依赖下次 `onResume` 的 `reload`）；如需实时刷新，建议 `todayTasks` 改为 `Flow` 持续观察。
  4. 端到端触发（Doze/重启/DND/时区切换）需真机验证（同 M1 验收跟进项 7/8）。

## M3（F2 语音）Android 完成项与未做项

> 阶段：M3（在 M1 数据层 + M2 通知层之上实现 Android 语音层）。依据 `设计规格_M3语音层.md`。
> 分层：语音层 `data/voice/`、领域拆分器 `domain/voice/`，均不新增 Task 字段；落库复用 M1 `TaskRepository.add(source=VOICE)`。

### 新增文件
- `data/voice/ASRController.kt`：平台无关 ASR 接口（规格 §2.2）——`isAvailable` / `suspend requestPermission()` / `start(onPartial,onFinal)` / `stop()` / `getBufferedText()` / `onDegrade`。
- `data/voice/PermissionState.kt` / `DegradeReason.kt` / `VoiceState.kt`：授权状态、降级原因、UI 语音状态枚举。
- `data/voice/NativeASRController.kt`：`android.speech.SpeechRecognizer` 实现（规格 §4）。
- `domain/voice/VoiceTaskSplitter.kt`：领域层自动拆分器（规格 §5），仅依赖 `ASRSplitConfig` 与 `TaskRepository`，不 import Speech。

### NativeASRController 要点（规格 §4.1–§4.4）
- 持续听：`EXTRA_LANGUAGE_MODEL=FREE_FORM`、`EXTRA_LANGUAGE="zh-CN"`、`EXTRA_PARTIAL_RESULTS=true`；`onResults` 后 `beginListening()` 形成持续听循环。
- 离线优先 → 联网回退（P0-1）：`EXTRA_PREFER_OFFLINE=true`；`ERROR_NETWORK` 首次重试一次，仍失败降级。
- 长停顿边界：`onRmsChanged` 监测静音累计，阈值取自 `config.splitPauseThresholdMs`（**禁止硬编码 1200**）；触发时把 `latestPartial` 作为一次 final 吐出，并置 `awaitingResultsAfterPause` 避免随后 `onResults` 重复落库。
- `onError` 映射（§6.1）：`ERROR_INSUFFICIENT_PERMISSIONS`→降级；`ERROR_NETWORK`→重试/降级；`ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT`→重启循环继续听（不生成空条）；其余→降级。
- `AudioFocus`：`AUDIOFOCUS_LOSS` 永久丢失→降级；`LOSS_TRANSIENT` 暂停（保留状态）、`GAIN` 恢复重听（§4.4）。
- `stop()` 前把尾句作为一次 final 收尾（空不回调，R-X4）。

### VoiceTaskSplitter 如何消费 asr_split_config.json（P0-4 防漂移）
- `AppContainer` 启动时由 `assets/asr_split_config.json` 经 `ASRSplitConfig.load()` 解析为单例（M1 已建结构）；config 为 null 时语音入口判不可用，记录流不中断。
- 拆分常量**全部来自 config**：`splitPunctuation` 转 `Set<String>` 仅其触发切分并丢弃尾标点；`includeEnumerationComma=false`→「、」保留在 title 内不切；`includeNewline=false`→换行保留不切；长停顿阈值取 `splitPauseThresholdMs`。
- 代码中**无任何拆分标点/1200 字面量**（CI/Review 可校验）。每段非空 → `Task.makeNew(source=VOICE)` → `repository.add()`；超 500 字截断（AC-29）。用户手动「落一条」优先于自动信号（R-E2）。

### 失败降级链（规格 §6）
- 触发：权限拒 / 无网无离线包 / 识别失败 / AudioFocus 永久丢失 → `onDegrade(reason)`。
- ViewModel 置 `VoiceState.Degraded` → 麦克风按钮置灰 + **Snackbar**「语音暂不可用，请改用文字输入」（`Scaffold` 承载）；文字输入保持可用（R-X1 / AC-6）。
- 提供「停止并保存当前已识别文本」：`saveBufferedAsText()` 把缓冲以**文字条目**（`source=TEXT`）落库，避免丢失已说内容；为空不落（R-X4）。

### 与 M1 接线
- `AppContainer` 注入 `NativeASRController` 与 `asrSplitConfig` 到 `TodayViewModel`；`TodayViewModel` 据 config 构造 `VoiceTaskSplitter`。
- 语音 final → `VoiceTaskSplitter.commitFinalSegment` → `repository.add(source=VOICE)`；Room 重载自动刷新列表，与 M1 文字输入并存，零返工。
- 新增语音状态 `voiceState` / `partialText` + 方法 `startVoice/stopVoice/commitManual/saveBufferedAsText/onPermissionDenied`。
- `TodayScreen` 加麦克风开关、实时 partial 文本、「落一条」「停止并保存」按钮；`RECORD_AUDIO` 运行时申请（`rememberLauncherForActivityResult`）；降级 Toast。

### F2 语音输入 UI（M3-D，Task #50 · `ui/screen/TodayScreen.kt`）
- **麦克风 FAB/按钮与文字输入并存**：列表上方麦克风 `IconButton` 切换录音（许可 `RECORD_AUDIO` 运行时申请）；
  与底部 `OutlinedTextField` 文字输入互不干扰；降级（`VoiceState.Degraded`）/ 不可用（`Unavailable`）时按钮置灰。
- **录音态视觉反馈**：听写中（`VoiceState.Listening`）显示红点脉冲（`VoiceRecordingIndicator` 呼吸式缩放+透明度）、
  `mm:ss` 计时（`LaunchedEffect` 1 秒心跳累加）、简易波形竖条（`VoiceWaveformBars` 起伏），纯视觉、不阻塞文字流。
- **实时中间文本**：`partialText` 经 `onPartial` 实时显示于控制条（空时显示「聆听中…」），仅展示不落库。
- **自动落一条**：`onFinal → splitter.commitFinalSegment` 按 JSON 标点切分并 `source=VOICE` 落库后刷新列表（Room 自动刷新）。
- **手动「落一条」**：`commitManual()` 把当前缓冲/partial 立即切分落库（优先于自动信号，R-E2）。
- **「停止并保存」**：`saveBufferedAsText()` 把当前缓冲以**文字条目**（`source=TEXT`）落库，避免丢失已说内容；为空不落（R-X4）。
- **降级 UI**：`VoiceState.Degraded` → 麦克风按钮置灰 + **Snackbar**「语音暂不可用，请改用文字输入」（由 `Scaffold` + `SnackbarHostState` 承载），文字录入仍可用（R-X1 / AC-6）。
- `AndroidManifest.xml` 已加 `RECORD_AUDIO` 权限。

### 刻意未做 / 需确认（M3）
- **iOS 端实现**：本任务仅 Android；iOS `NativeASRController`/`VoiceTaskSplitter` 不在范围内。
- **长按合并/拆分（§7 / AC-5）**：仅冻结接口语义，本 M3 未实现 `TaskMergeSplitUseCase`（标为后续）。
- **设置页隐私文案（§8）**：P0-1 文案未在设置页落地，仅降级 Toast 引导；建议 M3.1 补「语音开关 + 重授权入口」。
- **需确认 / 真机验证**：
  1. `onRmsChanged` 静音阈值（`SILENCE_RMS_THRESHOLD=1.0f`）为平台音频调参，非拆分常量，但需真机校准；长停顿与端侧 `onEndOfSpeech`/`onResults` 的边界去重逻辑需真机验证避免重复句段。
  2. 离线包回退联网的 `EXTRA_PREFER_OFFLINE` 行为因设备/系统版本而异，需真机验证 P0-1 链路与「停止并保存」时序。
  3. `SpeechRecognizer` 为 Google 服务依赖，非所有 ROM 内置；`isAvailable=false` 时降级路径已就绪。

## M4（提醒与首页增强）Android 完成项

> 阶段：M4（在 M1 数据层 + M2 通知层 + M3 语音层之上实现首页增强）。依据 `设计规格_M4增强.md`（D3 / D4 / S5 / R-4）。
> 复用 M1/M2/M3 全部能力，未新增 Task 字段；新增只读查询与方法均为数据层零返工。

### D3 错过的提醒区（AC-10）
- **检测做法**：`TodayViewModel.reload()` 将 `remindAt < now` 且 `isDone == false` 且展示日 `displayDay == 今日` 的任务归入 `missedTasks`（按 remindAt 升序）。
  - `displayDay(task)`（`domain/model/Task.kt`）沿用规格 §3.2：跨 0 点任务归 `remindAt` 所属本地日，普通任务归 `date`；仅展示层重归类，绝不写回 `date`。
  - 不引入触发日志表，按「应响未响」确定性推导（规格 §1.1）。
- **首页展示**：`TodayScreen` 顶部（进度之下、列表之上）渲染 `MissedReminderSection`「错过的提醒 · N」；单条 `TaskItem` 带橙红「提醒未达」胶囊（`badgeText="提醒未达"`）。空态整段不渲染。
- **点按行为**：勾选即标记完成（复用 `toggleDone` → `markDone` + 通知 `cancel`）；点击行进入行内编辑（与既有行为一致）。
- **刷新时机**：随 `reload()`（启动/前台/增删改后）重算，与 M2 `rescheduleAllPending` 同链路。

### D4 首页常驻提示（AC-20）
- **检测**：`data/reminder/NotificationStatusHelper.getStatus` 复用 M2 已就绪回调——
  - 通知权限：`NotificationManagerCompat.from(ctx).areNotificationsEnabled()`；
  - DND 拦截（API≥23）：`NotificationManager.currentInterruptionFilter` 为 `NONE`，或 `ALARMS`/`PRIORITY` 且未获「绕过勿扰」授权。
- **横幅**：仅当存在风险（通知关 / DND 拦截）时，`TodayScreen` 顶部常驻黄/橙横幅「提醒可能不送达，去开启通知 / 勿扰模式可能拦截提醒，去设置」，点击 `viewModel.openNotificationSettings` 深链：
  - 通知关 → `Settings.ACTION_APP_NOTIFICATION_SETTINGS`（`EXTRA_APP_PACKAGE`）；
  - DND → `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`。
- **重算**：`TodayScreen` 首次组合与 `MainActivity.onResume`、设置页关闭后均调 `refreshNotificationStatus`。无风险不渲染（零打扰）。

### S5 跨日提醒列表归类（R-U4 / AC-27②）
- **取数（仅展示层，不改 date）**：`TaskRepository`/`LocalTaskRepository` 新增只读查询 `tasksByDisplayDay(day)`，DAO 用 `date(remind_at/1000,'unixepoch','localtime')` 取 `remindAt` 本地日，并 `OR date = :day` 并入跨 0 点项；含已完成（置底由 `ORDER BY is_done` 控制）。
- **重归类**：`reload()` 基于该查询构建「错过的提醒 / 进行中 / 已完成」三区块，跨 0 点任务按展示日归入触发日进行中/已完成，原 `date` 存储不变。时区统一设备本地时区（R-U5）。
- 新增 `displayDay(task)` 工具函数（规格 §3.2），供 UI/判定复用。

### R-4 设置页（AC-22）
- 新增 `ui/screen/SettingsScreen.kt`，由首页右上角齿轮（`Icons.Filled.Settings`）以 `ModalBottomSheet` 进入。
- 内容：通知权限状态（绿/灰点）+ 去系统设置；麦克风权限状态 + 去应用详情设置（`ACTION_APPLICATION_DETAILS_SETTINGS`）；语音输入开关（持久化于 `util/SettingsPrefs`，关闭即禁用首页语音按钮）；P0-1 隐私说明文案；默认提醒策略只读展示（提前 10 分 + 到点 + 每 10 分重复最多 3 次）。
- 关闭设置页后重算 D4 横幅。

### R-4 合并/拆分 UI（AC-5）
- 新增领域用例 `domain/voice/TaskMergeSplitUseCase.kt`（接口 + `TaskMergeSplitUseCaseImpl`），复用 M1 `TaskRepository` 与 M2 `ReminderScheduler`，不新增 Task 字段。
  - `merge(tasks)`：保留最早 `date`、最前 `sortOrder`，title 以「、」连接，remindAt 取首个有提醒者，isDone 取「全完成才完成」；删除被并条目并调度主条提醒。
  - `split(task, at)`：第 `at` 字符处断开；断点为 `splitPunctuation` 标点时标点归属前段并丢弃；前段带走原 id 与 remindAt，后段新 id、相邻 `sortOrder`，无提醒。空段忽略（R-X4）。标点集取自 `ASRSplitConfig`（P0-4 防漂移）。
- **UI 入口**：`TaskItem` 长按（`combinedClickable` + `DropdownMenu`，并保留「更多」按钮兼容性）弹「合并到上一条 / 从此处拆分」。`TodayScreen` 按平铺顺序定位「上一条」（`mergeWithPrevious`），拆分弹 `SplitDialog`（光标定位拆分点）。调用后经 `repository.update/add/delete` 落库（`Flow` 自动刷新，重启保持）。
- 合并/拆分各标注埋点 `todo_merge` / `todo_split`（仅标注不写上报）。

### 接线与新增/修改文件
- 新增：`domain/voice/TaskMergeSplitUseCase.kt`、`data/reminder/NotificationStatusHelper.kt`、`util/SettingsPrefs.kt`、`ui/screen/SettingsScreen.kt`。
- 修改：`domain/model/Task.kt`（displayDay）、`data/repository/TaskRepository.kt` + `LocalTaskRepository.kt` + `dao/TaskDao.kt`（tasksByDisplayDay）、`ui/viewmodel/TodayViewModel.kt`（三区块 + D4 + 合并拆分 + 语音开关，reload 改用 tasksByDisplayDay）、`ui/screen/TodayScreen.kt`（D3/D4/分区/设置入口/拆分框）、`ui/screen/TaskItem.kt`（长按菜单 + 错过胶囊）、`di/AppContainer.kt`（注入新依赖）、`MainActivity.kt`（onResume 重算 D4）。
- 埋点调用点（仅标注）：`missed_reminder_shown`、`notification_banner_shown`、`settings_open`、`todo_merge`/`todo_split`。

### 刻意未做 / 需确认（M4）
- **iOS 端实现**：本任务仅 Android。
- **R-3 口径**：采用 M3「尾句单独成条」，由产品回写 PRD R-E2/AC-3（本 M4 仅作声明，不修改 PRD 原文）。
- **真机验证**：DND 拦截判定、`currentInterruptionFilter` 行为、跨日触发与归类、合并/拆分手感需真机验证（同 M2/M3 验收跟进项）。
- **需确认**：跨 0 点任务的 `tasksByDisplayDay` 依赖 SQLite `date()` 本地日函数；如厂商品牌 ROM 对 `date()` 本地时区处理有差异，建议真机抽样验证归类边界。

## 与规格的偏差 / 待确认点
- **Room 多对多**：Android 用显式 `task_tag` 关联表（规格 §7.2）；iOS 用原生 many-to-many，两者语义等价。
- **拖拽手势**：M1 排序通过「上移/下移」按钮驱动 `reorder`（持久化已验证），完整手指拖拽建议 F4 阶段增强。
- **Gradle Wrapper**：未提交 `gradlew`，由 Android Studio / 本地 Gradle 处理（见上「如何打开」）。
- **exportSchema=true**：已配置 `ksp arg room.schemaLocation`，schema 将生成于 `app/schemas/`（规格 §9 迁移准备）。

---

## M5 组织能力（F4 分类 / 优先级 / 标签）

> 基于 M1 已落地模型**扩展**，不重定义 `Category`/`Tag`/`Priority`/`TaskTagCrossRef` 与任何 `Task` 列（规格 §0 / §6 约束）。
> 严格对齐 `设计规格_M5组织能力.md`，覆盖 AC-12 / AC-13 / AC-30①②③④ 与 R-O1 / R-O2。

### 数据层新增（筛选 + 标签读写）
- `TaskRepository` 接口（M5 §3.2）新增：
  - `filteredTasks(date, filter: TaskFilter)` —— 组合筛选（分类+优先级+标签 AND；空条件=全部）
  - `tasksByCategory(categoryId, date)` / `tasksByPriority(priority, date)` / `tasksByTags(tagIds, date)` —— 单维便捷
  - `suggestTags(prefix, limit)` —— 标签联想补全（前缀先归一）
  - `tagsForTask(id)` / `setTags(taskId, tagIds)` —— 经 `task_tag` 联表读取 / 整体替换标签关联
  - `taskTagIds()` —— 批量读取 `taskId → 标签 id 集合` 映射（首页内存筛选用）
- `dao/TaskDao.kt` 新增：组合查询 `tasksByDateCategoryPriority`（分类含「其他=nil」语义、优先级可空）、`allCrossRefs`、`tagsForTask`（JOIN）、`suggestTags`（LIKE 归一前缀）、`setTags`（`@Transaction` 先删后插）。
- `LocalTaskRepository` 实现上述方法；标签筛选 AND 语义与 `untaggedOnly` 在内存层用 `TaskFilter.matches`（规格 §3.4）判定。

### 标签归一 / 去重（AC-30③ / R-O1）
- 归一函数集中至 `util/TagNormalizer.kt`（去首尾空格 → 全角转半角 → 小写），并补全 **U+3000 全角空格 → 半角空格** 分支以与 iOS 完全等价（规格 §5.1）。
- 写入与查询均经 `TagNormalizer.normalize`；`TagRepository.addOrReuse` 仍负责按归一名去重复用同一 `Tag` 行——**标签读取/写入复用既有 `TagRepository` 去重，未另起炉灶**。
- 编辑页保存时对每个标签调 `addOrReuse` 得到 `Tag.id`，收集为集合后调 `setTags` 整体替换（规格 §2.4 / §4.1）。

### 编辑页扩展（与 M4 提醒设置同 sheet，规格 §4.1 / §4.3）
- `ui/screen/ReminderSettingSheet.kt` 同 `ModalBottomSheet` 内新增三段，不另起新页：
  - **分类选择器**：4 预设 + 自建入口（`CategoryRepository.add`）；默认「其他」预设 `OTHER_ID`。
  - **优先级选择器**：高/中/低，默认「中」；列表行不强制展示标识。
  - **标签输入**：实时 `suggestTags` 联想 + 回车/逗号确认 + 归一去重 + 可删 chip；非必填可跳过。
- 保存改调 `TodayViewModel.saveTaskAll(...)`，一次性写 分类/优先级/标签（经 `setTags`）+ 提醒并重新排程。

### 首页筛选栏（规格 §4.2）
- `TodayScreen` 在 D4 常驻横幅**下**、D3 错过区**上**新增 `FilterBar`（横向滚动）：分类 / 优先级 / 标签（多选 AND）+ 仅无标签 + 清除（=恢复全部）。
- 筛选作用于 `TodayViewModel` 已加载的展示日集合，对「错过的提醒 / 进行中 / 已完成」三区块统一生效（内存过滤，零额外查询；规格 §3.4 推荐路径）。
- 与 M1–M4 既有 F1/F2/F3/F5/F6、D3/D4/S5 共存不冲突。

### 接线与新增/修改文件
- 新增：`domain/model/TaskFilter.kt`（`TaskFilter` + `matches`）、`util/TagNormalizer.kt`。
- 修改：`data/repository/TaskRepository.kt`、`LocalTaskRepository.kt`、`dao/TaskDao.kt`、`TagRepository.kt`（改引 `util.TagNormalizer`）、`Enums.kt`（新增 `Priority.displayName`）、`ui/viewmodel/TodayViewModel.kt`（filter/categories/allTags 状态 + `applyFilter`/`saveTaskAll`/`addCategory`/`suggestTags`/`tagsForTask`/`addTagFromInput`，构造器注入 `categoryRepository`/`tagRepository`）、`ui/screen/ReminderSettingSheet.kt`、`ui/screen/TodayScreen.kt`（FilterBar）、`di/AppContainer.kt`（注入 `categoryRepository`/`tagRepository`）。
- 刻意未做：iOS 端、F7/F8/F9（v1.1）。真机验证聚焦标签归一一致性与筛选 AND 语义（同 M1–M4 验收跟进项）。
