# Webhook功能配置示例

## application.yml配置

```yaml
# GitLab配置
gitlab:
  access-token: glpat-xxxxxxxxxxxxxxxxxxxx

# GitHub配置  
github:
  access-token: ghp_xxxxxxxxxxxxxxxxxxxx
```

## GitHub Webhook配置步骤

### 1. 进入仓库设置
- 打开GitHub仓库
- 点击 `Settings` 标签
- 左侧菜单选择 `Webhooks`
- 点击 `Add webhook` 按钮

### 2. 配置Webhook
```
Payload URL: http://your-domain:8080/api/webhook/github
Content type: application/json
Secret: (可选，建议配置)
Which events would you like to trigger this webhook?
  ☑️ Just the push event
Active: ☑️
```

### 3. 测试
- 保存后，GitHub会发送一个test事件
- 查看应用日志，确认收到webhook
- 提交包含`.flowtrans.xml`文件的代码到master分支
- 查看数据库，确认数据已保存

## GitLab Webhook配置步骤

### 1. 进入项目设置
- 打开GitLab项目
- 左侧菜单选择 `Settings` -> `Webhooks`

### 2. 配置Webhook
```
URL: http://your-domain:8080/api/webhook/gitlab
Secret Token: (可选，建议配置)
Trigger:
  ☑️ Push events
    Branch filter: (留空表示所有分支，系统会自动过滤master)
SSL verification:
  ☑️ Enable SSL verification (生产环境)
  ☐ Enable SSL verification (测试环境无SSL证书时)
```

### 3. 测试
- 点击 `Add webhook` 保存
- 点击 `Test` -> `Push events` 发送测试事件
- 查看应用日志和数据库

## 本地测试

### 使用ngrok暴露本地端口
```bash
# 安装ngrok
brew install ngrok

# 暴露本地8080端口
ngrok http 8080

# 复制生成的https URL配置到GitHub/GitLab
# 例如：https://abc123.ngrok.io/api/webhook/git
```

### 使用curl模拟webhook
```bash
# 模拟GitLab Push事件
curl -X POST http://localhost:8080/api/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Event: Push Hook" \
  -d '{
    "ref": "refs/heads/master",
    "project": {
      "id": 123,
      "name": "test-project",
      "web_url": "https://gitlab.com/user/test-project"
    },
    "commits": [
      {
        "id": "abc123",
        "message": "Add flowtrans.xml",
        "added": ["src/main/resources/flows/user_login.flowtrans.xml"],
        "modified": []
      }
    ]
  }'
```

### 健康检查
```bash
curl http://localhost:8080/api/webhook/health
```

## 多项目配置

如果有多个项目需要配置webhook：

1. 在每个项目中配置相同的Webhook URL
2. 系统会自动识别项目并处理
3. `from_jar`字段会记录来源：`项目名:分支:文件路径`

### 示例：
```
项目A: api-service
  - 配置Webhook: http://your-domain/api/webhook/gitlab
  - 提交文件: src/flows/login.flowtrans.xml
  - 数据库记录: from_jar = "api-service:master:src/flows/login.flowtrans.xml"

项目B: core-service
  - 配置Webhook: http://your-domain/api/webhook/gitlab
  - 提交文件: flows/payment.flowtrans.xml
  - 数据库记录: from_jar = "core-service:master:flows/payment.flowtrans.xml"
```

## 常见问题

### 1. Webhook未触发
- 检查Webhook配置是否正确
- 查看Git平台的Webhook日志
- 确认应用服务器可被外网访问

### 2. 文件下载失败
- 检查是否配置了access-token（私有仓库必需）
- 确认token权限是否足够
- 检查网络连接

### 3. XML解析失败
- 确认XML文件格式正确
- 查看应用日志，定位具体错误
- 确认`<flowtran>`标签包含必需的属性

### 4. 数据未保存
- 检查数据库连接
- 查看是否有SQL错误
- 确认flowtran和flow_step表已创建

## 安全建议

1. **配置Secret Token**：在Webhook配置中添加Secret
2. **验证Token**：在Controller中验证Secret Token
3. **IP白名单**：限制只接收来自Git平台的IP
4. **HTTPS**：生产环境使用HTTPS
5. **日志监控**：监控异常的webhook请求

## 日志级别配置

```yaml
logging:
  level:
    com.sunline.dict.service.impl.WebhookServiceImpl: INFO
    com.sunline.dict.service.impl.FlowXmlParseServiceImpl: INFO
    com.sunline.dict.controller.WebhookController: INFO
```

## 监控建议

1. 监控webhook接收数量
2. 监控XML解析成功率
3. 监控数据库写入失败情况
4. 设置告警规则

## 完成时间
2026-02-04
