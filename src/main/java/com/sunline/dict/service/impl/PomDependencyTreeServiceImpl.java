package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.PomDependencyTreeService;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

/**
 * POM依赖树服务实现
 */
@Service
public class PomDependencyTreeServiceImpl implements PomDependencyTreeService {
    
    private static final Logger log = LoggerFactory.getLogger(PomDependencyTreeServiceImpl.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    
    @Value("${git.gitlab.url:https://gitlab.spdb.com}")
    private String gitlabUrl;
    
    @Value("${git.gitlab.username:c-wangsh8}")
    private String gitlabUsername;
    
    @Value("${git.gitlab.password:Liang@201314}")
    private String gitlabPassword;
    
    @Value("${git.gitlab.token:}")
    private String gitlabToken;
    
    public PomDependencyTreeServiceImpl() {
    }
    
    /**
     * 获取GitLab认证头
     */
    private String getAuthHeader() {
        if (gitlabToken != null && !gitlabToken.trim().isEmpty()) {
            return "Bearer " + gitlabToken.trim();
        } else {
            String credentials = gitlabUsername + ":" + gitlabPassword;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }
    }
    
    /**
     * 从GitLab API获取文件内容
     */
    private String getFileContent(Integer projectId, String branch, String filePath) throws Exception {
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/" + 
                     encodeFilePath(filePath) + "/raw?ref=" + branch;
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", getAuthHeader());
            
            return httpClient.execute(request, (ClassicHttpResponse response) -> {
                try {
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    } else {
                        String errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        throw new RuntimeException("获取文件失败: " + response.getCode() + " - " + errorBody);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("读取文件内容失败: " + e.getMessage(), e);
                }
            });
        }
    }
    
    /**
     * URL编码文件路径
     */
    private String encodeFilePath(String filePath) {
        return filePath.replace("/", "%2F");
    }
    
    /**
     * 解析pom.xml文件
     */
    private Document parsePomXml(String xmlContent) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }
    
    /**
     * 获取元素文本内容
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }
    
    /**
     * 获取parent的artifactId
     */
    private String getParentArtifactId(Document doc) {
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            return getElementText(parent, "artifactId");
        }
        return null;
    }
    
    /**
     * 获取dependencies列表
     * 只获取<dependencies>节点下的依赖，不包括<dependencyManagement>节点下的依赖
     */
    private List<String> getDependencies(Document doc) {
        List<String> dependencies = new ArrayList<>();
        
        // 获取根元素
        Element rootElement = doc.getDocumentElement();
        
        // 先找到dependencyManagement节点，记录它下面的所有dependencies节点，用于排除
        Set<Element> excludedDependencies = new HashSet<>();
        NodeList dependencyManagementNodes = rootElement.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < dependencyManagementNodes.getLength(); i++) {
            Element dependencyManagement = (Element) dependencyManagementNodes.item(i);
            NodeList dmDependencies = dependencyManagement.getElementsByTagName("dependencies");
            for (int j = 0; j < dmDependencies.getLength(); j++) {
                Node dmDepNode = dmDependencies.item(j);
                if (dmDepNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element dmDepElement = (Element) dmDepNode;
                    // 检查这个dependencies节点的直接父节点是否是dependencyManagement
                    Node parent = dmDepElement.getParentNode();
                    if (parent != null && parent.equals(dependencyManagement)) {
                        excludedDependencies.add(dmDepElement);
                    }
                }
            }
        }
        
        // 查找所有的dependencies节点
        NodeList allDependenciesNodes = rootElement.getElementsByTagName("dependencies");
        for (int i = 0; i < allDependenciesNodes.getLength(); i++) {
            Node depNode = allDependenciesNodes.item(i);
            if (depNode.getNodeType() == Node.ELEMENT_NODE) {
                Element dependenciesElement = (Element) depNode;
                
                // 跳过dependencyManagement下的dependencies
                if (excludedDependencies.contains(dependenciesElement)) {
                    continue;
                }
                
                // 检查父节点链，确保不是dependencyManagement下的
                Node parent = dependenciesElement.getParentNode();
                boolean isUnderDependencyManagement = false;
                while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                    Element parentElement = (Element) parent;
                    if ("dependencyManagement".equals(parentElement.getTagName())) {
                        isUnderDependencyManagement = true;
                        break;
                    }
                    parent = parent.getParentNode();
                }
                
                // 如果不是dependencyManagement下的，则收集依赖
                if (!isUnderDependencyManagement) {
                    NodeList dependencyNodes = dependenciesElement.getChildNodes();
                    for (int j = 0; j < dependencyNodes.getLength(); j++) {
                        Node childNode = dependencyNodes.item(j);
                        if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element dependency = (Element) childNode;
                            if ("dependency".equals(dependency.getTagName())) {
                                String artifactId = getElementText(dependency, "artifactId");
                                if (artifactId != null && !artifactId.isEmpty()) {
                                    dependencies.add(artifactId);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    /**
     * 获取modules列表
     */
    private List<String> getModules(Document doc) {
        List<String> modules = new ArrayList<>();
        NodeList moduleNodes = doc.getElementsByTagName("module");
        
        for (int i = 0; i < moduleNodes.getLength(); i++) {
            Node moduleNode = moduleNodes.item(i);
            String moduleName = moduleNode.getTextContent().trim();
            if (!moduleName.isEmpty()) {
                modules.add(moduleName);
            }
        }
        
        return modules;
    }
    
    /**
     * 递归解析模块的pom.xml
     */
    private Map<String, Object> parseModulePom(Integer projectId, String branch, String modulePath) {
        Map<String, Object> moduleData = new HashMap<>();
        
        try {
            String pomPath = modulePath + "/pom.xml";
            String pomContent = getFileContent(projectId, branch, pomPath);
            Document doc = parsePomXml(pomContent);
            
            // 获取parent
            String moduleParent = getParentArtifactId(doc);
            if (moduleParent != null) {
                moduleData.put("module_parent", moduleParent);
            }
            
            // 获取dependencies
            List<String> moduleDependencies = getDependencies(doc);
            if (!moduleDependencies.isEmpty()) {
                moduleData.put("module_depend", moduleDependencies);
            }
            
            // 递归处理子模块
            List<String> subModules = getModules(doc);
            if (!subModules.isEmpty()) {
                List<Map<String, Object>> subModuleList = new ArrayList<>();
                for (String subModule : subModules) {
                    String subModulePath = modulePath + "/" + subModule;
                    Map<String, Object> subModuleData = parseModulePom(projectId, branch, subModulePath);
                    subModuleData.put("module_name", subModule);
                    subModuleList.add(subModuleData);
                }
                moduleData.put("sub_modules", subModuleList);
            }
            
        } catch (Exception e) {
            log.error("解析模块pom.xml失败: " + modulePath, e);
            moduleData.put("error", "解析失败: " + e.getMessage());
        }
        
        return moduleData;
    }
    
    @Override
    public Map<String, Object> buildPomDependencyTree(Integer projectId, String branch) {
        Map<String, Object> tree = new HashMap<>();
        
        try {
            // 获取根pom.xml
            String rootPomContent = getFileContent(projectId, branch, "pom.xml");
            Document rootDoc = parsePomXml(rootPomContent);
            
            // 获取current_parent
            String currentParent = getParentArtifactId(rootDoc);
            if (currentParent != null) {
                tree.put("current_parent", currentParent);
            }
            
            // 获取current_depend
            List<String> currentDepend = getDependencies(rootDoc);
            if (!currentDepend.isEmpty()) {
                tree.put("current_depend", currentDepend);
            }
            
            // 获取current_modules并递归解析
            List<String> modules = getModules(rootDoc);
            if (!modules.isEmpty()) {
                List<Map<String, Object>> moduleList = new ArrayList<>();
                for (String module : modules) {
                    Map<String, Object> moduleData = parseModulePom(projectId, branch, module);
                    moduleData.put("module_name", module);
                    moduleList.add(moduleData);
                }
                tree.put("current_modules", moduleList);
            }
            
        } catch (Exception e) {
            log.error("构建POM依赖树失败", e);
            tree.put("error", "构建失败: " + e.getMessage());
        }
        
        return tree;
    }
    
    @Override
    public String saveDependencyTreeToFile(String projectName, Map<String, Object> dependencyTree) {
        try {
            // 创建保存目录
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            Path saveDir = projectRoot.resolve("pom-dependency-trees");
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }
            
            // 保存文件
            String fileName = projectName + ".json";
            Path filePath = saveDir.resolve(fileName);
            
            // 格式化JSON
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dependencyTree);
            
            Files.write(filePath, jsonContent.getBytes(StandardCharsets.UTF_8));
            
            log.info("POM依赖树已保存到: {}", filePath);
            return filePath.toString();
            
        } catch (Exception e) {
            log.error("保存POM依赖树失败", e);
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }
}

