package com.sunline.dict.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 编译进度实时推送接口（SSE）
 *
 * <p>用法：
 * GET /api/build/progress/{projectName}
 * 前端通过 EventSource 监听，实时接收编译输出行。
 *
 * <p>前端示例：
 * <pre>
 * const es = new EventSource('/api/build/progress/ccbs-comm-api');
 * es.onmessage = e => console.log(e.data);
 * es.addEventListener('done', e => { console.log('编译完成', e.data); es.close(); });
 * es.addEventListener('error_event', e => { console.log('编译失败', e.data); es.close(); });
 * </pre>
 */
@RestController
@RequestMapping("/api/build")
public class BuildProgressController {

    private static final Logger log = LoggerFactory.getLogger(BuildProgressController.class);

    @Value("${build.base-path:/home/cbs/code}")
    private String basePath;

    @Value("${build.mvn-home:}")
    private String mvnHome;

    @Value("${build.settings-file:}")
    private String settingsFile;

    @Value("${build.build-timeout-minutes:30}")
    private int buildTimeoutMinutes;

    private final AtomicBoolean building = new AtomicBoolean(false);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 实时编译单个工程，通过 SSE 推送每行输出
     * GET /api/build/progress/{name}
     */
    @GetMapping(value = "/progress/{name}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter buildWithProgress(@PathVariable String name) {
        // 超时设置为编译超时 + 5分钟缓冲
        SseEmitter emitter = new SseEmitter((long) (buildTimeoutMinutes + 5) * 60 * 1000);

        if (!building.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event().name("error_event").data("当前有编译任务正在进行，请稍后再试"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        executor.submit(() -> {
            String projectDir = basePath + File.separator + name;
            File dir = new File(projectDir);

            try {
                if (!dir.exists() || !dir.isDirectory()) {
                    emitter.send(SseEmitter.event().name("error_event").data("工程目录不存在：" + projectDir));
                    emitter.complete();
                    return;
                }

                // 发送开始信息
                emitter.send(SseEmitter.event().name("start").data(
                        "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] 开始编译：" + name));

                ProcessBuilder pb = new ProcessBuilder(buildMvnArgs("clean", "install", "-DskipTests"));
                pb.directory(dir);
                pb.redirectErrorStream(true);
                pb.environment().put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");

                long startMs = System.currentTimeMillis();
                Process process = pb.start();

                // 实时读取并推送每行输出
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 过滤掉噪音行，只推关键进度
                        if (isProgressLine(line)) {
                            String formatted = formatLine(name, line);
                            emitter.send(SseEmitter.event()
                                    .name("progress")
                                    .data((Object) formatted));
                        }
                    }
                }

                process.waitFor();
                int exitCode = process.exitValue();
                long costSeconds = (System.currentTimeMillis() - startMs) / 1000;

                if (exitCode == 0) {
                    emitter.send(SseEmitter.event().name("done")
                            .data("✓ 编译成功：" + name + "（耗时 " + costSeconds + "s）"));
                } else {
                    emitter.send(SseEmitter.event().name("error_event")
                            .data("✗ 编译失败：" + name + "（exit=" + exitCode + "，耗时 " + costSeconds + "s）"));
                }

                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error_event").data("编译异常：" + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
                log.error("编译进度推送异常：{}", name, e);
            } finally {
                building.set(false);
            }
        });

        return emitter;
    }

    /**
     * 判断是否是值得推送的进度行（过滤掉 mvn 的噪音输出）
     */
    private boolean isProgressLine(String line) {
        if (line == null || line.trim().isEmpty()) return false;
        // 推送这些关键行
        return line.contains("[INFO] Building")       // 正在构建哪个模块
                || line.contains("[INFO] --- ")        // 插件执行阶段
                || line.contains("[INFO] BUILD SUCCESS")
                || line.contains("[INFO] BUILD FAILURE")
                || line.contains("[INFO] Total time")
                || line.contains("[ERROR]")
                || line.contains("[WARNING]")
                || line.startsWith("[INFO] Compiling")
                || line.contains("[INFO] Tests run");
    }

    private String formatLine(String projectName, String line) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "[" + time + "][" + projectName + "] " + line.trim();
    }

    private String buildMvnCmd() {
        if (mvnHome != null && !mvnHome.trim().isEmpty()) {
            return mvnHome + File.separator + "bin" + File.separator + "mvn";
        }
        return "mvn";
    }

    private java.util.List<String> buildMvnArgs(String... goals) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(buildMvnCmd());
        if (settingsFile != null && !settingsFile.trim().isEmpty()) {
            args.add("-s");
            args.add(settingsFile.trim());
        }
        for (String goal : goals) {
            args.add(goal);
        }
        return args;
    }
}
