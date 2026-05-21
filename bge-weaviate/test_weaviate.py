#!/usr/bin/env python3
"""
BGE + Weaviate 本地测试脚本
依赖: pip install weaviate-client
"""

import weaviate
from weaviate.classes.config import Configure, Property, DataType
import sys

WEAVIATE_URL = "http://localhost:8080"


def create_client():
    """创建 Weaviate 客户端"""
    return weaviate.connect_to_local(
        host="localhost",
        port=8080,
        grpc_port=50051,
        headers={"X-Request-Source": "test-script"},
    )


def test_connection():
    """测试连接"""
    print("1. 测试 Weaviate 连接...")
    try:
        client = create_client()
        if client.is_ready():
            print("   ✓ Weaviate 连接成功")
        else:
            print("   ✗ Weaviate 未就绪")
            return False
        client.close()
    except Exception as e:
        print(f"   ✗ 连接失败: {e}")
        return False
    return True


def create_schema(client):
    """创建测试集合（Collection）"""
    print("\n2. 创建 Collection schema...")
    try:
        if client.collections.exists("Article"):
            print("   Collection 'Article' 已存在，跳过创建")
            return True

        client.collections.create(
            name="Article",
            vector_config=Configure.Vectors.text2vec_transformers(),
            properties=[
                Property(name="title", data_type=DataType.TEXT),
                Property(name="content", data_type=DataType.TEXT),
            ],
        )
        print("   ✓ Collection 'Article' 创建成功")
    except Exception as e:
        print(f"   ✗ 创建失败: {e}")
        return False
    return True


def insert_data(client):
    """插入测试数据"""
    print("\n3. 插入测试数据...")
    try:
        articles = client.collections.get("Article")
        with articles.batch.dynamic() as batch:
            batch.add_object(
                properties={"title": "人工智能简介", "content": "人工智能是计算机科学的一个分支，致力于创建能够执行通常需要人类智能的任务的系统。"}
            )
            batch.add_object(
                properties={"title": "向量数据库", "content": "向量数据库专为存储和检索高维向量而设计，广泛应用于语义搜索和推荐系统。"}
            )
            batch.add_object(
                properties={"title": "机器学习", "content": "机器学习使计算机能够从数据中学习，无需显式编程即可做出预测或决策。"}
            )
        print("   ✓ 已插入 3 条文本")
    except Exception as e:
        print(f"   ✗ 插入失败: {e}")
        return False
    return True


def semantic_search(client, query: str, limit: int = 3):
    """语义搜索"""
    print(f"\n4. 语义搜索: \"{query}\"")
    try:
        articles = client.collections.get("Article")
        response = articles.query.near_text(query=query, limit=limit)

        for i, obj in enumerate(response.objects, 1):
            title = obj.properties.get("title", "")
            content = obj.properties.get("content", "")[:80]
            score = obj.metadata.distance if obj.metadata.distance is not None else 0
            print(f"   [{i}] {title}")
            print(f"       {content}...")
            print(f"       距离: {score:.4f}")
    except Exception as e:
        print(f"   ✗ 搜索失败: {e}")
        return False
    return True


def run_all_tests():
    """运行完整测试"""
    print("=" * 50)
    print("BGE + Weaviate 测试")
    print("=" * 50)

    if not test_connection():
        print("\n请确保 Docker 服务已启动: docker compose up -d")
        print("首次启动 BGE 模型加载约需 1-2 分钟，请耐心等待")
        sys.exit(1)

    client = create_client()
    try:
        if not create_schema(client):
            sys.exit(1)
        if not insert_data(client):
            sys.exit(1)
        semantic_search(client, "什么是 AI")
        semantic_search(client, "数据库和检索")
    finally:
        client.close()

    print("\n" + "=" * 50)
    print("✓ 测试完成")
    print("=" * 50)


if __name__ == "__main__":
    run_all_tests()
