package com.sunline.dict.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 排除登录页面、登录接口和webhook接口
        String uri = request.getRequestURI();
        if (uri.equals("/") || 
            uri.equals("/login.html") || 
            uri.startsWith("/api/auth/") ||
            uri.startsWith("/api/webhook/") ||  // webhook接口允许匿名访问
            uri.startsWith("/js/") ||
            uri.startsWith("/css/") ||
            uri.endsWith(".html") ||
            uri.endsWith(".js") ||
            uri.endsWith(".css")) {
            return true;
        }
        
        // 检查Session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            // 如果是API请求，返回JSON错误
            if (uri.startsWith("/api/")) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                // 否则跳转到登录页
                response.sendRedirect("/login.html");
            }
            return false;
        }
        
        return true;
    }
}

