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
        String uri = request.getRequestURI();
        if (isAnonymousAllowed(uri)) {
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

    private boolean isAnonymousAllowed(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return uri.equals("/")
                || uri.equals("/login.html")
                || uri.startsWith("/api/auth/")
                || uri.startsWith("/api/webhook/")
                || uri.equals("/api/build/batches")
                || uri.equals("/api/build/all")
                || uri.equals("/api/build/all/async")
                || uri.equals("/api/build/all/progress")
                || uri.equals("/api/build/all/status")
                || uri.equals("/api/build/async-build/status")
                || uri.startsWith("/api/build/records/")
                || uri.startsWith("/api/build/batch/")
                || uri.startsWith("/api/build/project/")
                || uri.startsWith("/api/build/clone/")
                || uri.startsWith("/api/relation/")
                || uri.startsWith("/js/")
                || uri.startsWith("/css/")
                || uri.endsWith(".html")
                || uri.endsWith(".js")
                || uri.endsWith(".css");
    }
}
