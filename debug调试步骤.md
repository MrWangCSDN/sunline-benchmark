# 调试步骤 - 交易计数为0问题

## 问题描述
扫描完成后，页面显示"成功扫描 0 个交易，553 个步骤"，但数据库中实际有553条交易记录。

## 可能原因

### 1. XML文件中id属性为空
- 如果`<flowtran>`标签没有id属性，或id为空字符串
- 则整个if块不会执行，flowtranCount保持为0
- 但这种情况下步骤数也应该是0

### 2. 过滤逻辑导致
- 如果输入了交易过滤，且所有交易都不在过滤列表中
- 会直接return，计数为0
- 但这种情况下数据不会入库

### 3. 异常被捕获
- 解析过程中抛异常，被catch住
- 计数不增加，但数据可能已经部分提交
- 需要查看日志

## 调试步骤

### 步骤1：清理数据
```sql
-- 清空现有数据
TRUNCATE TABLE flow_step;
TRUNCATE TABLE flowtran;
```

### 步骤2：重启应用
确保新代码（带日志的版本）生效。

### 步骤3：重新扫描
1. 访问扫描分析页面
2. 输入jar路径：`D:\code\ccbs-online-dist.jar`
3. **不要输入交易过滤**（留空）
4. 选择jar包：`dept-pbf`
5. 点击"开始扫描"

### 步骤4：查看后台日志

查找以下关键日志：

#### 4.1 文件发现日志
```
找到flowtrans.xml文件：xxx
```
应该看到很多这样的日志，每个XML文件一条。

#### 4.2 XML解析日志
```
解析XML根节点：id=C059, longname=对对起账票法, package=xxx, txnMode=xxx
```
检查id是否为空。

#### 4.3 交易保存日志
```
成功保存交易：id=C059, longname=对对起账票法, flowtranCount=1
```
每个交易保存后都应该有这条日志。

#### 4.4 XML解析完成日志
```
parseFlowtranXml完成，返回：flowtranCount=1, flowStepCount=2
```
每个XML文件解析完都应该有这条日志。

#### 4.5 累计日志
```
解析XML文件：xxx.flowtrans.xml, 交易数：1, 步骤数：2, 累计交易：1, 累计步骤：2
```
可以看到累计的过程。

### 步骤5：分析日志

#### 情况A：看不到"解析XML根节点"日志
**原因**：XML文件没有被正确识别或解析
**解决**：检查文件名匹配逻辑

#### 情况B：看到"解析XML根节点"，但id为空
**原因**：XML文件格式问题，`<flowtran>`标签没有id属性
**解决**：检查XML文件格式
**示例**：
```xml
<!-- 错误：没有id属性 -->
<flowtran longname="测试交易">

<!-- 正确：有id属性 -->
<flowtran id="C059" longname="测试交易">
```

#### 情况C：看到"解析XML根节点"，id有值，但没有"成功保存交易"日志
**原因**：在保存之前就返回了（被过滤或抛异常）
**检查**：
1. 是否输入了交易过滤
2. 是否有异常日志

#### 情况D：看到"成功保存交易"，但flowtranCount是0
**原因**：flowtranCount++没有执行（不太可能）
**解决**：需要进一步调试

#### 情况E：看到"parseFlowtranXml完成"，但flowtranCount是0
**原因**：整个if块没有执行
**检查**：id是否真的有值

#### 情况F：看到单个XML返回flowtranCount=1，但累计却是0
**原因**：累加逻辑有问题
**检查**：scanSingleJar方法的累加代码

### 步骤6：验证数据库

```sql
-- 查看交易数量
SELECT COUNT(*) FROM flowtran;

-- 查看步骤数量
SELECT COUNT(*) FROM flow_step;

-- 查看前10个交易
SELECT id, longname, txn_mode, from_jar 
FROM flowtran 
ORDER BY create_time DESC 
LIMIT 10;

-- 检查是否有id为空的交易
SELECT COUNT(*) FROM flowtran WHERE id IS NULL OR id = '';
```

## 预期结果

正常情况下，应该看到：
```
找到flowtrans.xml文件：xxx/C059.flowtrans.xml
解析XML根节点：id=C059, longname=对对起账票法, package=xxx, txnMode=query
成功保存交易：id=C059, longname=对对起账票法, flowtranCount=1
parseFlowtranXml完成，返回：flowtranCount=1, flowStepCount=2
解析XML文件：xxx/C059.flowtrans.xml, 交易数：1, 步骤数：2, 累计交易：1, 累计步骤：2

找到flowtrans.xml文件：xxx/C060.flowtrans.xml
解析XML根节点：id=C060, longname=对公起现, package=xxx, txnMode=query
成功保存交易：id=C060, longname=对公起现, flowtranCount=1
parseFlowtranXml完成，返回：flowtranCount=1, flowStepCount=3
解析XML文件：xxx/C060.flowtrans.xml, 交易数：1, 步骤数：3, 累计交易：2, 累计步骤：5

...

最终应该是：累计交易：553, 累计步骤：553（或更多）
```

## 快速检查命令

```bash
# 查看应用日志（Linux/Mac）
tail -f /path/to/application.log | grep -E "(找到flowtrans|解析XML|成功保存交易|累计交易)"

# 查看应用日志（Windows）
# 在日志文件中搜索上述关键字
```

## 常见问题

### Q1：日志太多，看不过来
A：可以只关注累计日志：
```bash
tail -f application.log | grep "累计交易"
```

### Q2：看到很多"交易ID xxx 不在过滤列表中，跳过"
A：说明您输入了交易过滤。请清空过滤条件重新扫描。

### Q3：日志显示flowtranCount=1，但最后显示0
A：说明累加环节有问题，需要检查scanSingleJar方法。

### Q4：数据库中的交易是之前扫描的
A：请先清空数据库，然后重新扫描：
```sql
TRUNCATE TABLE flow_step;
TRUNCATE TABLE flowtran;
```

## 联系支持

如果按照以上步骤仍然无法解决，请提供：
1. 完整的扫描日志
2. 数据库查询结果
3. 扫描时的参数（jar路径、过滤条件等）

