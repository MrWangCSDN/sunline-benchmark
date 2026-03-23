package com.sunline.dict.service.impl;

import com.sunline.dict.service.ComplexXmlParseService;
import com.sunline.dict.service.ComponentXmlParseService;
import com.sunline.dict.service.FlowXmlParseService;
import com.sunline.dict.service.ServiceFileXmlParseService;
import com.sunline.dict.service.ServiceImplXmlParseService;
import com.sunline.dict.service.TablesXmlParseService;
import com.sunline.dict.service.XmlScanService;
import com.sunline.dict.vectorization.ComplexVectorizationService;
import com.sunline.dict.vectorization.ComponentVectorizationService;
import com.sunline.dict.vectorization.FlowtranVectorizationService;
import com.sunline.dict.vectorization.MetadataTablesVectorizationService;
import com.sunline.dict.vectorization.ServiceVectorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * XML 全量扫描服务实现
 * 递归遍历本地文件夹，按文件后缀分派给对应的 XmlParseService 解析落库。
 *
 * 支持的文件类型及对应入库表：
 *   .flowtrans.xml                      → flowtran / flow_step
 *   .pcs.xml / .pbs.xml                → service / service_detail
 *   .pcsImpl.xml / .pbsImpl.xml 等     → service_impl
 *   .c_schema.xml                      → complex / complex_detail
 *   .pbcb.xml / .pbcp.xml / .pbcc.xml / .pbct.xml → component / component_detail
 *   .tables.xml                        → metadata_tables / metadata_tables_detail / metadata_tables_indexes
 */
@Service
public class XmlScanServiceImpl implements XmlScanService {

    private static final Logger log = LoggerFactory.getLogger(XmlScanServiceImpl.class);

    @Autowired
    private FlowXmlParseService flowXmlParseService;

    @Autowired
    private ComplexXmlParseService complexXmlParseService;

    @Autowired
    private ComponentXmlParseService componentXmlParseService;

    @Autowired
    private ServiceFileXmlParseService serviceFileXmlParseService;

    @Autowired
    private ServiceImplXmlParseService serviceImplXmlParseService;

    @Autowired
    private TablesXmlParseService tablesXmlParseService;

    @Value("${vectorization.enabled:false}")
    private boolean vectorizationEnabled;

    @Autowired(required = false)
    private FlowtranVectorizationService flowtranVectorizationService;

    @Autowired(required = false)
    private ServiceVectorizationService serviceVectorizationService;

    @Autowired(required = false)
    private ComplexVectorizationService complexVectorizationService;

    @Autowired(required = false)
    private ComponentVectorizationService componentVectorizationService;

    @Autowired(required = false)
    private MetadataTablesVectorizationService metadataTablesVectorizationService;

    @Override
    public Map<String, Object> scanAndSave(String folderPath) throws Exception {
        log.info("==================== 开始 XML 全量扫描 ====================");
        log.info("扫描目录: {}", folderPath);

        File rootDir = new File(folderPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("文件夹不存在或不是目录: " + folderPath);
        }

        int flowtranFiles = 0;
        int serviceFiles = 0, serviceImplFiles = 0, complexFiles = 0,
                componentFiles = 0, tablesFiles = 0;

        int flowtranCount = 0, flowStepCount = 0;
        int serviceCount = 0, serviceDetailCount = 0;
        int serviceImplCount = 0;
        int complexCount = 0, complexDetailCount = 0;
        int componentCount = 0, componentDetailCount = 0;
        int tablesCount = 0, tablesDetailCount = 0, tablesIndexesCount = 0;

        List<Map<String, String>> errors = new ArrayList<>();

        List<File> xmlFiles = new ArrayList<>();
        collectXmlFiles(rootDir, xmlFiles);
        log.info("共发现目标 XML 文件 {} 个", xmlFiles.size());

        for (File file : xmlFiles) {
            String fileName = file.getName();
            String sourceInfo = file.getAbsolutePath();

            try {
                String xmlContent = readFileContent(file);
                if (xmlContent == null || xmlContent.trim().isEmpty()) {
                    log.warn("文件内容为空，跳过: {}", sourceInfo);
                    continue;
                }

                // ===== .flowtrans.xml → flowtran / flow_step =====
                if (fileName.endsWith(".flowtrans.xml")) {
                    log.debug("解析 flowtrans 文件: {}", sourceInfo);
                    Map<String, Object> result = flowXmlParseService.parseAndSave(xmlContent, sourceInfo);
                    flowtranCount += toInt(result.get("flowtranCount"));
                    flowStepCount += toInt(result.get("flowStepCount"));
                    flowtranFiles++;
                    log.info("✓ flowtrans.xml flowtran={}, flowStep={} → {}",
                            result.get("flowtranCount"), result.get("flowStepCount"), fileName);
                    vectorize(() -> { if (flowtranVectorizationService != null) flowtranVectorizationService.vectorizeBySource(sourceInfo); }, "flowtran", sourceInfo);
                    continue;
                }

                // ===== .pcs.xml / .pbs.xml → service / service_detail =====
                String serviceType = resolveServiceType(fileName);
                if (serviceType != null) {
                    log.debug("解析服务文件 [{}]: {}", serviceType, sourceInfo);
                    Map<String, Object> result = serviceFileXmlParseService.parseAndSave(xmlContent, sourceInfo, serviceType);
                    serviceCount += toInt(result.get("serviceCount"));
                    serviceDetailCount += toInt(result.get("serviceDetailCount"));
                    serviceFiles++;
                    log.info("✓ 服务文件 [{}] service={}, detail={} → {}",
                            serviceType, result.get("serviceCount"), result.get("serviceDetailCount"), fileName);
                    vectorize(() -> { if (serviceVectorizationService != null) serviceVectorizationService.vectorizeBySource(sourceInfo); }, "service", sourceInfo);
                    continue;
                }

                // ===== .pcsImpl.xml 等 → service_impl =====
                String serviceImplType = resolveServiceImplType(fileName);
                if (serviceImplType != null) {
                    log.debug("解析服务实现文件 [{}]: {}", serviceImplType, sourceInfo);
                    Map<String, Object> result = serviceImplXmlParseService.parseAndSave(xmlContent, sourceInfo, serviceImplType);
                    serviceImplCount += toInt(result.get("serviceImplCount"));
                    serviceImplFiles++;
                    log.info("✓ 服务实现文件 [{}] count={} → {}",
                            serviceImplType, result.get("serviceImplCount"), fileName);
                    continue;
                }

                // ===== .c_schema.xml → complex / complex_detail =====
                if (fileName.endsWith(".c_schema.xml")) {
                    log.debug("解析 complex 文件: {}", sourceInfo);
                    Map<String, Object> result = complexXmlParseService.parseAndSave(xmlContent, sourceInfo);
                    complexCount += toInt(result.get("complexCount"));
                    complexDetailCount += toInt(result.get("complexDetailCount"));
                    complexFiles++;
                    log.info("✓ c_schema.xml complex={}, detail={} → {}",
                            result.get("complexCount"), result.get("complexDetailCount"), fileName);
                    vectorize(() -> { if (complexVectorizationService != null) complexVectorizationService.vectorizeBySource(sourceInfo); }, "complex", sourceInfo);
                    continue;
                }

                // ===== .pbcb/.pbcp/.pbcc/.pbct.xml → component / component_detail =====
                String componentType = resolveComponentType(fileName);
                if (componentType != null) {
                    log.debug("解析构件文件 [{}]: {}", componentType, sourceInfo);
                    Map<String, Object> result = componentXmlParseService.parseAndSave(xmlContent, sourceInfo, componentType);
                    componentCount += toInt(result.get("componentCount"));
                    componentDetailCount += toInt(result.get("componentDetailCount"));
                    componentFiles++;
                    log.info("✓ 构件文件 [{}] component={}, detail={} → {}",
                            componentType, result.get("componentCount"), result.get("componentDetailCount"), fileName);
                    vectorize(() -> { if (componentVectorizationService != null) componentVectorizationService.vectorizeBySource(sourceInfo); }, "component", sourceInfo);
                    continue;
                }

                // ===== .tables.xml → metadata_tables / detail / indexes =====
                if (fileName.endsWith(".tables.xml")) {
                    log.debug("解析 tables 文件: {}", sourceInfo);
                    Map<String, Object> result = tablesXmlParseService.parseAndSave(xmlContent, sourceInfo);
                    tablesCount += toInt(result.get("tablesCount"));
                    tablesDetailCount += toInt(result.get("detailCount"));
                    tablesIndexesCount += toInt(result.get("indexesCount"));
                    tablesFiles++;
                    log.info("✓ tables.xml tables={}, detail={}, indexes={} → {}",
                            result.get("tablesCount"), result.get("detailCount"), result.get("indexesCount"), fileName);
                    vectorize(() -> { if (metadataTablesVectorizationService != null) metadataTablesVectorizationService.vectorizeBySource(sourceInfo); }, "metadata_tables", sourceInfo);
                    continue;
                }

                log.warn("未匹配到任何已知 XML 类型，跳过: {}", sourceInfo);

            } catch (Exception e) {
                log.error("解析文件失败: {} → {}", fileName, e.getMessage(), e);
                Map<String, String> err = new LinkedHashMap<>();
                err.put("file", sourceInfo);
                err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
                errors.add(err);
            }
        }

        log.info("==================== XML 全量扫描完成 ====================");
        log.info("flowtrans文件: {} 个  → flowtran={}, flowStep={}", flowtranFiles, flowtranCount, flowStepCount);
        log.info("服务文件: {} 个  → service={}, detail={}", serviceFiles, serviceCount, serviceDetailCount);
        log.info("服务实现文件: {} 个  → serviceImpl={}", serviceImplFiles, serviceImplCount);
        log.info("complex文件: {} 个  → complex={}, detail={}", complexFiles, complexCount, complexDetailCount);
        log.info("构件文件: {} 个  → component={}, detail={}", componentFiles, componentCount, componentDetailCount);
        log.info("tables文件: {} 个  → tables={}, detail={}, indexes={}", tablesFiles, tablesCount, tablesDetailCount, tablesIndexesCount);
        if (!errors.isEmpty()) {
            log.warn("解析失败文件: {} 个", errors.size());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFiles", flowtranFiles + serviceFiles + serviceImplFiles + complexFiles + componentFiles + tablesFiles);

        Map<String, Object> fileStats = new LinkedHashMap<>();
        fileStats.put("flowtranFiles", flowtranFiles);
        fileStats.put("serviceFiles", serviceFiles);
        fileStats.put("serviceImplFiles", serviceImplFiles);
        fileStats.put("complexFiles", complexFiles);
        fileStats.put("componentFiles", componentFiles);
        fileStats.put("tablesFiles", tablesFiles);
        summary.put("fileStats", fileStats);

        Map<String, Object> recordStats = new LinkedHashMap<>();
        recordStats.put("flowtranCount", flowtranCount);
        recordStats.put("flowStepCount", flowStepCount);
        recordStats.put("serviceCount", serviceCount);
        recordStats.put("serviceDetailCount", serviceDetailCount);
        recordStats.put("serviceImplCount", serviceImplCount);
        recordStats.put("complexCount", complexCount);
        recordStats.put("complexDetailCount", complexDetailCount);
        recordStats.put("componentCount", componentCount);
        recordStats.put("componentDetailCount", componentDetailCount);
        recordStats.put("tablesCount", tablesCount);
        recordStats.put("tablesDetailCount", tablesDetailCount);
        recordStats.put("tablesIndexesCount", tablesIndexesCount);
        summary.put("recordStats", recordStats);

        summary.put("errors", errors);
        summary.put("errorCount", errors.size());

        return summary;
    }

    /**
     * 递归收集所有目标 XML 文件
     */
    private void collectXmlFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectXmlFiles(f, result);
            } else if (isTargetXmlFile(f.getName())) {
                result.add(f);
            }
        }
    }

    private boolean isTargetXmlFile(String name) {
        return name.endsWith(".flowtrans.xml")
                || resolveServiceType(name) != null
                || resolveServiceImplType(name) != null
                || resolveComponentType(name) != null
                || name.endsWith(".c_schema.xml")
                || name.endsWith(".tables.xml");
    }

    private String resolveServiceType(String name) {
        if (name.endsWith(".pcs.xml")) return "pcs";
        if (name.endsWith(".pbs.xml")) return "pbs";
        return null;
    }

    private String resolveServiceImplType(String name) {
        if (name.endsWith(".pcsImpl.xml"))  return "pcsImpl";
        if (name.endsWith(".pbsImpl.xml"))  return "pbsImpl";
        if (name.endsWith(".pbcbImpl.xml")) return "pbcbImpl";
        if (name.endsWith(".pbcpImpl.xml")) return "pbcpImpl";
        if (name.endsWith(".pbccImpl.xml")) return "pbccImpl";
        if (name.endsWith(".pbctImpl.xml")) return "pbctImpl";
        return null;
    }

    private String resolveComponentType(String name) {
        if (name.endsWith(".pbcb.xml")) return "pbcb";
        if (name.endsWith(".pbcp.xml")) return "pbcp";
        if (name.endsWith(".pbcc.xml")) return "pbcc";
        if (name.endsWith(".pbct.xml")) return "pbct";
        return null;
    }

    private String readFileContent(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                return new String(bytes);
            } catch (Exception ex) {
                log.error("读取文件失败: {}", file.getAbsolutePath(), ex);
                return null;
            }
        }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    /**
     * 安全调用向量化，失败不影响主流程
     */
    private void vectorize(Runnable action, String modelType, String sourceInfo) {
        if (!vectorizationEnabled) return;
        try {
            action.run();
            log.info("✓ {} 向量化完成 → {}", modelType, sourceInfo);
        } catch (Exception e) {
            log.warn("✗ {} 向量化失败（不影响主流程）→ {}，错误：{}", modelType, sourceInfo, e.getMessage());
        }
    }
}
