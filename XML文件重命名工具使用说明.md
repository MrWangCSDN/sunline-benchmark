# XML文件重命名工具使用说明

## 功能概述

这个工具用于批量重命名和修改XML文件：
- 将 `.serviceType.xml` 结尾的文件重命名为指定类型（如 `.pbs.xml`）
- 将 `.serviceImpl.xml` 结尾的文件重命名为指定类型（如 `.pbsImpl.xml`）
- 对于 `.serviceType.xml` 文件，还会修改XML内容中的 `<serviceType>` 节点属性

## 支持的类型

共12种类型：
- `pbs` → `.pbs.xml`
- `pbsImpl` → `.pbsImpl.xml`
- `pcs` → `.pbs.xml`
- `pcsImpl` → `.pcsImpl.xml`
- `pbcb` → `.pbcb.xml`
- `pbcbImpl` → `.pbcbImpl.xml`
- `pbcc` → `.pbcc.xml`
- `pbccImpl` → `.pbccImpl.xml`
- `pbcp` → `.pbcp.xml`
- `pbcpImpl` → `.pbcpImpl.xml`
- `pbct` → `.pbct.xml`
- `pbctImpl` → `.pbctImpl.xml`

## 使用方法

### 1. 配置路径和类型

打开 `XmlFileRenamer.java` 文件，找到 `getPathTypeMapping()` 方法，在其中配置路径和类型的对应关系。

### 2. 配置方式

#### 方式1：单个路径对应一种类型

```java
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // 一个路径对应一种类型
    pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
    pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
    pathTypeMapping.put("D:\\code\\project3\\src", "pbcb");
    
    return pathTypeMapping;
}
```

#### 方式2：多个路径对应同一种类型

```java
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // 多个路径对应同一种类型
    String[] pbsPaths = {
        "D:\\code\\project1\\src",
        "D:\\code\\project2\\src",
        "D:\\code\\project3\\src"
    };
    for (String path : pbsPaths) {
        pathTypeMapping.put(path, "pbs");
    }
    
    return pathTypeMapping;
}
```

#### 方式3：混合配置（推荐）

```java
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // 单个路径配置
    pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
    pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
    
    // 多个路径对应同一种类型
    String[] pbcbPaths = {
        "D:\\code\\project3\\src",
        "D:\\code\\project4\\src",
        "D:\\code\\project5\\src"
    };
    for (String path : pbcbPaths) {
        pathTypeMapping.put(path, "pbcb");
    }
    
    // 更多配置...
    String[] pbctPaths = {
        "D:\\code\\module1\\src",
        "D:\\code\\module2\\src"
    };
    for (String path : pbctPaths) {
        pathTypeMapping.put(path, "pbct");
    }
    
    return pathTypeMapping;
}
```

### 3. 运行程序

```bash
# 在IDE中运行 main 方法
# 或者编译后运行
javac XmlFileRenamer.java
java XmlFileRenamer
```

### 4. 执行示例

```
=== XML文件重命名工具 ===
支持的类型：pbs, pbsImpl, pcs, pcsImpl, pbcb, pbcbImpl, pbcc, pbccImpl, pbcp, pbcpImpl, pbct, pbctImpl

开始批量处理...
共 5 个路径需要处理
----------------------------------------

[1/5] 处理路径: D:\code\project1\src
类型: pbs
---
✓ D:\code\project1\src\service\UserService.serviceType.xml -> UserService.pbs.xml
  已修改XML内容: UserService.serviceType.xml
完成，处理了 1 个文件

[2/5] 处理路径: D:\code\project2\src
类型: pcs
---
✓ D:\code\project2\src\service\OrderService.serviceType.xml -> OrderService.pcs.xml
  已修改XML内容: OrderService.serviceType.xml
完成，处理了 1 个文件

[3/5] 处理路径: D:\code\project3\src
类型: pbcb
---
✓ D:\code\project3\src\service\PaymentService.serviceType.xml -> PaymentService.pbcb.xml
  已修改XML内容: PaymentService.serviceType.xml
完成，处理了 1 个文件

[4/5] 处理路径: D:\code\project4\src
类型: pbcb
---
✓ D:\code\project4\src\service\AccountService.serviceType.xml -> AccountService.pbcb.xml
  已修改XML内容: AccountService.serviceType.xml
完成，处理了 1 个文件

[5/5] 处理路径: D:\code\project5\src
类型: pbcb
---
✓ D:\code\project5\src\service\BillingService.serviceType.xml -> BillingService.pbcb.xml
  已修改XML内容: BillingService.serviceType.xml
完成，处理了 1 个文件

========================================
批量处理完成！
共处理 5 个路径
共处理 5 个文件
```

## XML内容修改规则

### 对于 `.serviceType.xml` 文件

#### 1. pbs 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" ...>

<!-- 修改后 -->
<serviceType kind="pbs" category="PBS_XML" outBound="false" ...>
```
- 设置 `kind="pbs"`
- 设置 `category="PBS_XML"`（如果存在则修改，不存在则新增）
- 设置 `outBound="false"`（如果存在则修改，不存在则新增）

---

#### 2. pcs 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" ...>

<!-- 修改后 -->
<serviceType kind="pcs" category="PCS_XML" outBound="false" ...>
```
- 设置 `kind="pcs"`
- 设置 `category="PCS_XML"`（如果存在则修改，不存在则新增）
- 设置 `outBound="false"`（如果存在则修改，不存在则新增）

---

#### 3. pbcb 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" category="XXX" outBound="true" ...>

<!-- 修改后 -->
<serviceType kind="pbcb" ...>
```
- 设置 `kind="pbcb"`
- **删除** `category` 属性（如果存在）
- **删除** `outBound` 属性（如果存在）

---

#### 4. pbcp 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" category="XXX" outBound="true" ...>

<!-- 修改后 -->
<serviceType kind="pbcp" ...>
```
- 设置 `kind="pbcp"`
- **删除** `category` 属性（如果存在）
- **删除** `outBound` 属性（如果存在）

---

#### 5. pbcc 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" category="XXX" outBound="true" ...>

<!-- 修改后 -->
<serviceType kind="pbcc" ...>
```
- 设置 `kind="pbcc"`
- **删除** `category` 属性（如果存在）
- **删除** `outBound` 属性（如果存在）

---

#### 6. pbct 类型
```xml
<!-- 修改前 -->
<serviceType kind="xxx" category="XXX" outBound="true" ...>

<!-- 修改后 -->
<serviceType kind="pbct" ...>
```
- 设置 `kind="pbct"`
- **删除** `category` 属性（如果存在）
- **删除** `outBound` 属性（如果存在）

---

### 对于 `.serviceImpl.xml` 文件

**只重命名文件，不修改XML内容**

```xml
<!-- 文件内容保持不变 -->
<serviceImplementation ...>
    ...
</serviceImplementation>
```

---

## 处理规则总结

| 类型 | 文件名修改 | XML内容修改 |
|------|-----------|------------|
| pbs | `.serviceType.xml` → `.pbs.xml` | kind="pbs", category="PBS_XML", outBound="false" |
| pbsImpl | `.serviceImpl.xml` → `.pbsImpl.xml` | 不修改 |
| pcs | `.serviceType.xml` → `.pcs.xml` | kind="pcs", category="PCS_XML", outBound="false" |
| pcsImpl | `.serviceImpl.xml` → `.pcsImpl.xml` | 不修改 |
| pbcb | `.serviceType.xml` → `.pbcb.xml` | kind="pbcb", 删除category和outBound |
| pbcbImpl | `.serviceImpl.xml` → `.pbcbImpl.xml` | 不修改 |
| pbcc | `.serviceType.xml` → `.pbcc.xml` | kind="pbcc", 删除category和outBound |
| pbccImpl | `.serviceImpl.xml` → `.pbccImpl.xml` | 不修改 |
| pbcp | `.serviceType.xml` → `.pbcp.xml` | kind="pbcp", 删除category和outBound |
| pbcpImpl | `.serviceImpl.xml` → `.pbcpImpl.xml` | 不修改 |
| pbct | `.serviceType.xml` → `.pbct.xml` | kind="pbct", 删除category和outBound |
| pbctImpl | `.serviceImpl.xml` → `.pbctImpl.xml` | 不修改 |

---

## 配置说明

### 路径格式

- **Windows**：使用反斜杠，如 `D:\\code\\project1\\src`（注意双反斜杠）
- **Linux/Mac**：使用正斜杠，如 `/home/user/project1/src`
- **相对路径**：支持相对路径，如 `./src`、`../project/src`

### 类型说明

支持的类型（必须小写）：
- `pbs`, `pbsImpl`
- `pcs`, `pcsImpl`
- `pbcb`, `pbcbImpl`
- `pbcc`, `pbccImpl`
- `pbcp`, `pbcpImpl`
- `pbct`, `pbctImpl`

### 配置灵活性

✅ **支持一个路径对应一种类型**
```java
pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
```

✅ **支持多个路径对应一种类型**
```java
String[] paths = {"path1", "path2", "path3"};
for (String path : paths) {
    pathTypeMapping.put(path, "pbs");
}
```

✅ **支持混合配置**
- 可以同时配置单个路径和多个路径
- 可以配置不同的路径对应不同的类型
- 可以配置多个路径对应同一种类型

## 注意事项

### 1. 备份文件

⚠️ **重要**：执行前请先备份文件！工具会直接修改文件，无法撤销。

### 2. 每次只处理一种类型

- 每次运行只能处理一种类型
- 如果需要处理多种类型，需要多次运行程序

### 3. 递归处理

- 工具会递归处理文件夹及其所有子文件夹
- 确保有足够的权限访问所有文件

### 4. 文件锁定

- 确保文件没有被其他程序打开
- 如果文件被占用，重命名会失败

### 5. XML格式

- 工具会保持XML的格式和编码（UTF-8）
- 如果XML格式不正确，可能会处理失败

---

## 错误处理

### 常见错误

1. **文件夹不存在**
   ```
   错误：文件夹不存在或不是有效目录！
   ```
   - 检查路径是否正确
   - 确保路径是文件夹而不是文件

2. **不支持的类型**
   ```
   错误：不支持的类型！支持的类型：pbs, pbsImpl, ...
   ```
   - 检查输入的类型是否在支持列表中
   - 注意大小写（必须小写）

3. **重命名失败**
   ```
   ✗ 重命名失败: D:\path\to\file.xml
   ```
   - 检查文件是否被占用
   - 检查是否有写权限
   - 检查目标文件名是否已存在

4. **XML解析失败**
   ```
   ✗ 处理文件失败: ..., 错误: ...
   ```
   - 检查XML文件格式是否正确
   - 检查文件编码是否为UTF-8

---

## 使用示例

### 示例1：批量替换为 pbs 类型

```
输入：
  文件夹路径: D:\project\src
  类型: pbs

处理：
  UserService.serviceType.xml → UserService.pbs.xml（修改XML内容）
  OrderService.serviceImpl.xml → OrderService.pbsImpl.xml（只重命名）
```

### 示例2：批量替换为 pbcb 类型

```
输入：
  文件夹路径: D:\project\src
  类型: pbcb

处理：
  PaymentService.serviceType.xml → PaymentService.pbcb.xml
    - kind="pbcb"
    - 删除 category 属性
    - 删除 outBound 属性
```

---

## 代码位置

工具类位置：
```
src/main/java/com/sunline/dict/util/XmlFileRenamer.java
```

---

## 扩展说明

如果需要添加新的类型，可以：
1. 在 `SUPPORTED_TYPES` 数组中添加新类型
2. 在 `modifyXmlContent` 方法中添加对应的处理逻辑

---

## 总结

✅ **功能完整**：支持12种类型的文件重命名和内容修改  
✅ **递归处理**：自动处理所有子文件夹  
✅ **安全可靠**：详细的错误处理和日志输出  
✅ **易于使用**：交互式命令行界面  

**使用前请务必备份文件！** ⚠️

