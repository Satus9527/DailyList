# GitHub 同步规则（全员 · 全角色）

> 适用范围：本项目的所有角色（项目 / 产品 / 架构 / 开发 / 测试）及其子代理。
> 仓库：`https://github.com/Satus9527/DailyList.git`

## 核心规则
**任何角色，每完成一个基本项目进程（阶段交付、里程碑、评审结论、可运行代码增量等），必须立即将变更提交（commit）并推送到 GitHub 仓库。** 不等、不攒、不依赖他人代传。

## 约定
1. 文档统一放 `docs/`；源码按模块组织于仓库根目录。
2. 提交前先 `git pull --rebase origin main` 避免冲突；空仓首次直接推 `main`。
3. Commit message 格式：`[角色] 阶段/里程碑 - 简述`
   例：`[产品] PRD v1 完成`、`[架构] M1 数据层脚手架`、`[项目] 阶段三评审结论`。
4. 推送失败（鉴权 / 冲突）立即上报，不得静默跳过。
5. **对子代理同样生效**：每次给角色（子代理）派活时，必须在其 prompt 中注入"完成后 git commit 并 push 到 GitHub（仓库 https://github.com/Satus9527/DailyList.git）"。

## 仓库信息
- 远程：origin = `https://github.com/Satus9527/DailyList.git`
- 默认分支：`main`
- 本规则文档自身也随首次推送入库，作为统一上下文的一部分。

## 执行机制（落地方式）
- **工作副本**：`/workspace`（各角色/子代理在此产出文档与代码）。
- **仓库克隆**：本地克隆于沙箱内的 DailyList 仓库，`docs/` 存放规划/PRD/评审等文档，源码后续按模块置于仓库根。
- **每个基本进程完成后**，由协调角色（或该角色本人在获得仓库与 token 上下文时）执行：
  1. 将 `/workspace` 中本次变更的产出复制到仓库对应目录（`docs/` 或根）；
  2. `git pull --rebase origin main` 先同步远端；
  3. `git add -A && git commit -m "[角色] 阶段/里程碑 - 简述"`；
  4. `git push origin main` 上传。
- 推送失败（鉴权/冲突）立即上报，不静默跳过。
- 目标：让 GitHub 始终反映最新项目上下文，任何角色的成果不积压。
