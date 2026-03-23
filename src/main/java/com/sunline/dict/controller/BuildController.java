package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.BuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工程编译接口
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/build/all        - 串行编译所有工程（同步，等待结果返回）</li>
 *   <li>POST /api/build/all/async  - 后台异步编译所有工程（立即返回，结果看日志）</li>
 *   <li>POST /api/build/{name}     - 编译单个工程（同步）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/build")
public class BuildController {

    private static final Logger log = LoggerFactory.getLogger(BuildController.class);

    @Autowired
    private BuildService buildService;

    /**
     * 串行编译所有工程（同步等待，返回每个工程结果）
     * 工程较多时耗时较长，建议用 async 接口
     */
    @PostMapping("/all")
    public Result<List<Map<String, Object>>> buildAll() {
        log.info("触发全量编译（同步）");
        List<Map<String, Object>> results = buildService.buildAll();
        return Result.success(results);
    }

    /**
     * 后台异步编译所有工程，立即返回
     * 编译结果通过日志查看：/home/cbs/logs/build/
     */
    @PostMapping("/all/async")
    public Result<String> buildAllAsync() {
        log.info("触发全量编译（异步）");
        buildService.buildAllAsync();
        return Result.success("异步编译任务已启动，结果请查看日志：/home/cbs/logs/build/");
    }

    /**
     * 编译单个工程（同步）
     *
     * @param name 工程目录名，如 ccbs-comm-api
     */
    @PostMapping("/{name}")
    public Result<Map<String, Object>> buildOne(@PathVariable String name) {
        log.info("触发单个工程编译：{}", name);
        Map<String, Object> result = buildService.buildOne(name);
        return Result.success(result);
    }
}
