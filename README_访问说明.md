# 系统访问说明

## 正确的访问方式

### HTTP 访问（开发环境）

系统运行在 HTTP 协议的 8080 端口，请使用以下地址访问：

```
http://localhost:8080
```

或者使用服务器 IP：

```
http://your-server-ip:8080
```

### ⚠️ 注意事项

1. **请勿使用 HTTPS 协议访问**
   - ❌ 错误：`https://localhost:8080`
   - ✅ 正确：`http://localhost:8080`

2. **浏览器自动跳转问题**
   - 某些浏览器会自动将 HTTP 升级为 HTTPS
   - 如果遇到无法访问的情况，请检查浏览器地址栏
   - 确保地址以 `http://` 开头，而不是 `https://`

3. **清除浏览器缓存**
   - 如果之前使用过 HTTPS 访问，浏览器可能会缓存
   - 建议清除浏览器缓存或使用无痕模式访问

4. **常见错误信息**
   - 如果看到 `Invalid character found in method name` 错误
   - 这表示客户端使用了 HTTPS 访问 HTTP 端口
   - 请检查访问地址，确保使用 `http://` 协议

## 主要功能页面

- 登录页面：`http://localhost:8080/login.html`
- 主页面：`http://localhost:8080/index.html`
- Git 分支管理：`http://localhost:8080/index.html` （登录后选择"Git代码管理"菜单）

## 生产环境建议

在生产环境中，建议配置 HTTPS 证书以提高安全性。需要：

1. 申请 SSL 证书
2. 在 `application.yml` 中配置 SSL
3. 使用 HTTPS 协议访问（端口通常为 8443 或 443）

## 技术支持

如有问题，请联系开发团队。

