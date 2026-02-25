package com.sunline.dict.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步配置
 * 启用 @Async 注解支持
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 使用默认的异步配置
    // Spring Boot 会自动创建一个 SimpleAsyncTaskExecutor
}

