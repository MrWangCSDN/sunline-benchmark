# Excel数据清洗功能更新说明

## 更新概述

在原有的"保留Sheet页"功能基础上，新增了"Sheet页名称替换"功能，支持灵活的功能组合使用。

## 新增功能

### 1. Sheet页名称替换

#### 功能描述
- 支持批量替换sheet页名称
- 格式：`旧名称 --> 新名称`（每行一个规则）
- **旧名称匹配忽略大小写**（例如：USER_INFO、user_info、User_Info都会匹配）
- 新名称保持原样
- 自动更新总览表B列和所有相关超链接

#### 使用示例
```
user_info --> user_info_new
order_list --> order_list_v2
product --> product_detail
```

#### 执行规则
- 逐行解析替换规则
- 使用 `-->` 作为分隔符（前后可以有空格）
- 如果新名称已存在，跳过该规则并记录警告
- 替换成功后，自动更新所有相关引用

### 2. 灵活的功能组合

支持三种使用方式：

#### 方式1：仅保留Sheet页
- 只填写"保留的Sheet页名称"
- 不填写"Sheet页名称替换规则"
- 删除不在保留列表中的sheet页
- 清洗总览表

#### 方式2：仅替换Sheet页名称
- 只填写"Sheet页名称替换规则"
- 不填写"保留的Sheet页名称"
- 根据规则重命名sheet页
- 更新总览表和超链接

#### 方式3：组合使用
- 同时填写两个输入框
- 执行顺序：**先保留，再替换**
- 确保在保留的sheet基础上进行重命名

## 修改文件清单

### 1. 前端文件
- `src/main/resources/static/excel-clean.html`
  - 添加"Sheet页名称替换规则"输入框
  - 修改按钮启用逻辑（至少配置一个功能）
  - 更新使用说明
  - 添加renameRules参数传递
  - 更新结果统计显示

### 2. 后端Service
- `src/main/java/com/sunline/dict/service/ExcelCleanService.java`
  - 接口方法添加 `renameRules` 参数

- `src/main/java/com/sunline/dict/service/impl/ExcelCleanServiceImpl.java`
  - 添加 `renameRules` 参数解析
  - 新增 `renameSheets` 方法：执行sheet页重命名
  - 新增 `updateOverviewTableName` 方法：更新总览表中的表名
  - 修改 `cleanOverviewSheet` 方法：支持跳过清洗（当未配置保留规则时）
  - 添加条件判断，支持功能独立或组合执行
  - 返回结果中添加 `renamedSheetCount` 统计

### 3. Controller
- `src/main/java/com/sunline/dict/controller/ExcelCleanController.java`
  - 添加 `renameRules` 参数（可选，默认为空字符串）
  - 修改验证逻辑：至少需要配置一个功能
  - 传递 `renameRules` 参数到Service

## 核心实现逻辑

### renameSheets方法
```java
private int renameSheets(Workbook workbook, Map<String, String> renameMap) {
    // 1. 遍历所有sheet
    // 2. 根据重命名规则修改名称
    // 3. 检查新名称是否已存在
    // 4. 更新总览表B列
    // 5. 修复所有超链接
    // 6. 返回重命名数量
}
```

### updateOverviewTableName方法
```java
private void updateOverviewTableName(Workbook workbook, String oldName, String newName) {
    // 1. 获取总览表（第一个sheet）
    // 2. 遍历B列，查找旧名称
    // 3. 更新为新名称
}
```

### 执行顺序
1. **保留Sheet页**（如果配置了保留规则）
   - 删除不在列表中的sheet
   - 清洗总览表
   - 修复超链接

2. **替换Sheet页名称**（如果配置了重命名规则）
   - 重命名sheet
   - 更新总览表B列
   - 修复超链接

## 使用示例

### 示例1：仅保留
```
保留的Sheet页名称：
user_table
order_table
product_table

Sheet页名称替换规则：
（留空）
```

### 示例2：仅替换
```
保留的Sheet页名称：
（留空）

Sheet页名称替换规则：
user_info --> user_info_new
order_list --> order_list_v2
```

### 示例3：组合使用
```
保留的Sheet页名称：
user_table
order_table
product_table

Sheet页名称替换规则：
user_table --> user_info
order_table --> order_list
```

## 返回结果

```json
{
    "fileName": "xxx_清洗版_20260204_153028.xlsx",
    "originalSheetCount": 50,
    "deletedSheetCount": 40,
    "renamedSheetCount": 3,
    "remainingSheetCount": 10,
    "deletedRowCount": 40
}
```

## 注意事项

1. **第一个sheet页保护**：总览表始终会保留，不受任何规则影响
2. **忽略大小写匹配**：
   - 保留功能：sheet页名称匹配忽略大小写
   - 替换功能：旧名称匹配忽略大小写（USER_INFO、user_info、User_Info都会匹配到同一个sheet）
   - 新名称保持原样
3. **名称冲突检测**：如果新名称已存在，会跳过该重命名规则
4. **超链接自动更新**：重命名后会自动更新总览表和各sheet页之间的所有超链接
5. **执行顺序**：先执行保留，再执行重命名
6. **参数可选**：至少需要配置一个功能（保留或重命名）

## 测试建议

1. 测试仅保留功能
2. 测试仅重命名功能
3. 测试组合使用
4. 测试名称冲突情况
5. 测试超链接是否正确更新
6. 测试空规则的处理

## 完成时间
2026-02-04
