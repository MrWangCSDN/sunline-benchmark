# Webhook 功能完整实现总结

## 已实现功能列表

### 1. 自动解析 flowtrans.xml（基础功能）✅

**功能**：监听 Git 仓库（GitLab/GitHub）master 分支的 Push 事件，自动下载、解析 `.flowtrans.xml` 文件并保存到数据库。

**实现要点**：
- 支持 GitLab 和 GitHub 两种平台
- 只处理 master 分支的提交
- 收集 commits 中的 added 和 modified 文件
- 通过 GitLab API 或 GitHub raw URL 下载文件
- 解析 XML 并保存到 `flowtran` 和 `flow_step` 表
- 采用"先删后插"策略，避免重复数据

**相关文件**：
- `WebhookServiceImpl.java`
- `FlowXmlParseServiceImpl.java`
- `WebhookController.java`

---

### 2. 文件删除同步删库 ✅

**功能**：当开发者在 Git 仓库中删除 `.flowtrans.xml` 文件时，Webhook 自动删除数据库中对应的 `flowtran` 和 `flow_step` 记录。

**实现要点**：
- 收集 commits 中的 **removed** 文件
- 根据 `from_jar` 字段（存储的是 sourceInfo，如 `projectName:master:filePath`）查询对应的 flowtran
- 先删除关联的 `flow_step`（按 `flow_id`），再删除 `flowtran`
- 支持 GitLab 和 GitHub

**关键代码**：
```java
private void collectFlowtransFiles(Map<String, Object> commit, 
                                   Set<String> flowtransFiles, 
                                   Set<String> removedFlowtransFiles)

private int[] deleteFlowtranBySourceInfo(String sourceInfo)
```

**实现时间**：2026-02-09

---

### 3. UAT 分支提交校验 ✅

**功能**：当开发者提交代码到 **UAT 分支**时，自动校验该 commit 是否已在 **SIT 分支**上存在。支持白名单关键字（如"用于贯标"）跳过校验。

**实现要点**：
- 检测 push 的分支是否为 `refs/heads/uat`
- 对每个 commit：
  - 检查 message 是否包含白名单关键字（配置的 `bypass-keywords`）
  - 如果包含，跳过校验
  - 否则，调用 GitLab API 检查该 commit 是否在 sit 分支
- 调用 GitLab API：`GET /api/v4/projects/:id/repository/commits/:sha/refs?type=branch`
- 如果有任何 commit 不在 SIT 且不在白名单，返回错误

**配置项**：
```yaml
gitlab:
  validate-uat-from-sit: true  # 启用校验
  bypass-keywords: 用于贯标,紧急修复  # 白名单关键字
```

**关键代码**：
```java
private String validateCommitsInSit(Map<String, Object> payload, ...)

private boolean checkCommitInBranch(String gitlabDomain, 
                                   String projectIdentifier, 
                                   String commitSha, 
                                   String branchName, 
                                   String token)
```

**相关文档**：
- `Webhook_UAT分支校验说明.md`（详细使用文档）

**实现时间**：2026-02-09

---

### 4. 权限白名单配置 ✅

**功能**：Webhook 接口无需登录即可访问，已添加到权限白名单。

**实现要点**：
- `LoginInterceptor`：排除 `/api/webhook/**`
- `WebMvcConfig`：排除 `/api/webhook/**`

---

### 5. 多种 Token 配置来源 ✅

**功能**：支持从多个配置项读取 GitLab token，提高兼容性。

**实现要点**：
```java
@Value("${gitlab.access-token:${gitlab.accessToken:${git.token:${git.gitlab.token:${database.export.token:}}}}}")
private String gitlabAccessToken;
```

**优先级**（从高到低）：
1. `gitlab.access-token`
2. `gitlab.accessToken`
3. `git.token`
4. `git.gitlab.token`
5. `database.export.token`

---

### 6. 404 错误修复与增强 ✅

**功能**：修复 GitLab API 下载文件时的 404 错误，增强错误提示。

**改进点**：
- 文件路径编码改用 `.replace("/", "%2F")`（替代 `URLEncoder.encode`）
- 支持用 `path_with_namespace`（URL 编码）作为项目标识（部分自建 GitLab 更稳定）
- 添加超时设置（连接超时 10s，读取超时 30s）
- 404 错误时输出详细提示信息

**实现时间**：2026-02-04

---

### 7. Git Clone 备用方案 ✅

**功能**：当 GitLab API 持续返回 404 时，可改用 `git clone` 方式下载文件。

**配置项**：
```yaml
gitlab:
  download-mode: clone  # api=仅 API；clone=仅 git clone
```

**实现要点**：
- API 下载失败时自动尝试 clone
- 使用临时目录，clone 后读取文件，完成后自动清理
- 需要本机已安装 git 命令

**关键代码**：
```java
private String downloadFileFromGitLabByClone(String projectUrl, 
                                             String pathWithNamespace, 
                                             String filePath, 
                                             String branch, 
                                             String token)
```

**实现时间**：2026-02-09

---

## 配置文件完整示例

```yaml
# Git配置
git:
  gitlab:
    url: http://gitlab.spdb.com

# Webhook 调 GitLab API 用的 token
gitlab:
  access-token: WScw9XyM8hM1sYKLAz8o  # GitLab token
  download-mode: api  # api=仅 API；clone=仅 git clone（API 404 时可改 clone）
  
  # UAT 分支提交校验
  validate-uat-from-sit: true  # 启用 uat 分支校验（默认 false）
  bypass-keywords: 用于贯标,紧急修复  # 白名单关键字（逗号分隔）

# 数据库导出配置（包含 token，Webhook 会回退读取）
database:
  export:
    token: WScw9XyM8hM1sYKLAz8o  # 与 gitlab.access-token 保持一致
```

---

## API 接口

### 1. GitLab Webhook 接口
```
POST /api/webhook/gitlab
Content-Type: application/json
X-Gitlab-Event: Push Hook

{
  "ref": "refs/heads/master",
  "commits": [...],
  "project": {
    "id": 63480,
    "path_with_namespace": "ccbs-sett/sett-pbf",
    "web_url": "https://gitlab.spdb.com/ccbs-sett/sett-pbf"
  }
}
```

### 2. GitHub Webhook 接口
```
POST /api/webhook/github
Content-Type: application/json
X-GitHub-Event: push

{
  "ref": "refs/heads/master",
  "commits": [...],
  "repository": {
    "name": "project-name",
    "clone_url": "https://github.com/user/repo.git"
  }
}
```

### 3. 通用接口（自动识别）
```
POST /api/webhook/git
```

### 4. 健康检查
```
GET /api/webhook/health
```

---

## 相关文档

| 文档 | 说明 |
|------|------|
| `Webhook快速开始.md` | 5分钟快速上手指南 |
| `Webhook功能使用说明.md` | 详细的功能说明和配置 |
| `Webhook功能配置示例.md` | 各种场景的配置示例 |
| `Webhook权限配置说明.md` | 权限白名单配置 |
| `Webhook问题修复说明.md` | 404 错误等问题修复记录 |
| `Webhook_UAT分支校验说明.md` | UAT→SIT 校验功能详细说明 |
| `Webhook实现方案对比.md` | 不同实现方案的对比 |
| `Webhook功能实现清单.md` | 功能实现检查清单 |

---

## 工作流程图

### Master 分支提交流程

```
开发者 push 到 master
    ↓
GitLab/GitHub 触发 Webhook
    ↓
POST /api/webhook/gitlab (或 github)
    ↓
WebhookServiceImpl.handleGitLabPushEvent
    ↓
收集 added/modified/removed 的 .flowtrans.xml
    ↓
处理删除：deleteFlowtranBySourceInfo (按 from_jar 删除)
    ↓
处理新增/修改：downloadFileFromGitLab (或 ByClone)
    ↓
FlowXmlParseServiceImpl.parseAndSave
    ↓
先删后插：flowtranMapper.deleteById + insert
          flowStepMapper.delete + insert
    ↓
返回结果：{"success": true, "flowtranCount": 2, "flowStepCount": 15}
```

### UAT 分支校验流程

```
开发者 push 到 uat
    ↓
GitLab 触发 Webhook
    ↓
WebhookServiceImpl.handleGitLabPushEvent
    ↓
检测到 ref = "refs/heads/uat" 且 validateUatFromSit = true
    ↓
validateCommitsInSit(payload, ...)
    ↓
对每个 commit：
  - 检查 message 是否包含白名单关键字
  - 包含 → 跳过校验
  - 不包含 → 调用 GitLab API 检查是否在 sit 分支
    ↓
checkCommitInBranch(domain, projectId, sha, "sit", token)
    ↓
GET /api/v4/projects/:id/repository/commits/:sha/refs?type=branch
    ↓
解析响应，查找 "name":"sit"
    ↓
所有 commit 都通过 → 返回 null (继续处理)
有 commit 不通过 → 返回错误信息
    ↓
返回结果：{"success": false, "message": "UAT 分支校验失败..."}
```

---

## 数据库表结构

### flowtran 表
```sql
CREATE TABLE `flowtran` (
  `id` varchar(50) PRIMARY KEY,  -- 交易ID
  `longname` varchar(200),        -- 交易名称
  `package_path` varchar(500),    -- 包路径
  `txn_mode` varchar(50),         -- 事务模式
  `from_jar` varchar(500),        -- 来源（如：projectName:master:filePath）
  `create_time` datetime,
  `update_time` datetime
);
```

### flow_step 表
```sql
CREATE TABLE `flow_step` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `flow_id` varchar(50),          -- 关联 flowtran.id
  `node_name` varchar(200),       -- 节点名称
  `node_type` varchar(50),        -- service/method
  `step` int,                     -- 步骤顺序
  `node_longname` varchar(500),   -- 节点长名称
  `incorrect_calls` text,         -- 违规调用
  `create_time` datetime,
  `update_time` datetime,
  KEY `idx_flow_id` (`flow_id`)
);
```

---

## 注意事项

### 1. Webhook 不能阻止提交

⚠️ **重要**：Webhook 是**事后通知**，在代码已 push 后才触发。UAT 校验返回失败**不会阻止代码提交**，只能：
- 在 webhook 历史中显示错误
- 记录日志便于审计
- 提醒管理员人工处理

如需真正阻止提交，需使用 GitLab Protected Branches 或 Push Rules。

### 2. Token 权限

`gitlab.access-token` 必须具备以下权限：
- `read_repository`：读取仓库文件
- `read_api`：调用 API 查询 commit 所在分支

### 3. 性能考虑

- **UAT 校验**：每个 commit 需调用一次 GitLab API，大量 commit 时可能较慢
- **文件下载**：API 方式较快，clone 方式较慢但更稳定
- **数据库操作**：采用"先删后插"，避免主键冲突

---

## 后续扩展方向

1. **发送通知**  
   校验失败时，通过钉钉/企业微信/邮件通知相关人员

2. **自动回滚**  
   校验失败时，自动调用 GitLab API 删除或 revert 不合规的 commit

3. **多级校验**  
   支持 PROD→UAT、UAT→SIT 等多级环境校验

4. **审计日志**  
   将所有 webhook 事件和校验结果持久化到数据库，便于追溯

5. **批量处理**  
   支持手动触发"全量扫描"，处理历史数据

---

## 完成时间

- 基础功能：2026-01-20
- 404 修复：2026-02-04
- 文件删除同步删库：2026-02-09
- Git Clone 备用方案：2026-02-09
- UAT 分支校验：2026-02-09
