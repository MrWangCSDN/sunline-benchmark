# Webhook功能 - 快速开始

## 功能概览

本系统的 Webhook 包含两大功能：

1. **自动解析 flowtrans.xml**（master 分支）  
   监听 Git 仓库的 Push 事件，自动解析 `.flowtrans.xml` 文件并保存到数据库

2. **UAT 分支提交校验**（可选）  
   提交到 UAT 分支时，校验 commit 是否已在 SIT 分支存在。详见 [Webhook_UAT分支校验说明.md](./Webhook_UAT分支校验说明.md)

---

## 5分钟快速上手

### 步骤1：确认数据库表存在
```bash
# 执行SQL创建表（如果还没创建）
mysql -u用户名 -p数据库名 < src/main/resources/sql/create_jar_scan_tables.sql
```

### 步骤2：配置 Webhook（可选）

编辑`application.yml`：
```yaml
gitlab:
  access-token: glpat-your-token-here  # GitLab token（私有仓库必需）
  validate-uat-from-sit: true  # 可选：启用 UAT 分支校验
  bypass-keywords: 用于贯标,紧急修复  # 可选：白名单关键字

github:
  access-token: ghp_your-token-here  # GitHub token（私有仓库必需）
```

**配置说明**：
- `access-token`：私有仓库必填，公开仓库可选
- `validate-uat-from-sit`：是否启用 UAT→SIT 校验，默认 `false`。详见 [Webhook_UAT分支校验说明.md](./Webhook_UAT分支校验说明.md)
- `bypass-keywords`：白名单关键字（逗号分隔），commit message 包含时跳过 UAT 校验

### 步骤3：确认权限配置（已自动配置）

Webhook接口已自动添加到白名单，允许匿名访问：
- `LoginInterceptor.java`：排除 `/api/webhook/**`
- `WebMvcConfig.java`：排除 `/api/webhook/**`

无需手动配置。

### 步骤4：重启应用
```bash
./restart.sh
```

### 步骤5：配置Git Webhook

#### GitLab
1. 进入项目 Settings -> Webhooks
2. URL: `http://your-domain:8080/api/webhook/gitlab`
3. 勾选 Push events
4. 点击 Add webhook

#### GitHub
1. 进入仓库 Settings -> Webhooks -> Add webhook
2. Payload URL: `http://your-domain:8080/api/webhook/github`
3. Content type: application/json
4. Just the push event
5. 点击 Add webhook

### 步骤6：测试

#### 方式1：提交代码测试
```bash
# 在Git仓库中修改.flowtrans.xml文件
git add src/flows/test.flowtrans.xml
git commit -m "Update flowtrans"
git push origin master

# 查看应用日志
tail -f logs/application.log
```

#### 方式2：使用curl测试
```bash
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
    "commits": [{
      "added": [],
      "modified": ["src/flows/test.flowtrans.xml"]
    }]
  }'
```

#### 方式3：健康检查
```bash
curl http://localhost:8080/api/webhook/health

# 期望输出：
# {"code":200,"message":"操作成功","data":"Webhook服务运行正常"}
```

### 步骤7：验证数据
```sql
-- 查看flowtran表
SELECT * FROM flowtran ORDER BY update_time DESC LIMIT 10;

-- 查看flow_step表
SELECT * FROM flow_step WHERE flow_id = 'YOUR_TRANSACTION_ID' ORDER BY step;
```

## 常用命令

### 查看webhook日志
```bash
# 实时查看
tail -f logs/application.log | grep -i webhook

# 查看最近的webhook请求
grep "收到Git" logs/application.log | tail -20
```

### 测试健康状态
```bash
curl http://localhost:8080/api/webhook/health
```

### 模拟GitHub Push
```bash
curl -X POST http://localhost:8080/api/webhook/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -d @github-webhook-sample.json
```

### 模拟GitLab Push
```bash
curl -X POST http://localhost:8080/api/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Event: Push Hook" \
  -d @gitlab-webhook-sample.json
```

## 本地开发测试

### 使用ngrok暴露本地端口
```bash
# 1. 安装ngrok
brew install ngrok  # macOS
# 或访问 https://ngrok.com/download

# 2. 启动ngrok
ngrok http 8080

# 3. 复制生成的URL
# 例如：https://abc123.ngrok.io

# 4. 配置到Git平台的Webhook URL
# https://abc123.ngrok.io/api/webhook/git
```

### 使用localtunnel暴露本地端口
```bash
# 1. 安装localtunnel
npm install -g localtunnel

# 2. 启动localtunnel
lt --port 8080 --subdomain myapp

# 3. 使用生成的URL
# https://myapp.loca.lt/api/webhook/git
```

## 示例文件

### github-webhook-sample.json
```json
{
  "ref": "refs/heads/master",
  "repository": {
    "name": "my-project",
    "clone_url": "https://github.com/myuser/my-project.git"
  },
  "commits": [
    {
      "id": "abc123def456",
      "message": "Update transaction flow",
      "added": ["src/main/resources/flows/user_login.flowtrans.xml"],
      "modified": [],
      "removed": []
    }
  ]
}
```

### gitlab-webhook-sample.json
```json
{
  "ref": "refs/heads/master",
  "project": {
    "id": 12345,
    "name": "my-project",
    "web_url": "https://gitlab.com/myuser/my-project"
  },
  "commits": [
    {
      "id": "abc123def456",
      "message": "Update transaction flow",
      "added": [],
      "modified": ["src/main/resources/flows/user_login.flowtrans.xml"],
      "removed": []
    }
  ]
}
```

## 故障排查

### 问题1：Webhook未收到
```bash
# 检查应用是否运行
curl http://localhost:8080/api/webhook/health

# 检查端口是否开放
netstat -an | grep 8080

# 检查防火墙
sudo iptables -L
```

### 问题2：文件下载失败
```bash
# 检查access-token配置
grep -A 2 "gitlab:" src/main/resources/application.yml

# 手动测试文件下载
curl -H "PRIVATE-TOKEN: your-token" \
  "https://gitlab.com/api/v4/projects/123/repository/files/path%2Fto%2Ffile.xml/raw?ref=master"
```

### 问题3：XML解析失败
```bash
# 查看详细日志
grep "解析XML" logs/application.log | tail -20

# 验证XML格式
xmllint --noout path/to/file.flowtrans.xml
```

### 问题4：数据未保存
```sql
-- 检查表是否存在
SHOW TABLES LIKE 'flowtran';
SHOW TABLES LIKE 'flow_step';

-- 查看最近的数据
SELECT * FROM flowtran ORDER BY update_time DESC LIMIT 5;
```

## 最佳实践

1. **使用通用端点**：`/api/webhook/git` 可以自动识别GitHub和GitLab
2. **配置Secret**：生产环境建议配置Secret Token
3. **监控日志**：定期检查webhook处理日志
4. **数据备份**：定期备份flowtran和flow_step表
5. **测试先行**：在测试项目先配置测试，确认无误后再配置生产项目

## 下一步

- 查看 `Webhook功能使用说明.md` 了解详细功能
- 查看 `Webhook功能配置示例.md` 了解配置方法
- 查看 `Webhook功能实现清单.md` 了解技术实现

## 完成时间
2026-02-04
