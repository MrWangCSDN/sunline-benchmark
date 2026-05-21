package com.sunline.dict.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private LoginInterceptor loginInterceptor;
    
    /**
     * 配置静态资源访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源路径
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/");
    }
    
    /**
     * 配置视图控制器
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 默认首页
        registry.addViewController("/").setViewName("forward:/index.html");
    }
    
    /**
     * 配置跨域
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    
    /**
     * 配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login.html",
                    "/api/auth/**",
                    "/api/webhook/**",  // webhook接口允许匿名访问
                    "/api/build/batches",       // 批次列表查询（免鉴权）
                    "/api/build/all",          // 触发全量编译（免鉴权）
                    "/api/build/all/async",    // 触发全量编译异步（免鉴权）
                    "/api/build/all/progress", // SSE进度订阅（免鉴权）
                    "/api/build/all/status",   // 状态查询（免鉴权）
                    "/api/build/async-build/status", // 异步构建状态回调（免鉴权）
                    "/api/build/records/**",   // 操作明细查询（免鉴权）
                    "/api/build/batch/**",     // 单批次编译触发（免鉴权）
                    "/api/build/project/**",   // 单工程编译触发（免鉴权）
                    "/api/build/clone/**",     // 全量clone（免鉴权）
                    "/api/relation/**",        // 调用关系图谱（免鉴权）
                    "/js/**",
                    "/css/**",
                    "/*.html",
                    "/*.js",
                    "/*.css"
                );
    }
}

