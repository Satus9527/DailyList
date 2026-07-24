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

## 与规格的偏差 / 待确认点
- **Room 多对多**：Android 用显式 `task_tag` 关联表（规格 §7.2）；iOS 用原生 many-to-many，两者语义等价。
- **拖拽手势**：M1 排序通过「上移/下移」按钮驱动 `reorder`（持久化已验证），完整手指拖拽建议 F4 阶段增强。
- **Gradle Wrapper**：未提交 `gradlew`，由 Android Studio / 本地 Gradle 处理（见上「如何打开」）。
- **exportSchema=true**：已配置 `ksp arg room.schemaLocation`，schema 将生成于 `app/schemas/`（规格 §9 迁移准备）。
