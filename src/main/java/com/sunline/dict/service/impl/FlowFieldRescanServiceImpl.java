package com.sunline.dict.service.impl;

import com.sunline.dict.service.FlowFieldRescanService;
import com.sunline.dict.service.FlowXmlParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class FlowFieldRescanServiceImpl implements FlowFieldRescanService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldRescanServiceImpl.class);

    @Autowired
    private FlowXmlParseService flowXmlParseService;

    /** 自注入用于绕过 @Async 同类调用代理失效问题 */
    @Autowired
    @Lazy
    private FlowFieldRescanServiceImpl self;

    /** 进度对象内存表，仅保留最近 10 个 operationId */
    private final Map<String, Progress> progressMap = new ConcurrentHashMap<>();

    /** 同时只允许一个 RUNNING */
    private volatile String runningOperationId = null;

    @Override
    public synchronized Map<String, Object> startRescan(String sourcePath) throws Exception {
        if (sourcePath == null || sourcePath.isEmpty()) {
            throw new IllegalArgumentException("sourcePath 不能为空");
        }
        Path root = Paths.get(sourcePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("sourcePath 不是有效目录: " + sourcePath);
        }
        if (runningOperationId != null) {
            Progress running = progressMap.get(runningOperationId);
            if (running != null && "RUNNING".equals(running.status)) {
                throw new IllegalStateException("已有重扫任务运行中: operationId=" + runningOperationId);
            }
        }

        // 列出全部 .flowtrans.xml
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".flowtrans.xml"))
                        .toList();
        }

        String operationId = UUID.randomUUID().toString();
        Progress p = new Progress();
        p.operationId = operationId;
        p.total = files.size();
        p.processed = new AtomicInteger(0);
        p.status = "RUNNING";
        p.startTime = LocalDateTime.now();
        p.errors = Collections.synchronizedList(new ArrayList<>());

        progressMap.put(operationId, p);
        runningOperationId = operationId;
        trimProgressMap();

        // 异步执行（通过自注入代理触发 @Async）
        self.runAsync(operationId, files);

        Map<String, Object> ret = new HashMap<>();
        ret.put("operationId", operationId);
        ret.put("totalFiles", files.size());
        ret.put("status", "RUNNING");
        return ret;
    }

    @Async
    public void runAsync(String operationId, List<Path> files) {
        Progress p = progressMap.get(operationId);
        try {
            for (Path file : files) {
                p.current = file.toString();
                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                    flowXmlParseService.parseAndSave(fis, file.toString());
                } catch (Exception e) {
                    String msg = file.getFileName() + ": " + e.getMessage();
                    log.warn("rescan 解析失败 {}", msg);
                    if (p.errors.size() < 100) p.errors.add(msg);
                }
                p.processed.incrementAndGet();
            }
            p.status = "COMPLETED";
        } catch (Exception e) {
            log.error("rescan 异常", e);
            p.status = "FAILED";
            p.errors.add("FATAL: " + e.getMessage());
        } finally {
            p.endTime = LocalDateTime.now();
            if (operationId.equals(runningOperationId)) runningOperationId = null;
        }
    }

    @Override
    public Map<String, Integer> rescanOne(String flowId, String sourcePath) throws Exception {
        Path root = Paths.get(sourcePath);
        Path target;
        try (Stream<Path> walk = Files.walk(root)) {
            target = walk.filter(Files::isRegularFile)
                         .filter(p -> p.getFileName().toString().equals(flowId + ".flowtrans.xml"))
                         .findFirst()
                         .orElseThrow(() -> new IllegalArgumentException(
                                 "未找到 " + flowId + ".flowtrans.xml in " + sourcePath));
        }
        try (FileInputStream fis = new FileInputStream(target.toFile())) {
            Map<String, Object> result = flowXmlParseService.parseAndSave(fis, target.toString());
            Map<String, Integer> ret = new HashMap<>();
            ret.put("inputCount",  toInt(result.get("inputFieldCount")));
            ret.put("outputCount", toInt(result.get("outputFieldCount")));
            return ret;
        }
    }

    @Override
    public Map<String, Object> getProgress(String operationId) {
        Progress p = progressMap.get(operationId);
        if (p == null) {
            Map<String, Object> ret = new HashMap<>();
            ret.put("operationId", operationId);
            ret.put("status", "UNKNOWN");
            return ret;
        }
        Map<String, Object> ret = new HashMap<>();
        ret.put("operationId", p.operationId);
        ret.put("total", p.total);
        ret.put("processed", p.processed.get());
        ret.put("current", p.current);
        ret.put("status", p.status);
        ret.put("errors", new ArrayList<>(p.errors));
        ret.put("startTime", p.startTime);
        ret.put("endTime", p.endTime);
        return ret;
    }

    private void trimProgressMap() {
        if (progressMap.size() <= 10) return;
        progressMap.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().startTime))
                .limit(progressMap.size() - 10)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(progressMap::remove);
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private static class Progress {
        String operationId;
        int total;
        AtomicInteger processed;
        volatile String current;
        volatile String status;          // RUNNING | COMPLETED | FAILED
        List<String> errors;
        LocalDateTime startTime;
        volatile LocalDateTime endTime;
    }
}
