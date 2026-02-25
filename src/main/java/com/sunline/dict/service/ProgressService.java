package com.sunline.dict.service;

import com.sunline.dict.common.ImportProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度服务
 */
@Service
public class ProgressService {
    
    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);
    
    // 存储所有客户端的SSE连接
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    /**
     * 创建SSE连接
     */
    public SseEmitter createEmitter(String clientId) {
        log.info("创建SSE连接 - clientId: {}", clientId);
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        
        emitter.onCompletion(() -> {
            log.info("SSE连接完成 - clientId: {}", clientId);
            emitters.remove(clientId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时 - clientId: {}", clientId);
            emitters.remove(clientId);
        });
        emitter.onError(e -> {
            log.error("SSE连接错误 - clientId: {}, error: {}", clientId, e.getMessage());
            emitters.remove(clientId);
        });
        
        emitters.put(clientId, emitter);
        log.info("SSE连接已存储 - clientId: {}, 当前连接数: {}", clientId, emitters.size());
        
        return emitter;
    }
    
    /**
     * 发送进度更新（异步执行，不受事务影响）
     */
    @Async
    public void sendProgress(String clientId, ImportProgress progress) {
        log.debug("尝试发送进度 - clientId: {}, percentage: {}%, status: {}", 
            clientId, progress.getPercentage(), progress.getStatus());
        
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                log.info("发送SSE消息 - clientId: {}, percentage: {}%, message: {}", 
                    clientId, progress.getPercentage(), progress.getMessage());
                // 不指定事件名称，这样前端的 onmessage 才能接收到
                emitter.send(progress);
                log.info("✅ SSE消息发送成功 - clientId: {}, percentage: {}%", clientId, progress.getPercentage());
            } catch (IOException e) {
                log.error("❌ 发送SSE消息失败 - clientId: {}, error: {}", clientId, e.getMessage(), e);
                emitters.remove(clientId);
            }
        } else {
            log.warn("⚠️ 未找到SSE连接 - clientId: {}, 当前连接数: {}, 所有clientId: {}", 
                clientId, emitters.size(), emitters.keySet());
        }
    }
    
    /**
     * 完成并关闭连接
     */
    public void complete(String clientId, ImportProgress progress) {
        log.info("完成SSE连接 - clientId: {}", clientId);
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                // 不指定事件名称，这样前端的 onmessage 才能接收到
                emitter.send(progress);
                emitter.complete();
                log.info("✅ SSE连接已完成并关闭 - clientId: {}", clientId);
            } catch (IOException e) {
                log.error("完成SSE连接时出错 - clientId: {}, error: {}", clientId, e.getMessage());
            } finally {
                emitters.remove(clientId);
            }
        } else {
            log.warn("⚠️ 未找到SSE连接，无法完成 - clientId: {}", clientId);
        }
    }
}

