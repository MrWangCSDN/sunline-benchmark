# Webhook实现方案对比

## 问题：为什么需要下载文件？

标准的Git Webhook Payload **只包含文件路径，不包含文件内容**：

```json
{
  "commits": [{
    "added": ["src/flows/test.flowtrans.xml"],
    "modified": ["src/flows/user.flowtrans.xml"]
  }]
}
```

要解析`.flowtrans.xml`文件，必须获取文件的完整XML内容。

---

## 方案对比

### 方案1：从Git API下载文件（当前实现）

#### 优点
- ✅ 无需本地仓库
- ✅ 支持多个远程项目
- ✅ 轻量级，不占用磁盘空间

#### 缺点
- ❌ 需要网络访问Git服务器
- ❌ 私有仓库需要access-token
- ❌ 每个文件都需要发起HTTP请求

#### 适用场景
- 应用服务器可以访问外网
- 项目较多，不想本地clone所有仓库

---

### 方案2：使用本地Git仓库（推荐⭐）

#### 实现逻辑
```
Webhook通知
    ↓
git pull更新本地仓库
    ↓
直接读取本地文件
    ↓
解析XML
```

#### 优点
- ✅ 不需要下载文件
- ✅ 不需要access-token
- ✅ 速度更快（本地文件系统）
- ✅ 可以读取多个文件进行关联分析

#### 缺点
- ❌ 需要预先clone仓库到本地
- ❌ 需要维护本地仓库（磁盘空间）
- ❌ 多项目需要clone多个仓库

#### 适用场景
- ⭐ **推荐**：应用服务器在内网，已有本地Git仓库
- 项目数量不多（1-5个）
- 需要高性能

---

### 方案3：使用Git命令克隆+读取

#### 实现逻辑
```
Webhook通知
    ↓
git clone到临时目录（或git pull）
    ↓
读取文件
    ↓
解析XML
    ↓
删除临时目录
```

#### 优点
- ✅ 灵活，动态克隆
- ✅ 可以使用SSH密钥认证

#### 缺点
- ❌ 每次都要clone，速度慢
- ❌ 占用临时磁盘空间
- ❌ 需要Git命令行工具

---

### 方案4：使用Webhook Payload中的文件内容（不可行）

#### 说明
标准的Git Webhook不会在payload中包含文件内容，只有：
- 文件路径（added/modified/removed）
- Commit信息（message、author、timestamp）
- 少量的diff信息（可能有，但不完整）

❌ **此方案不可行**

---

## 推荐方案：本地Git仓库方式

### 实现步骤

#### 1. 配置本地仓库路径

**application.yml**：
```yaml
webhook:
  local-repos:
    - name: ccbs-sett-impl
      path: /data/repos/ccbs-sett-impl
    - name: api-service
      path: /data/repos/api-service
```

#### 2. 修改WebhookServiceImpl

```java
@Value("${webhook.local-repos}")
private List<Map<String, String>> localRepos;

@Override
public Map<String, Object> handleGitLabPushEvent(Map<String, Object> payload) {
    // 获取项目名称
    String projectName = ...;
    
    // 查找本地仓库路径
    String repoPath = findLocalRepoPath(projectName);
    if (repoPath == null) {
        log.warn("未配置本地仓库: {}", projectName);
        return createResult(false, "未配置本地仓库", 0, 0);
    }
    
    // 更新本地仓库
    gitPullRepo(repoPath);
    
    // 直接读取本地文件
    for (String filePath : flowtransFiles) {
        File localFile = new File(repoPath, filePath);
        if (localFile.exists()) {
            String content = Files.readString(localFile.toPath());
            // 解析...
        }
    }
}

private void gitPullRepo(String repoPath) {
    try {
        ProcessBuilder pb = new ProcessBuilder("git", "pull");
        pb.directory(new File(repoPath));
        Process process = pb.start();
        process.waitFor();
    } catch (Exception e) {
        log.error("git pull失败", e);
    }
}
```

#### 3. 预先clone仓库

```bash
# 在服务器上clone仓库
mkdir -p /data/repos
cd /data/repos
git clone https://gitlab.com/user/ccbs-sett-impl.git
git clone https://gitlab.com/user/api-service.git
```

---

## 混合方案（最佳实践⭐⭐⭐）

### 实现逻辑

```java
// 1. 优先使用本地仓库
String repoPath = findLocalRepoPath(projectName);
if (repoPath != null) {
    // 使用本地文件
    gitPullRepo(repoPath);
    content = readLocalFile(repoPath, filePath);
} else {
    // 降级：从API下载
    content = downloadFileFromGitLab(projectUrl, projectId, filePath, branch);
}
```

### 优点
- ✅ 最佳性能（优先本地）
- ✅ 高可用（API作为降级方案）
- ✅ 灵活配置（可以混合使用）

---

## 我应该实现哪个方案？

### 问题1：您的项目部署方式？
- A. 应用服务器在内网，可以访问本地Git仓库
- B. 应用服务器可以访问外网Git平台API
- C. 两者都可以

### 问题2：项目数量？
- A. 1-3个项目
- B. 5-10个项目
- C. 10个以上

### 问题3：性能要求？
- A. 高性能要求（秒级响应）
- B. 中等性能（几秒到十几秒）
- C. 性能不重要

---

## 建议

| 场景 | 推荐方案 |
|------|---------|
| 内网环境，已有本地仓库 | **方案2：本地Git仓库** ⭐⭐⭐ |
| 外网环境，无本地仓库 | 方案1：从API下载（当前实现） |
| 混合环境，高可用要求 | **混合方案：本地优先+API降级** ⭐⭐⭐ |

---

## 是否需要我实现本地Git仓库方案？

如果您的环境已经有本地Git仓库，我可以立即实现**方案2**或**混合方案**，性能会大幅提升！

请告诉我：
1. 是否有本地Git仓库？
2. 本地仓库路径是什么？
3. 项目名称是什么？

我将立即优化实现方案。
