# Webhook 向量化接入设计方案

## 一、当前 Webhook 流程概览

```
Git Push → WebhookController (/api/webhook/github | /api/webhook/gitlab)
         → WebhookServiceImpl.handlePushEvent | handleGitLabPushEvent
         → 收集变更文件 → 删除(deleteBySourceInfo) → 新增/修改(parseAndSave)
```

### 1.1 模型类型与解析服务

| 文件类型 | 解析服务 | 主表 | 来源字段 |
|----------|----------|------|----------|
| .flowtrans.xml | FlowXmlParseService | flowtran, flow_step | from_jar |
| .c_schema.xml | ComplexXmlParseService | complex, complex_detail | from_jar |
| .d_schema.xml | DictXmlParseService | dict, dict_detail | from_jar |
| .u_schema.xml | UschemaXmlParseService | uschema, uschema_detail | from_jar |
| .e_schema.xml | EschemaXmlParseService | eschema, eschema_detail | from_jar |
| .tables.xml | TablesXmlParseService | metadata_tables, metadata_tables_detail | from_jar |
| .pbcb.xml 等 | ComponentXmlParseService | component, component_detail | from_jar |
| .pcs.xml, .pbs.xml | ServiceFileXmlParseService | service_file, service_detail | from_jar |
| *Impl.xml | ServiceImplXmlParseService | service_impl | from_jar |

### 1.2 sourceInfo 格式

- **GitHub**: `projectName:filePath`
- **GitLab**: `projectName:master:filePath`

---

## 二、向量化接入架构

```
parseAndSave 成功
       ↓
VectorizationService.vectorizeBySource(modelType, sourceInfo)
       ↓
  1. 按 from_jar=sourceInfo 查询主表记录
  2. 为每条记录构建待向量化文本（id + longname + 关键字段概要）
  3. 调用 Embedding API 获取向量
  4. Upsert 到 Qdrant（point id = modelType:recordId，payload 含 sourceInfo 便于删除）

deleteBySourceInfo 调用后
       ↓
VectorizationService.deleteBySource(modelType, sourceInfo)
       ↓
  按 payload.sourceInfo 过滤，删除 Qdrant 中对应 points
```

---

## 三、实现清单

### 3.1 新增依赖（pom.xml）

```xml
<!-- Qdrant Java Client -->
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-client</artifactId>
    <version>1.12.0</version>
</dependency>
```

### 3.2 配置（application.yml）

```yaml
# 向量化配置（可选，不配置则 Webhook 不执行向量化）
vectorization:
  enabled: true
  embedding:
    url: http://localhost:8080  # embedding-service 地址，与 sunline-benchmark 同机时 localhost
  qdrant:
    host: localhost
    port: 6334
  collection: xml_models  # 统一 collection，用 payload.model_type 区分
  vector-size: 768       # BGE-base-zh 维度
```

### 3.3 新增模块

| 类 | 职责 |
|----|------|
| `VectorizationService` | 接口：vectorizeBySource、deleteBySource |
| `VectorizationServiceImpl` | 按 modelType 查表、组文本、调 embedding、写 Qdrant |
| `EmbeddingClient` | 调用 embedding-service `/embed` 接口 |
| `QdrantVectorStore` | Collection 管理、point upsert/delete |

### 3.4 WebhookServiceImpl 改动

在 **parseAndSave** 成功后增加（需 `vectorization.enabled=true`）：

```java
// 示例：c_schema 解析完成后
if (vectorizationEnabled && parseResult.get("complexCount") != 0) {
    try {
        vectorizationService.vectorizeBySource("complex", sourceInfo);
    } catch (Exception e) {
        log.warn("向量化失败，不影响主流程: {}", e.getMessage());
    }
}
```

在 **deleteBySourceInfo** 调用后增加：

```java
// 示例：complex 删除后
if (vectorizationEnabled) {
    try {
        vectorizationService.deleteBySource("complex", sourceInfo);
    } catch (Exception e) {
        log.warn("向量删除失败: {}", e.getMessage());
    }
}
```

---

## 四、待向量化文本构建规则（示例）

| modelType | 文本来源 |
|-----------|----------|
| flowtran | id + longname + txnMode |
| complex | id + longname + packagePath |
| dict | id + longname |
| uschema | id + longname |
| eschema | id + longname |
| metadata_tables | tableName + longname |
| component | id + longname + kind |
| service_file | id + longname + kind |
| service_impl | id + longname |

可根据实际业务补充 detail 字段概要（如前 N 个字段名）。

---

## 五、Qdrant 数据结构

- **Collection**: 统一 `xml_models`，或按 modelType 分 collection（可选）
- **Vector**: 768 维，Cosine 距离
- **Point payload**:
  - `model_type`: flowtran | complex | dict | ...
  - `source_info`: 来源标识，用于按来源删除
  - `record_id`: 业务主键
  - `longname`: 长名称（便于检索结果展示）

---

## 六、部署与配置

| 环境 | embedding-service | Qdrant | vectorization.enabled |
|------|-------------------|--------|------------------------|
| 开发 | http://localhost:8080 | localhost:6334 | true |
| 生产 | http://embedding-service:8765 或内网 IP:8080 | 内网 IP:6334 | true |

若 embedding 或 Qdrant 未部署，设置 `vectorization.enabled: false` 即可跳过向量化，Webhook 主流程不受影响。

---

## 七、后续：检索 API

新增接口示例：

- `POST /api/vector/search`：语义搜索，入参 `query`、`modelType`（可选）、`limit`
- 流程：query → embedding API → Qdrant search → 返回 payload（含 record_id、longname 等）

---

如需开始实现，可按上述清单逐步开发。
