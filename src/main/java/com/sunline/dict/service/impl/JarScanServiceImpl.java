package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.dto.ServiceTypeFileInfo;
import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.JarScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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

/**
 * Jar包扫描服务实现类
 */
@Service
public class JarScanServiceImpl implements JarScanService {
    
    private static final Logger log = LoggerFactory.getLogger(JarScanServiceImpl.class);
    
    @Autowired
    private FlowtranMapper flowtranMapper;
    
    @Autowired
    private FlowStepMapper flowStepMapper;
    
    // 扫描进度信息
    private volatile Map<String, Object> scanProgress = new ConcurrentHashMap<>();
    
    // 扫描取消标志
    private volatile boolean cancelFlag = false;
    
    // 线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // 临时目录（用于存放从Fat Jar提取的jar文件）
    private volatile File tempDirectory = null;
    
    // 上传的临时文件（用于处理完成后删除）
    private volatile File uploadedTempFile = null;
    
    // 上传的临时文件路径（用于在扫描完成后删除）
    private volatile String uploadedTempFilePath = null;
    
    // ServiceType文件缓存：key为文件名（不含扩展名），value为文件信息
    private static final Map<String, ServiceTypeFileInfo> serviceTypeFileCache = new ConcurrentHashMap<>();
    
    // 需要扫描的API jar包列表（用于缓存ServiceType文件）
    private static final String[] API_JAR_NAMES = {
        "comm-pbs-api", "comm-pcs-api", "comm-pbcb-api", "comm-pbcp-api", "comm-pbcc-api",
        "dept-pbs-api", "dept-pcs-api", "dept-pbcb-api", "dept-pbcp-api",
        "loan-pbs-api", "loan-pcs-api", "loan-pbcb-api", "loan-pbcp-api",
        "sett-pbs-api", "sett-pcs-api", "sett-pbcb-api", "sett-pbcp-api"
    };
    
    // 支持的ServiceType文件扩展名
    private static final String[] SERVICE_TYPE_EXTENSIONS = {
        ".pbs.xml", ".pcs.xml", ".pbcb.xml", ".pbcp.xml", ".pbcc.xml",
        ".serviceType.xml", ".apsServiceType.xml", ".pbct.xml"
    };
    
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
    public void scanJar(String jarPath, List<String> jarNames, List<String> transactionIds) {
        log.info("开始扫描jar包，路径：{}, jar包列表：{}, 交易过滤：{}", jarPath, jarNames, transactionIds);
        
        // 先缓存ServiceType文件
        cacheServiceTypeFiles(jarPath);
        
        cancelFlag = false;
        tempDirectory = null;
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 初始化进度信息
            scanProgress.put("status", "scanning");
            scanProgress.put("total", jarNames.size());
            scanProgress.put("current", 0);
            scanProgress.put("details", new ArrayList<>());
            
            Map<String, File> jarFileMap = new HashMap<>();
            
            // 处理路径（兼容Windows和Linux路径）
            // Windows路径可能包含反斜杠，需要规范化
            String normalizedPath = jarPath.trim().replace("\\", "/");
            log.info("原始路径：{}", jarPath);
            log.info("规范化路径：{}", normalizedPath);
            
            File inputPath = new File(normalizedPath);
            log.info("文件对象路径：{}", inputPath.getAbsolutePath());
            log.info("文件是否存在：{}", inputPath.exists());
            log.info("是否是文件：{}", inputPath.isFile());
            log.info("是否是目录：{}", inputPath.isDirectory());
            
            if (!inputPath.exists()) {
                // 如果规范化后的路径不存在，尝试使用原始路径
                inputPath = new File(jarPath);
                log.info("尝试原始路径：{}", inputPath.getAbsolutePath());
                log.info("原始路径是否存在：{}", inputPath.exists());
                
                if (!inputPath.exists()) {
                    throw new RuntimeException("路径不存在：" + jarPath + " (规范化后：" + normalizedPath + ")");
                }
            }
            
            // 如果传入的是单个文件（可能是Controller层保存的上传文件），记录其路径以便后续删除
            if (inputPath.isFile() && jarPath.endsWith(".jar")) {
                this.uploadedTempFilePath = jarPath;
            }
            
            // 判断输入的是jar文件还是目录
            if (inputPath.isFile()) {
                // 输入的是Fat Jar文件，读取其中的BOOT-INF/lib/目录
                log.info("检测到输入的是jar文件，将读取其中的lib目录");
                jarFileMap = scanJarFromFatJar(inputPath, jarNames);
            } else if (inputPath.isDirectory()) {
                // 输入的是目录，直接读取目录下的jar文件
                log.info("检测到输入的是目录，将读取目录下的jar文件");
                jarFileMap = scanJarFromDirectory(inputPath, jarNames);
            } else {
                throw new RuntimeException("不支持的路径类型，请输入Fat Jar文件路径或lib目录路径");
            }
            
            if (jarFileMap.isEmpty()) {
                throw new RuntimeException("未找到匹配的jar包");
            }
            
            // 多线程扫描jar包
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Map.Entry<String, File> entry : jarFileMap.entrySet()) {
                if (cancelFlag) {
                    break;
                }
                String jarName = entry.getKey();
                File jarFile = entry.getValue();
                Future<Map<String, Object>> future = executorService.submit(() -> scanSingleJar(jarName, jarFile, transactionIds));
                futures.add(future);
            }
            
            // 等待所有线程完成
            List<Map<String, Object>> details = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            for (Future<Map<String, Object>> future : futures) {
                if (cancelFlag) {
                    break;
                }
                try {
                    Map<String, Object> detail = future.get();
                    details.add(detail);
                    if ("success".equals(detail.get("status"))) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("获取扫描结果失败", e);
                    failCount++;
                }
            }
            
            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("details", details);
            
            // 更新进度信息
            scanProgress.put("status", cancelFlag ? "cancelled" : "completed");
            scanProgress.put("result", result);
            
        } catch (Exception e) {
            log.error("扫描jar包失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            scanProgress.put("status", "error");
            scanProgress.put("error", e.getMessage());
        } finally {
            // 清理临时目录
            cleanupTempDirectory();
            // 清理上传的临时文件
            cleanupUploadedTempFile();
        }
    }
    
    @Override
    @Async
    public void scanJarFromUpload(MultipartFile jarFile, List<String> jarNames, List<String> transactionIds) {
        log.info("开始扫描上传的jar包，文件名：{}, jar包列表：{}, 交易过滤：{}", 
                jarFile.getOriginalFilename(), jarNames, transactionIds);
        
        File tempFile = null;
        try {
            // 保存上传的文件到临时目录
            String originalFilename = jarFile.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "uploaded.jar";
            }
            
            // 创建临时文件
            File baseTempDir = getTempDirectory();
            File tempDir = new File(baseTempDir, "jar-upload-" + System.currentTimeMillis());
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
            
            // 使用保存的临时文件路径调用scanJar方法
            scanJar(tempFile.getAbsolutePath(), jarNames, transactionIds);
            
        } catch (Exception e) {
            log.error("处理上传的jar包失败", e);
            scanProgress.put("status", "error");
            scanProgress.put("error", "处理上传的jar包失败: " + e.getMessage());
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
     * 从Fat Jar文件中扫描jar包
     */
    private Map<String, File> scanJarFromFatJar(File fatJarFile, List<String> jarNames) throws Exception {
        Map<String, File> jarFileMap = new HashMap<>();
        
        // 创建临时目录存放提取的jar文件
        File baseTempDir = getTempDirectory();
        File tempDir = new File(baseTempDir, "jar-scan-" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
        }
        
        // 保存临时目录引用，用于清理
        this.tempDirectory = tempDir;
        log.info("创建临时目录：{}", tempDir.getAbsolutePath());
        
        try (JarFile fatJar = new JarFile(fatJarFile)) {
            Enumeration<JarEntry> entries = fatJar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 查找BOOT-INF/lib/目录下的jar文件
                if ((entryName.startsWith("BOOT-INF/lib/") || entryName.startsWith("WEB-INF/lib/")) 
                    && entryName.endsWith(".jar")) {
                    
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    
                    // 检查是否匹配要扫描的jar包
                    for (String jarName : jarNames) {
                        if (fileName.startsWith(jarName)) {
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
                            log.info("从Fat Jar中提取：{} -> {}", entryName, targetFile.getAbsolutePath());
                            break;
                        }
                    }
                }
            }
        }
        
        if (jarFileMap.isEmpty()) {
            throw new RuntimeException("在Fat Jar中未找到匹配的jar包");
        }
        
        return jarFileMap;
    }
    
    /**
     * 从目录中扫描jar包
     */
    private Map<String, File> scanJarFromDirectory(File directory, List<String> jarNames) {
        Map<String, File> jarFileMap = new HashMap<>();
        
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            throw new RuntimeException("目录中未找到jar包文件");
        }
        
        for (File file : files) {
            String fileName = file.getName();
            for (String jarName : jarNames) {
                // 忽略版本号，只匹配jar包名称前缀
                if (fileName.startsWith(jarName) && fileName.endsWith(".jar")) {
                    jarFileMap.put(jarName, file);
                    log.info("找到jar包：{}", file.getAbsolutePath());
                    break;
                }
            }
        }
        
        if (jarFileMap.isEmpty()) {
            throw new RuntimeException("未找到匹配的jar包");
        }
        
        return jarFileMap;
    }
    
    /**
     * 扫描单个jar包
     */
    private Map<String, Object> scanSingleJar(String jarName, File jarFile, List<String> transactionIds) {
        Map<String, Object> result = new HashMap<>();
        result.put("jarName", jarName);
        
        int flowtranCount = 0;
        int flowStepCount = 0;
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements() && !cancelFlag) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 查找flowtrans.xml文件（注意：文件名是flowtrans，带s）
                if (entryName.endsWith(".flowtrans.xml") || entryName.endsWith("flowtrans.xml")) {
                    log.info("找到flowtrans.xml文件：{}", entryName);
                    
                    try (InputStream is = jar.getInputStream(entry)) {
                        Map<String, Object> parseResult = parseFlowtranXml(is, jarName, transactionIds);
                        int txnCount = (int) parseResult.getOrDefault("flowtranCount", 0);
                        int stepCount = (int) parseResult.getOrDefault("flowStepCount", 0);
                        flowtranCount += txnCount;
                        flowStepCount += stepCount;
                        log.info("解析XML文件：{}, 交易数：{}, 步骤数：{}, 累计交易：{}, 累计步骤：{}", 
                                 entryName, txnCount, stepCount, flowtranCount, flowStepCount);
                    } catch (Exception e) {
                        log.error("解析flowtrans.xml文件失败：{}", entryName, e);
                    }
                }
            }
            
            result.put("status", "success");
            result.put("flowtranCount", flowtranCount);
            result.put("flowStepCount", flowStepCount);
            result.put("message", "成功扫描 " + flowtranCount + " 个交易，" + flowStepCount + " 个步骤");
            
        } catch (Exception e) {
            log.error("扫描jar包失败：{}", jarName, e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        // 更新进度
        updateProgress(result);
        
        return result;
    }
    
    /**
     * 解析flowtrans.xml文件
     */
    @Transactional(rollbackFor = Exception.class)
    private Map<String, Object> parseFlowtranXml(InputStream is, String fromJar, List<String> transactionIds) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int flowtranCount = 0;
        int flowStepCount = 0;
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);
        
        Element root = doc.getDocumentElement();
        
        // 解析flowtran节点
        String id = root.getAttribute("id");
        String longname = root.getAttribute("longname");
        String packagePath = root.getAttribute("package");
        String txnMode = root.getAttribute("txnMode");
        
        log.debug("解析XML根节点：id={}, longname={}, package={}, txnMode={}", id, longname, packagePath, txnMode);
        
        if (id != null && !id.isEmpty()) {
            // 如果指定了交易过滤，检查当前交易是否在过滤列表中
            if (transactionIds != null && !transactionIds.isEmpty()) {
                if (!transactionIds.contains(id)) {
                    log.debug("交易ID {} 不在过滤列表中，跳过", id);
                    result.put("flowtranCount", 0);
                    result.put("flowStepCount", 0);
                    return result;
                }
            }
            // 保存或更新flowtran信息
            Flowtran flowtran = new Flowtran();
            flowtran.setId(id);
            flowtran.setLongname(longname);
            flowtran.setPackagePath(packagePath);
            // 只有当txnMode属性存在且不为空时才设置
            if (txnMode != null && !txnMode.isEmpty()) {
                flowtran.setTxnMode(txnMode);
            }
            flowtran.setFromJar(fromJar);
            flowtran.setCreateTime(LocalDateTime.now());
            flowtran.setUpdateTime(LocalDateTime.now());
            
            // 先删除已存在的记录
            flowtranMapper.deleteById(id);
            // 插入新记录
            flowtranMapper.insert(flowtran);
            flowtranCount++;
            log.info("成功保存交易：id={}, longname={}, flowtranCount={}", id, longname, flowtranCount);
            
            // 删除该交易的所有步骤
            QueryWrapper<FlowStep> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("flow_id", id);
            flowStepMapper.delete(deleteWrapper);
            
            // 解析flow节点（递归扫描所有嵌套的service和method）
            NodeList flowNodes = root.getElementsByTagName("flow");
            if (flowNodes.getLength() > 0) {
                Element flowElement = (Element) flowNodes.item(0);
                
                // 使用递归方式扫描所有嵌套的service和method节点
                List<Element> stepElements = new ArrayList<>();
                collectServiceAndMethodNodes(flowElement, stepElements);
                
                log.info("交易ID: {}, 找到 {} 个流程步骤节点（包括嵌套的）", id, stepElements.size());
                
                int step = 1;
                for (Element element : stepElements) {
                    String tagName = element.getTagName();
                    
                    FlowStep flowStep = new FlowStep();
                    flowStep.setFlowId(id);
                    flowStep.setNodeType(tagName);
                    flowStep.setStep(step++);
                    flowStep.setCreateTime(LocalDateTime.now());
                    flowStep.setUpdateTime(LocalDateTime.now());
                    
                    if ("service".equals(tagName)) {
                        // service节点：node_name记录serviceName属性
                        String serviceName = element.getAttribute("serviceName");
                        String nodeLongname = element.getAttribute("longname");
                        
                        if (serviceName == null || serviceName.trim().isEmpty()) {
                            log.warn("service节点缺少serviceName属性，flowId: {}, step: {}", id, step - 1);
                            serviceName = ""; // 设置为空字符串，避免null
                        }
                        
                        flowStep.setNodeName(serviceName);
                        flowStep.setNodeLongname(nodeLongname);
                        
                        log.debug("  步骤 {}: service - serviceName={}, longname={}", step - 1, serviceName, nodeLongname);
                    } else if ("method".equals(tagName)) {
                        // method节点：node_name记录method属性
                        String methodName = element.getAttribute("method");
                        String nodeLongname = element.getAttribute("longname");
                        
                        if (methodName == null || methodName.trim().isEmpty()) {
                            log.warn("method节点缺少method属性，flowId: {}, step: {}", id, step - 1);
                            methodName = ""; // 设置为空字符串，避免null
                        }
                        
                        flowStep.setNodeName(methodName);
                        flowStep.setNodeLongname(nodeLongname);
                        
                        log.debug("  步骤 {}: method - method={}, longname={}", step - 1, methodName, nodeLongname);
                    }
                    
                    flowStepMapper.insert(flowStep);
                    flowStepCount++;
                }
            }
        }
        
        result.put("flowtranCount", flowtranCount);
        result.put("flowStepCount", flowStepCount);
        
        log.info("parseFlowtranXml完成，返回：flowtranCount={}, flowStepCount={}", flowtranCount, flowStepCount);
        
        return result;
    }
    
    /**
     * 递归收集所有的service和method节点（包括嵌套的）
     * 支持嵌套在 block、case、when、otherwise、if、else、loop、foreach 等节点中的 service/method
     * @param parentElement 父节点
     * @param resultList 结果列表
     */
    private void collectServiceAndMethodNodes(Element parentElement, List<Element> resultList) {
        NodeList children = parentElement.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                // 如果是service或method节点，直接添加到结果列表
                if ("service".equals(tagName) || "method".equals(tagName)) {
                    String nodeName = "service".equals(tagName) 
                        ? element.getAttribute("serviceName") 
                        : element.getAttribute("method");
                    String nodeLongname = element.getAttribute("longname");
                    log.debug("    发现{}节点: {} (longname: {})", tagName, nodeName, nodeLongname);
                    resultList.add(element);
                }
                // 如果是需要深入查找的容器节点，继续递归
                else if (isContainerNode(tagName)) {
                    String containerInfo = getContainerNodeInfo(element, tagName);
                    log.debug("    进入{}节点: {}", tagName, containerInfo);
                    
                    // 递归扫描容器节点内的所有子节点
                    collectServiceAndMethodNodes(element, resultList);
                }
                // 其他节点也继续递归（确保不遗漏任何嵌套的service/method）
                else {
                    collectServiceAndMethodNodes(element, resultList);
                }
            }
        }
    }
    
    /**
     * 判断是否是容器节点（需要深入查找service/method的节点）
     */
    private boolean isContainerNode(String tagName) {
        // 常见的容器节点类型
        return "block".equals(tagName) || 
               "case".equals(tagName) || 
               "when".equals(tagName) || 
               "otherwise".equals(tagName) ||
               "if".equals(tagName) ||
               "else".equals(tagName) ||
               "loop".equals(tagName) ||
               "foreach".equals(tagName) ||
               "switch".equals(tagName) ||
               "choose".equals(tagName) ||
               "flow".equals(tagName); // flow节点本身也可能嵌套
    }
    
    /**
     * 获取容器节点的信息（用于日志）
     */
    private String getContainerNodeInfo(Element element, String tagName) {
        StringBuilder info = new StringBuilder();
        
        // block节点：显示id和test属性
        if ("block".equals(tagName)) {
            String id = element.getAttribute("id");
            String test = element.getAttribute("test");
            if (!id.isEmpty()) {
                info.append("id=").append(id);
            }
            if (!test.isEmpty()) {
                if (info.length() > 0) info.append(", ");
                String testShort = test.length() > 50 ? test.substring(0, 50) + "..." : test;
                info.append("test=").append(testShort);
            }
        }
        // case/when节点：显示test属性
        else if ("case".equals(tagName) || "when".equals(tagName)) {
            String test = element.getAttribute("test");
            if (!test.isEmpty()) {
                String testShort = test.length() > 50 ? test.substring(0, 50) + "..." : test;
                info.append("test=").append(testShort);
            }
        }
        // 其他节点：显示id属性
        else {
            String id = element.getAttribute("id");
            if (!id.isEmpty()) {
                info.append("id=").append(id);
            }
        }
        
        return info.length() > 0 ? info.toString() : "(无属性)";
    }
    
    /**
     * 更新扫描进度
     */
    private synchronized void updateProgress(Map<String, Object> detail) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) scanProgress.get("details");
        if (details == null) {
            details = new ArrayList<>();
            scanProgress.put("details", details);
        }
        details.add(detail);
        
        int current = (int) scanProgress.getOrDefault("current", 0);
        scanProgress.put("current", current + 1);
    }
    
    @Override
    public Map<String, Object> getScanProgress() {
        return new HashMap<>(scanProgress);
    }
    
    @Override
    public void cancelScan() {
        cancelFlag = true;
        scanProgress.put("status", "cancelled");
        // 清理临时目录
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
    
    /**
     * 缓存ServiceType文件
     * 扫描指定jar包中的servicetype文件夹下的所有xml文件
     */
    private void cacheServiceTypeFiles(String jarPath) {
        log.info("开始缓存ServiceType文件，jar路径：{}", jarPath);
        
        // 清空之前的缓存
        serviceTypeFileCache.clear();
        
        // 处理路径（兼容Windows和Linux路径）
        String normalizedPath = jarPath.trim().replace("\\", "/");
        File inputPath = new File(normalizedPath);
        
        // 如果规范化路径不存在，尝试原始路径
        if (!inputPath.exists()) {
            inputPath = new File(jarPath);
        }
        
        if (!inputPath.exists()) {
            log.warn("jar路径不存在，跳过ServiceType文件缓存：{}", jarPath);
            return;
        }
        
        Map<String, File> jarFileMap = new HashMap<>();
        
        // 判断输入的是jar文件还是目录
        if (inputPath.isFile()) {
            // 输入的是Fat Jar文件
            log.info("检测到jar文件：{}", inputPath.getAbsolutePath());
            try {
                jarFileMap = scanJarFromFatJarForCache(inputPath);
            } catch (Exception e) {
                log.error("从Fat Jar中扫描ServiceType文件失败", e);
                return;
            }
        } else if (inputPath.isDirectory()) {
            // 输入的是目录
            log.info("检测到目录：{}", inputPath.getAbsolutePath());
            jarFileMap = scanJarFromDirectoryForCache(inputPath);
        } else {
            log.warn("不支持的路径类型，跳过ServiceType文件缓存：{}", jarPath);
            return;
        }
        
        if (jarFileMap.isEmpty()) {
            log.warn("未找到匹配的API jar包，跳过ServiceType文件缓存");
            return;
        }
        
        // 扫描每个jar包中的servicetype文件
        int totalCached = 0;
        for (Map.Entry<String, File> entry : jarFileMap.entrySet()) {
            String jarName = entry.getKey();
            File jarFile = entry.getValue();
            
            try {
                int count = cacheServiceTypeFilesFromJar(jarFile, jarName);
                totalCached += count;
                log.info("从jar包 {} 中缓存了 {} 个ServiceType文件", jarName, count);
            } catch (Exception e) {
                log.error("缓存jar包 {} 中的ServiceType文件失败", jarName, e);
            }
        }
        
        log.info("ServiceType文件缓存完成，共缓存 {} 个文件", totalCached);
    }
    
    /**
     * 从Fat Jar中扫描API jar包（用于缓存）
     */
    private Map<String, File> scanJarFromFatJarForCache(File fatJarFile) throws Exception {
        Map<String, File> jarFileMap = new HashMap<>();
        
        File baseTempDir = getTempDirectory();
        File tempDir = new File(baseTempDir, "jar-scan-cache-" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
        }
        
        this.tempDirectory = tempDir;
        
        try (JarFile fatJar = new JarFile(fatJarFile)) {
            Enumeration<JarEntry> entries = fatJar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 查找BOOT-INF/lib/目录下的API jar包
                if ((entryName.startsWith("BOOT-INF/lib/") || entryName.startsWith("WEB-INF/lib/")) 
                    && entryName.endsWith(".jar")) {
                    
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    
                    // 检查是否匹配API jar包
                    for (String apiJarName : API_JAR_NAMES) {
                        if (fileName.startsWith(apiJarName)) {
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
                            jarFileMap.put(apiJarName, targetFile);
                            log.debug("从Fat Jar中提取API jar：{} -> {}", entryName, targetFile.getAbsolutePath());
                            break;
                        }
                    }
                }
            }
        }
        
        return jarFileMap;
    }
    
    /**
     * 从目录中扫描API jar包（用于缓存）
     */
    private Map<String, File> scanJarFromDirectoryForCache(File directory) {
        Map<String, File> jarFileMap = new HashMap<>();
        
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            return jarFileMap;
        }
        
        for (File file : files) {
            String fileName = file.getName();
            for (String apiJarName : API_JAR_NAMES) {
                if (fileName.startsWith(apiJarName) && fileName.endsWith(".jar")) {
                    jarFileMap.put(apiJarName, file);
                    log.debug("找到API jar包：{}", file.getAbsolutePath());
                    break;
                }
            }
        }
        
        return jarFileMap;
    }
    
    /**
     * 从单个jar包中缓存ServiceType文件
     */
    private int cacheServiceTypeFilesFromJar(File jarFile, String jarName) throws Exception {
        int count = 0;
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 查找servicetype文件夹下的xml文件
                if (entryName.contains("/servicetype/") || entryName.contains("\\servicetype\\")) {
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    if (fileName.contains("\\")) {
                        fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                    }
                    
                    // 检查是否是支持的ServiceType文件类型
                    boolean isServiceTypeFile = false;
                    for (String ext : SERVICE_TYPE_EXTENSIONS) {
                        if (fileName.endsWith(ext)) {
                            isServiceTypeFile = true;
                            break;
                        }
                    }
                    
                    if (isServiceTypeFile) {
                        // 提取文件名（不含扩展名）作为key
                        String fileKey = extractFileKey(fileName);
                        
                        // 提取文件路径（servicetype下的相对路径）
                        String filePath = extractServiceTypePath(entryName);
                        
                        // 缓存文件信息
                        ServiceTypeFileInfo fileInfo = new ServiceTypeFileInfo(fileName, filePath, jarName);
                        serviceTypeFileCache.put(fileKey, fileInfo);
                        count++;
                        
                        log.debug("缓存ServiceType文件：key={}, fileName={}, path={}, jar={}", 
                                 fileKey, fileName, filePath, jarName);
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * 提取文件key（文件名不含扩展名）
     */
    private String extractFileKey(String fileName) {
        // 去掉所有支持的扩展名
        for (String ext : SERVICE_TYPE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        // 如果没有匹配的扩展名，返回原文件名（去掉.xml）
        if (fileName.endsWith(".xml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
    
    /**
     * 提取servicetype下的相对路径
     */
    private String extractServiceTypePath(String entryName) {
        // 查找servicetype文件夹的位置
        int serviceTypeIndex = entryName.indexOf("/servicetype/");
        if (serviceTypeIndex == -1) {
            serviceTypeIndex = entryName.indexOf("\\servicetype\\");
        }
        
        if (serviceTypeIndex != -1) {
            // 提取servicetype/后面的路径
            String path = entryName.substring(serviceTypeIndex + "/servicetype/".length());
            // 统一使用正斜杠
            return path.replace("\\", "/");
        }
        
        // 如果找不到servicetype，返回文件名
        return entryName.substring(entryName.lastIndexOf("/") + 1);
    }
    
    /**
     * 根据node_name匹配ServiceType文件
     */
}

