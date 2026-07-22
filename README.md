# DailyList · 每日计划 App

极低门槛的当日待办记录工具：语音 + 文字快速记录，本地提醒，纯本地零登录。iOS（SwiftUI + Core Data）与 Android（Jetpack Compose + Room）双端原生实现。

## 仓库结构
- `docs/`：需求规划、PRD、技术架构、评审结论、设计规格、验收报告等全过程文档
- `shared/`：双端同源配置（如 ASR 拆分规则 `asr_split_config.json`，对应 P0-4）
- `ios/`：iOS 工程（SwiftUI + Core Data）
- `android/`：Android 工程（Jetpack Compose + Room）

## 阶段进度
- Stage 4 / Milestone M1（已完成）：数据层 + F1 文字记录 + F5 完成/编辑 + F6 本地持久化。详见 `docs/M1验收报告.md`
- 下一里程碑 M2：F3 本地提醒（含重启/跨日补偿）

## 同步规则
任何角色完成一个基本项目进程即提交并推送到本仓库（见 `docs/GITHUB_SYNC_RULE.md`）。
