# Implementation Plan: 调用关系全量扫描页面

**Branch**: `002-call-relation-graph` | **Date**: 2026-03-25 | **Spec**: [spec.md](./spec.md)  
**Input**: 用现有页面前后端框架实现

## Summary

新增 `call-relation.html` 静态页面，复用现有 Vue 3 + axios 前端框架，调用已实现的 `POST /api/relation/scan` 接口触发全量扫描。由于扫描接口是同步阻塞的（耗时可能数分钟），前端通过**客户端计时器 + 轮询 summary 接口**的组合方式，在等待期间持续呈现执行状态，避免页面假死。后端零改动，仅需补建数据库表并写入菜单记录。

---

## Technical Context

**Language/Version**: Java 17 / HTML + JavaScript (ES6)  
**Primary Dependencies**: Spring Boot 3.1.5（已有）、Vue 3 CDN（已内嵌 static/js/）、axios CDN（已内嵌 static/js/）  
**Storage**: MySQL 8.0（已有）、`call_relation` 表（SQL 脚本已就绪）  
**Testing**: 浏览器手动验证（与现有页面一致）  
**Target Platform**: 桌面浏览器（Chrome/Edge，≥1280px）  
**Project Type**: 内嵌静态前端 + Spring Boot 后端  
**Performance Goals**: 扫描接口超时上限 30 分钟；页面自身加载 < 2 秒  
**Constraints**: 不引入新的 npm/构建工具；不修改任何 Java 代码；不新增后端接口  
**Scale/Scope**: 单页面，单个用户操作场景

---

## Constitution Check

| 原则 | 检查项 | 结论 |
|------|--------|------|
| I. 分层架构 | 本功能不涉及分层调用，仅前端页面 | ✅ 不适用 |
| II. 领域隔离 | 不涉及跨域调用 | ✅ 不适用 |
| III. 接口与实现分离 | 后端零改动，已有接口符合规范 | ✅ 通过 |
| IV. Webhook 驱动 | 全量扫描由用户手动触发，不影响 Webhook 链路 | ✅ 通过 |
| V. 规格驱动开发 | 已有 spec.md → clarify → 本 plan.md | ✅ 通过 |
| VI. 代码质量 | 纯前端 HTML，无需 Javadoc；API 路径沿用 `/api/relation/*` | ✅ 通过 |
| VII. 简洁优先 | 复用已有 Vue 3 + axios，零新依赖 | ✅ 通过 |

---

## Project Structure

### Documentation (this feature)

```text
specs/002-call-relation-graph/
├── spec.md              # 需求规格
├── plan.md              # 本文件
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit.tasks 生成
```

### Source Code (新增/变更文件)

```text
src/main/resources/
├── static/
│   └── call-relation.html          ← 新增：全量扫描操作页面
└── sql/
    ├── create_call_relation_table.sql   ← 已存在，确认已在目标库执行
    └── add_call_relation_menu.sql       ← 已存在，确认已在目标库执行
```

**零改动文件**：所有 Java 代码、`application.yml`、其他 HTML 页面均无需改动。

---

## API 契约（复用现有接口）

### 1. 触发全量扫描

```
POST /api/relation/scan
Content-Type: application/json
（无请求体）

响应（同步，等待扫描完成后返回）：
{
  "code": 200,
  "data": {
    "totalFiles": 142,        // 含 getInstance 的文件总数
    "implFiles": 98,          // Impl 骨架文件数
    "utilFiles": 44,          // 工具类文件数
    "totalEdges": 3210,       // 写入的调用边总数
    "flowEdges": 156,         // 交易→服务编排边数
    "violations": 3,          // 违规调用边数
    "costMs": 18450           // 耗时（毫秒）
  }
}
```

**重要特性**：该接口是**同步阻塞**的，axios 需配置足够长的 timeout（建议 1800000ms = 30 分钟）。

### 2. 轮询统计概览（进度感知）

```
GET /api/relation/summary

响应：
{
  "code": 200,
  "data": {
    "totalEdges": 0,           // 扫描中为 0（清表后批量写入），完成后为最终数
    "violations": 0,
    "crossDomainCalls": 0
  }
}
```

**轮询策略**：扫描进行中每 3 秒调用一次，用于刷新统计数字（扫描完成后会从 0 跳升为最终值）。

---

## 前端实现方案

### 页面结构（Vue 3 组件状态）

```
状态机：
  idle      → 初始状态，按钮可点击
  scanning  → 扫描中，按钮禁用，计时器运行，summary 轮询开启
  success   → 扫描成功，展示结果摘要，按钮恢复
  error     → 扫描失败，展示错误信息，按钮恢复
```

### 关键数据字段

```javascript
data() {
  return {
    status: 'idle',          // idle | scanning | success | error
    scanResult: null,        // fullScan() 返回的 data 对象
    summaryData: null,       // querySummary() 返回的 data 对象
    elapsedSeconds: 0,       // 客户端计时（每秒 +1）
    errorMsg: '',            // 错误信息

    // 内部定时器引用（不暴露到模板）
    _timerInterval: null,
    _pollInterval: null,
  }
}
```

### 进度反馈机制（双轨并行）

```
触发扫描时同时启动：

轨道 A：客户端计时器（setInterval 1000ms）
  → 每秒更新 elapsedSeconds
  → 展示："已运行 XX 秒"
  → 用户感知：页面活着，不是假死

轨道 B：summary 轮询（setInterval 3000ms）
  → GET /api/relation/summary
  → 展示当前库中已有边数
  → 扫描完成瞬间数字从 0 跳升（视觉上的"完成信号"）

axios 主请求：POST /api/relation/scan（timeout 1800000ms）
  → resolve：清除两个定时器，切换状态 scanning→success，展示结果
  → reject：清除两个定时器，切换状态 scanning→error，展示错误
```

### UI 布局（参考 `service-build-scan.html` 风格）

```
┌─────────────────────────────────────────────────────┐
│  调用关系全量扫描                                      │
│  扫描全部 Java 骨架代码，重建调用关系图谱数据            │
├─────────────────────────────────────────────────────┤
│  [统计概览区]                                         │
│  调用关系总数: 3210  |  违规调用: 3  |  跨域调用: 12   │
├─────────────────────────────────────────────────────┤
│  [操作区]                                            │
│  [ 触发全量扫描 ]  ← 按钮（扫描中变灰+spinner）        │
│                                                     │
│  [状态区]                                            │
│  ● 就绪 / ⏳ 扫描中，已运行 18 秒 / ✅ 扫描成功        │
├─────────────────────────────────────────────────────┤
│  [结果区（仅扫描成功后显示）]                          │
│  扫描文件总数: 142    Impl 骨架文件: 98               │
│  写入调用边: 3210     交易→服务编排边: 156             │
│  违规调用: 3          耗时: 18.45 秒                  │
└─────────────────────────────────────────────────────┘
```

---

## 数据库操作（手动执行，非代码变更）

| 操作 | SQL 文件 | 说明 |
|------|---------|------|
| 建表 | `src/main/resources/sql/create_call_relation_table.sql` | 使用 `CREATE TABLE IF NOT EXISTS`，安全可重复执行 |
| 写入菜单 | `src/main/resources/sql/add_call_relation_menu.sql` | 向 `sys_menu` 表插入菜单记录，执行前确认未重复执行 |

---

## 风险与应对

| 风险 | 可能性 | 影响 | 应对 |
|------|--------|------|------|
| 扫描超过 30 分钟 | 低 | 中 | axios timeout 后前端展示"超时"提示，提示用户查看服务端日志 |
| 代码目录不存在 | 中 | 低 | 后端返回 error，前端显示具体错误信息 |
| 菜单重复插入 | 低 | 低 | 执行前先查 `sys_menu` 确认不存在再执行 SQL |
| 并发触发扫描 | 低 | 中 | 前端按钮禁用防二次点击；后端暂无锁机制，可接受 |

---

## 实现顺序

1. **确认 SQL 已执行**：在目标数据库执行建表 SQL + 菜单 SQL
2. **新建 `call-relation.html`**：按上述 UI 布局和状态机实现，风格对齐现有页面
3. **端到端验证**：访问页面 → 触发扫描 → 观察状态变化 → 核对结果数字与库中行数一致
