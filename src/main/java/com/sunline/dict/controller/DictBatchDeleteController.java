package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.DictBatchDeleteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 在途字典批量删除Controller
 */
@RestController
@RequestMapping("/api/dict-batch-delete")
@CrossOrigin
public class DictBatchDeleteController {
    
    private static final Logger log = LoggerFactory.getLogger(DictBatchDeleteController.class);
    
    @Autowired
    private DictBatchDeleteService dictBatchDeleteService;
    
    /**
     * 批量删除在途字典数据
     */
    @PostMapping("/delete")
    public Result<Map<String, Object>> batchDelete(@RequestParam("dictFile") MultipartFile dictFile) {
        try {
            // 验证文件
            if (dictFile == null || dictFile.isEmpty()) {
                return Result.error("请上传字典文件");
            }
            
            String originalFilename = dictFile.getOriginalFilename();
            if (originalFilename == null || 
                (!originalFilename.toLowerCase().endsWith(".xlsx") && 
                 !originalFilename.toLowerCase().endsWith(".xls"))) {
                return Result.error("请上传Excel文件（.xlsx 或 .xls）");
            }
            
            log.info("开始批量删除在途字典数据，文件：{}", originalFilename);
            
            // 执行批量删除
            Map<String, Object> result = dictBatchDeleteService.batchDelete(dictFile);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量删除在途字典数据失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}

