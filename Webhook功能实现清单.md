# Webhook功能实现清单

## 功能概述

实现了Git Webhook接收功能，自动处理master分支提交的`.flowtrans.xml`文件，解析并保存到数据库。

## 新增文件

### 1. Controller层
- **文件**：`src/main/java/com/sunline/dict/controller/WebhookController.java`
- **功能**：
  - `POST /api/webhook/github`：接收GitHub webhook
  - `POST /api/webhook/gitlab`：接收GitLab webhook
  - `POST /api/webhook/git`：通用webhook端点（自动识别）
  - `GET /api/webhook/health`：健康检查

### 2. Service层

#### WebhookService
- **接口**：`src/main/java/com/sunline/dict/service/WebhookService.java`
- **实现**：`src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java`
- **功能**：
  - 处理Git push事件
  - 过滤master分支
  - 收集.flowtrans.xml文件
  - 下载文件内容
  - 调用XML解析服务

#### FlowXmlParseService
- **接口**：`src/main/java/com/sunline/dict/service/FlowXmlParseService.java`
- **实现**：`src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java`
- **功能**：
  - 解析flowtrans.xml文件
  - 提取交易信息（id、longname、package、txnMode）
  - 递归提取流程步骤（service、method节点）
  - 保存到flowtran和flow_step表

### 3. 文档
- `Webhook功能使用说明.md`：完整的使用说明
- `Webhook功能配置示例.md`：配置步骤和测试方法
- `Webhook功能实现清单.md`：实现清单（本文档）

## 核心技术实现

### 1. Webhook接收
```java
@PostMapping("/gitlab")
public Result<Map<String, Object>> handleGitLabWebhook(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Gitlab-Event") String event) {
    // 处理webhook
}
```

### 2. 分支过滤
```java
String ref = (String) payload.get("ref");
if (ref == null || !ref.equals("refs/heads/master")) {
    return createResult(false, "非master分支push事件", 0, 0);
}
```

### 3. 文件过滤
```java
private void collectFlowtransFiles(Map<String, Object> commit, Set<String> flowtransFiles) {
    List<String> added = (List<String>) commit.get("added");
    List<String> modified = (List<String>) commit.get("modified");
    
    // 只收集.flowtrans.xml结尾的文件
    for (String file : added) {
        if (file.endsWith(".flowtrans.xml")) {
            flowtransFiles.add(file);
        }
    }
}
```

### 4. 文件下载
```java
// GitHub: 使用raw.githubusercontent.com
String rawUrl = repoUrl.replace("github.com", "raw.githubusercontent.com") 
    + "/" + branch + "/" + filePath;

// GitLab: 使用API
String apiUrl = gitlabDomain + "/api/v4/projects/" + projectId 
    + "/repository/files/" + encodedFilePath + "/raw?ref=" + branch;
```

### 5. XML解析
```java
Document doc = builder.parse(xmlContent);
Element root = doc.getDocumentElement();

// 提取flowtran属性
String id = root.getAttribute("id");
String longname = root.getAttribute("longname");

// 递归提取flow步骤
collectServiceAndMethodNodes(flowElement, stepElements);
```

### 6. 数据保存
```java
// 先删除旧数据
flowtranMapper.deleteById(id);
flowStepMapper.delete(new QueryWrapper<FlowStep>().eq("flow_id", id));

// 插入新数据
flowtranMapper.insert(flowtran);
flowStepMapper.insert(flowStep);
```

## 数据库表

### flowtran表
```sql
CREATE TABLE IF NOT EXISTS flowtran (
    id VARCHAR(100) PRIMARY KEY COMMENT '交易ID',
    longname VARCHAR(500) COMMENT '交易名称',
    package_path VARCHAR(500) COMMENT '包路径',
    txn_mode VARCHAR(50) COMMENT '事务模式',
    from_jar VARCHAR(100) COMMENT '来源jar包',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### flow_step表
```sql
CREATE TABLE IF NOT EXISTS flow_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_id VARCHAR(100) NOT NULL,
    node_name VARCHAR(500),
    node_type VARCHAR(50),
    step INT,
    node_longname VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_node (flow_id, node_name),
    INDEX idx_flow_id (flow_id)
);
```

## 配置参数说明

### gitlab.access-token
- **必需性**：私有GitLab仓库必需，公开仓库可选
- **获取方式**：GitLab -> Settings -> Access Tokens
- **权限**：需要`read_repository`权限
- **格式**：`glpat-xxxxxxxxxxxxxxxxxxxx`

### github.access-token
- **必需性**：私有GitHub仓库必需，公开仓库可选
- **获取方式**：GitHub -> Settings -> Developer settings -> Personal access tokens
- **权限**：需要`repo`权限
- **格式**：`ghp_xxxxxxxxxxxxxxxxxxxx`

## 支持的Webhook负载格式

### GitHub Push Event
```json
{
  "ref": "refs/heads/master",
  "repository": {
    "name": "project-name",
    "clone_url": "https://github.com/user/project-name.git"
  },
  "commits": [
    {
      "added": ["path/to/file.flowtrans.xml"],
      "modified": ["path/to/other.flowtrans.xml"],
      "removed": []
    }
  ]
}
```

### GitLab Push Event
```json
{
  "ref": "refs/heads/master",
  "project": {
    "id": 123,
    "name": "project-name",
    "web_url": "https://gitlab.com/user/project-name"
  },
  "commits": [
    {
      "added": ["path/to/file.flowtrans.xml"],
      "modified": ["path/to/other.flowtrans.xml"],
      "removed": []
    }
  ]
}
```

## 处理逻辑流程图

```
收到Webhook请求
    ↓
识别事件类型（GitHub/GitLab）
    ↓
验证是否为master分支push事件？
    ├─ 否 → 忽略，返回成功
    └─ 是 → 继续
    ↓
从commits中提取文件列表
    ↓
过滤.flowtrans.xml文件
    ↓
是否有.flowtrans.xml文件？
    ├─ 否 → 忽略，返回成功
    └─ 是 → 继续
    ↓
遍历每个.flowtrans.xml文件：
    ├─ 下载文件内容
    ├─ 解析XML
    ├─ 提取flowtran信息
    ├─ 递归提取flow_step信息
    ├─ 删除旧数据
    └─ 插入新数据
    ↓
返回处理结果（交易数、步骤数）
```

## 错误处理

1. **网络错误**：捕获并记录日志，继续处理其他文件
2. **XML解析错误**：记录日志，跳过该文件
3. **数据库错误**：记录日志，抛出异常
4. **webhook格式错误**：返回错误信息

## 性能考虑

1. **单文件处理**：每个文件独立处理，失败不影响其他文件
2. **覆盖更新**：相同交易ID直接覆盖，避免重复数据
3. **批量删除**：先删除所有步骤，再批量插入

## 扩展功能建议

### 1. 异步处理
```java
@Async
public CompletableFuture<Map<String, Object>> handlePushEvent(Map<String, Object> payload) {
    // 异步处理，不阻塞webhook响应
}
```

### 2. 消息队列
使用RabbitMQ或Kafka，将webhook事件放入队列，异步处理

### 3. 多分支支持
支持dev、test等分支，添加分支标识到from_jar字段

### 4. 增量更新
只更新变化的字段，而不是完全替换

### 5. 操作记录
创建webhook_log表，记录每次webhook的处理情况

## 依赖说明

使用Spring Boot自带的依赖，无需添加额外依赖：
- Spring Web：接收HTTP请求
- XML解析：使用JDK自带的`javax.xml`
- HTTP客户端：使用JDK自带的`HttpURLConnection`
- MyBatis Plus：数据库操作

## 完成时间
2026-02-04
