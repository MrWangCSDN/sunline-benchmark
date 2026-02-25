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
                    "/js/**",
                    "/css/**",
                    "/*.html",
                    "/*.js",
                    "/*.css"
                );
    }
}

