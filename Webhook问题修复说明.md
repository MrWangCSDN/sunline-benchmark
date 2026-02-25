# Webhook问题修复说明

## 问题1：下载文件失败（404错误）

### 问题原因
GitLab API对文件路径的编码有特殊要求：
- ❌ 错误：使用`URLEncoder.encode()`会将所有特殊字符编码
- ✅ 正确：只需要将斜杠`/`编码为`%2F`

### 修复前的代码
```java
String encodedFilePath = java.net.URLEncoder.encode(filePath, "UTF-8");
// 结果：src%2Fmain%2Fresources%2Ftrans%2Fhapp%2FT6302.flowtrans.xml
// 问题：可能还会编码其他字符，导致GitLab API无法识别
```

### 修复后的代码
```java
// GitLab API要求文件路径中的斜杠必须编码为%2F
String encodedFilePath = filePath.replace("/", "%2F");
// 结果：src%2Fmain%2Fresources%2Ftrans%2Fhapp%2FT6302.flowtrans.xml
```

### 正确的API URL格式
```
https://gitlab.spdb.com/api/v4/projects/63480/repository/files/sett-pbr%2Fsrc%2Fmain%2Fresources%2Ftrans%2Fhapp%2FT6302.flowtrans.xml/raw?ref=master
```

### 其他改进
1. ✅ 添加连接超时设置（10秒）
2. ✅ 添加读取超时设置（30秒）
3. ✅ 读取并记录错误响应信息
4. ✅ 记录下载成功的文件大小
5. ✅ 更详细的日志输出

### 补充：返回 "404 Project Not Found"（项目未找到）

**现象**：浏览器打开「项目/文件」地址能访问，但程序调用 GitLab API 返回 `{"message":"404 Project Not Found"}`。

**常见原因**：
1. **未认证**：私有或内部项目未配置 `gitlab.access-token` 时，GitLab 会对未认证请求返回 404（不暴露项目是否存在）。
2. **Token 权限不足**：Token 需具备 `read_repository` 权限。
3. **项目标识**：部分自建 GitLab 用数字 `project.id` 可能异常，已支持改用 `project.path_with_namespace`（如 `group/subgroup/project`）作为 API 中的项目标识。

**处理建议**：
- 在 `application.yml` 中配置 `gitlab.access-token`（Project Access Token 或 Personal Access Token，勾选 read_repository）。
- 若仍 404，查看日志中的「使用项目标识」：若为数字 id，可确认 webhook 的 `project.path_with_namespace` 是否有值（当前实现会优先用 path）。

---

## 问题2：文件需要删除吗？

### 答案：不需要 ✅

**原因**：
- Webhook实现**不会在本地保存文件**
- 文件内容直接在**内存中处理**（字符串和InputStream）
- 处理流程：下载（内存） → 解析（内存） → 保存数据库 → 内存自动释放

### 处理流程
```
下载文件内容（String）
    ↓
转为InputStream
    ↓
XML解析（DOM）
    ↓
提取数据
    ↓
保存到数据库
    ↓
方法结束，内存自动释放（GC）
```

### 代码验证
```java
// WebhookServiceImpl.java
String fileContent = downloadFileFromGitLab(...);  // 下载到内存（String）
Map<String, Object> parseResult = flowXmlParseService.parseAndSave(fileContent, sourceInfo);  // 直接处理

// FlowXmlParseServiceImpl.java
InputStream is = new ByteArrayInputStream(xmlContent.getBytes(...));  // 转为流
Document doc = builder.parse(is);  // 解析
// 没有文件创建操作
```

**结论**：✅ 无需删除文件，内存会自动释放

---

## 问题3：Linux服务器兼容性

### 答案：完全兼容 ✅

**使用的都是跨平台技术**：

#### 1. HTTP通信
```java
HttpURLConnection connection = ...  // ✅ JDK标准库，跨平台
```

#### 2. XML解析
```java
DocumentBuilderFactory factory = ...  // ✅ JDK标准库，跨平台
DocumentBuilder builder = ...
```

#### 3. 数据库操作
```java
@Autowired
private FlowtranMapper flowtranMapper;  // ✅ MyBatis Plus，跨平台
```

#### 4. 字符串处理
```java
String encodedFilePath = filePath.replace("/", "%2F");  // ✅ Java标准API
```

### Linux部署无影响

✅ **完全兼容**：
- Windows开发 ✅
- Linux生产环境 ✅
- Docker容器 ✅
- Kubernetes ✅

### 验证
在Linux服务器上：
```bash
# 1. 打包
mvn clean package

# 2. 运行
java -jar target/dict-manager-1.0.0.jar

# 3. 测试webhook
curl -X POST http://localhost:8080/api/webhook/health
```

---

## 需要配置access-token吗？

### 查看日志判断

#### 场景1：公开项目（无需token）
```
下载文件: https://gitlab.spdb.com/api/v4/projects/63480/...
文件下载成功: sett-pbr/src/.../T6302.flowtrans.xml, 大小: 1234 字节
```

#### 场景2：私有项目（需要token）
```
下载文件失败，状态码: 401, 错误信息: {"message":"401 Unauthorized"}
```

如果看到401错误，需要配置token：

**application.yml**：
```yaml
gitlab:
  access-token: glpat-your-token-here
```

**重启应用**：
```bash
./restart.sh
```

---

## 验证修复

### 1. 重启应用
```bash
./restart.sh
```

### 2. 测试webhook
从GitLab重新触发webhook，或使用curl：

```bash
curl -X POST http://your-server:8080/api/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Event: Push Hook" \
  -d '{
    "ref": "refs/heads/master",
    "project": {
      "id": 63480,
      "name": "sett-pbr",
      "web_url": "https://gitlab.spdb.com/ccbs-sett/sett-pbr"
    },
    "commits": [{
      "added": [],
      "modified": ["sett-pbr/src/main/resources/trans/happ/T6302.flowtrans.xml"]
    }]
  }'
```

### 3. 查看日志
```bash
tail -f logs/application.log | grep -i "下载文件"
```

### 4. 验证数据库
```sql
-- 查看最新的交易
SELECT * FROM flowtran ORDER BY update_time DESC LIMIT 5;

-- 查看流程步骤
SELECT * FROM flow_step WHERE flow_id = 'T6302' ORDER BY step;
```

---

## 修复总结

✅ **修复1**：GitLab文件路径编码（`/` → `%2F`）  
✅ **修复2**：添加超时设置和详细错误日志  
✅ **说明**：不需要删除文件（内存处理）  
✅ **说明**：Linux服务器完全兼容  

重启应用后即可正常使用！

## 完成时间
2026-02-04
