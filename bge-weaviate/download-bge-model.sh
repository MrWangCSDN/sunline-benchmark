#!/bin/bash
# BGE-base-zh-v1.5 模型下载脚本
# 用法: ./download-bge-model.sh [输出目录]
# 默认输出: ./model-bge-base-zh

OUT_DIR="${1:-./model-bge-base-zh}"
mkdir -p "$OUT_DIR"
cd "$OUT_DIR"

echo "=== 方式 A: 从 HuggingFace 直接下载 ==="
echo "若在国内网络，可能需要代理。请先设置: export https_proxy=http://127.0.0.1:7890"

BASE="https://huggingface.co/BAAI/bge-base-zh-v1.5/resolve/main"

for f in config.json tokenizer_config.json vocab.txt tokenizer.json special_tokens_map.json pytorch_model.bin; do
  echo "下载 $f ..."
  wget -q --show-progress "$BASE/$f" -O "$f" 2>/dev/null || curl -L -o "$f" "$BASE/$f"
done

echo ""
echo "=== 检查文件 ==="
ls -la
echo ""
echo "完成. 将整个 $OUT_DIR 目录打包: zip -r bge-base-zh.zip $(basename $OUT_DIR)/"
echo "上传到服务器后解压到 /www/models/bge-base-zh"
