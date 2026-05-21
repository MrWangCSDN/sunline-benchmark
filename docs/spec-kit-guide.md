# Spec Kit 文件结构与使用指南

## 一、目录总览

```
.specify/
├── init-options.json                    ← 初始化配置（自动生成，勿改）
├── memory/
│   └── constitution.md                  ← 项目宪法（你的项目核心规则）
├── scripts/bash/
│   ├── common.sh                        ← 公共函数库（自动调用，勿改）
│   ├── check-prerequisites.sh           ← 前置检查脚本（自动调用）
│   ├── create-new-feature.sh            ← 创建新功能分支+目录（自动调用）
│   ├── setup-plan.sh                    ← 初始化技术方案文件（自动调用）
│   └── update-agent-context.sh          ← 更新 AI 助手上下文（自动调用）
└── templates/
    ├── constitution-template.md          ← 宪法模板
    ├── spec-template.md                  ← 需求规格模板
    ├── plan-template.md                  ← 技术方案模板
    ├── tasks-template.md                 ← 任务拆分模板
    ├── checklist-template.md             ← 质量检查清单模板
    └── agent-file-template.md            ← AI 助手上下文文件模板
```

---

## 二、每个文件详解

### 2.1 `init-options.json` — 初始化配置

**内容**：记录 `specify init` 时的选项（AI 助手类型、脚本类型、版本等）。

```json
{
  "ai": "cursor-agent",
  "branch_numbering": "sequential",
  "speckit_version": "0.4.1"
}
```

**你需要做的**：什么都不用做。这是自动生成的元数据。

---

### 2.2 `memory/constitution.md` — 项目宪法（最重要）

**内容**：项目的"最高法律"，定义了：
- 核心原则（架构规范、领域规则、代码质量等）
- 技术标准（技术栈、版本）
- 开发工作流
- 治理规则

**你需要做的**：

| 场景 | 操作 |
|------|------|
| 首次 | 在 Cursor 中输入 `/speckit.constitution` + 你的项目原则描述 |
| 新增原则 | 再次执行 `/speckit.constitution` + 新原则 |
| 修改原则 | 手动编辑该文件，更新版本号和修改日期 |

**已完成**：我们已经创建了 v1.0.0 版本，包含 7 条核心原则。

---

### 2.3 `templates/spec-template.md` — 需求规格模板

**内容**：定义了需求规格的标准格式：
- 用户故事（按优先级 P1/P2/P3 排列）
- 每个故事的独立测试方案
- 功能需求
- 非功能需求
- 验收标准清单

**你需要做的**：

```
/speckit.specify 构建一个调用关系图谱功能，扫描 Java 骨架代码中的...
```

执行后会自动：
1. 创建 Git 分支（如 `001-call-relation`）
2. 在 `specs/001-call-relation/` 下生成 `spec.md`
3. AI 助手按模板填充你的需求

**输出文件**：`specs/001-call-relation/spec.md`

---

### 2.4 `templates/plan-template.md` — 技术方案模板

**内容**：定义了技术方案的标准格式：
- 技术上下文（语言、框架、数据库、目标平台等）
- 宪法合规检查（Constitution Check）
- 实现步骤
- 数据模型
- API 契约
- 风险评估

**你需要做的**：

```
/speckit.plan 
使用 Spring Boot 3.x + MyBatis Plus + MySQL。
扫描引擎用多线程并行，正则匹配 SysUtil.getInstance。
前端用 Vue + ECharts 关系图。
```

执行后会自动：
1. 在功能目录下生成 `plan.md`
2. 可能还会生成 `research.md`（技术调研）、`data-model.md`（数据模型）、`contracts/`（API 契约）

**输出文件**：`specs/001-call-relation/plan.md`

---

### 2.5 `templates/tasks-template.md` — 任务拆分模板

**内容**：定义了任务列表的标准格式：
- 按用户故事分组
- 每个任务有 ID、并行标记 `[P]`、精确文件路径
- 依赖关系排序
- 检查点验证

**你需要做的**：

```
/speckit.tasks
```

执行后会自动：
1. 读取 `spec.md` 和 `plan.md`
2. 生成结构化的任务列表 `tasks.md`
3. 任务按依赖关系排序，标注哪些可以并行

**输出文件**：`specs/001-call-relation/tasks.md`

---

### 2.6 `templates/checklist-template.md` — 质量检查清单模板

**内容**：定义了质量验证清单的格式：
- 按类别分组（如需求完整性、技术一致性、安全性等）
- 每项有编号（CHK001、CHK002...）
- 支持勾选标记

**你需要做的**（可选）：

```
/speckit.checklist 生成代码质量和架构合规检查清单
```

**输出文件**：功能目录下的 `checklist.md`

---

### 2.7 `templates/agent-file-template.md` — AI 助手上下文模板

**内容**：当 `/speckit.plan` 执行时，自动生成/更新 Cursor 的规则文件（`.cursor/rules/specify-rules.mdc`），告诉 AI 助手：
- 当前项目使用的技术栈
- 项目结构
- 构建/测试命令
- 代码风格规范
- 最近的变更历史

**你需要做的**：什么都不用做，`/speckit.plan` 会自动调用 `update-agent-context.sh` 更新。

---

### 2.8 脚本文件（scripts/bash/）

这些脚本是 Spec Kit 的"引擎"，由斜杠命令自动调用，你不需要手动执行。

| 脚本 | 被谁调用 | 做什么 |
|------|---------|--------|
| `common.sh` | 所有脚本 | 公共函数：找仓库根目录、获取分支名、模板解析 |
| `create-new-feature.sh` | `/speckit.specify` | 创建功能分支 + `specs/` 目录 + 从模板复制 `spec.md` |
| `setup-plan.sh` | `/speckit.plan` | 从模板复制 `plan.md` 到功能目录 |
| `check-prerequisites.sh` | `/speckit.tasks`、`/speckit.implement` | 检查 `plan.md`、`spec.md` 是否存在，列出可用文档 |
| `update-agent-context.sh` | `/speckit.plan` 完成后 | 解析 `plan.md` 中的技术栈，更新 `.cursor/rules/specify-rules.mdc` |

---

## 三、完整使用流程

```
步骤1（一次性）：
  /speckit.constitution + 项目原则描述
  → 生成 .specify/memory/constitution.md

步骤2（每个新功能）：
  /speckit.specify + 功能需求描述
  → 创建分支 001-xxx
  → 生成 specs/001-xxx/spec.md

步骤3（可选）：
  /speckit.clarify
  → AI 向你提问，澄清模糊需求
  → 更新 spec.md 中的 Clarifications 部分

步骤4：
  /speckit.plan + 技术栈描述
  → 生成 specs/001-xxx/plan.md
  → 可能还有 research.md、data-model.md、contracts/
  → 自动更新 .cursor/rules/specify-rules.mdc

步骤5（可选）：
  /speckit.analyze
  → 跨文档一致性检查（spec vs plan vs tasks 是否对齐）

步骤6：
  /speckit.tasks
  → 生成 specs/001-xxx/tasks.md

步骤7：
  /speckit.implement
  → AI 按 tasks.md 逐个执行任务，写代码
```

## 四、斜杠命令速查表

| 命令 | 用途 | 必选/可选 | 何时用 |
|------|------|----------|--------|
| `/speckit.constitution` | 定义项目宪法 | **必选**（一次性） | 项目初始化时 |
| `/speckit.specify` | 写需求规格 | **必选** | 每个新功能开始时 |
| `/speckit.clarify` | 澄清需求 | 可选 | specify 后、plan 前 |
| `/speckit.plan` | 写技术方案 | **必选** | 需求确认后 |
| `/speckit.checklist` | 生成质量清单 | 可选 | plan 后 |
| `/speckit.analyze` | 一致性分析 | 可选 | tasks 后、implement 前 |
| `/speckit.tasks` | 任务拆分 | **必选** | plan 确认后 |
| `/speckit.implement` | 执行实现 | **必选** | tasks 确认后 |

## 五、生成的功能目录结构

每执行一次完整流程，会在 `specs/` 下生成：

```
specs/
└── 001-call-relation/
    ├── spec.md          ← 需求规格（/speckit.specify 生成）
    ├── plan.md          ← 技术方案（/speckit.plan 生成）
    ├── research.md      ← 技术调研（/speckit.plan 可能生成）
    ├── data-model.md    ← 数据模型（/speckit.plan 可能生成）
    ├── tasks.md         ← 任务列表（/speckit.tasks 生成）
    ├── checklist.md     ← 质量清单（/speckit.checklist 生成）
    └── contracts/       ← API 契约（/speckit.plan 可能生成）
        └── api-spec.json
```

## 六、注意事项

1. **templates/ 目录是模板**，不要直接编辑里面的占位符。它们被斜杠命令读取后填充到 `specs/` 目录
2. **scripts/ 目录是引擎**，不要手动执行。由斜杠命令自动调用
3. **memory/constitution.md 是活文档**，可以随时通过 `/speckit.constitution` 更新，版本号会自增
4. **specs/ 目录纳入 Git 版本控制**，这样需求和方案有完整的变更历史
5. 宪法是所有后续命令的"基准"——`/speckit.plan` 会检查方案是否符合宪法原则
