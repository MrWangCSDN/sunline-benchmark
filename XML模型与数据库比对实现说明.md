# XML模型与数据库比对 - 实现说明

## 功能概述

基于Excel文档比对功能，针对XML模型和数据库表结构比对的特殊需求进行定制。

## 已创建的文件

1. ✅ 前端页面：`src/main/resources/static/xml-db-compare.html`
2. ✅ Controller：`src/main/java/com/sunline/dict/controller/XmlDbCompareController.java`
3. ✅ Service接口：`src/main/java/com/sunline/dict/service/XmlDbCompareService.java`
4. ✅ Service实现框架：`src/main/java/com/sunline/dict/service/impl/XmlDbCompareServiceImpl.java`
5. ✅ SQL脚本：`src/main/resources/sql/add_xml_db_compare_menu.sql`
6. ✅ 菜单：已更新 `index.html`

## 核心实现要点

### 1. 特殊的比对规则

#### 字段部分只比对特定列
```
比对列：
- A列：字段名（完全匹配）
- B列：中文名（完全匹配）
- D列：数据库类型（应用等价规则）
- F列：空值（完全匹配）
- G列：默认值（去除::后缀后匹配）

忽略列：
- C列：字段类型
- E列：元数据类型
- H列：下拉列表
- I列：字段说明
- J列：是否贯标
```

#### D列（数据库类型）等价规则
```java
date == timestamp
numeric == decimal
varchar(100) == varchar(100)  // 完全匹配
```

#### G列（默认值）处理规则
```java
0::bigint → 0
0::numeric → 0
NULL::character varying → NULL
'default'::text → 'default'

比对时只比较 :: 前面的部分
```

### 2. 复用ExcelCompareService的代码

可以通过以下方式实现：

#### 方案A：组合模式（推荐）
```java
@Service
public class XmlDbCompareServiceImpl implements XmlDbCompareService {
    
    @Autowired
    private ExcelCompareServiceImpl excelCompareService;
    
    public Map<String, Object> compareFiles(...) {
        // 1. 调用ExcelCompareService的核心方法
        // 2. 在关键的比对点应用特殊规则
        // 3. 修改修订记录的sheet名称
    }
}
```

#### 方案B：继承模式
```java
@Service
public class XmlDbCompareServiceImpl extends ExcelCompareServiceImpl {
    
    @Override
    protected boolean compareAndMergeRow(...) {
        // 重写行比对逻辑，应用特殊规则
    }
}
```

### 3. 需要重写的核心方法

```java
/**
 * 比对字段行（应用XML-DB特殊规则）
 */
private boolean compareFieldRow(Row xmlRow, Row dbRow, Row resultRow) {
    boolean hasChanges = false;
    
    // 只比对A、B、D、F、G列
    int[] columnsToCompare = {0, 1, 3, 5, 6};  // A, B, D, F, G
    
    for (int colIndex : columnsToCompare) {
        String xmlValue = getCellValueAsString(xmlRow.getCell(colIndex));
        String dbValue = getCellValueAsString(dbRow.getCell(colIndex));
        
        // 应用特殊规则
        if (colIndex == 3) {  // D列：数据库类型
            if (!isTypeEquivalent(xmlValue, dbValue)) {
                hasChanges = true;
                // 标记差异
            }
        } else if (colIndex == 6) {  // G列：默认值
            String normalized1 = normalizeDefaultValue(xmlValue);
            String normalized2 = normalizeDefaultValue(dbValue);
            if (!Objects.equals(normalized1, normalized2)) {
                hasChanges = true;
                // 标记差异
            }
        } else {
            // A, B, F列：完全匹配
            if (!Objects.equals(xmlValue, dbValue)) {
                hasChanges = true;
                // 标记差异
            }
        }
    }
    
    return hasChanges;
}
```

### 4. 修订记录改名

```java
// 创建差异结果sheet（而不是"修订记录"）
Sheet revisionSheet = workbook.createSheet("差异结果");
```

## 实现步骤

### 阶段1：基础实现（当前）
- ✅ 创建基础设施（页面、Controller、Service）
- ✅ 添加菜单
- ✅ 定义特殊规则方法（isTypeEquivalent、normalizeDefaultValue）

### 阶段2：核心实现（待完成）
- ⏳ 实现 `compareFiles` 方法
- ⏳ 实现特殊的字段比对逻辑
- ⏳ 修改修订记录sheet名称
- ⏳ 应用类型等价和默认值处理规则

### 阶段3：测试和优化
- ⏳ 测试各种比对场景
- ⏳ 性能优化（复用样式缓存等）
- ⏳ 错误处理

## 使用示例

### 输入
- XML模型Excel：`model_v1.xlsx`
- 数据库Excel：`database_sit.xlsx`

### 输出
- 差异分析Excel：`model_v1_差异分析_20260202_153025.xlsx`
- 包含"差异结果"sheet页

### 差异结果示例

```
user_table表 - 字段差异：
A列: id （无差异）
B列: 主键ID → 用户ID （中文名不同）
D列: bigint → int8 （等价，无差异）
F列: N → Y （空值约束不同）
G列: 0::bigint → 0::numeric （值相同，无差异）
```

## 当前状态

基础框架已完成，核心比对逻辑需要完整实现。

建议：复用ExcelCompareServiceImpl的代码，通过参数化控制比对规则。

