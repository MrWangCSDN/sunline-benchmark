#!/bin/bash

# 应用名称
APP_NAME="sunline-benchmark"

# JAR文件路径（根据实际打包后的jar文件名调整）
JAR_NAME="dict-manager-1.0.0.jar"

# JVM参数
JVM_OPTS="-Xms1024m -Xmx2048m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/"

# Spring Boot配置
SPRING_OPTS="--spring.profiles.active=dev"

# 日志目录
LOG_DIR="logs"

# PID文件
PID_FILE="${APP_NAME}.pid"

# 创建日志目录
if [ ! -d "${LOG_DIR}" ]; then
    mkdir -p "${LOG_DIR}"
fi

# 检查应用是否已经运行
if [ -f "${PID_FILE}" ]; then
    PID=$(cat "${PID_FILE}")
    if ps -p ${PID} > /dev/null 2>&1; then
        echo "应用已经在运行中，PID: ${PID}"
        exit 1
    else
        echo "检测到PID文件，但进程不存在，删除旧的PID文件"
        rm -f "${PID_FILE}"
    fi
fi

# 查找JAR文件（优先查找当前目录，其次查找target目录）
JAR_PATH=""
if [ -f "${JAR_NAME}" ]; then
    JAR_PATH="${JAR_NAME}"
    echo "找到JAR文件: ${JAR_PATH}"
elif [ -f "target/${JAR_NAME}" ]; then
    JAR_PATH="target/${JAR_NAME}"
    echo "找到JAR文件: ${JAR_PATH}"
else
    echo "错误: 未找到JAR文件"
    echo "已查找路径:"
    echo "  1. 当前目录: ${JAR_NAME}"
    echo "  2. target目录: target/${JAR_NAME}"
    echo "请确认JAR文件是否存在，或修改脚本中的JAR_NAME变量"
    exit 1
fi

# 启动应用
echo "正在启动 ${APP_NAME}..."
nohup java ${JVM_OPTS} -jar ${JAR_PATH} ${SPRING_OPTS} > ${LOG_DIR}/app.log 2>&1 &

# 保存PID
echo $! > "${PID_FILE}"

# 等待应用启动
echo "等待应用启动..."
sleep 3

# 检查应用是否成功启动
if [ -f "${PID_FILE}" ]; then
    PID=$(cat "${PID_FILE}")
    if ps -p ${PID} > /dev/null 2>&1; then
        echo "应用启动成功！"
        echo "PID: ${PID}"
        echo "日志文件: ${LOG_DIR}/app.log"
        echo "可以使用 tail -f ${LOG_DIR}/app.log 查看日志"
    else
        echo "应用启动失败，请查看日志文件: ${LOG_DIR}/app.log"
        rm -f "${PID_FILE}"
        exit 1
    fi
else
    echo "应用启动失败，未生成PID文件"
    exit 1
fi

