# 字典管理系统

基于 SpringBoot + MyBatis Plus + MySQL 的字典数据管理系统，支持Excel文件导入和数据展示。

## 技术栈

### 后端
- SpringBoot 3.1.5
- MyBatis Plus 3.5.4.1
- MySQL 8.0.33
- Apache POI 5.2.3（Excel处理）
- JDK 17

### 前端
- Vue 3
- Axios
- 原生HTML5/CSS3

## 功能特性

- ✅ Excel文件导入（支持.xlsx和.xls格式）
- ✅ 数据分页展示
- ✅ 关键词搜索（支持多字段模糊查询）
- ✅ 数据统计
- ✅ 清空数据
- ✅ 响应式设计，支持移动端访问

## 数据库配置

### 连接信息
- 地址: 103.47.81.50:3306
- 数据库: apimega
- 用户名: apimega
- 密码: Liang@201314

### 初始化数据库

在MySQL中执行以下SQL创建表：

```sql
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS apimega DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE apimega;

-- 创建字典数据表
CREATE TABLE dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    data_item_code VARCHAR(50) NOT NULL COMMENT '数据项编号',
    english_abbr VARCHAR(200) COMMENT '英文简称',
    chinese_name VARCHAR(200) COMMENT '中文名称',
    dict_attr VARCHAR(50) COMMENT '字典属性',
    domain_chinese_name VARCHAR(200) COMMENT '域中文名称',
    data_type VARCHAR(50) COMMENT '数据类型',
    data_format VARCHAR(100) COMMENT '数据格式',
    java_esf_name VARCHAR(200) COMMENT 'JAVA/ESF规范命名',
    esf_data_format VARCHAR(100) COMMENT 'ESF数据格式',
    gaussdb_data_format VARCHAR(100) COMMENT 'GaussDB数据格式',
    goldendb_data_format VARCHAR(100) COMMENT 'GoldenDB数据格式',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_data_item_code (data_item_code),
    INDEX idx_english_abbr (english_abbr),
    INDEX idx_chinese_name (chinese_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';
```

或者直接执行项目中的SQL文件：
```bash
mysql -h 103.47.81.50 -P 3306 -u apimega -p < src/main/resources/sql/init.sql
```

## 快速开始

### 1. 克隆项目
```bash
git clone <repository-url>
cd sunline-benchmark
```

### 2. 配置数据库
确保MySQL数据库已经创建并运行了初始化脚本（见上方）。

数据库配置在 `src/main/resources/application.yml` 中，默认已配置好。

### 3. 编译项目
```bash
mvn clean package
```

### 4. 运行项目

#### 方式一：使用Maven
```bash
mvn spring-boot:run
```

#### 方式二：运行JAR包
```bash
java -jar target/dict-manager-1.0.0.jar
```

### 5. 访问系统
打开浏览器访问：`http://localhost:8080`

## Excel文件格式要求

Excel文件应包含以下列（按顺序）：

| 列序号 | 列名 | 说明 |
|-------|------|------|
| 1 | 数据项编号 | 如：EDD-000001 |
| 2 | 英文简称 | 如：Sctr_Cd |
| 3 | 中文名称 | 如：板块代码 |
| 4 | 字典属性 | 如：主本/扩展 |
| 5 | 域中文名称 | 如：板块 |
| 6 | 数据类型 | 如：代码类 |
| 7 | 数据格式 | 如：2!an |
| 8 | JAVA/ESF规范命名 | 如：sctrCd |
| 9 | ESF数据格式 | 如：CHAR(2) |
| 10 | GaussDB数据格式 | 如：CHAR(2) |
| 11 | GoldenDB数据格式 | 如：CHAR(2) |

## API接口文档

### 1. 导入Excel
- **URL**: `/api/dict/import`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **参数**: `file` (Excel文件)
- **返回**: 
```json
{
    "code": 200,
    "message": "成功导入5000条数据",
    "data": 5000,
    "timestamp": 1699999999999
}
```

### 2. 分页查询
- **URL**: `/api/dict/page`
- **Method**: `GET`
- **参数**: 
  - `current`: 当前页（默认1）
  - `size`: 每页大小（默认10）
  - `keyword`: 搜索关键词（可选）
- **返回**: 
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [...],
        "total": 5000,
        "pages": 500,
        "current": 1,
        "size": 10
    }
}
```

### 3. 获取数据总数
- **URL**: `/api/dict/count`
- **Method**: `GET`
- **返回**: 
```json
{
    "code": 200,
    "message": "操作成功",
    "data": 5000
}
```

### 4. 清空数据
- **URL**: `/api/dict/truncate`
- **Method**: `DELETE`
- **返回**: 
```json
{
    "code": 200,
    "message": "清空成功",
    "data": true
}
```

### 5. 根据ID查询
- **URL**: `/api/dict/{id}`
- **Method**: `GET`
- **参数**: `id` (路径参数)

### 6. 新增数据
- **URL**: `/api/dict`
- **Method**: `POST`
- **Content-Type**: `application/json`
- **Body**: DictData对象

### 7. 更新数据
- **URL**: `/api/dict`
- **Method**: `PUT`
- **Content-Type**: `application/json`
- **Body**: DictData对象

### 8. 删除数据
- **URL**: `/api/dict/{id}`
- **Method**: `DELETE`
- **参数**: `id` (路径参数)

## 项目结构

```
sunline-benchmark/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── sunline/
│   │   │           └── dict/
│   │   │               ├── DictManagerApplication.java    # 启动类
│   │   │               ├── common/
│   │   │               │   └── Result.java                # 统一响应结果
│   │   │               ├── config/
│   │   │               │   ├── MyBatisPlusConfig.java     # MyBatis Plus配置
│   │   │               │   └── WebMvcConfig.java          # Web MVC配置
│   │   │               ├── controller/
│   │   │               │   └── DictDataController.java    # 控制器
│   │   │               ├── entity/
│   │   │               │   └── DictData.java              # 实体类
│   │   │               ├── mapper/
│   │   │               │   └── DictDataMapper.java        # Mapper接口
│   │   │               └── service/
│   │   │                   ├── DictDataService.java       # 服务接口
│   │   │                   └── impl/
│   │   │                       └── DictDataServiceImpl.java # 服务实现
│   │   └── resources/
│   │       ├── application.yml                             # 应用配置
│   │       ├── sql/
│   │       │   └── init.sql                                # 数据库初始化脚本
│   │       └── static/
│   │           └── index.html                              # 前端页面
│   └── test/
│       └── java/
├── pom.xml                                                 # Maven配置
├── README.md                                               # 项目说明
└── .gitignore                                              # Git忽略配置
```

## 开发说明

### 修改数据库配置
如需修改数据库连接信息，请编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-host:3306/your-database
    username: your-username
    password: your-password
```

### 修改服务端口
在 `application.yml` 中修改：

```yaml
server:
  port: 8080  # 修改为你需要的端口
```

### 修改分页大小
在前端页面 `index.html` 中修改：

```javascript
data() {
    return {
        pageSize: 20,  // 修改每页显示的记录数
        // ...
    }
}
```

## 常见问题

### 1. 启动报错：无法连接数据库
- 检查数据库服务是否启动
- 检查数据库连接配置是否正确
- 检查防火墙是否允许连接

### 2. Excel导入失败
- 检查Excel文件格式是否正确
- 检查Excel列的顺序是否与要求一致
- 查看后台日志获取详细错误信息

### 3. 前端页面无法访问
- 检查SpringBoot是否正常启动
- 检查端口是否被占用
- 检查浏览器控制台是否有错误信息

## 文档导航

本项目提供了完善的文档体系，请根据您的需求选择：

| 文档 | 适用人群 | 阅读时间 |
|------|---------|---------|
| [📖 文档导航.md](📖%20文档导航.md) | 所有人 | 5分钟 |
| [开始使用.md](开始使用.md) | 新用户 | 5分钟 |
| [如何运行.md](如何运行.md) | 部署人员 | 10分钟 |
| [Excel模板说明.md](Excel模板说明.md) | 数据导入者 | 8分钟 |
| [系统架构说明.md](系统架构说明.md) | 开发者 | 20分钟 |
| [项目清单.md](项目清单.md) | 所有人 | 10分钟 |
| [✅ 项目交付清单.md](✅%20项目交付清单.md) | 项目经理 | 15分钟 |

**强烈推荐**: 从 [开始使用.md](开始使用.md) 开始！

## 许可证

本项目仅供学习交流使用。

## 联系方式

如有问题，请联系项目维护者。

