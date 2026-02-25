#!/bin/bash

# 应用名称
APP_NAME="sunline-benchmark"

# PID文件
PID_FILE="${APP_NAME}.pid"

# 检查PID文件是否存在
if [ ! -f "${PID_FILE}" ]; then
    echo "应用状态: 未运行（未找到PID文件）"
    exit 1
fi

# 读取PID
PID=$(cat "${PID_FILE}")

# 检查进程是否存在
if ! ps -p ${PID} > /dev/null 2>&1; then
    echo "应用状态: 未运行（进程不存在，PID文件残留）"
    echo "建议删除PID文件: rm -f ${PID_FILE}"
    exit 1
fi

# 获取进程信息
echo "应用状态: 运行中"
echo "PID: ${PID}"
echo ""
echo "进程详情:"
ps -fp ${PID}
echo ""
echo "内存使用:"
ps -o pid,rss,vsz,comm -p ${PID}
echo ""
echo "端口监听:"
netstat -tulnp 2>/dev/null | grep ${PID} || lsof -Pan -p ${PID} -i 2>/dev/null

