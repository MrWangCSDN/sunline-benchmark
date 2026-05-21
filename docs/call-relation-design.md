# 调用关系图谱 — 设计文档

## 一、架构层级与调用规则

### 1.1 四层架构

```
┌─────────────────────────────────────────────────────────────┐
│  交易层 flowtrans                                            │
│  编排：pcs / pbs / method          ✅ 可跨领域                │
└──────────┬──────────┬──────────────┬────────────────────────┘
           │          │              │
           ▼          ▼              ▼
┌────────────────┐ ┌─────────┐ ┌──────────┐
│  pcs           │ │  pbs    │ │  method  │
│ (调用 pbs)     │ │         │ │ (终结点)  │
│ ✅可跨领域      │ │         │ └──────────┘
└───────┬────────┘ └────┬────┘
        │               │
        ▼               ▼
    ┌────────┐   ┌─────────────────────────────────┐
    │  pbs   │   │  pbcb / pbcp / pbcc / pbct       │
    └────┬───┘   └──────┬───────────┬───────────────┘
         │              │           │
         │              ▼           ▼
         │        ┌──────────┐ ┌──────────┐
         │        │pbcb/pbcp │ │  pbcc    │
         │        │→pbcc/pbct│ │→ pbct    │
         │        └─────┬────┘ └────┬─────┘
         │              │           │
         ▼              ▼           ▼
    ┌───────────────────────────────────────┐
    │           bcc（表 DAO）                 │
    │       只有构件层能调用                    │
    └───────────────────────────────────────┘
```

### 1.2 合法调用规则

| 调用方 | 可调用目标 | 跨领域规则 | 同层调用 |
|--------|-----------|-----------|---------|
| flowtrans | pcs, pbs, method | ✅ 允许 | — |
| pcs | pbs | ✅ 允许 | ❌ pcs 不调 pcs |
| pbs | pbcb, pbcp | ❌ 必须同领域 | ❌ pbs 不调 pbs |
| pbs | pbcc, pbct | ✅ 允许 | — |
| pbcb, pbcp | pbcc, pbct | ✅ 允许 | ❌ 不能调同类型 |
| pbcc | pbct | ✅ 允许 | ❌ pbcc 不调 pbcc |
| pbcb/pbcp/pbcc/pbct | bcc | ✅ 允许 | — |

### 1.3 四个领域

| 代号 | 领域 | 对应工程前缀 |
|------|------|------------|
| comm | 公共 | ccbs-comm-* |
| dept | 存款 | ccbs-dept-* |
| loan | 贷款 | ccbs-loan-* |
| sett | 结算 | ccbs-sett-* |

---

## 二、数据模型

### 2.1 核心表：call_relation（调用关系边表）

```sql
CREATE TABLE IF NOT EXISTS call_relation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    caller_id       VARCHAR(200) NOT NULL COMMENT '调用方 ID（XML 定义的 serviceType id）',
    caller_type     VARCHAR(20)  NOT NULL COMMENT '调用方类型：FLOWTRANS/PCS/PBS/PBCB/PBCP/PBCC/PBCT',
    caller_method   VARCHAR(200)          COMMENT '调用方骨架方法名',
    caller_domain   VARCHAR(20)           COMMENT '调用方领域：comm/dept/sett/loan',
    callee_id       VARCHAR(200) NOT NULL COMMENT '被调用方 ID',
    callee_type     VARCHAR(20)  NOT NULL COMMENT '被调用方类型：PCS/PBS/PBCB/PBCP/PBCC/PBCT/BCC/METHOD',
    callee_method   VARCHAR(200)          COMMENT '被调用方方法名',
    callee_domain   VARCHAR(20)           COMMENT '被调用方领域',
    callee_class    VARCHAR(200)          COMMENT '被调用方 Java 类名（getInstance 参数）',
    from_jar        VARCHAR(200)          COMMENT '来源工程',
    is_direct       TINYINT DEFAULT 1     COMMENT '1=直接调用 0=通过私有/工具方法间接调用',
    cross_domain    TINYINT DEFAULT 0     COMMENT '是否跨领域调用：0否 1是',
    rule_violation  TINYINT DEFAULT 0     COMMENT '是否违反调用规则：0否 1是',
    violation_desc  VARCHAR(500)          COMMENT '违规描述',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_relation (caller_id, caller_type, caller_method, callee_id, callee_type, callee_method),
    INDEX idx_caller (caller_id, caller_type),
    INDEX idx_caller_method (caller_id, caller_type, caller_method),
    INDEX idx_callee (callee_id, callee_type),
    INDEX idx_callee_method (callee_id, callee_type, callee_method),
    INDEX idx_violation (rule_violation),
    INDEX idx_cross_domain (cross_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用关系表（全链路依赖图的边）';
```

### 2.2 菜单

```sql
INSERT INTO sys_menu (name, url, icon, sort_order, parent_id, create_time, update_time)
VALUES ('调用关系图谱', '/call-relation.html', 'fas fa-project-diagram', 28, NULL, NOW(), NOW());
```

### 2.3 已有依赖表

| 表 | 作用 | 关系 |
|----|------|------|
| `flowtran` | 交易定义 | 节点（点） |
| `flow_step` | 交易编排步骤 | 交易→服务的边来源 |
| `service` | 服务定义（pbs/pcs） | 节点（点） |
| `component` | 构件定义（pbcb/pbcp/pbcc/pbct） | 节点（点） |
| `metadata_tables` | 表定义（bcc） | 节点（点） |
| `service_type_impl_file` | Impl 实现类信息 | 关联 Impl 文件与 serviceType |

---

## 三、扫描引擎设计

### 3.1 扫描源头

**只扫描含 `SysUtil.getInstance` 的 Java 文件**，不遍历全部文件。

文件分两类：

| 类别 | 匹配规则 | 处理方式 |
|------|---------|---------|
| Impl 骨架文件 | 文件名以 `PcsImpl.java`/`PbsImpl.java`/`PbcbImpl.java`/`PbcpImpl.java`/`PbccImpl.java`/`PbctImpl.java` 结尾 | 作为**入口**，完整解析方法+冒泡归集 |
| 工具类文件 | 含 `SysUtil.getInstance` 但非 Impl | 作为**中间节点**，注册到工具类注册表 |

### 3.2 全量扫描流程

```
阶段0：grep 收集
  遍历 /home/cbs/code 下所有 *.java 文件
  过滤出包含 "SysUtil.getInstance" 的文件
  → 分为 Impl 文件 + 工具类文件

阶段1：构建工具类注册表
  对每个工具类文件：
    解析方法块 → 提取 getInstance 调用
    注册到内存 Map：ClassName.methodName → [CallInfo 列表]

阶段2：多线程扫描 Impl 文件
  每个 Impl 文件：
    ├─ Step 1：拆分方法块（花括号配对，跳过注释/字符串）
    ├─ Step 2：提取变量→类名映射（处理变量方式 getInstance）
    ├─ Step 3：提取每个方法的 getInstance 调用（链式 + 变量）
    ├─ Step 4：构建类内方法调用图（只追踪同文件内的方法）
    ├─ Step 5：匹配工具类注册表（跨文件追踪）
    ├─ Step 6：冒泡归集（从骨架方法递归收集所有下游调用）
    └─ Step 7：输出方法级精确的调用边

阶段3：交易→服务 边
  从 flow_step 表直接转化（不需要扫 Java）

阶段4：批量写入
  清空 call_relation 表 → 批量 INSERT 所有边

阶段5：规则校验
  每条边在写入时自动判定：
    - 跨域标记（cross_domain）
    - 违规检测（rule_violation + violation_desc）
```

### 3.3 增量扫描流程

```
Webhook 推送 → 提取变更的 Java 文件列表
  │
  ├── 过滤出含 getInstance 的文件
  │
  ├── 分两堆：
  │     ├── Impl 文件
  │     └── 非 Impl 文件（工具类）
  │
  ├── Step 1: 更新工具类注册表
  │     对每个变更的工具类 → 重新解析 → 更新内存注册表
  │
  ├── Step 2: 收集需要重新扫描的 Impl 文件
  │     ├── 本身就变了的 Impl 文件
  │     └── 没变但调了"变更工具类"的 Impl 文件（grep 找出）
  │     → 合并去重
  │
  └── Step 3: 重新扫描这些 Impl 文件
        对每个 Impl：DELETE 旧边 → 扫描 → INSERT 新边
```

### 3.4 冒泡归集算法

**目标**：对每个骨架方法（public），把它直接+间接触达的所有 `getInstance` 汇总到它名下。

**示例**：

```java
public class LoanApplyPbsImpl {
    public Output applyLoan(Input input) {       // 骨架方法
        checkRisk(input);                         // 调私有方法
        MyUtils.doSomething(input);               // 调工具类
        SysUtil.getInstance(LoanAcctPbcb.class).createAcct(input);
    }
    private void checkRisk(Input input) {         // 私有方法
        SysUtil.getInstance(RiskPbcc.class).evaluate(input);
        formatResult(input);                      // 调另一个私有方法
    }
    private String formatResult(Input input) {    // 私有方法
        return SysUtil.getInstance(CommPbct.class).fmt(input);
    }
}
// 工具类 MyUtils.java
public class MyUtils {
    public static void doSomething(Input input) {
        SysUtil.getInstance(CheckPbcc.class).validate(input);
    }
}
```

**冒泡过程**：

```
formatResult 贡献:    CommPbct.fmt              ↑ 冒泡给 checkRisk
checkRisk 贡献:       RiskPbcc.evaluate         ↑ 冒泡给 applyLoan
                      + CommPbct.fmt (来自 formatResult)
MyUtils.doSomething:  CheckPbcc.validate        ↑ 从注册表冒泡给 applyLoan

applyLoan 最终汇总:
  直接:    LoanAcctPbcb.createAcct
  间接:    RiskPbcc.evaluate, CommPbct.fmt, CheckPbcc.validate
```

**伪代码**：

```java
List<CallInfo> collectTransitive(String methodName, ..., Set<String> visited) {
    if (!visited.add(methodName)) return List.of(); // 防循环

    List<CallInfo> result = new ArrayList<>();
    // 自身的 getInstance
    result.addAll(directCalls.get(methodName));
    // 自身的工具类调用
    result.addAll(utilCalls.get(methodName));
    // 递归展开类内调用
    for (String internal : internalCalls.get(methodName)) {
        List<CallInfo> bubbled = collectTransitive(internal, ..., visited);
        result.addAll(bubbled); // 标记为间接调用
    }
    return result;
}
```

### 3.5 getInstance 匹配模式

| 写法 | 正则 | 说明 |
|------|------|------|
| 链式调用 `SysUtil.getInstance(Xxx.class).method(...)` | `SysUtil\.getInstance\(\s*(\w+)\.class\s*\)\.(\w+)\s*\(` | 直接拿到类名+方法名 |
| 变量声明 `XxxPbcb xxx = SysUtil.getInstance(XxxPbcb.class)` | `(\w+)\s+(\w+)\s*=\s*SysUtil\.getInstance\(\s*(\w+)\.class\s*\)` | 建立 varName→className 映射 |
| 字段赋值 `xxx = SysUtil.getInstance(XxxPbcb.class)` | `(\w+)\s*=\s*SysUtil\.getInstance\(\s*(\w+)\.class\s*\)` | 同上 |
| 变量调用 `xxx.method(...)` | `varName\.(\w+)\s*\(` | 通过映射表找到对应类名 |

### 3.6 类型推断规则

| Java 类名后缀 | 推断类型 |
|--------------|---------|
| `*Pcs` | PCS |
| `*Pbs` | PBS |
| `*Pbcb` | PBCB |
| `*Pbcp` | PBCP |
| `*Pbcc` | PBCC |
| `*Pbct` | PBCT |
| 其他 | BCC |

### 3.7 领域推断规则

从文件路径或 ID 名称中提取：
- 路径含 `ccbs-comm` 或 `/comm/` → `comm`
- 路径含 `ccbs-dept` 或 `/dept/` → `dept`
- 路径含 `ccbs-loan` 或 `/loan/` → `loan`
- 路径含 `ccbs-sett` 或 `/sett/` → `sett`

---

## 四、规则校验引擎

每条边在写入时自动检测以下违规：

| 规则 | 说明 | violation_desc 示例 |
|------|------|-------------------|
| 同层调用 | 任何类型不允许调用自身类型 | `同层调用违规：PBS 不允许调用 PBS` |
| PCS 越级 | PCS 不允许直接调用构件 | `层级违规：PCS 只能调用 PBS，不允许直接调用 PBCB` |
| PBS 反向调用 | PBS 不允许调用 PCS | `层级违规：PBS 不允许调用 PCS` |
| PBS 跨域调 PBCB/PBCP | PBS 调 PBCB/PBCP 必须同领域 | `跨域违规：PBS(comm) 不允许跨域调用 PBCB(dept)` |
| PBCB/PBCP 越级 | 不允许调用 PBS/PCS/同级 | `层级违规：PBCB 不允许调用 PBS` |
| PBCC 越级 | 不允许调用 PBS/PCS/PBCB/PBCP/PBCC | `层级违规：PBCC 不允许调用 PBCB` |
| PBCT 越级 | 只能调用 BCC | `层级违规：PBCT 只能调用 BCC` |

---

## 五、接口设计

### 5.1 全量扫描

```
POST /api/relation/scan

请求体：无

返回示例：
{
  "code": 200,
  "data": {
    "totalFiles": 2500,
    "implFiles": 2000,
    "utilFiles": 500,
    "totalEdges": 15000,
    "flowEdges": 3000,
    "violations": 120,
    "costMs": 8500
  }
}
```

### 5.2 增量扫描

```
POST /api/relation/scan/incr

请求体：
{
  "changedFiles": [
    "ccbs-loan-impl/src/main/java/com/.../LoanApplyPbsImpl.java",
    "ccbs-comm-impl/src/main/java/com/.../MyUtils.java"
  ]
}

返回示例：
{
  "code": 200,
  "data": {
    "rescanFiles": 3,
    "newEdges": 25,
    "changedUtilClasses": ["MyUtils"],
    "costMs": 120
  }
}
```

### 5.3 影响面查询（向上递归）

```
GET /api/relation/impact?id=AcctPbcb&type=PBCB

场景：查看 AcctPbcb 构件被谁调用了，一直追溯到交易层

返回示例：
{
  "code": 200,
  "data": {
    "id": "AcctPbcb",
    "type": "PBCB",
    "callers": [
      {
        "callerId": "AcctPbs",
        "callerType": "PBS",
        "callerMethod": "queryAcct",
        "callerDomain": "comm",
        "calleeMethod": "query",
        "upstreamCallers": [
          {
            "callerId": "AcctPcs",
            "callerType": "PCS",
            "callerMethod": "doQuery",
            "upstreamCallers": [
              {
                "callerId": "TXN_ACCT_QUERY",
                "callerType": "FLOWTRANS",
                "upstreamCallers": []
              }
            ]
          }
        ]
      }
    ]
  }
}
```

### 5.4 依赖链查询（向下递归）

```
GET /api/relation/dependency?id=LoanApplyPbs&type=PBS

场景：查看 LoanApplyPbs 服务调了哪些构件和表

返回示例：
{
  "code": 200,
  "data": {
    "id": "LoanApplyPbs",
    "type": "PBS",
    "callees": [
      {
        "calleeId": "LoanAcctPbcb",
        "calleeType": "PBCB",
        "calleeMethod": "createAcct",
        "callerMethod": "applyLoan",
        "downstreamCallees": [
          {
            "calleeId": "LoanAcctDao",
            "calleeType": "BCC",
            "calleeMethod": "odb1",
            "downstreamCallees": []
          }
        ]
      },
      {
        "calleeId": "RiskPbcc",
        "calleeType": "PBCC",
        "calleeMethod": "evaluate",
        "callerMethod": "applyLoan",
        "downstreamCallees": [...]
      }
    ]
  }
}
```

### 5.5 违规调用列表

```
GET /api/relation/violations

返回示例：
{
  "code": 200,
  "data": [
    {
      "callerId": "SettAcctPbs",
      "callerType": "PBS",
      "callerMethod": "transfer",
      "callerDomain": "sett",
      "calleeId": "DeptAcctPbcb",
      "calleeType": "PBCB",
      "calleeDomain": "dept",
      "ruleViolation": 1,
      "violationDesc": "跨域违规：PBS(sett) 不允许跨域调用 PBCB(dept)"
    }
  ]
}
```

### 5.6 统计概览

```
GET /api/relation/summary

返回示例：
{
  "code": 200,
  "data": {
    "totalEdges": 15000,
    "violations": 120,
    "crossDomainCalls": 800
  }
}
```

---

## 六、性能预估

| 阶段 | 预估耗时 | 说明 |
|------|---------|------|
| grep 阶段 | 2~5 秒 | 遍历所有 Java 文件，文本搜索 |
| 工具类注册表 | < 1 秒 | 几百个文件解析 |
| Impl 扫描（多线程） | 5~15 秒 | 2000 个文件，8 线程并行 |
| 交易→服务 边 | < 1 秒 | 数据库查询转化 |
| 写入数据库 | 2~5 秒 | 批量 INSERT |
| **全量总耗时** | **10~30 秒** | |
| **增量单次** | **< 500 毫秒** | 通常只扫 1~5 个文件 |

---

## 七、后续规划

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | 建表 + 核心扫描引擎 + API | ✅ 已完成 |
| Phase 2 | 前端可视化页面（关系图/树形/表格） | 待开发 |
| Phase 3 | Webhook 触发增量更新自动化 | 待集成 |
| Phase 4 | 变更影响评审报告（改了某个表，自动列出影响交易） | 待开发 |
