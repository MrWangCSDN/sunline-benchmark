package com.sunline.dict.service.impl;

import com.sunline.dict.entity.Component;
import com.sunline.dict.entity.ComponentDetail;
import com.sunline.dict.entity.HardCodeMethodStack;
import com.sunline.dict.entity.ServiceDetail;
import com.sunline.dict.entity.ServiceFile;
import com.sunline.dict.entity.ServiceImplFile;
import com.sunline.dict.mapper.ComponentDetailMapper;
import com.sunline.dict.mapper.ComponentMapper;
import com.sunline.dict.mapper.HardCodeMethodStackMapper;
import com.sunline.dict.mapper.ServiceDetailMapper;
import com.sunline.dict.mapper.ServiceFileMapper;
import com.sunline.dict.mapper.ServiceImplFileMapper;
import com.sunline.dict.service.MethodStackScanService;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 方法栈扫描服务实现类（重构版本）
 */
@Service
public class MethodStackScanServiceImpl implements MethodStackScanService {
    
    private static final Logger log = LoggerFactory.getLogger(MethodStackScanServiceImpl.class);
    
    @Autowired
    private HardCodeMethodStackMapper hardCodeMethodStackMapper;
    
    @Autowired
    private ServiceFileMapper serviceFileMapper;

    @Autowired
    private ServiceDetailMapper serviceDetailMapper;

    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private ComponentDetailMapper componentDetailMapper;
    
    @Autowired
    private ServiceImplFileMapper serviceImplFileMapper;
    
    // 扫描进度信息
    private volatile Map<String, Object> scanProgress = new ConcurrentHashMap<>();
    
    // 扫描取消标志
    private volatile boolean cancelFlag = false;
    
    // 临时目录（用于存放从Fat Jar提取的jar文件）
    private volatile File tempDirectory = null;
    
    // 上传的临时文件（用于处理完成后删除）
    private volatile File uploadedTempFile = null;
    
    // 上传的临时文件路径（用于在扫描完成后删除）
    private volatile String uploadedTempFilePath = null;
    
    // 找不到的类文件记录
    private final List<String> notFoundClassRecords = new CopyOnWriteArrayList<>();
    
    // SysUtil类名和方法名常量
    private static final String SYS_UTIL_CLASS = "com/spdb/ccbs/aps/api/online/SysUtil";
    private static final String GET_INSTANCE_METHOD = "getInstance";
    private static final String GET_REMOTE_INSTANCE_METHOD = "getRemoteInstance";
    
    // 支持的包路径前缀
    private static final String[] SUPPORTED_PACKAGE_PREFIXES = {
        "com/spdb/ccbs/comm/",
        "com/spdb/ccbs/dept/",
        "com/spdb/ccbs/loan/",
        "com/spdb/ccbs/sett/"
    };
    
    // ServiceFile 缓存（service + component 合并），key = serviceTypeId
    private final ConcurrentHashMap<String, ServiceFile> serviceFileCache = new ConcurrentHashMap<>();

    // ServiceDetail 缓存（service_detail + component_detail 合并），key = serviceTypeId
    private final ConcurrentHashMap<String, List<ServiceDetail>> serviceTypeCache = new ConcurrentHashMap<>();
    
    // jar包文件缓存，key为jar包名称（不含版本号），value为jar文件对象
    private final ConcurrentHashMap<String, File> jarFileCache = new ConcurrentHashMap<>();
    
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
    public void scanMethodStack(String jarPath, List<String> jarNamePrefixes) {
        log.info("==================== 开始代码方法栈扫描 ====================");
        log.info("jar包路径：{}", jarPath);
        log.info("jar包前缀：{}", jarNamePrefixes);
        
        // 初始化进度信息
        initProgress();
        
        try {
            // 第一步：加载ServiceType缓存
            loadServiceTypeCache();
            
            // 加载jar包文件
            loadJarFiles(jarPath, jarNamePrefixes);
            
            // 第二步：查询service_type_impl_file表并单线程处理
            processServiceImplFiles();
            
            scanProgress.put("status", "completed");
            scanProgress.put("message", "扫描完成！");
            log.info("==================== 代码方法栈扫描完成 ====================");
            
            // 输出找不到的类文件记录到txt文件
            if (!notFoundClassRecords.isEmpty()) {
                exportNotFoundClassRecords();
            }
            
        } catch (Exception e) {
            log.error("扫描方法栈失败", e);
            scanProgress.put("status", "error");
            scanProgress.put("message", "扫描失败：" + e.getMessage());
        } finally {
            cleanupTempDirectory();
            cleanupUploadedTempFile();
            jarFileCache.clear();
            notFoundClassRecords.clear();
        }
    }
    
    @Override
    @Async
    public void scanMethodStackFromUpload(MultipartFile jarFile, List<String> jarNamePrefixes) {
        log.info("开始扫描上传的方法栈文件，文件名：{}, jar包前缀：{}", 
                jarFile.getOriginalFilename(), jarNamePrefixes);
        
        File tempFile = null;
        try {
            // 保存上传的文件到临时目录
            String originalFilename = jarFile.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "uploaded.jar";
            }
            
            // 创建临时文件
            File baseTempDir = getTempDirectory();
            File tempDir = new File(baseTempDir, "method-stack-upload-" + System.currentTimeMillis());
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
            
            // 使用保存的临时文件路径调用scanMethodStack方法
            scanMethodStack(tempFile.getAbsolutePath(), jarNamePrefixes);
            
        } catch (Exception e) {
            log.error("处理上传的方法栈文件失败", e);
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
     * 初始化进度信息
     */
    private void initProgress() {
        scanProgress.clear();
        scanProgress.put("status", "scanning");
        scanProgress.put("total", 0);
        scanProgress.put("current", 0);
        scanProgress.put("success", 0);
        scanProgress.put("failed", 0);
        scanProgress.put("totalClasses", 0);
        scanProgress.put("totalRecords", 0);
        scanProgress.put("message", "开始扫描...");
        scanProgress.put("details", new CopyOnWriteArrayList<>());
        cancelFlag = false;
    }
    
    /**
     * 第一步：加载ServiceType缓存
     * Map<service_type_id, List<ServiceTypeFile>>
     */
    private void loadServiceTypeCache() {
        log.info("------------ 第一步：加载 Service + Component 缓存 ------------");
        
        serviceFileCache.clear();
        serviceTypeCache.clear();

        // 加载 service 表
        List<ServiceFile> allServiceFiles = serviceFileMapper.selectList(null);
        for (ServiceFile sf : allServiceFiles) {
            serviceFileCache.put(sf.getId(), sf);
        }

        // 加载 component 表，合并到 serviceFileCache
        List<Component> allComponents = componentMapper.selectList(null);
        for (Component c : allComponents) {
            if (!serviceFileCache.containsKey(c.getId())) {
                ServiceFile proxy = new ServiceFile();
                proxy.setId(c.getId());
                proxy.setLongname(c.getLongname());
                proxy.setPackagePath(c.getPackagePath());
                proxy.setKind(c.getKind());
                proxy.setServiceType(c.getComponentType());
                proxy.setFromJar(c.getFromJar());
                serviceFileCache.put(c.getId(), proxy);
            }
        }
        log.info("加载 service+component 缓存：{} 条", serviceFileCache.size());

        // 加载 service_detail 表
        List<ServiceDetail> allDetails = serviceDetailMapper.selectList(null);
        for (ServiceDetail sd : allDetails) {
            if (sd.getServiceTypeId() != null) {
                serviceTypeCache.computeIfAbsent(sd.getServiceTypeId(), k -> new ArrayList<>()).add(sd);
            }
        }

        // 加载 component_detail 表，合并到 serviceTypeCache
        List<ComponentDetail> allCompDetails = componentDetailMapper.selectList(null);
        for (ComponentDetail cd : allCompDetails) {
            if (cd.getComponentId() != null) {
                ServiceDetail proxy = new ServiceDetail();
                proxy.setServiceTypeId(cd.getComponentId());
                proxy.setServiceId(cd.getServiceId());
                proxy.setServiceName(cd.getServiceName());
                proxy.setServiceLongname(cd.getServiceLongname());
                serviceTypeCache.computeIfAbsent(cd.getComponentId(), k -> new ArrayList<>()).add(proxy);
            }
        }
        
        log.info("Service+Component 缓存加载完成，共 {} 个接口，{} 个方法",
            serviceTypeCache.size(), allDetails.size() + allCompDetails.size());
        
        log.info("缓存中的 ServiceType ID 示例（前20个）：");
        int count = 0;
        for (Map.Entry<String, List<ServiceDetail>> entry : serviceTypeCache.entrySet()) {
            if (count++ < 20) {
                String serviceTypeId = entry.getKey();
                List<ServiceDetail> methods = entry.getValue();
                log.info("  [{}] ServiceTypeId: {} ({} 个方法)", count, serviceTypeId, methods.size());
                
                if (!methods.isEmpty()) {
                    StringBuilder methodNames = new StringBuilder("      方法列表: ");
                    for (ServiceDetail sd : methods) {
                        methodNames.append(sd.getServiceName()).append(", ");
                    }
                    log.info(methodNames.toString());
                }
            }
        }
        
        log.info("----------------------------------------");
    }
    
    /**
     * 加载jar包文件
     */
    private void loadJarFiles(String jarPath, List<String> jarNamePrefixes) throws Exception {
        log.info("------------ 加载jar包文件 ------------");
        
        File inputPath = new File(jarPath);
        if (!inputPath.exists()) {
            throw new RuntimeException("jar包路径不存在：" + jarPath);
        }
        
        // 如果传入的是单个文件（可能是Controller层保存的上传文件），记录其路径以便后续删除
        if (inputPath.isFile() && jarPath.endsWith(".jar")) {
            this.uploadedTempFilePath = jarPath;
        }
        
        if (inputPath.isFile() && jarPath.endsWith(".jar")) {
            // Fat Jar文件
            loadJarFilesFromFatJar(inputPath, jarNamePrefixes);
        } else if (inputPath.isDirectory()) {
            // 目录
            loadJarFilesFromDirectory(inputPath, jarNamePrefixes);
        } else {
            throw new RuntimeException("不支持的路径类型：" + jarPath);
        }
        
        log.info("加载了 {} 个jar包文件", jarFileCache.size());
    }
    
    /**
     * 从Fat Jar中加载jar包文件
     */
    private void loadJarFilesFromFatJar(File fatJarFile, List<String> jarNamePrefixes) throws Exception {
        File baseTempDir = getTempDirectory();
        File tempDir = new File(baseTempDir, "method-stack-scan-" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
        }
        this.tempDirectory = tempDir;
        
        try (JarFile fatJar = new JarFile(fatJarFile)) {
            Enumeration<JarEntry> entries = fatJar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if ((entryName.startsWith("BOOT-INF/lib/") || entryName.startsWith("WEB-INF/lib/")) 
                    && entryName.endsWith(".jar")) {
                    
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    
                    for (String prefix : jarNamePrefixes) {
                        if (fileName.startsWith(prefix)) {
                            File targetFile = new File(tempDir, fileName);
                            try (InputStream is = fatJar.getInputStream(entry);
                                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            
                            String jarNameWithoutVersion = extractJarNameWithoutVersion(fileName);
                            jarFileCache.put(jarNameWithoutVersion, targetFile);
                            log.info("从Fat Jar中提取：{} -> {}", fileName, jarNameWithoutVersion);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 从目录中加载jar包文件
     */
    private void loadJarFilesFromDirectory(File directory, List<String> jarNamePrefixes) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            return;
        }
        
        for (File file : files) {
            String fileName = file.getName();
            for (String prefix : jarNamePrefixes) {
                if (fileName.startsWith(prefix)) {
                    String jarNameWithoutVersion = extractJarNameWithoutVersion(fileName);
                    jarFileCache.put(jarNameWithoutVersion, file);
                    log.info("找到jar包：{} -> {}", fileName, jarNameWithoutVersion);
                    break;
                }
            }
        }
    }
    
    /**
     * 第二步：处理ServiceImpl文件（单线程顺序处理）
     */
    private void processServiceImplFiles() {
        log.info("------------ 第二步：遍历ServiceImpl记录并处理（单线程） ------------");
        
        List<ServiceImplFile> allImplFiles = serviceImplFileMapper.selectList(null);
        log.info("从 serviceImpl 表加载了 {} 条记录", allImplFiles.size());
        
        scanProgress.put("total", allImplFiles.size());
        
        // 单线程顺序处理每个ServiceImpl记录
        int totalRecords = 0;
        int successCount = 0;
        int failedCount = 0;
        int current = 0;
        
        for (ServiceImplFile implFile : allImplFiles) {
            if (cancelFlag) {
                log.info("扫描已取消，停止处理");
                break;
            }
            
            current++;
            scanProgress.put("current", current);
            
            try {
                int records = processServiceImpl(implFile);
                totalRecords += records;
                if (records >= 0) {
                    successCount++;
                    log.info("[{}/{}] 处理成功：{}，生成 {} 条记录", 
                        current, allImplFiles.size(), implFile.getId(), records);
                } else {
                    failedCount++;
                    log.warn("[{}/{}] 处理失败：{}", 
                        current, allImplFiles.size(), implFile.getId());
                }
            } catch (Exception e) {
                log.error("[{}/{}] 处理异常：{}", 
                    current, allImplFiles.size(), implFile.getId(), e);
                failedCount++;
            }
            
            // 每处理10个输出一次进度
            if (current % 10 == 0) {
                log.info("进度：{}/{} ({}%)，成功：{}，失败：{}，记录：{}", 
                    current, allImplFiles.size(), 
                    (current * 100 / allImplFiles.size()),
                    successCount, failedCount, totalRecords);
            }
        }
        
        scanProgress.put("success", successCount);
        scanProgress.put("failed", failedCount);
        scanProgress.put("totalRecords", totalRecords);
        
        log.info("========================================");
        log.info("处理完成：");
        log.info("  总数：{}", allImplFiles.size());
        log.info("  成功：{}", successCount);
        log.info("  失败：{}", failedCount);
        log.info("  生成记录：{}", totalRecords);
        log.info("========================================");
    }
    
    /**
     * 处理单个ServiceImpl记录
     * 返回生成的记录数，-1表示失败
     */
    private int processServiceImpl(ServiceImplFile implFile) {
        try {
            String serviceImplId = implFile.getId();
            String fromJar = implFile.getFromJar();
            String packagePath = implFile.getPackagePath();
            String serviceTypeId = resolveServiceTypeIdFromImpl(implFile);
            
            log.debug("处理 ServiceImpl：{} (jar: {}, package: {})", 
                serviceImplId, fromJar, packagePath);
            
            List<ServiceDetail> serviceMethods = serviceTypeCache.get(serviceTypeId);
            if (serviceMethods == null || serviceMethods.isEmpty()) {
                log.debug("ServiceType {} 下没有方法，跳过", serviceTypeId);
                return 0;
            }
            
            log.debug("ServiceType {} 下有 {} 个方法", serviceTypeId, serviceMethods.size());
            
            String jarName = extractJarNameFromPath(fromJar);
            File jarFile = jarFileCache.get(jarName);
            if (jarFile == null) {
                log.warn("未找到 jar 包文件：{} (原始 fromJar: {})", jarName, fromJar);
                return -1;
            }
            
            String className = serviceImplId.contains(".") 
                ? serviceImplId.substring(serviceImplId.lastIndexOf(".") + 1)
                : serviceImplId;
            
            return parseClassFile(jarFile, packagePath, className, serviceTypeId, serviceMethods);
            
        } catch (Exception e) {
            log.error("处理 ServiceImpl 失败：{}", implFile.getId(), e);
            return -1;
        }
    }

    private String resolveServiceTypeIdFromImpl(ServiceImplFile sif) {
        String st = sif.getServiceType();
        if (st != null && !st.isEmpty() && !st.contains("Impl")
                && !st.equals("pcs") && !st.equals("pbs")
                && !st.equals("pbcb") && !st.equals("pbcp")
                && !st.equals("pbcc") && !st.equals("pbct")) {
            return st;
        }
        String id = sif.getId();
        if (id != null && id.contains(".")) {
            return id.split("\\.", 2)[0];
        }
        return st;
    }

    private String extractJarNameFromPath(String fromJar) {
        if (fromJar == null) return "";
        String name = fromJar.replace("\\", "/");
        if (name.contains("/")) {
            String[] segments = name.split("/");
            for (String seg : segments) {
                if (seg.startsWith("ccbs-") && !seg.endsWith(".xml")) {
                    return extractJarNameWithoutVersion(seg);
                }
            }
        }
        return extractJarNameWithoutVersion(fromJar);
    }
    
    /**
     * 第三步：解析class文件并查找SysUtil调用
     */
    @Transactional(rollbackFor = Exception.class)
    private int parseClassFile(File jarFile, String packageName, String className, 
                               String serviceTypeId, List<ServiceDetail> serviceMethods) throws Exception {
        
        String classFilePath = packageName.replace(".", "/") + "/" + className + ".class";
        
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry classEntry = jar.getJarEntry(classFilePath);
            if (classEntry == null) {
                log.warn("在jar包 {} 中未找到类文件：{}", jarFile.getName(), classFilePath);
                
                // 记录找不到的类文件信息
                String record = String.format("jar包: %s | 实现类: %s | 包路径: %s | 预期路径: %s",
                    jarFile.getName(), className, packageName, classFilePath);
                notFoundClassRecords.add(record);
                log.info("已记录找不到的类文件：{}", record);
                
                return 0;
            }
            
            try (InputStream is = jar.getInputStream(classEntry)) {
                ClassReader classReader = new ClassReader(is);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, 0);
                
                int recordCount = 0;
                
                // 遍历类中的所有方法
                for (Object methodObj : classNode.methods) {
                    MethodNode method = (MethodNode) methodObj;
                    
                    // 跳过构造函数和静态初始化块
                    if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
                        continue;
                    }
                    
                    ServiceDetail matchedService = findMatchingService(serviceMethods, method.name);
                    if (matchedService == null) {
                        continue;
                    }
                    
                    log.debug("解析方法：{}.{}", className, method.name);
                    
                    // 第四步：递归查找方法中的SysUtil调用
                    Set<String> visitedMethods = new HashSet<>();
                    List<SysUtilCall> calls = findSysUtilCalls(classNode, method, jarFile, visitedMethods);
                    
                    // 记录到数据库
                    for (SysUtilCall call : calls) {
                        HardCodeMethodStack stack = new HardCodeMethodStack();
                        stack.setServiceTypeId(serviceTypeId);
                        stack.setServiceTypeImplId(className);
                        ServiceFile sf = serviceFileCache.get(serviceTypeId);
                        stack.setServiceTypeKind(sf != null ? sf.getKind() : null);
                        stack.setServiceId(matchedService.getServiceId());
                        stack.setServiceName(matchedService.getServiceName());
                        stack.setCodeServiceType(call.getClassName());
                        stack.setCodeMethodType(call.getMethodName());
                        stack.setCreateTime(LocalDateTime.now());
                        stack.setUpdateTime(LocalDateTime.now());
                        
                        hardCodeMethodStackMapper.insert(stack);
                        recordCount++;
                        
                        log.info("记录方法调用：{}.{} -> {}.{}", 
                            matchedService.getServiceName(), method.name, 
                            call.getClassName(), call.getMethodName());
                    }
                }
                
                return recordCount;
            }
        }
    }
    
    /**
     * 第四步：递归查找方法中的SysUtil调用
     */
    private List<SysUtilCall> findSysUtilCalls(ClassNode classNode, MethodNode method, 
                                               File jarFile, Set<String> visitedMethods) {
        List<SysUtilCall> calls = new ArrayList<>();
        
        if (method.instructions == null) {
            return calls;
        }
        
        String methodSignature = method.name + method.desc;
        if (visitedMethods.contains(methodSignature)) {
            return calls; // 防止循环调用
        }
        visitedMethods.add(methodSignature);
        
        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != null) {
            if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                
                // 检查是否是SysUtil.getInstance或SysUtil.getRemoteInstance
                if (SYS_UTIL_CLASS.equals(methodInsn.owner)) {
                    if (GET_INSTANCE_METHOD.equals(methodInsn.name)) {
                        // SysUtil.getInstance(xxx.class)
                        SysUtilCall call = extractSysUtilCall(methodInsn, false);
                        if (call != null) {
                            calls.add(call);
                        }
                    } else if (GET_REMOTE_INSTANCE_METHOD.equals(methodInsn.name)) {
                        // SysUtil.getRemoteInstance(xxx.class) - 只支持单级
                        SysUtilCall call = extractSysUtilCall(methodInsn, true);
                        if (call != null) {
                            calls.add(call);
                        }
                    }
                } else {
                    // 检查是否需要递归到该方法中
                    String calledClass = methodInsn.owner;
                    
                    // 只递归到支持的包路径内的方法
                    if (isInSupportedPackage(calledClass) && !isGetInstanceCall(methodInsn)) {
                        // 如果是同一个类中的方法（静态方法或私有方法），递归查找
                        if (calledClass.equals(classNode.name)) {
                            MethodNode calledMethod = findMethod(classNode, methodInsn.name, methodInsn.desc);
                            if (calledMethod != null) {
                                calls.addAll(findSysUtilCalls(classNode, calledMethod, jarFile, visitedMethods));
                            }
                        }
                        // 如果是其他类的方法，可以进一步递归（这里暂时不实现，因为需要加载其他class文件）
                    }
                }
            }
            
            insn = insn.getNext();
        }
        
        visitedMethods.remove(methodSignature);
        return calls;
    }
    
    /**
     * 提取SysUtil调用信息
     */
    private SysUtilCall extractSysUtilCall(MethodInsnNode getInstanceInsn, boolean isRemote) {
        // 查找前一个LDC指令，获取Class参数
        AbstractInsnNode prev = getInstanceInsn.getPrevious();
        
        while (prev != null) {
            if (prev.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldc = (LdcInsnNode) prev;
                if (ldc.cst instanceof Type) {
                    Type type = (Type) ldc.cst;
                    if (type.getSort() == Type.OBJECT) {
                        String fullClassName = type.getInternalName(); // 如：com/spdb/ccbs/dept/.../DpCbProdCvrsnBcsSvtp
                        String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf("/") + 1); // DpCbProdCvrsnBcsSvtp
                        
                        log.info("========================================");
                        log.info("发现SysUtil.{}调用", isRemote ? "getRemoteInstance" : "getInstance");
                        log.info("  完整类名: {}", fullClassName);
                        log.info("  简单类名: {}", simpleClassName);
                        log.info("========================================");
                        
                        // 检查是否是多级路径（如 xxxx.xxx.class）
                        if (isRemote && fullClassName.contains("/")) {
                            log.debug("跳过getRemoteInstance多级路径：{}", fullClassName);
                            return null;
                        }
                        
                        // 尝试多种方式匹配ServiceType
                        String matchedServiceTypeId = findMatchingServiceTypeId(simpleClassName);
                        
                        if (matchedServiceTypeId != null) {
                            // 查找后续的方法调用
                            String methodName = findMethodCallAfter(getInstanceInsn);
                            
                            log.info("匹配成功：类名={} -> ServiceTypeId={}, 方法={}", 
                                simpleClassName, matchedServiceTypeId, methodName);
                            
                            return new SysUtilCall(matchedServiceTypeId, methodName != null ? methodName : "");
                        } else {
                            log.debug("未匹配到ServiceType：类名={}", simpleClassName);
                        }
                        
                        break;
                    }
                }
            }
            
            if (prev.getType() == AbstractInsnNode.METHOD_INSN) {
                break; // 遇到其他方法调用，停止查找
            }
            
            prev = prev.getPrevious();
        }
        
        return null;
    }
    
    /**
     * 查找匹配的ServiceTypeId
     * 优先精确匹配，如果找不到则尝试其他策略
     */
    private String findMatchingServiceTypeId(String className) {
        log.debug("尝试匹配ServiceType，类名：{}", className);
        log.debug("当前缓存中共有 {} 个ServiceType", serviceTypeCache.size());
        
        // 策略1：精确匹配（最优先）
        if (serviceTypeCache.containsKey(className)) {
            log.info("✓ 精确匹配成功：{}", className);
            return className;
        }
        
        log.debug("✗ 精确匹配失败，类名 {} 不在缓存中", className);
        log.debug("缓存中的key示例（前5个）：");
        int count = 0;
        for (String key : serviceTypeCache.keySet()) {
            if (count++ < 5) {
                log.debug("  - {}", key);
            }
        }
        
        // 策略2：尝试去除常见后缀（作为备选）
        String[] suffixes = {"Svtp", "Impl", "Pojo", "Po", "Vo", "Dto"};
        for (String suffix : suffixes) {
            if (className.endsWith(suffix)) {
                String withoutSuffix = className.substring(0, className.length() - suffix.length());
                if (serviceTypeCache.containsKey(withoutSuffix)) {
                    log.info("✓ 去除后缀 '{}' 后匹配成功：{} -> {}", suffix, className, withoutSuffix);
                    return withoutSuffix;
                }
            }
        }
        
        log.warn("✗ 未找到匹配的ServiceType：{}", className);
        return null;
    }
    
    /**
     * 查找getInstance调用后的方法调用
     */
    private String findMethodCallAfter(MethodInsnNode getInstanceInsn) {
        AbstractInsnNode next = getInstanceInsn.getNext();
        int depth = 0;
        
        while (next != null && depth < 20) { // 限制查找深度
            if (next.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsn = (MethodInsnNode) next;
                // 检查是否是实例方法调用
                if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL || 
                    methodInsn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                    return methodInsn.name;
                }
            }
            
            next = next.getNext();
            depth++;
        }
        
        return null;
    }
    
    /**
     * 查找匹配的Service方法
     */
    private ServiceDetail findMatchingService(List<ServiceDetail> serviceMethods, String methodName) {
        for (ServiceDetail sd : serviceMethods) {
            if (methodName.equals(sd.getServiceName())) {
                return sd;
            }
        }
        return null;
    }
    
    /**
     * 在ClassNode中查找指定的方法
     */
    private MethodNode findMethod(ClassNode classNode, String name, String desc) {
        for (Object methodObj : classNode.methods) {
            MethodNode method = (MethodNode) methodObj;
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }
    
    /**
     * 检查类是否在支持的包范围内
     */
    private boolean isInSupportedPackage(String className) {
        for (String prefix : SUPPORTED_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否是getInstance/getRemoteInstance调用
     */
    private boolean isGetInstanceCall(MethodInsnNode methodInsn) {
        return SYS_UTIL_CLASS.equals(methodInsn.owner) && 
               (GET_INSTANCE_METHOD.equals(methodInsn.name) || 
                GET_REMOTE_INSTANCE_METHOD.equals(methodInsn.name));
    }
    
    /**
     * 提取jar包名称（不含版本号）
     */
    private String extractJarNameWithoutVersion(String jarName) {
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4);
        }
        
        // 递归去掉所有版本号相关的后缀
        while (true) {
            int lastDashIndex = jarName.lastIndexOf("-");
            if (lastDashIndex <= 0) {
                break;
            }
            
            String afterLastDash = jarName.substring(lastDashIndex + 1);
            
            // 检查是否是版本号标识
            if (afterLastDash.matches("(?i)(sit|dev|prod|test|uat|pre)") || 
                afterLastDash.matches(".*\\d+.*") || 
                afterLastDash.equalsIgnoreCase("SNAPSHOT")) {
                jarName = jarName.substring(0, lastDashIndex);
            } else {
                break;
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
        if (details != null) {
            details.add("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message);
        }
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
     * 导出找不到的类文件记录到txt文件
     */
    private void exportNotFoundClassRecords() {
        try {
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "not_found_classes_" + timestamp + ".txt";
            File outputFile = new File(fileName);
            
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(outputFile))) {
                
                writer.write("==========================================\n");
                writer.write("找不到的类文件记录\n");
                writer.write("生成时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
                writer.write("共 " + notFoundClassRecords.size() + " 条记录\n");
                writer.write("==========================================\n\n");
                
                int index = 1;
                for (String record : notFoundClassRecords) {
                    writer.write(index + ". " + record + "\n");
                    index++;
                }
                
                writer.write("\n==========================================\n");
                writer.write("说明：\n");
                writer.write("这些类在jar包中未找到，可能是因为：\n");
                writer.write("1. 数据库中的包路径(service_impl_package)与实际jar包中的路径不一致\n");
                writer.write("2. 类文件不存在于对应的jar包中\n");
                writer.write("3. jar包版本不匹配\n");
                writer.write("==========================================\n");
            }
            
            log.info("找不到的类文件记录已导出到：{}", outputFile.getAbsolutePath());
            scanProgress.put("notFoundClassFile", outputFile.getAbsolutePath());
            updateProgress("找不到的类文件记录已导出到：" + outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("导出找不到的类文件记录失败", e);
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
     * SysUtil调用信息
     */
    private static class SysUtilCall {
        private final String className;
        private final String methodName;
        
        public SysUtilCall(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
        
        public String getClassName() {
            return className;
        }
        
        public String getMethodName() {
            return methodName;
        }
    }
}
