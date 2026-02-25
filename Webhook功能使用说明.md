# Webhook功能使用说明

## 功能概述

实现了Webhook接收功能，当Git仓库（GitHub/GitLab）的master分支有新的提交时，自动解析提交中的`.flowtrans.xml`文件，并将交易信息和流程步骤保存到数据库。

## 支持的Git平台

- ✅ GitHub
- ✅ GitLab
- ✅ 自动识别（通用端点）

## Webhook端点

### 1. GitHub Webhook
```
POST http://your-domain/api/webhook/github
```

### 2. GitLab Webhook
```
POST http://your-domain/api/webhook/gitlab
```

### 3. 通用Webhook（自动识别）
```
POST http://your-domain/api/webhook/git
```

### 4. 健康检查
```
GET http://your-domain/api/webhook/health
```

## 触发条件

1. ✅ **分支**：只处理`master`分支的push事件
2. ✅ **文件**：只处理`.flowtrans.xml`结尾的文件
3. ✅ **操作**：处理新增（added）和修改（modified）的文件

## 处理流程

```
Git Push (master) 
    ↓
Webhook接收
    ↓
过滤.flowtrans.xml文件
    ↓
下载文件内容
    ↓
解析XML
    ↓
保存到数据库
    ↓
返回处理结果
```

## 数据库表

### flowtran表（交易信息）
| 字段 | 说明 |
|------|------|
| id | 交易ID（从XML的id属性） |
| longname | 交易名称 |
| package_path | 包路径 |
| txn_mode | 事务模式 |
| from_jar | 来源信息（格式：项目名:分支:文件路径） |

### flow_step表（流程步骤）
| 字段 | 说明 |
|------|------|
| id | 主键（自增） |
| flow_id | 流程ID（关联flowtran.id） |
| node_name | 节点名称（service的serviceName或method的method属性） |
| node_type | 节点类型（service/method） |
| step | 步骤顺序 |
| node_longname | 节点长名称 |

## XML解析规则

### flowtran节点
```xml
<flowtran id="TXN001" longname="用户登录交易" package="com.example" txnMode="REQUIRED">
    <flow>
        ...
    </flow>
</flowtran>
```

提取信息：
- `id`属性 → flowtran.id
- `longname`属性 → flowtran.longname
- `package`属性 → flowtran.package_path
- `txnMode`属性 → flowtran.txn_mode

### flow节点
递归扫描所有`<service>`和`<method>`节点（包括嵌套在`<case>`、`<when>`等标签内的）：

```xml
<flow>
    <service serviceName="UserService" longname="用户服务"/>
    <case>
        <when>
            <method method="validateUser" longname="验证用户"/>
        </when>
    </case>
</flow>
```

提取信息：
- service节点：`serviceName`属性 → flow_step.node_name
- method节点：`method`属性 → flow_step.node_name
- `longname`属性 → flow_step.node_longname
- 节点类型 → flow_step.node_type
- 出现顺序 → flow_step.step

## 配置说明

### application.yml
```yaml
# GitLab配置（可选）
gitlab:
  access-token: your-gitlab-access-token

# GitHub配置（可选）
github:
  access-token: your-github-access-token
```

**说明**：
- 如果Git仓库是公开的，可以不配置access-token
- 如果是私有仓库，需要配置相应的access-token才能下载文件

## GitHub Webhook配置

1. 进入GitHub仓库设置
2. 选择 Settings -> Webhooks -> Add webhook
3. 配置：
   - **Payload URL**: `http://your-domain/api/webhook/github`
   - **Content type**: `application/json`
   - **Secret**: （可选）
   - **Which events**: 选择 `Just the push event`
   - **Active**: 勾选

## GitLab Webhook配置

1. 进入GitLab项目设置
2. 选择 Settings -> Webhooks
3. 配置：
   - **URL**: `http://your-domain/api/webhook/gitlab`
   - **Secret Token**: （可选）
   - **Trigger**: 只勾选 `Push events`
   - **SSL verification**: 根据实际情况选择
   - **Enable webhook**: 勾选

## 数据更新策略

- **交易信息**：每次都会删除旧记录，插入新记录（覆盖更新）
- **流程步骤**：先删除该交易的所有步骤，再插入新步骤（完全替换）

## 日志示例

```
收到GitLab Webhook，事件类型: Push Hook
master分支push事件，开始处理
发现修改文件: src/main/resources/flows/user_login.flowtrans.xml
找到 1 个.flowtrans.xml文件
下载文件: https://gitlab.com/api/v4/projects/123/repository/files/...
解析XML根节点：id=TXN001, longname=用户登录交易, package=com.example, txnMode=REQUIRED
成功保存交易：id=TXN001, longname=用户登录交易
交易ID: TXN001, 找到 5 个流程步骤节点（包括嵌套的）
解析完成，交易数：1, 步骤数：5
GitLab Webhook处理完成，交易数：1, 步骤数：5
```

## 测试Webhook

### 使用curl测试
```bash
curl -X POST http://localhost:8080/api/webhook/git \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Event: Push Hook" \
  -d '{
    "ref": "refs/heads/master",
    "commits": [{
      "added": ["src/flows/test.flowtrans.xml"],
      "modified": []
    }],
    "project": {
      "id": 123,
      "name": "test-project",
      "web_url": "https://gitlab.com/user/test-project"
    }
  }'
```

### 健康检查
```bash
curl http://localhost:8080/api/webhook/health
```

## 返回结果

### 成功响应
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "success": true,
        "message": "处理成功",
        "flowtranCount": 1,
        "flowStepCount": 5
    }
}
```

### 忽略响应（非master分支或无.flowtrans.xml文件）
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "success": false,
        "message": "非master分支push事件",
        "flowtranCount": 0,
        "flowStepCount": 0
    }
}
```

## 注意事项

1. **安全性**：建议在Webhook配置中使用Secret Token进行验证
2. **网络访问**：应用服务器需要能够访问Git仓库
3. **权限**：如果是私有仓库，需要配置access-token
4. **分支限制**：目前只处理master分支，其他分支自动忽略
5. **文件过滤**：只处理`.flowtrans.xml`结尾的文件
6. **数据覆盖**：相同交易ID的数据会被覆盖

## 文件结构

### 新增文件
- `src/main/java/com/sunline/dict/controller/WebhookController.java`
- `src/main/java/com/sunline/dict/service/WebhookService.java`
- `src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java`
- `src/main/java/com/sunline/dict/service/FlowXmlParseService.java`
- `src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java`
- `Webhook功能使用说明.md`（本文档）

## 扩展建议

### 1. 添加Secret验证
```java
@PostMapping("/gitlab")
public Result<Map<String, Object>> handleGitLabWebhook(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Gitlab-Token", required = false) String token) {
    
    // 验证token
    if (!isValidToken(token)) {
        return Result.error("Token验证失败");
    }
    // ...
}
```

### 2. 支持更多分支
修改`WebhookServiceImpl`中的分支判断逻辑

### 3. 异步处理
使用`@Async`注解实现异步处理，避免阻塞Webhook响应

### 4. 添加重试机制
对文件下载失败的情况添加重试逻辑

## 完成时间
2026-02-04
