# BGE-base-zh 模型下载与打包

## 一、下载方式

### 方式 A：HuggingFace（需可访问 huggingface.co）

```bash
# Mac 终端执行（如本机需翻墙，先开启代理）
cd /Users/wangshanhe/Desktop/myproject/sunline-benchmark/bge-weaviate

# 1. 创建虚拟环境
python3 -m venv .venv-download
source .venv-download/bin/activate

# 2. 下载
pip install -q huggingface_hub
python3 -c "
from huggingface_hub import snapshot_download
snapshot_download(repo_id='BAAI/bge-base-zh-v1.5', local_dir='./model-bge-base-zh')
"
deactivate
```

### 方式 B：ModelScope（国内镜像，无需翻墙）

```bash
cd /Users/wangshanhe/Desktop/myproject/sunline-benchmark/bge-weaviate

python3 -m venv .venv-download
source .venv-download/bin/activate
pip install -q modelscope

python3 -c "
from modelscope import snapshot_download
snapshot_download('AI-ModelScope/bge-base-zh-v1.5', cache_dir='./model-bge-base-zh')
"
deactivate

# ModelScope 下载到 cache_dir 下会有子目录，需整理
# 将 model-bge-base-zh/AI-ModelScope/bge-base-zh-v1.5/* 移到 model-bge-base-zh/
```

### 方式 C：直接 wget（HuggingFace CDN）

若网络可访问，在模型目录下执行：

```bash
mkdir -p model-bge-base-zh
cd model-bge-base-zh

wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/config.json
wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/pytorch_model.bin
wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/tokenizer_config.json
wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/vocab.txt
wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/tokenizer.json
wget https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main/special_tokens_map.json
```

## 二、打包

```bash
cd /Users/wangshanhe/Desktop/myproject/sunline-benchmark/bge-weaviate
tar -czvf bge-base-zh.tar.gz model-bge-base-zh/
# 或
zip -r bge-base-zh.zip model-bge-base-zh/
```

## 三、上传到 Linux 服务器

```bash
scp bge-base-zh.tar.gz root@ldclouda30627:/www/
```

## 四、服务器解压与挂载

```bash
# 在服务器上
cd /www
tar -xzvf bge-base-zh.tar.gz

# 若解压后是 model-bge-base-zh/，创建 bge-m3 软链或直接挂载
# embedding-service 需要 /models/bge-m3，所以：
mv model-bge-base-zh bge-base-zh  # 或保持 model-bge-base-zh

# 重启容器并挂载
docker stop embedding-service
docker rm embedding-service

docker run -d \
  --name embedding-service \
  --network vec-net \
  -p 8080:8080 \
  -v /www/bge-base-zh:/models/bge-m3 \
  embedding-service:1.0
```

## 五、模型文件清单（确保完整）

- config.json
- pytorch_model.bin（约 400MB）
- tokenizer_config.json
- vocab.txt
- tokenizer.json
- special_tokens_map.json
