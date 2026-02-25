# XML模型与数据库比对 - 完整规则说明

## 比对规则总览

### 字段部分（最复杂）

| 列 | 列名 | 是否比对 | 特殊处理规则 |
|---|------|---------|-------------|
| A | 字段名 | ✅ | 完全匹配 |
| B | 中文名 | ✅ | **去除括号**：`集中代收(1-是,2-否)` → `集中代收` |
| C | 字段类型 | ❌ | 跳过不比对 |
| D | 数据库类型 | ✅ | **类型等价**：`date`=`timestamp`, `numeric`=`decimal` |
| E | 元数据类型 | ❌ | 跳过不比对 |
| F | 空值 | ✅ | 完全匹配（Y/N） |
| G | 默认值 | ✅ | **去除括号和::后缀**：`(0)::numeric` → `0` |
| H | 下拉列表 | ❌ | 跳过不比对 |
| I | 字段说明 | ❌ | 跳过不比对 |
| J | 是否贯标 | ❌ | 跳过不比对 |

### 索引部分

| 列 | 列名 | 是否比对 | 特殊处理规则 |
|---|------|---------|-------------|
| A | 索引ID | ✅ | **忽略大小写**：`idx_user` = `IDX_USER` |
| B | 字段 | ✅ | **忽略大小写**：`user_id` = `USER_ID` |
| C | 索引类型 | ✅ | **忽略大小写**：`unique` = `UNIQUE` |
| 其他 | - | ❌ | 跳过不比对 |

### 主键部分

| 列 | 列名 | 是否比对 | 特殊处理规则 |
|---|------|---------|-------------|
| A | 主键名称 | ✅ | **忽略大小写**：`pk_table` = `PK_TABLE` |
| B | 字段 | ✅ | **忽略大小写**：`id,org_id` = `ID,ORG_ID` |
| 其他 | - | ❌ | 跳过不比对 |

## 详细处理规则

### 1. B列中文名处理

#### 括号去除规则
```
集中代收 == 集中代收(1-是,2-否) ✅
用户名 == 用户名（必填） ✅
状态 == 状态[0-无效,1-有效] ✅
金额 == 金额【单位：元】 ✅
```

#### 支持的括号类型
- `()` 英文圆括号
- `（）` 中文圆括号
- `[]` 英文方括号
- `【】` 中文方括号

#### 实现
```java
private String removeParentheses(String value) {
    value.replaceAll("\\([^)]*\\)", "");     // ()
    value.replaceAll("（[^）]*）", "");       // （）
    value.replaceAll("\\[[^\\]]*\\]", "");   // []
    value.replaceAll("【[^】]*】", "");       // 【】
}
```

### 2. D列数据库类型等价

#### 等价规则
```
date == timestamp ✅
timestamp == date ✅

numeric == decimal ✅
decimal == numeric ✅

numeric(14,7) == decimal(14,7) ✅ (带参数也等价)

varchar(100) == varchar(100) ✅ (完全相同)
varchar(100) ≠ varchar(200) ❌ (长度不同)
```

#### 实现逻辑
```java
private boolean isTypeEquivalent(String type1, String type2) {
    // 1. 完全相同 → true
    // 2. 都包含"date"/"timestamp" → true
    // 3. 都包含"numeric"/"decimal" → true
    // 4. 否则 → false
}
```

### 3. G列默认值处理

#### 处理规则
```
0 == 0::numeric ✅
0 == (0)::numeric ✅
0::bigint == (0)::numeric ✅
NULL == NULL::character varying ✅
'default' == ('default')::text ✅

1 ≠ 0::numeric ❌
NULL ≠ 0::numeric ❌
```

#### 处理步骤
```
(0)::numeric
↓ 步骤1：去除括号
0::numeric
↓ 步骤2：去除::后缀
0
```

#### 实现
```java
private String normalizeDefaultValue(String value) {
    // 1. 去除外层括号：(0)::numeric → 0::numeric
    while (value.startsWith("(") && value.contains(")::")) {
        value = value.substring(1, value.indexOf(")")) 
              + value.substring(value.indexOf(")") + 1);
    }
    
    // 2. 去除::类型后缀：0::numeric → 0
    if (value.contains("::")) {
        value = value.substring(0, value.indexOf("::"));
    }
    
    return value.trim();
}
```

### 4. 索引/主键忽略大小写

#### 匹配阶段（查找）
```java
// getSectionMap方法
map.put(key.trim().toLowerCase(), rowIndex);

// 结果：
"idx_user" → map["idx_user"]
"IDX_USER" → map["idx_user"]  // 都映射到同一个key
```

#### 比较阶段（判断相等）
```java
// areValuesEqual方法
if ("索引".equals(sectionType) || "主键".equals(sectionType)) {
    return value1.trim().equalsIgnoreCase(value2.trim());
}

// 结果：
"idx_user".equalsIgnoreCase("IDX_USER") → true ✅
"pk_table".equalsIgnoreCase("PK_TABLE") → true ✅
```

## 完整测试矩阵

### 字段部分测试

| XML模型 | 数据库 | 列 | 结果 | 原因 |
|---------|--------|---|------|------|
| `user_id` | `user_id` | A | ✅ 相等 | 完全匹配 |
| `用户ID` | `用户ID(必填)` | B | ✅ 相等 | 去除括号后相等 |
| - | - | C | - | 跳过 |
| `date` | `timestamp` | D | ✅ 相等 | 类型等价 |
| - | - | E | - | 跳过 |
| `Y` | `N` | F | ❌ 不等 | 空值约束不同 |
| `0` | `(0)::numeric` | G | ✅ 相等 | 规范化后都是0 |

### 索引部分测试

| XML模型 | 数据库 | 列 | 结果 | 原因 |
|---------|--------|---|------|------|
| `idx_user` | `IDX_USER` | A | ✅ 相等 | 忽略大小写 |
| `user_id` | `USER_ID` | B | ✅ 相等 | 忽略大小写 |
| `unique` | `UNIQUE` | C | ✅ 相等 | 忽略大小写 |

### 主键部分测试

| XML模型 | 数据库 | 列 | 结果 | 原因 |
|---------|--------|---|------|------|
| `pk_table` | `PK_TABLE` | A | ✅ 相等 | 忽略大小写 |
| `id,org_id` | `ID,ORG_ID` | B | ✅ 相等 | 忽略大小写 |

## 优势

✅ **智能匹配**：忽略大小写、括号、类型后缀  
✅ **减少误报**：真正的差异才会被标记  
✅ **灵活适配**：适应不同的命名风格  
✅ **精确分析**：只关注核心差异

所有规则已完整实现！

