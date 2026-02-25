# Webhook UAT 分支校验功能说明

## 功能概述

当开发者向 **UAT 分支**提交代码时，Webhook 会自动校验：该 commit 是否已经在 **SIT 分支**上存在。

**校验规则**：
- ✅ 如果 commit 已在 SIT 分支，允许提交（webhook 返回成功）
- ❌ 如果 commit 不在 SIT 分支，webhook 返回失败（记录日志和错误信息）
- ⚠️ 如果 commit message 包含**白名单关键字**（如"用于贯标"），跳过校验，直接放行

---

## 配置说明

在 `application.yml` 中配置：

```yaml
gitlab:
  access-token: your-token-here  # 必需，用于调用 GitLab API
  
  # UAT 分支校验配置
  validate-uat-from-sit: true  # 是否启用 uat 分支校验（默认 false）
  bypass-keywords: 用于贯标,紧急修复  # 白名单关键字（逗号分隔）
```

### 配置项说明

| 配置项 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `gitlab.access-token` | 是 | 无 | GitLab 访问 Token，需具备 `read_repository` 权限 |
| `gitlab.validate-uat-from-sit` | 否 | `false` | 是否启用 UAT 分支校验 |
| `gitlab.bypass-keywords` | 否 | 空 | 白名单关键字，逗号分隔。commit message 包含任一关键字时跳过校验 |

---

## 使用场景

### 场景 1：正常流程（校验通过）

1. 开发者先将代码提交到 **SIT 分支**
2. SIT 环境测试通过
3. 开发者将**同一个 commit** 提交到 **UAT 分支**（如 cherry-pick 或 merge）
4. Webhook 检测到 UAT 分支 push，校验该 commit 在 SIT 分支存在
5. ✅ **校验通过**，webhook 返回成功

### 场景 2：违规提交（校验失败）

1. 开发者直接向 **UAT 分支**提交新代码（跳过 SIT）
2. Webhook 检测到 UAT 分支 push，校验该 commit 在 SIT 分支**不存在**
3. ❌ **校验失败**，webhook 返回错误：
   ```
   UAT 分支校验失败，以下 commit 需先提交到 sit 分支: 
   Commit a1b2c3d4 (feat: 新功能) 不在 sit 分支
   ```
4. 管理员可在 GitLab Webhook 历史中看到此错误

### 场景 3：白名单放行

1. 开发者向 **UAT 分支**提交紧急修复
2. commit message: `fix: 紧急修复生产问题（用于贯标）`
3. Webhook 检测到 message 包含白名单关键字 **"用于贯标"**
4. ⚠️ **跳过校验**，直接放行

---

## 工作原理

### 1. 分支检测

Webhook 收到 GitLab Push 事件后，解析 `ref` 字段：
- `refs/heads/uat` → 触发校验
- `refs/heads/master` 或其它分支 → 不触发校验（但 master 仍处理 flowtrans.xml）

### 2. Commit 校验

对每个提交到 UAT 的 commit：

1. **白名单检查**  
   检查 commit message 是否包含配置的白名单关键字，包含则跳过校验

2. **调用 GitLab API**  
   ```
   GET /api/v4/projects/:id/repository/commits/:sha/refs?type=branch
   ```
   该 API 返回包含该 commit 的所有分支列表

3. **判断结果**  
   - 如果返回的分支列表中包含 `"name":"sit"`，说明在 SIT 分支
   - 否则，记录违规信息

4. **汇总结果**  
   - 所有 commit 都在 SIT 或在白名单 → 校验通过
   - 有任何 commit 不在 SIT 且不在白名单 → 校验失败，返回错误

---

## 注意事项

### 1. Webhook 不能真正"阻止"提交

⚠️ **重要**：GitLab Webhook 是**事后通知**机制，在代码已经 push 后才触发。因此 webhook 返回失败**不会阻止代码提交**，只能：
- 在 webhook 历史中显示错误
- 记录日志便于审计
- 后续可扩展为发送通知、自动回滚等

如果需要**真正阻止**不符合规则的提交，需要使用：
- GitLab **Protected Branches** 规则
- GitLab **Push Rules**（Premium 功能）
- GitLab **Server-side Git hooks**（需服务器权限）

### 2. Token 权限要求

`gitlab.access-token` 必须具备 **`read_repository`** 权限，否则无法调用 GitLab API 查询 commit 所在分支。

### 3. 多个 Commit 的情况

如果一次 push 包含多个 commit，会逐个校验。只要有一个不符合规则，整个 push 都会被标记为失败。

### 4. 白名单关键字匹配

- 关键字匹配是**子串匹配**（不区分大小写的简单 `contains` 检查）
- 多个关键字用英文逗号分隔
- 只要 commit message 包含**任意一个**关键字，就跳过校验

---

## 配置示例

### 示例 1：启用校验，单个白名单关键字

```yaml
gitlab:
  access-token: WScw9XyM8hM1sYKLAz8o
  validate-uat-from-sit: true
  bypass-keywords: 用于贯标
```

### 示例 2：启用校验，多个白名单关键字

```yaml
gitlab:
  access-token: WScw9XyM8hM1sYKLAz8o
  validate-uat-from-sit: true
  bypass-keywords: 用于贯标,紧急修复,hotfix
```

### 示例 3：禁用校验（默认）

```yaml
gitlab:
  access-token: WScw9XyM8hM1sYKLAz8o
  validate-uat-from-sit: false  # 或直接不配置此项
```

---

## 日志示例

### 校验通过

```
2026-02-09 15:30:00 [main] INFO  WebhookServiceImpl - 收到GitLab Push事件
2026-02-09 15:30:00 [main] INFO  WebhookServiceImpl - 检测到 uat 分支 push，开始校验 commit 是否在 sit 分支存在
2026-02-09 15:30:01 [main] DEBUG WebhookServiceImpl - Commit a1b2c3d4 在分支 sit 上: true
2026-02-09 15:30:01 [main] INFO  WebhookServiceImpl - UAT 分支校验通过
```

### 校验失败

```
2026-02-09 15:35:00 [main] INFO  WebhookServiceImpl - 收到GitLab Push事件
2026-02-09 15:35:00 [main] INFO  WebhookServiceImpl - 检测到 uat 分支 push，开始校验 commit 是否在 sit 分支存在
2026-02-09 15:35:01 [main] WARN  WebhookServiceImpl - Commit a1b2c3d4 (feat: 新功能) 不在 sit 分支
2026-02-09 15:35:01 [main] ERROR WebhookServiceImpl - UAT 分支校验失败: UAT 分支校验失败，以下 commit 需先提交到 sit 分支: Commit a1b2c3d4 (feat: 新功能) 不在 sit 分支
```

### 白名单放行

```
2026-02-09 15:40:00 [main] INFO  WebhookServiceImpl - 收到GitLab Push事件
2026-02-09 15:40:00 [main] INFO  WebhookServiceImpl - 检测到 uat 分支 push，开始校验 commit 是否在 sit 分支存在
2026-02-09 15:40:00 [main] INFO  WebhookServiceImpl - Commit a1b2c3d4 包含白名单关键字 '用于贯标'，跳过校验
2026-02-09 15:40:00 [main] INFO  WebhookServiceImpl - UAT 分支校验通过
```

---

## 故障排查

### 问题 1：校验一直通过，即使 commit 不在 SIT

**可能原因**：
1. `validate-uat-from-sit` 未设置为 `true`
2. commit message 包含了白名单关键字
3. GitLab API 调用失败（token 无效或权限不足）

**排查**：
1. 检查配置：`grep "validate-uat-from-sit" application.yml`
2. 查看日志：`grep "UAT 分支校验" logs/application.log`
3. 检查 token 权限

### 问题 2：API 调用失败，返回 401

**原因**：`gitlab.access-token` 无效或权限不足

**解决**：
1. 确认 token 配置正确
2. 在 GitLab 中重新生成 token，勾选 `read_repository` 权限
3. 更新配置并重启应用

### 问题 3：所有 commit 都被判定为"不在 SIT"

**可能原因**：
1. 项目标识错误（`projectId` 或 `pathWithNamespace`）
2. 网络问题，无法访问 GitLab API
3. SIT 分支名称不是 "sit"（如 "SIT"、"test"）

**解决**：
1. 查看日志中的 API 请求 URL
2. 手动在浏览器中测试该 API
3. 如果分支名不是 "sit"，需修改代码中的 `checkCommitInBranch` 调用

---

## 扩展功能

如需增强此功能，可考虑：

1. **发送通知**  
   校验失败时，通过钉钉/企业微信/邮件通知相关人员

2. **自动回滚**  
   校验失败时，自动调用 GitLab API 删除或 revert 不合规的 commit

3. **支持多环境**  
   不仅 UAT→SIT，还支持 PROD→UAT 等多级校验

4. **记录到数据库**  
   将校验结果持久化，便于后续审计和统计

---

## 完成时间

2026-02-09
