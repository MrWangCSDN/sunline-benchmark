#!/bin/bash

# 应用名称
APP_NAME="sunline-benchmark"

# PID文件
PID_FILE="${APP_NAME}.pid"

# 检查PID文件是否存在
if [ ! -f "${PID_FILE}" ]; then
    echo "应用未运行（未找到PID文件）"
    exit 0
fi

# 读取PID
PID=$(cat "${PID_FILE}")

# 检查进程是否存在
if ! ps -p ${PID} > /dev/null 2>&1; then
    echo "应用未运行（进程不存在）"
    rm -f "${PID_FILE}"
    exit 0
fi

# 停止应用
echo "正在停止 ${APP_NAME}... (PID: ${PID})"
kill ${PID}

# 等待进程结束
COUNT=0
while ps -p ${PID} > /dev/null 2>&1; do
    sleep 1
    COUNT=$((COUNT+1))
    echo "等待进程结束... (${COUNT}秒)"
    
    # 如果超过30秒还没结束，强制杀死
    if [ ${COUNT} -ge 30 ]; then
        echo "进程未正常结束，强制终止..."
        kill -9 ${PID}
        sleep 2
        break
    fi
done

# 检查进程是否已经结束
if ps -p ${PID} > /dev/null 2>&1; then
    echo "应用停止失败"
    exit 1
else
    echo "应用已停止"
    rm -f "${PID_FILE}"
    exit 0
fi

