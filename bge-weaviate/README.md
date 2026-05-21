# BGE + Weaviate 本地 Docker 环境

在 Mac 上使用 Docker 运行 BGE 嵌入模型 + Weaviate 向量数据库。

## 快速开始

### 1. 启动服务

```bash
cd bge-weaviate
docker compose -f docker-compose.yml up -d
```

若在项目根目录执行，可使用：
```bash
docker compose -f bge-weaviate/docker-compose.yml up -d
```

首次启动时，BGE 模型下载和加载约需 1–2 分钟，可通过日志查看进度：

```bash
docker compose logs -f bge
```

当看到 `ready` 或类似就绪提示后，再继续测试。

### 2. 验证服务

```bash
# Weaviate 健康检查
curl http://localhost:8080/v1/.well-known/ready

# BGE 推理服务
curl http://localhost:8081/ready
```

### 3. 运行测试脚本

```bash
pip install -r requirements.txt
python test_weaviate.py
```

## 架构说明

| 服务   | 端口 | 说明                    |
|--------|------|-------------------------|
| Weaviate | 8080 | 向量数据库 API          |
| BGE      | 8081 | 嵌入模型推理（内部 8080） |

- Weaviate 使用 `text2vec-transformers` 模块
- 每次写入和查询时，由 BGE 容器生成文本向量
- 当前使用 `BAAI/bge-base-en-v1.5`（英文），体积较小、启动更快

## 切换到中文 BGE 模型（可选）

如需使用中文 BGE，可改为自定义镜像。在 `docker-compose.yml` 中，将 `bge` 服务改为：

```yaml
bge:
  build:
    context: .
    dockerfile: Dockerfile.bge-zh
  # 或使用预构建镜像（若存在）:
  # image: cr.weaviate.io/semitechnologies/transformers-inference:sentence-transformers-BAAI-bge-base-zh-v1.5
```

并添加 `Dockerfile.bge-zh`（基于 Weaviate 自定义模型构建文档）。

## 常用命令

```bash
docker compose up -d      # 后台启动
docker compose down       # 停止并删除容器
docker compose ps         # 查看运行状态
docker compose logs -f    # 查看日志
```

## 注意

- Mac 使用 CPU 推理，首条请求可能较慢
- Apple Silicon (M1/M2/M3) 下 x86 镜像会通过 Rosetta 运行
