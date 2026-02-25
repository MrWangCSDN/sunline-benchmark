package com.sunline.dict.service.impl;

import com.sunline.dict.entity.ServiceTypeFile;
import com.sunline.dict.mapper.ServiceTypeFileMapper;
import com.sunline.dict.service.ServiceTypeScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * ServiceType扫描服务实现类
 */
@Service
public class ServiceTypeScanServiceImpl implements ServiceTypeScanService {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceTypeScanServiceImpl.class);
    
    @Autowired
    private ServiceTypeFileMapper serviceTypeFileMapper;
    
    // 扫描进度信息
    private volatile Map<String, Object> scanProgress = new ConcurrentHashMap<>();
    
    // 扫描取消标志
    private volatile boolean cancelFlag = false;
    
    // 线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // 临时目录（用于存放从Fat Jar提取的jar文件）
    private volatile File tempDirectory = null;
    
    // 上传的临时文件（用于处理完成后删除）
    private volatile File uploadedTempFile = null;
    
    // 上传的临时文件路径（用于在扫描完成后删除）
    private volatile String uploadedTempFilePath = null;
    
    // 支持的ServiceType文件扩展名
    private static final String[] SERVICE_TYPE_EXTENSIONS = {
        ".pbs.xml", ".pcs.xml", ".pbcb.xml", ".pbcp.xml", ".pbcc.xml",
        ".serviceType.xml", ".apsServiceType.xml", ".pbct.xml"
    };
    
    // 用于提取kind的正则表达式
    private static final Pattern KIND_PATTERN = Pattern.compile("\\.(pbs|pcs|pbcb|pbcp|pbcc|pbct|serviceType|apsServiceType)\\.xml$");
    
    /**
     * 获取临时目录路径
     * Windows环境：使用系统临时目录
     * Linux环境：使用 /home/cbs/benchmark/tmp
     */
    private File getTempDirectory() {
        String osName = System.getProperty("os.name").toLowerCase();
        File tempDir;
        
        if (osName.contains("linux")) {
            // Linux环境：使用指定目录
            tempDir = new File("/home/cbs/benchmark/tmp");
            // 如果目录不存在，创建它
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    log.warn("无法创建Linux临时目录：{}，将使用系统临时目录", tempDir.getAbsolutePath());
                    tempDir = new File(System.getProperty("java.io.tmpdir"));
                } else {
                    log.info("已创建Linux临时目录：{}", tempDir.getAbsolutePath());
                }
            }
        } else {
            // Windows或其他环境：使用系统临时目录
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        
        return tempDir;
    }
    
    @Override
    @Async
    public void scanServiceTypeFiles(String jarPath, List<String> jarNames) {
        log.info("开始扫描ServiceType文件，路径：{}, jar包列表：{}", jarPath, jarNames);
        
        // 初始化进度信息
        scanProgress.clear();
        scanProgress.put("status", "scanning");
        scanProgress.put("total", 0);
        scanProgress.put("current", 0);
        scanProgress.put("success", 0);
        scanProgress.put("failed", 0);
        scanProgress.put("message", "开始扫描...");
        scanProgress.put("details", new CopyOnWriteArrayList<>());
        cancelFlag = false;
        
        try {
            File inputPath = new File(jarPath);
            if (!inputPath.exists()) {
                throw new RuntimeException("jar路径不存在：" + jarPath);
            }
            
            // 如果传入的是单个文件（可能是Controller层保存的上传文件），记录其路径以便后续删除
            if (inputPath.isFile() && jarPath.endsWith(".jar")) {
                this.uploadedTempFilePath = jarPath;
            }
            
            Map<String, File> jarFileMap = new HashMap<>();
            
            // 判断输入的是jar文件还是目录
            if (inputPath.isFile() && jarPath.endsWith(".jar")) {
                // 输入的是Fat Jar文件
                jarFileMap = scanJarFromFatJar(inputPath, jarNames);
            } else if (inputPath.isDirectory()) {
                // 输入的是目录
                jarFileMap = scanJarFromDirectory(inputPath, jarNames);
            } else {
                throw new RuntimeException("不支持的路径类型：" + jarPath);
            }
            
            if (jarFileMap.isEmpty()) {
                throw new RuntimeException("未找到匹配的jar包");
            }
            
            scanProgress.put("total", jarFileMap.size());
            updateProgress("找到 " + jarFileMap.size() + " 个匹配的jar包，开始扫描...");
            
            // 使用线程池并发处理每个jar包
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            
            for (Map.Entry<String, File> entry : jarFileMap.entrySet()) {
                if (cancelFlag) {
                    log.info("扫描已取消");
                    break;
                }
                
                String jarName = entry.getKey();
                File jarFile = entry.getValue();
                
                Future<Map<String, Object>> future = executorService.submit(() -> {
                    try {
                        return scanSingleJar(jarName, jarFile);
                    } catch (Exception e) {
                        log.error("扫描jar包失败：{}", jarName, e);
                        Map<String, Object> result = new HashMap<>();
                        result.put("jarName", jarName);
                        result.put("success", false);
                        result.put("error", e.getMessage());
                        result.put("fileCount", 0);
                        result.put("recordCount", 0);
                        return result;
                    }
                });
                
                futures.add(future);
            }
            
            // 等待所有任务完成
            int totalFiles = 0;
            int totalRecords = 0;
            int successCount = 0;
            int failedCount = 0;
            
            for (Future<Map<String, Object>> future : futures) {
                try {
                    Map<String, Object> result = future.get();
                    totalFiles += (Integer) result.getOrDefault("fileCount", 0);
                    totalRecords += (Integer) result.getOrDefault("recordCount", 0);
                    
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                } catch (Exception e) {
                    log.error("获取扫描结果失败", e);
                    failedCount++;
                }
                
                int current = (int) scanProgress.getOrDefault("current", 0);
                scanProgress.put("current", current + 1);
            }
            
            scanProgress.put("status", "completed");
            scanProgress.put("success", successCount);
            scanProgress.put("failed", failedCount);
            scanProgress.put("totalFiles", totalFiles);
            scanProgress.put("totalRecords", totalRecords);
            updateProgress("扫描完成！共处理 " + totalFiles + " 个文件，生成 " + totalRecords + " 条记录");
            
        } catch (Exception e) {
            log.error("扫描ServiceType文件失败", e);
            scanProgress.put("status", "error");
            scanProgress.put("message", "扫描失败：" + e.getMessage());
        } finally {
            // 清理临时目录
            cleanupTempDirectory();
            // 清理上传的临时文件
            cleanupUploadedTempFile();
        }
    }
    
    @Override
    @Async
    public void scanServiceTypeFilesFromUpload(MultipartFile jarFile, List<String> jarNames) {
        log.info("开始扫描上传的ServiceType文件，文件名：{}, jar包列表：{}", 
                jarFile.getOriginalFilename(), jarNames);
        
        File tempFile = null;
        try {
            // 保存上传的文件到临时目录
            String originalFilename = jarFile.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "uploaded.jar";
            }
            
            // 创建临时文件
            File baseTempDir = getTempDirectory();
            File tempDir = new File(baseTempDir, "service-type-upload-" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
            }
            
            tempFile = new File(tempDir, originalFilename);
            log.info("保存上传的文件到临时目录：{}", tempFile.getAbsolutePath());
            
            // 使用transferTo方法直接保存文件（避免异步时临时文件被清理的问题）
            jarFile.transferTo(tempFile);
            log.info("文件保存成功，大小：{} bytes", tempFile.length());
            
            // 保存临时文件引用，用于后续清理
            this.uploadedTempFile = tempFile;
            
            // 使用保存的临时文件路径调用scanServiceTypeFiles方法
            scanServiceTypeFiles(tempFile.getAbsolutePath(), jarNames);
            
        } catch (Exception e) {
            log.error("处理上传的ServiceType文件失败", e);
            scanProgress.put("status", "error");
            scanProgress.put("message", "处理上传的jar包失败: " + e.getMessage());
        } finally {
            // 删除上传的临时文件
            cleanupUploadedFile();
        }
    }
    
    /**
     * 清理上传的临时文件
     */
    private void cleanupUploadedFile() {
        if (uploadedTempFile != null) {
            try {
                // 删除文件
                if (uploadedTempFile.exists() && uploadedTempFile.delete()) {
                    log.info("已删除上传的临时文件：{}", uploadedTempFile.getAbsolutePath());
                }
                
                // 删除父目录（如果为空）
                File parentDir = uploadedTempFile.getParentFile();
                if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                    File[] files = parentDir.listFiles();
                    if (files == null || files.length == 0) {
                        if (parentDir.delete()) {
                            log.info("已删除临时目录：{}", parentDir.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("清理上传的临时文件失败：{}", uploadedTempFile.getAbsolutePath(), e);
            } finally {
                uploadedTempFile = null;
            }
        }
    }
    
    /**
     * 从Fat Jar中扫描指定的jar包
     */
    private Map<String, File> scanJarFromFatJar(File fatJarFile, List<String> jarNames) throws Exception {
        Map<String, File> jarFileMap = new HashMap<>();
        
        File baseTempDir = getTempDirectory();
        File tempDir = new File(baseTempDir, "service-type-scan-" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
        }
        
        this.tempDirectory = tempDir;
        
        try (JarFile fatJar = new JarFile(fatJarFile)) {
            Enumeration<JarEntry> entries = fatJar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 查找BOOT-INF/lib/或WEB-INF/lib/目录下的jar包
                if ((entryName.startsWith("BOOT-INF/lib/") || entryName.startsWith("WEB-INF/lib/")) 
                    && entryName.endsWith(".jar")) {
                    
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    
                    // 检查是否匹配指定的jar包（支持忽略版本号）
                    for (String jarName : jarNames) {
                        // 匹配逻辑：文件名以jarName开头（忽略版本号）
                        // 例如：sett-pbs-api-sit-1.0.0-SNAPSHOT.jar 匹配 sett-pbs-api
                        if (fileName.startsWith(jarName + "-") || fileName.startsWith(jarName + ".") || fileName.equals(jarName + ".jar")) {
                            // 提取jar文件到临时目录
                            File targetFile = new File(tempDir, fileName);
                            try (InputStream is = fatJar.getInputStream(entry);
                                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            jarFileMap.put(jarName, targetFile);
                            log.info("从Fat Jar中提取jar：{} (匹配: {}) -> {}", entryName, jarName, targetFile.getAbsolutePath());
                            break;
                        }
                    }
                }
            }
        }
        
        return jarFileMap;
    }
    
    /**
     * 从目录中扫描指定的jar包
     */
    private Map<String, File> scanJarFromDirectory(File directory, List<String> jarNames) {
        Map<String, File> jarFileMap = new HashMap<>();
        
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            return jarFileMap;
        }
        
        for (File file : files) {
            String fileName = file.getName();
            for (String jarName : jarNames) {
                // 匹配逻辑：文件名以jarName开头（忽略版本号）
                if ((fileName.startsWith(jarName + "-") || fileName.startsWith(jarName + ".") || fileName.equals(jarName + ".jar")) 
                    && fileName.endsWith(".jar")) {
                    jarFileMap.put(jarName, file);
                    log.info("找到jar包：{} (匹配: {})", file.getAbsolutePath(), jarName);
                    break;
                }
            }
        }
        
        return jarFileMap;
    }
    
    /**
     * 扫描单个jar包
     */
    private Map<String, Object> scanSingleJar(String jarName, File jarFile) throws Exception {
        log.info("开始扫描jar包：{}", jarName);
        
        Map<String, Object> result = new HashMap<>();
        result.put("jarName", jarName);
        result.put("success", true);
        
        int fileCount = 0;
        int recordCount = 0;
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            int totalEntries = 0;
            int checkedEntries = 0;
            
            while (entries.hasMoreElements()) {
                if (cancelFlag) {
                    break;
                }
                
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                totalEntries++;
                
                // 跳过目录和META-INF等系统文件夹
                if (entry.isDirectory() || entryName.startsWith("META-INF/")) {
                    continue;
                }
                
                // 提取文件名
                String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                if (fileName.contains("\\")) {
                    fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                }
                
                // 检查是否是支持的ServiceType文件类型（不限制路径，只要文件名匹配就处理）
                boolean isServiceTypeFile = false;
                for (String ext : SERVICE_TYPE_EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        isServiceTypeFile = true;
                        checkedEntries++;
                        break;
                    }
                }
                
                if (isServiceTypeFile) {
                    fileCount++;
                    log.info("找到ServiceType文件：{} (完整路径: {})", fileName, entryName);
                    try (InputStream is = jar.getInputStream(entry)) {
                        int records = parseServiceTypeXml(is, fileName, jarName, entryName);
                        recordCount += records;
                        log.info("解析文件：{}，生成 {} 条记录", fileName, records);
                    } catch (Exception e) {
                        log.error("解析文件失败：{}，路径：{}", fileName, entryName, e);
                    }
                }
            }
            
            log.info("jar包 {} 扫描统计：总条目={}，检查条目={}，匹配文件={}，生成记录={}", 
                    jarName, totalEntries, checkedEntries, fileCount, recordCount);
        }
        
        log.info("jar包 {} 扫描完成，找到 {} 个文件，生成 {} 条记录", jarName, fileCount, recordCount);
        
        result.put("fileCount", fileCount);
        result.put("recordCount", recordCount);
        updateProgress("完成扫描 " + jarName + "：处理 " + fileCount + " 个文件，生成 " + recordCount + " 条记录");
        
        return result;
    }
    
    /**
     * 解析ServiceType XML文件
     */
    @Transactional(rollbackFor = Exception.class)
    private int parseServiceTypeXml(InputStream is, String fileName, String jarName, String entryPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);
        
        Element root = doc.getDocumentElement();
        if (!"serviceType".equals(root.getTagName())) {
            log.warn("文件根节点不是serviceType：{}", fileName);
            return 0;
        }
        
        // 提取serviceType节点的属性
        String serviceTypeId = root.getAttribute("id");
        String serviceTypeLongName = root.getAttribute("longname");
        String serviceTypePackage = root.getAttribute("package");
        
        // 从文件名提取kind
        String serviceTypeKind = extractKindFromFileName(fileName);
        
        // 提取jar包名称（不含版本号）
        String serviceTypeFromJar = extractJarNameWithoutVersion(jarName);
        
        // 获取所有service节点
        NodeList serviceNodes = root.getElementsByTagName("service");
        int recordCount = 0;
        
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element serviceElement = (Element) serviceNodes.item(i);
            
            String serviceId = serviceElement.getAttribute("id");
            String serviceName = serviceElement.getAttribute("name");
            String serviceLongName = serviceElement.getAttribute("longname");
            
            // 创建ServiceTypeFile对象
            ServiceTypeFile serviceTypeFile = new ServiceTypeFile();
            serviceTypeFile.setServiceTypeId(serviceTypeId);
            serviceTypeFile.setServiceTypeLongName(serviceTypeLongName);
            serviceTypeFile.setServiceTypeKind(serviceTypeKind);
            serviceTypeFile.setServiceTypeFromJar(serviceTypeFromJar);
            serviceTypeFile.setServiceTypePackage(serviceTypePackage);
            serviceTypeFile.setServiceId(serviceId);
            serviceTypeFile.setServiceName(serviceName);
            serviceTypeFile.setServiceLongName(serviceLongName);
            serviceTypeFile.setCreateTime(LocalDateTime.now());
            serviceTypeFile.setUpdateTime(LocalDateTime.now());
            
            // 保存到数据库
            serviceTypeFileMapper.insert(serviceTypeFile);
            recordCount++;
        }
        
        log.debug("解析文件 {} 完成，生成 {} 条记录", fileName, recordCount);
        return recordCount;
    }
    
    /**
     * 从文件名提取kind（如：xxxx.pbcb.xml -> pbcb）
     */
    private String extractKindFromFileName(String fileName) {
        java.util.regex.Matcher matcher = KIND_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 如果是.serviceType.xml或.apsServiceType.xml，返回空字符串或特殊标记
        if (fileName.endsWith(".serviceType.xml") || fileName.endsWith(".apsServiceType.xml")) {
            return ""; // 或者返回 "serviceType"
        }
        
        return "";
    }
    
    /**
     * 提取jar包名称（不含版本号）
     */
    private String extractJarNameWithoutVersion(String jarName) {
        // 例如：sett-pbs-api-sit-1.0.0-SNAPSHOT.jar -> sett-pbs-api
        // 或者：sett-pbs-api.jar -> sett-pbs-api
        
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4);
        }
        
        // 查找最后一个"-"后面的数字或版本号模式，去掉版本号部分
        // 简单处理：找到最后一个数字开头的部分，去掉它
        int lastDashIndex = jarName.lastIndexOf("-");
        if (lastDashIndex > 0) {
            String afterLastDash = jarName.substring(lastDashIndex + 1);
            // 如果最后一部分是版本号（包含数字），则去掉
            if (afterLastDash.matches(".*\\d+.*")) {
                return jarName.substring(0, lastDashIndex);
            }
        }
        
        return jarName;
    }
    
    /**
     * 更新进度信息
     */
    private void updateProgress(String message) {
        scanProgress.put("message", message);
        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) scanProgress.get("details");
        if (details == null) {
            details = new CopyOnWriteArrayList<>();
            scanProgress.put("details", details);
        }
        details.add("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message);
    }
    
    @Override
    public Map<String, Object> getScanProgress() {
        return new HashMap<>(scanProgress);
    }
    
    @Override
    public void cancelScan() {
        cancelFlag = true;
        scanProgress.put("status", "cancelled");
        scanProgress.put("message", "扫描已取消");
        cleanupTempDirectory();
    }
    
    /**
     * 清理临时目录
     */
    private void cleanupTempDirectory() {
        if (tempDirectory != null && tempDirectory.exists()) {
            try {
                deleteDirectory(tempDirectory);
                log.info("已清理临时目录：{}", tempDirectory.getAbsolutePath());
                tempDirectory = null;
            } catch (Exception e) {
                log.error("清理临时目录失败：{}", tempDirectory.getAbsolutePath(), e);
            }
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            log.warn("无法删除文件：{}", directory.getAbsolutePath());
        }
    }
    
    /**
     * 清理上传的临时文件（Controller层保存的文件）
     */
    private void cleanupUploadedTempFile() {
        if (uploadedTempFilePath != null) {
            try {
                File uploadedFile = new File(uploadedTempFilePath);
                if (uploadedFile.exists()) {
                    // 删除文件
                    if (uploadedFile.delete()) {
                        log.info("已删除上传的临时文件：{}", uploadedTempFilePath);
                    }
                    
                    // 删除父目录（如果为空）
                    File parentDir = uploadedFile.getParentFile();
                    if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                        File[] files = parentDir.listFiles();
                        if (files == null || files.length == 0) {
                            if (parentDir.delete()) {
                                log.info("已删除临时目录：{}", parentDir.getAbsolutePath());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("清理上传的临时文件失败：{}", uploadedTempFilePath, e);
            } finally {
                uploadedTempFilePath = null;
            }
        }
    }
}

