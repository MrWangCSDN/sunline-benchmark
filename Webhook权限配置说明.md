# Webhook权限配置说明

## 问题描述

Webhook接口测试时返回 `401 未登录` 错误，说明接口被登录拦截器拦截了。

## 解决方案

将webhook相关的API路径添加到白名单，允许匿名访问。

## 修改文件

### 1. LoginInterceptor.java

**文件位置**：`src/main/java/com/sunline/dict/config/LoginInterceptor.java`

**修改内容**：

```java
// 排除登录页面、登录接口和webhook接口
String uri = request.getRequestURI();
if (uri.equals("/") || 
    uri.equals("/login.html") || 
    uri.startsWith("/api/auth/") ||
    uri.startsWith("/api/webhook/") ||  // ✅ 新增：webhook接口允许匿名访问
    uri.startsWith("/js/") ||
    uri.startsWith("/css/") ||
    uri.endsWith(".html") ||
    uri.endsWith(".js") ||
    uri.endsWith(".css")) {
    return true;
}
```

### 2. WebMvcConfig.java

**文件位置**：`src/main/java/com/sunline/dict/config/WebMvcConfig.java`

**修改内容**：

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(loginInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/login.html",
                "/api/auth/**",
                "/api/webhook/**",  // ✅ 新增：webhook接口允许匿名访问
                "/js/**",
                "/css/**",
                "/*.html",
                "/*.js",
                "/*.css"
            );
}
```

## 白名单路径

现在以下webhook端点可以匿名访问：

✅ `POST /api/webhook/github` - GitHub webhook  
✅ `POST /api/webhook/gitlab` - GitLab webhook  
✅ `POST /api/webhook/git` - 通用webhook（自动识别）  
✅ `GET /api/webhook/health` - 健康检查  

## 安全说明

### 当前配置
- webhook接口允许匿名访问
- 适用于内网环境或开发测试环境

### 生产环境建议

#### 1. 添加Secret Token验证

**修改WebhookController**：
```java
@PostMapping("/gitlab")
public Result<Map<String, Object>> handleGitLabWebhook(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
        @RequestHeader(value = "X-Gitlab-Event", required = false) String event) {
    
    // 验证Secret Token
    if (!isValidToken(token)) {
        log.warn("Webhook token验证失败");
        return Result.error("Token验证失败");
    }
    
    // 继续处理...
}
```

**添加验证方法**：
```java
@Value("${webhook.secret-token:}")
private String webhookSecretToken;

private boolean isValidToken(String token) {
    if (webhookSecretToken == null || webhookSecretToken.isEmpty()) {
        return true; // 未配置secret，允许所有请求
    }
    return webhookSecretToken.equals(token);
}
```

**配置application.yml**：
```yaml
webhook:
  secret-token: your-secret-token-here
```

#### 2. IP白名单

**添加IP过滤**：
```java
private static final Set<String> ALLOWED_IPS = Set.of(
    "192.30.252.0/22",  // GitHub webhook IPs
    "185.199.108.0/22", // GitHub webhook IPs
    "140.82.112.0/20",  // GitHub webhook IPs
    // GitLab.com IPs
    // 或您自建GitLab服务器的IP
);

private boolean isAllowedIP(String ip) {
    // 实现IP白名单验证
}
```

#### 3. 使用Spring Security

如果项目使用Spring Security，配置：
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/api/webhook/**").permitAll()  // webhook允许匿名
                .antMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .csrf().disable();  // webhook需要禁用CSRF
    }
}
```

## 重启应用

修改配置后，需要重启应用：

```bash
./restart.sh
```

## 验证配置

### 1. 健康检查（无需登录）
```bash
curl http://localhost:8080/api/webhook/health

# 期望返回：
# {"code":200,"message":"操作成功","data":"Webhook服务运行正常"}
```

### 2. 测试webhook（无需登录）
```bash
curl -X POST http://localhost:8080/api/webhook/gitlab \
  -H "Content-Type: application/json" \
  -H "X-Gitlab-Event: Push Hook" \
  -d '{"ref":"refs/heads/master","commits":[],"project":{"id":123,"name":"test"}}'

# 应该返回200，而不是401
```

### 3. 其他API（需要登录）
```bash
curl http://localhost:8080/api/dict/list

# 期望返回：
# {"code":401,"message":"未登录"}
```

## 注意事项

1. **只有webhook路径被排除**：其他API仍需登录
2. **适用于所有webhook端点**：`/api/webhook/**` 下的所有路径都可以匿名访问
3. **安全考虑**：生产环境建议添加Secret Token验证或IP白名单
4. **重启生效**：修改配置后需要重启应用

## 故障排查

### 仍然返回401
1. 确认代码修改已生效
2. 确认应用已重启
3. 检查日志：`grep "webhook" logs/application.log`
4. 确认URL路径正确：`/api/webhook/gitlab`

### 返回404
1. 确认Controller已加载
2. 检查RequestMapping路径
3. 查看启动日志：`grep "Mapped.*webhook" logs/application.log`

### 返回500
1. 查看详细错误日志
2. 检查payload格式是否正确
3. 确认数据库连接正常

## 完成时间
2026-02-04
