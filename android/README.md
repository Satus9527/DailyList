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

## 与规格的偏差 / 待确认点
- **Room 多对多**：Android 用显式 `task_tag` 关联表（规格 §7.2）；iOS 用原生 many-to-many，两者语义等价。
- **拖拽手势**：M1 排序通过「上移/下移」按钮驱动 `reorder`（持久化已验证），完整手指拖拽建议 F4 阶段增强。
- **Gradle Wrapper**：未提交 `gradlew`，由 Android Studio / 本地 Gradle 处理（见上「如何打开」）。
- **exportSchema=true**：已配置 `ksp arg room.schemaLocation`，schema 将生成于 `app/schemas/`（规格 §9 迁移准备）。
