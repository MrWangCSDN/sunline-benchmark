# Tasks: 调用关系全量扫描页面

**Input**: Design documents from `/specs/002-call-relation-graph/`  
**Prerequisites**: plan.md ✅、spec.md ✅  
**Tests**: 不包含（未在规格中要求，手动验证即可）  
**Organization**: 单用户故事，任务按实现顺序排列

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（文件不同、无依赖关系）
- **[US1]**: 归属唯一用户故事"触发全量扫描并查看执行状态"
- 所有文件路径均为绝对相对项目根目录

---

## Phase 1: Setup（数据库准备）

**Purpose**: 确保后端数据表和菜单记录就绪，前端页面才能被导航到并有数据可展示

**⚠️ CRITICAL**: Phase 1 完成后前端开发才能做完整端到端验证

- [x] T001 在目标数据库执行 `src/main/resources/sql/create_call_relation_table.sql`，确认 `call_relation` 表已建立（`CREATE TABLE IF NOT EXISTS`，幂等可重复）
- [x] T002 在目标数据库执行 `src/main/resources/sql/add_call_relation_menu.sql`，确认 `sys_menu` 新增"调用关系图谱"记录，URL 为 `/call-relation.html`；执行前 `SELECT * FROM sys_menu WHERE url='/call-relation.html'` 确认不重复

**Checkpoint**: 数据库表已建立、菜单记录存在 → 可开始前端开发

---

## Phase 2: User Story 1 — 触发全量扫描并查看执行状态（Priority: P1）🎯 MVP

**Goal**: 提供一个操作页面，用户点击按钮触发全量扫描，能持续看到执行状态，完成后显示结果摘要

**Independent Test**: 访问 `http://localhost:8080/call-relation.html` → 页面正常加载 → 点击"触发全量扫描" → 按钮禁用、计时器开始 → 等待扫描完成 → 页面显示写入边数和耗时

### 实现任务

- [x] T003 [US1] 新建 `src/main/resources/static/call-relation.html`，搭建页面骨架：HTML 结构、引入 `/js/vue.global.js` 和 `/js/axios.min.js`、`<div id="app">`、Vue 3 `createApp` 挂载，参照 `service-build-scan.html` 的整体布局风格（紫色渐变背景 + 白色卡片）

- [x] T004 [US1] 在 `call-relation.html` 的 Vue data 中定义状态字段：`status`（'idle'|'scanning'|'success'|'error'）、`scanResult`（null / 扫描返回对象）、`summaryData`（null / summary 返回对象）、`elapsedSeconds`（0）、`errorMsg`（''）；实现 `computed` 属性 `statusText`（返回对应中文描述）和 `isScanning`（布尔）

- [x] T005 [US1] 在 `call-relation.html` 实现"统计概览"卡片区域：页面 `mounted` 时调用 `GET /api/relation/summary`，将 `totalEdges`、`violations`、`crossDomainCalls` 显示为三个数字卡片；数字使用大字号加粗展示

- [x] T006 [US1] 在 `call-relation.html` 实现"触发全量扫描"按钮及 axios 调用：点击后将 `status` 置为 `'scanning'`，调用 `POST /api/relation/scan`（axios timeout 设为 `1800000`，即 30 分钟）；resolve 时将 `status` 置为 `'success'`，保存 `scanResult`；reject 时将 `status` 置为 `'error'`，保存 `errorMsg`；无论成功失败均清除所有定时器并刷新 summary 数据

- [x] T007 [US1] 在 `call-relation.html` 实现**客户端计时器**（轨道 A）：`status` 变为 `'scanning'` 时启动 `setInterval(1000)`，每秒 `elapsedSeconds++`；扫描结束时清除；在状态区域显示"已运行 XX 秒"

- [x] T008 [US1] 在 `call-relation.html` 实现 **summary 轮询**（轨道 B）：`status` 变为 `'scanning'` 时启动 `setInterval(3000)`，每 3 秒调用 `GET /api/relation/summary` 并更新 `summaryData`；在状态区域显示"当前已落库边数: X"；扫描结束时清除轮询定时器

- [x] T009 [US1] 在 `call-relation.html` 实现**结果摘要区域**（仅 `status === 'success'` 时显示）：展示 `scanResult` 中的 `totalFiles`、`implFiles`、`utilFiles`、`totalEdges`、`flowEdges`、`violations`、`costMs`（转换为秒，保留两位小数）；使用网格卡片布局，违规数 > 0 时以橙色/红色高亮

- [x] T010 [US1] 在 `call-relation.html` 实现**错误提示区域**（仅 `status === 'error'` 时显示）：红色背景卡片，显示 `errorMsg`，并提示"可查看服务端日志排查原因"

- [x] T011 [US1] 在 `call-relation.html` 完善**边界情况处理**：(1) 扫描中按钮禁用且样式变灰；(2) 页面 `beforeUnmount` 生命周期钩子清除所有定时器防止内存泄漏；(3) 页面首次加载时在统计区域未获取到数据时显示"-"占位符，不显示 0 或空白

**Checkpoint**: 完成 T003–T011 后，US1 可独立验证：访问页面、触发扫描、全程状态反馈正常、结果显示正确

---

## Phase 3: Polish（收尾）

**Purpose**: 细节打磨，确保体验一致

- [x] T012 检查 `call-relation.html` 的页面标题（`<title>`）、header h1 文案与已有页面保持统一风格；确认菜单入口在导航栏可见并能正常跳转至 `/call-relation.html`

---

## Dependencies & Execution Order

### Phase 依赖

- **Phase 1（数据库准备）**：无依赖，立即可开始
- **Phase 2（US1 实现）**：T003 可在 Phase 1 同时开始，但完整端到端验证需 Phase 1 完成
- **Phase 3（收尾）**：依赖 Phase 2 全部完成

### Phase 2 内部顺序

```
T003（骨架）
  ↓
T004（状态数据）
  ↓
T005（summary 展示）← 可与 T006 并行开始
T006（按钮 + axios）
  ↓
T007（计时器）← 可与 T008 并行
T008（summary 轮询）
  ↓
T009（结果展示）
T010（错误展示）← 可与 T009 并行
  ↓
T011（边界处理）
```

### 并行机会

- T001 和 T002 可并行（不同 SQL 文件，但建议先执行 T001 再 T002 以防依赖）
- T005 和 T006 确定数据/事件流后可并行开发
- T007 和 T008 均依赖 T006 的 `status` 状态变量，确定后可并行开发
- T009 和 T010 均为结果展示区域，可并行开发

---

## Implementation Strategy

### MVP（最快路径，约 2 小时）

1. T001 + T002：执行 SQL（5 分钟）
2. T003：页面骨架（15 分钟）
3. T004 + T006：状态机 + 按钮扫描（30 分钟）
4. T007：计时器（10 分钟）
5. T009 + T010：结果展示（20 分钟）
6. 验证：端到端冒烟测试（10 分钟）

完成后即可交付可用版本，T005、T008、T011、T012 为增强项。

---

## Notes

- [P] 标注的任务文件不同，可并行，但因本功能全在单一 HTML 文件中，实际并行价值有限
- [US1] 是唯一用户故事标签，所有实现任务均属于它
- 每完成 2-3 个任务建议在浏览器中验证一次，避免积累 bug
- T006 的 axios timeout 1800000ms 是关键配置，务必设置，否则长扫描会被浏览器截断
- 参考文件：`src/main/resources/static/service-build-scan.html`（页面结构）、`src/main/resources/static/layer-call-rule.html`（CSS 风格）
