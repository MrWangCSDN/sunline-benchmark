# Feature Specification: 影响面查询接口

**Feature Branch**: `001-impact-query-api`  
**Created**: 2026-02-27  
**Status**: Draft  
**Input**: 基于 call_relation 表，设计影响面查询接口，支持从任意节点（pbs/pcs/pbcb/pbcp/pbcc/pbct/tables/c_schema）向上追溯到交易层或向下追踪到表层

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 变更影响面分析（向上追溯） (Priority: P1)

作为一名开发人员，当我修改了某个构件（如 `AcctPbcb` 的 `query` 方法）或某张表（如 `cust_info`），我希望能一键查出"谁调了它"，从构件层一直追溯到交易层，完整呈现所有受影响的上游调用方，以便评估本次修改的影响范围。

**Why this priority**: 这是影响面分析的核心场景——"改了底层，影响了哪些交易"，是开发变更评审和上线评估的刚需。

**Independent Test**: 给定一个已入库的构件 ID，调用影响面查询接口，验证返回的树形结构包含正确的上游 pbs→pcs→flowtrans 链路。

**Acceptance Scenarios**:

1. **Given** call_relation 表中已有 `AcctPbcb.query` 被 `AcctPbs.queryAcct` 调用的边，且 `AcctPbs` 被交易 `TXN_ACCT_QUERY` 编排，**When** 查询 `AcctPbcb` 的影响面，**Then** 返回树形结构包含 `AcctPbs.queryAcct → TXN_ACCT_QUERY`。
2. **Given** 某个表 DAO 被多个构件的不同方法调用，**When** 查询该表的影响面，**Then** 返回所有调用链路（多条分支），每条从表层追溯到交易层。
3. **Given** 调用链中存在跨域调用（如 loan 领域的 pbs 调了 comm 领域的 pbcc），**When** 查询影响面，**Then** 跨域边需标注 `crossDomain: 1`。

---

### User Story 2 - 依赖链分析（向下追踪） (Priority: P1)

作为一名开发人员或架构师，当我查看某个交易或服务时，我希望能一键查出"它调了谁"，从交易层一直追踪到表层，完整呈现所有下游被调用方，以便了解该交易的完整依赖图。

**Why this priority**: 与影响面分析互补——"这个交易依赖了什么"，是交易上线前的依赖确认和故障排查的关键。

**Independent Test**: 给定一个交易 ID，调用依赖链查询接口，验证返回的树形结构包含正确的 flowtrans→pbs/pcs→pbcb→bcc 链路。

**Acceptance Scenarios**:

1. **Given** 交易 `TXN_LOAN_APPLY` 编排了 `LoanApplyPbs`，`LoanApplyPbs.applyLoan` 调了 `LoanAcctPbcb.createAcct` 和 `RiskPbcc.evaluate`，**When** 查询 `TXN_LOAN_APPLY` 的依赖链，**Then** 返回完整的两条分支树。
2. **Given** 某个 pbs 方法通过私有方法间接调用了构件（`isDirect=0`），**When** 查询依赖链，**Then** 间接调用也出现在结果中。

---

### User Story 3 - 精确到方法级的影响面查询 (Priority: P2)

作为一名开发人员，当我只修改了某个构件的某一个方法（如 `AcctPbcb.query`），我不希望看到整个构件的全部调用方，只想看到调了这个具体方法的上游。

**Why this priority**: 方法级精度能显著缩小影响范围，避免过度评审。

**Independent Test**: 传入构件 ID + 方法名，验证返回结果只包含调用了该方法的链路，不包含调用了同构件其他方法的链路。

**Acceptance Scenarios**:

1. **Given** `AcctPbcb` 有两个方法 `query` 和 `update`，`PbsA.methodA` 调了 `query`，`PbsB.methodB` 调了 `update`，**When** 查询 `AcctPbcb.query` 的影响面，**Then** 只返回 `PbsA.methodA` 的链路，不返回 `PbsB.methodB`。

---

### User Story 4 - 多节点类型统一查询 (Priority: P2)

作为一名用户，我希望影响面/依赖链查询支持所有节点类型，包括 pbs、pcs、pbcb、pbcp、pbcc、pbct、tables（BCC）、c_schema（复合类型），使用相同的接口格式，只需传入不同的 type 参数。

**Why this priority**: 统一接口降低使用成本，用户不需要记多个接口。

**Independent Test**: 分别用不同的 type 值（PBS/PBCB/BCC 等）调用同一个接口，验证均能返回正确结构。

**Acceptance Scenarios**:

1. **Given** call_relation 表中有 PBS 类型的边，**When** 以 `type=PBS` 查询影响面，**Then** 返回正确结果。
2. **Given** call_relation 表中有 BCC 类型的边，**When** 以 `type=BCC` 查询影响面，**Then** 返回正确结果。
3. **Given** call_relation 表中有 c_schema 对应的类型边，**When** 以 `type=COMPLEX` 查询影响面，**Then** 返回正确结果。

---

### User Story 5 - 违规调用快速定位 (Priority: P3)

作为一名架构师，我希望能直接查看所有违反调用规则的边（同层调用、跨域违规等），以便做架构治理。

**Why this priority**: 规则治理是长期需求，不影响核心查询功能。

**Independent Test**: 调用违规查询接口，验证返回的列表只包含 `rule_violation=1` 的记录。

**Acceptance Scenarios**:

1. **Given** call_relation 表中有 `PBS→PBS` 的同层调用边，**When** 查询违规列表，**Then** 该边出现在结果中，`violationDesc` 包含"同层调用违规"。
2. **Given** 有 `PBS(sett)→PBCB(dept)` 的跨域违规边，**When** 查询违规列表，**Then** 该边出现在结果中，`violationDesc` 包含"跨域违规"。

---

### Edge Cases

- 查询的节点 ID 在 call_relation 中不存在时，返回空的 callers/callees 列表，不报错
- 调用链中存在环形引用（理论上不应该有，但防御性处理）时，递归深度限制在 10 层，避免死循环
- 某条边的 callee_domain 为 null（无法识别领域），跨域标记为 0，不视为违规
- call_relation 表为空时（未执行过扫描），所有查询返回空结果，并提示"请先执行全量扫描"

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供影响面查询接口，支持从任意节点 ID + 类型向上递归查询所有调用方
- **FR-002**: 系统 MUST 提供依赖链查询接口，支持从任意节点 ID + 类型向下递归查询所有被调用方
- **FR-003**: 影响面和依赖链查询 MUST 支持精确到方法级（传入可选的 method 参数）
- **FR-004**: 递归查询 MUST 限制最大深度为 10 层，防止环形引用导致死循环
- **FR-005**: 查询结果 MUST 包含每条边的完整信息：调用方/被调用方的 ID、类型、方法名、领域、跨域标记、违规标记
- **FR-006**: 系统 MUST 提供违规调用列表查询接口，返回所有 `rule_violation=1` 的边
- **FR-007**: 系统 MUST 提供统计概览接口，返回总边数、违规数、跨域调用数
- **FR-008**: 所有查询接口 MUST 支持以下节点类型：FLOWTRANS、PCS、PBS、PBCB、PBCP、PBCC、PBCT、BCC、COMPLEX、METHOD
- **FR-009**: 查询结果 MUST 以树形 JSON 结构返回，支持前端直接渲染为关系图
- **FR-010**: 所有查询接口 MUST 免鉴权（通过 WebMvcConfig 白名单）

### Key Entities

- **CallRelation（调用关系边）**: 存储调用方→被调用方的方法级关系，包含双方的 ID、类型、方法名、领域、跨域标记、违规标记
- **节点（Node）**: 交易(flowtrans)、服务(pcs/pbs)、构件(pbcb/pbcp/pbcc/pbct)、表(bcc/tables)、复合类型(complex/c_schema)
- **调用链（Chain）**: 从任意节点出发，沿边递归向上或向下遍历形成的有向树

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 给定任意已入库的节点 ID，影响面/依赖链查询在 2 秒内返回完整树形结果
- **SC-002**: 方法级查询的结果精度 100%——不包含未调用该方法的无关链路
- **SC-003**: 递归查询在环形引用场景下 MUST 在 10 层内终止，不产生超时或内存溢出
- **SC-004**: 违规查询结果覆盖所有已定义的规则类型（同层调用、跨域违规、层级越级）
- **SC-005**: 所有接口返回格式统一（Result 包装），前端无需针对不同接口做特殊处理

## Assumptions

- call_relation 表已通过全量扫描或增量扫描写入了数据（接口本身不负责扫描，扫描由 `/api/relation/scan` 触发）
- 节点类型的推断（从类名后缀判断 PBS/PBCB 等）在扫描阶段已完成，查询阶段直接使用 `caller_type`/`callee_type` 字段
- 领域信息在扫描阶段已写入 `caller_domain`/`callee_domain` 字段，查询阶段直接使用
- c_schema（复合类型）在 call_relation 中以 `callee_type = 'COMPLEX'` 存储
- 前端可视化页面属于后续阶段（Phase 4），本规格只涉及 API 接口设计
