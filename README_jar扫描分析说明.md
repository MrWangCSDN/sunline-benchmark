# Jar包扫描分析功能说明

## 功能概述

本功能用于扫描Fat Jar包中的`flowtrans.xml`交易定义文件，自动提取交易信息和流程步骤信息并存储到数据库中。

## 使用步骤

### 1. 数据库初始化

执行以下SQL脚本创建相关表：

```bash
# 创建交易信息表和流程步骤表
mysql -u<用户名> -p<密码> <数据库名> < src/main/resources/sql/create_jar_scan_tables.sql

# 添加菜单权限
mysql -u<用户名> -p<密码> <数据库名> < src/main/resources/sql/add_jar_scan_menu.sql
```

### 2. 访问功能

1. 启动应用
2. 登录系统
3. 在左侧菜单中找到"Git代码管理" -> "扫描分析"
4. 进入扫描分析页面

### 3. 配置扫描

1. **输入Jar包路径**：支持两种输入方式
   - **方式1（推荐）**：直接输入Fat Jar文件完整路径
     - Windows示例：`D:\code\ccbs-online-dist.jar`
     - Linux/Mac示例：`/path/to/ccbs-online-dist.jar`
     - 系统会自动读取jar文件中的`BOOT-INF/lib/`或`WEB-INF/lib/`目录
   
   - **方式2**：输入已解压的lib目录路径
     - 例如：`/path/to/fatjar/lib`

2. **交易ID过滤（可选）**：输入要扫描的交易ID
   - 多个交易ID用逗号分隔，例如：`ba3401,ba3402,dpcb2294`
   - 留空表示扫描所有交易，不做过滤
   - 交易ID对应flowtrans.xml文件中`<flowtran>`标签的`id`属性

3. **选择要扫描的Jar包**：勾选需要扫描的jar包
   - ap-tran
   - dept-pbf
   - loan-pbf
   - sett-pbf
   - comm-pbf
   - 支持全选/取消全选

4. **开始扫描**：点击"开始扫描"按钮

### 4. 查看进度

- 实时显示扫描进度条
- 显示当前扫描进度（已完成/总数）
- 显示每个jar包的扫描结果
  - 成功：显示绿色边框，统计交易数和步骤数
  - 失败：显示红色边框，显示错误信息

### 5. 取消扫描

在扫描过程中，可以点击"取消扫描"按钮终止扫描任务。

## 技术实现

### 数据表结构

#### flowtran表（交易信息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(100) | 交易ID（主键） |
| longname | VARCHAR(500) | 交易名称 |
| package_path | VARCHAR(500) | 包路径 |
| txn_mode | VARCHAR(50) | 事务模式 |
| from_jar | VARCHAR(100) | 来源jar包 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### flow_step表（流程步骤表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID（自增） |
| flow_id | VARCHAR(100) | 流程ID（关联flowtran.id） |
| node_name | VARCHAR(500) | 节点名称 |
| node_type | VARCHAR(50) | 节点类型（service/method） |
| step | INT | 步骤顺序 |
| node_longname | VARCHAR(500) | 节点长名称 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 扫描规则

1. **Jar包匹配**：忽略版本号，只匹配jar包名称前缀
   - 例如：选择`ap-tran`，会匹配`ap-tran-1.0.0.jar`、`ap-tran-2.0.0-SNAPSHOT.jar`等

2. **XML文件识别**：扫描jar包中所有以`.flowtrans.xml`或`flowtrans.xml`结尾的文件

3. **交易信息提取**：
   - id：来自`<flowtran>`标签的`id`属性
   - longname：来自`<flowtran>`标签的`longname`属性
   - package_path：来自`<flowtran>`标签的`package`属性
   - txn_mode：来自`<flowtran>`标签的`txnMode`属性（如果不存在该属性，则不落库）
   - from_jar：来源jar包名称

4. **流程步骤提取**：
   - 从`<flow>`节点下**递归**提取所有`<service>`和`<method>`节点（包括嵌套在`<case>`、`<when>`等标签内的）
   - node_name：service节点的`serviceName`属性或method节点的`method`属性
   - node_type：`service`或`method`
   - step：节点在flow中的顺序（按照在XML中出现的顺序）
   - node_longname：节点的`longname`属性
   - **支持深度嵌套**：会扫描整个flow子树，无论节点嵌套多深

5. **交易过滤**：
   - 支持按交易ID过滤扫描范围
   - 如果指定了交易ID列表，只扫描列表中的交易
   - 未指定则扫描所有交易

### 多线程扫描

- 每个jar包使用独立的线程进行扫描
- 线程池大小：5个线程
- 支持并发扫描，提高扫描效率

### 数据更新策略

- 如果交易ID已存在，先删除旧记录，再插入新记录
- 更新交易时，同时删除该交易的所有步骤，再重新插入

## 注意事项

1. **路径格式**：
   - 支持直接输入Fat Jar文件路径（推荐）
   - 也支持输入已解压的lib目录路径
   - Windows路径使用反斜杠`\`，如：`D:\code\app.jar`
   - Linux/Mac路径使用正斜杠`/`，如：`/home/user/app.jar`

2. **权限问题**：确保应用有读取jar包文件的权限

3. **jar包版本**：系统会自动忽略版本号，匹配jar包名称前缀

4. **数据覆盖**：重复扫描会覆盖已有数据

5. **性能考虑**：大量jar包扫描时可能需要较长时间，请耐心等待

6. **临时文件**：从Fat Jar中提取jar包时会创建临时文件，扫描完成后会自动清理

## 常见问题

### 1. 扫描失败：未找到jar包

**原因**：jar包路径不正确或jar包名称不匹配

**解决方案**：
- 检查输入的路径是否正确
- 确认lib目录下存在所选的jar包
- 查看jar包文件名是否以所选名称开头

### 2. 解析XML失败

**原因**：flowtrans.xml文件格式不正确

**解决方案**：
- 检查XML文件是否符合flowtrans规范
- 查看应用日志获取详细错误信息

### 3. 数据库连接失败

**原因**：数据库配置不正确或数据库服务未启动

**解决方案**：
- 检查`application.yml`中的数据库配置
- 确认数据库服务正在运行
- 检查数据库用户权限

## 联系支持

如有问题，请查看应用日志或联系技术支持。

