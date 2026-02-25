#!/bin/bash

# 应用名称
APP_NAME="sunline-benchmark"

echo "正在重启 ${APP_NAME}..."

# 停止应用
./stop.sh

# 等待2秒
sleep 2

# 启动应用
./start.sh

