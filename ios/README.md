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
    TodayTaskViewModel.swift      # F1/F5/F6 聚合，X/Y 进度
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

## 与规格的偏差 / 待确认点
- **Core Data 多对多建模**：iOS 采用原生 many-to-many 关系（`Task.tags`），Android Room 用显式 `task_tag` 关联表；
  两者语义等价，属双端存储差异的正常取舍（规格 §6 实体清单含 TaskTag，Room 侧已落表）。
- **拖拽手势**：M1 列表排序通过「上移/下移」按钮驱动 `reorder`（持久化已验证），完整手指拖拽手势建议在 F4 阶段增强。
- **模型形式**：采用 code-first 模型而非 `.xcdatamodeld`，便于源码直落仓库（见上「如何打开」）。
