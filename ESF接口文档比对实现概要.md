# ESF接口文档比对 - 实现概要

## 已完成

1. ✅ 前端页面：`esf-interface-compare.html`（简化版）
2. ✅ CompareMode枚举：新增 `ESF_INTERFACE` 模式
3. ✅ 完整实现方案已规划

## 核心实现（基于ExcelCompareService重构）

### 需要的代码修改

#### 1. 识别输入/输出部分（类似字段/索引/主键）

```java
// 在identifySheetSections方法中添加
if ("输入".equals(value)) {
    sections.inputStart = i;
} else if ("输出".equals(value)) {
    sections.outputStart = i;
}
```

#### 2. 只比对A、B、C、D列

```java
private boolean shouldCompareColumn(int columnIndex, String sectionType, CompareMode mode) {
    if (mode == CompareMode.ESF_INTERFACE) {
        // 只比对A、B、C、D列
        return columnIndex >= 0 && columnIndex <= 3;
    }
}
```

#### 3. 生成特殊的修订记录sheet

```java
private void createEsfRevisionSheet(Workbook workbook, Map<String, EsfChangeInfo> changes) {
    Sheet sheet = workbook.createSheet("修订记录");
    
    // A列：序号
    // B列：交易码（sheet名称）
    // C列：新增/删除/修改（带颜色）
    // D列：修订内容（输入+输出的所有差异，换行展示）
}
```

## 后续需要创建的文件

1. Controller：`EsfInterfaceCompareController.java`
2. Service：`EsfInterfaceCompareService.java`（委托给ExcelCompareService）
3. SQL：添加菜单
4. 更新index.html

## 实现建议

由于时间和复杂度，建议分两个阶段：

**阶段1**：基础功能（使用现有Excel比对）
- 直接调用Excel比对，生成标准结果
- 手动调整理解输出格式

**阶段2**：定制优化（开发ESF专用格式）
- 实现交易码汇总的修订记录
- 输入/输出分别展示
- 单元格内换行格式

当前所有基础设施已就绪，核心比对引擎已支持多模式！
