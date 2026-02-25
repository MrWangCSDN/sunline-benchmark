# 启动脚本使用说明

## 脚本列表

### 1. start.sh - 启动应用
启动 Spring Boot 应用程序。

**使用方法：**
```bash
./start.sh
```

**功能：**
- 检查应用是否已经运行
- 查找并启动 JAR 文件
- 设置 JVM 参数（可自定义）
- 后台运行应用
- 保存进程 PID
- 输出日志到 `logs/app.log`

**配置项（在 start.sh 中修改）：**
- `JAR_NAME`: JAR 文件名称（默认：dict-0.0.1-SNAPSHOT.jar）
- `JVM_OPTS`: JVM 参数（默认：-Xms512m -Xmx1024m）
- `SPRING_OPTS`: Spring Boot 配置（默认：--spring.profiles.active=prod）

---

### 2. stop.sh - 停止应用
停止正在运行的应用程序。

**使用方法：**
```bash
./stop.sh
```

**功能：**
- 读取 PID 文件
- 优雅停止应用（发送 SIGTERM 信号）
- 等待最多 30 秒
- 如果超时则强制终止（发送 SIGKILL 信号）
- 清理 PID 文件

---

### 3. restart.sh - 重启应用
先停止再启动应用程序。

**使用方法：**
```bash
./restart.sh
```

**功能：**
- 调用 stop.sh 停止应用
- 等待 2 秒
- 调用 start.sh 启动应用

---

### 4. status.sh - 查看应用状态
查看应用程序运行状态和详细信息。

**使用方法：**
```bash
./status.sh
```

**功能：**
- 检查应用是否运行
- 显示进程 PID
- 显示进程详情
- 显示内存使用情况
- 显示端口监听情况

---

## 使用示例

### 首次启动
```bash
# 1. 打包应用
mvn clean package

# 2. 启动应用
./start.sh

# 3. 查看日志
tail -f logs/app.log
```

### 日常维护
```bash
# 查看应用状态
./status.sh

# 重启应用
./restart.sh

# 停止应用
./stop.sh
```

---

## 日志文件

应用日志输出到 `logs/app.log` 文件。

**查看实时日志：**
```bash
tail -f logs/app.log
```

**查看最后 100 行日志：**
```bash
tail -n 100 logs/app.log
```

**搜索错误日志：**
```bash
grep ERROR logs/app.log
```

---

## 故障排查

### 问题 1: "未找到 JAR 文件"
**原因：** 应用未打包或 JAR 文件名不匹配。

**解决方法：**
```bash
# 重新打包
mvn clean package

# 或修改 start.sh 中的 JAR_NAME 为实际的 JAR 文件名
```

### 问题 2: "应用已经在运行中"
**原因：** 应用已经启动。

**解决方法：**
```bash
# 查看状态
./status.sh

# 如果需要重启
./restart.sh
```

### 问题 3: "PID 文件残留"
**原因：** 应用异常终止，PID 文件未清理。

**解决方法：**
```bash
# 手动删除 PID 文件
rm -f sunline-benchmark.pid

# 重新启动
./start.sh
```

### 问题 4: "端口被占用"
**原因：** 默认端口（8080）已被其他程序占用。

**解决方法：**
```bash
# 查看端口占用
lsof -i :8080

# 修改配置文件中的端口，或在 start.sh 中添加端口参数
# SPRING_OPTS="--spring.profiles.active=prod --server.port=8081"
```

---

## JVM 参数调优

根据服务器配置，可以在 `start.sh` 中调整 JVM 参数：

**小内存服务器（2GB）：**
```bash
JVM_OPTS="-Xms256m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/"
```

**中等内存服务器（4GB）：**
```bash
JVM_OPTS="-Xms512m -Xmx1024m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/"
```

**大内存服务器（8GB+）：**
```bash
JVM_OPTS="-Xms1024m -Xmx2048m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/"
```

---

## 环境配置

### 开发环境
修改 `start.sh` 中的：
```bash
SPRING_OPTS="--spring.profiles.active=dev"
```

### 生产环境
修改 `start.sh` 中的：
```bash
SPRING_OPTS="--spring.profiles.active=prod"
```

---

## 注意事项

1. **权限问题：** 确保脚本有执行权限（已设置）
2. **Java 环境：** 确保系统已安装 Java（JDK 8 或更高版本）
3. **端口冲突：** 确保应用端口未被占用
4. **日志文件：** 定期清理日志文件，避免占用过多磁盘空间
5. **PID 文件：** 不要手动修改或删除 PID 文件（除非故障排查需要）

---

## 开机自启动（可选）

如果需要应用开机自启动，可以使用 systemd 或 crontab。

### 使用 systemd
创建 `/etc/systemd/system/sunline-benchmark.service` 文件：
```ini
[Unit]
Description=Sunline Benchmark Application
After=network.target

[Service]
Type=forking
User=your-username
WorkingDirectory=/path/to/sunline-benchmark
ExecStart=/path/to/sunline-benchmark/start.sh
ExecStop=/path/to/sunline-benchmark/stop.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启用开机自启动：
```bash
sudo systemctl daemon-reload
sudo systemctl enable sunline-benchmark
sudo systemctl start sunline-benchmark
```

---

## 技术支持

如有问题，请查看：
- 应用日志：`logs/app.log`
- Spring Boot 文档：https://spring.io/projects/spring-boot
- 项目 README.md

