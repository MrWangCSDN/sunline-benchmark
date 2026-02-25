# Case/When结构扫描测试用例

## 测试目的

验证递归扫描能否正确处理各种case/when嵌套结构。

## 测试场景

### 场景1：基本case/when结构（单个when）

```xml
<flow>
    <method method="init"/>
    <case>
        <when test="condition1">
            <service serviceName="Service1"/>
        </when>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - Service1
- 步骤3: method - finish
- **总计：3个步骤**

---

### 场景2：多个when分支（您的情况）

```xml
<flow>
    <method method="init"/>
    <case id="prcCzz" test="cptl_drcn==#dict...">
        <when id="when1" test="test1">
            <service serviceName="Service1"/>
        </when>
        <when id="when2" test="test2">
            <service serviceName="Service2"/>
        </when>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - Service1 ✅
- 步骤3: service - Service2 ✅
- 步骤4: method - finish
- **总计：4个步骤**

**日志输出示例**：
```
交易ID: cdcb1060, 找到 4 个流程步骤节点（包括嵌套的）
    发现method节点: init
    进入case节点 (id=prcCzz, test=cptl_drcn==#dict...)
    进入when节点 (id=when1, test=test1)
    发现service节点: Service1
    进入when节点 (id=when2, test=test2)
    发现service节点: Service2
    发现method节点: finish
```

---

### 场景3：case/when/otherwise结构

```xml
<flow>
    <method method="init"/>
    <case>
        <when test="condition1">
            <service serviceName="Service1"/>
        </when>
        <when test="condition2">
            <service serviceName="Service2"/>
        </when>
        <otherwise>
            <service serviceName="ServiceDefault"/>
        </otherwise>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - Service1
- 步骤3: service - Service2
- 步骤4: service - ServiceDefault
- 步骤5: method - finish
- **总计：5个步骤**

---

### 场景4：嵌套的case/when

```xml
<flow>
    <method method="init"/>
    <case>
        <when test="typeA">
            <case>
                <when test="subCondition1">
                    <service serviceName="ServiceA1"/>
                </when>
                <when test="subCondition2">
                    <service serviceName="ServiceA2"/>
                </when>
            </case>
        </when>
        <when test="typeB">
            <service serviceName="ServiceB"/>
        </when>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - ServiceA1
- 步骤3: service - ServiceA2
- 步骤4: service - ServiceB
- 步骤5: method - finish
- **总计：5个步骤**

---

### 场景5：when中包含多个service/method

```xml
<flow>
    <method method="init"/>
    <case>
        <when test="condition1">
            <service serviceName="Service1"/>
            <method method="process1"/>
            <service serviceName="Service2"/>
        </when>
        <when test="condition2">
            <method method="process2"/>
            <service serviceName="Service3"/>
        </when>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - Service1
- 步骤3: method - process1
- 步骤4: service - Service2
- 步骤5: method - process2
- 步骤6: service - Service3
- 步骤7: method - finish
- **总计：7个步骤**

---

### 场景6：空when分支

```xml
<flow>
    <method method="init"/>
    <case>
        <when test="condition1">
            <!-- 空的，没有service/method -->
        </when>
        <when test="condition2">
            <service serviceName="Service2"/>
        </when>
    </case>
    <method method="finish"/>
</flow>
```

**预期结果**：
- 步骤1: method - init
- 步骤2: service - Service2 （空when不影响）
- 步骤3: method - finish
- **总计：3个步骤**

---

## 递归扫描算法说明

### 算法伪代码

```
function collectNodes(parentElement, resultList):
    for each child in parentElement.children:
        if child.tagName == "service" or child.tagName == "method":
            resultList.add(child)  // 找到目标节点，添加到结果
        
        // 无论是什么标签，都继续递归扫描其子节点
        collectNodes(child, resultList)
```

### 关键特性

1. **深度优先遍历（DFS）**
   - 先访问第一个子节点
   - 递归访问其所有后代
   - 然后才访问下一个兄弟节点

2. **访问顺序**
   - 按照XML文档顺序（document order）
   - 保证步骤序号正确

3. **无条件递归**
   - 对所有元素节点都递归（不管是case、when、if还是其他）
   - 只要是service或method就收集

4. **不重复收集**
   - 每个节点只访问一次
   - 不会重复添加

---

## 实际验证方法

### 方法1：查看日志

重启应用后扫描，查看日志输出：

```bash
# 查找特定交易的日志
grep "交易ID: cdcb1060" application.log -A 20
```

应该看到类似：
```
交易ID: cdcb1060, 找到 7 个流程步骤节点（包括嵌套的）
    进入case节点 (id=prcCzz, ...)
    进入when节点 (id=when1, ...)
    发现service节点: DpCbPrcAcctDeptSvcApsSvtp.DpIoDpLDttyAcctDeptConfirmAps
    进入when节点 (id=when2, ...)
    发现service节点: IoTaDpenWaInWrItePfrSerNumAcctqApI
  步骤 1: method - ...
  步骤 2: service - DpCbPrcAcctDeptSvcApsSvtp.DpIoDpLDttyAcctDeptConfirmAps
  步骤 3: service - IoTaDpenWaInWrItePfrSerNumAcctqApI
  ...
```

### 方法2：查询数据库

```sql
-- 查看特定交易的所有步骤
SELECT step, node_type, node_name, node_longname 
FROM flow_step 
WHERE flow_id = 'cdcb1060' 
ORDER BY step;

-- 统计每个交易的步骤数
SELECT flow_id, COUNT(*) as step_count 
FROM flow_step 
GROUP BY flow_id 
ORDER BY step_count DESC;
```

### 方法3：对比XML和数据库

1. 手动统计XML中有多少个service和method
2. 查询数据库中该交易的步骤数
3. 两者应该一致

---

## 可能的问题和解决方案

### 问题1：步骤数少于预期

**可能原因**：
- XML格式问题（标签未闭合等）
- 属性名称错误（serviceName写成了service-name）
- 节点嵌套在注释中

**解决方法**：
- 检查XML格式
- 查看解析错误日志
- 验证XML是否符合规范

### 问题2：步骤顺序不对

**可能原因**：
- XML中节点顺序本身就不是预期的
- 多个相同step值（数据库问题）

**解决方法**：
- 检查XML源文件
- 验证step字段是否唯一递增

### 问题3：重复的步骤

**可能原因**：
- 多次扫描同一个文件
- 未清空旧数据

**解决方法**：
```sql
-- 检查是否有重复
SELECT flow_id, node_name, COUNT(*) as cnt 
FROM flow_step 
GROUP BY flow_id, node_name 
HAVING cnt > 1;

-- 清空重新扫描
TRUNCATE TABLE flow_step;
TRUNCATE TABLE flowtran;
```

---

## 总结

✅ **您的情况（两个when，各含一个service）是完全支持的**

递归算法会：
1. 扫描case节点
2. 进入第一个when
3. 找到第一个service，添加到结果
4. 回到case，继续扫描
5. 进入第二个when
6. 找到第二个service，添加到结果

**最终结果：两个service都会被正确扫描到！** ✅

---

## 测试建议

建议您：
1. **重启应用**（加载新的日志代码）
2. **重新扫描**一个包含case/when结构的交易
3. **查看日志**，确认所有service都被找到
4. **查询数据库**，验证步骤数正确

如果发现问题，请提供：
- 具体的交易ID
- XML文件片段
- 日志输出
- 数据库查询结果

我会帮您分析！

