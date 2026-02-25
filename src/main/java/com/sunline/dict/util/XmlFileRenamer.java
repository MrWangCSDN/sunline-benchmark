package com.sunline.dict.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * XML文件重命名和内容修改工具
 * 用于批量将 .serviceType.xml 和 .serviceImpl.xml 文件重命名并修改内容
 */
public class XmlFileRenamer {
    
    // 支持的类型列表
    private static final String[] SUPPORTED_TYPES = {
        "pbs", "pbsImpl", "pcs", "pcsImpl", 
        "pbcb", "pbcbImpl", "pbcc", "pbccImpl", 
        "pbcp", "pbcpImpl", "pbct", "pbctImpl"
    };
    
    /**
     * 配置：路径和类型的对应关系
     * 
     * 配置方式1：一个路径对应一种类型
     *   pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
     * 
     * 配置方式2：多个路径对应一种类型（使用列表）
     *   List<String> pbsPaths = Arrays.asList(
     *       "D:\\code\\project1\\src",
     *       "D:\\code\\project2\\src"
     *   );
     *   for (String path : pbsPaths) {
     *       pathTypeMapping.put(path, "pbs");
     *   }
     */
    private static Map<String, String> getPathTypeMapping() {
        Map<String, String> pathTypeMapping = new HashMap<>();
        
        // ========== 在这里配置路径和类型的对应关系 ==========
        
        // 示例1：单个路径对应一种类型
        // pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
        // pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
        // pathTypeMapping.put("D:\\code\\project3\\src", "pbcb");
        
        // 示例2：多个路径对应同一种类型
        // String[] pbsPaths = {
        //     "D:\\code\\project1\\src",
        //     "D:\\code\\project2\\src",
        //     "D:\\code\\project3\\src"
        // };
        // for (String path : pbsPaths) {
        //     pathTypeMapping.put(path, "pbs");
        // }
        
        // 示例3：混合配置
        // pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
        // pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
        // 
        // String[] pbcbPaths = {
        //     "D:\\code\\project3\\src",
        //     "D:\\code\\project4\\src"
        // };
        // for (String path : pbcbPaths) {
        //     pathTypeMapping.put(path, "pbcb");
        // }
        
        // ========== 配置结束 ==========
        
        return pathTypeMapping;
    }
    
    public static void main(String[] args) {
        System.out.println("=== XML文件重命名工具 ===");
        System.out.println("支持的类型：" + String.join(", ", SUPPORTED_TYPES));
        System.out.println();
        
        // 获取配置
        Map<String, String> pathTypeMapping = getPathTypeMapping();
        
        if (pathTypeMapping.isEmpty()) {
            System.err.println("错误：未配置任何路径和类型的对应关系！");
            System.err.println("请在 getPathTypeMapping() 方法中配置路径和类型。");
            return;
        }
        
        // 验证配置
        List<String> invalidPaths = new ArrayList<>();
        List<String> invalidTypes = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : pathTypeMapping.entrySet()) {
            String path = entry.getKey();
            String type = entry.getValue();
            
            // 验证路径
            File folder = new File(path);
            if (!folder.exists() || !folder.isDirectory()) {
                invalidPaths.add(path);
            }
            
            // 验证类型
            if (!isValidType(type)) {
                invalidTypes.add(type + " (路径: " + path + ")");
            }
        }
        
        if (!invalidPaths.isEmpty()) {
            System.err.println("错误：以下路径不存在或不是有效目录：");
            for (String path : invalidPaths) {
                System.err.println("  - " + path);
            }
            return;
        }
        
        if (!invalidTypes.isEmpty()) {
            System.err.println("错误：以下类型不支持：");
            for (String type : invalidTypes) {
                System.err.println("  - " + type);
            }
            System.err.println("支持的类型：" + String.join(", ", SUPPORTED_TYPES));
            return;
        }
        
        // 执行批量处理
        System.out.println("开始批量处理...");
        System.out.println("共 " + pathTypeMapping.size() + " 个路径需要处理");
        System.out.println("----------------------------------------");
        
        int totalCount = 0;
        int index = 1;
        for (Map.Entry<String, String> entry : pathTypeMapping.entrySet()) {
            String path = entry.getKey();
            String type = entry.getValue();
            
            System.out.println("\n[" + index + "/" + pathTypeMapping.size() + "] 处理路径: " + path);
            System.out.println("类型: " + type);
            System.out.println("---");
            
            File folder = new File(path);
            int count = processFolder(folder, type);
            totalCount += count;
            
            System.out.println("完成，处理了 " + count + " 个文件");
            index++;
        }
        
        System.out.println("\n========================================");
        System.out.println("批量处理完成！");
        System.out.println("共处理 " + pathTypeMapping.size() + " 个路径");
        System.out.println("共处理 " + totalCount + " 个文件");
    }
    
    /**
     * 检查类型是否有效
     */
    private static boolean isValidType(String type) {
        for (String supportedType : SUPPORTED_TYPES) {
            if (supportedType.equals(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 递归处理文件夹
     */
    private static int processFolder(File folder, String type) {
        int count = 0;
        
        File[] files = folder.listFiles();
        if (files == null) {
            return 0;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 递归处理子文件夹
                count += processFolder(file, type);
            } else if (file.isFile()) {
                // 处理文件
                if (processFile(file, type)) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * 处理单个文件
     */
    private static boolean processFile(File file, String type) {
        String fileName = file.getName();
        String newFileName = null;
        boolean needModifyContent = false;
        
        // 判断是 Impl 类型还是普通类型
        boolean isImplType = type.endsWith("Impl");
        
        // 检查是否是 .serviceType.xml 结尾
        if (fileName.endsWith(".serviceType.xml")) {
            // 只有非Impl类型才处理 .serviceType.xml
            if (!isImplType) {
                newFileName = fileName.replace(".serviceType.xml", "." + type + ".xml");
                needModifyContent = true;
            } else {
                // Impl类型不处理 .serviceType.xml 文件
                return false;
            }
        }
        // 检查是否是 .serviceImpl.xml 结尾
        else if (fileName.endsWith(".serviceImpl.xml")) {
            // 只有Impl类型才处理 .serviceImpl.xml
            if (isImplType) {
                newFileName = fileName.replace(".serviceImpl.xml", "." + type + ".xml");
                needModifyContent = false; // serviceImpl 只需要改文件名
            } else {
                // 非Impl类型不处理 .serviceImpl.xml 文件
                return false;
            }
        } else {
            // 不是目标文件，跳过
            return false;
        }
        
        try {
            File newFile = new File(file.getParent(), newFileName);
            
            // 如果是 serviceType，需要修改XML内容
            if (needModifyContent) {
                // 先修改XML内容，然后重命名
                modifyXmlContent(file, type);
                // 重命名文件
                if (file.renameTo(newFile)) {
                    System.out.println("✓ " + file.getAbsolutePath() + " -> " + newFile.getName());
                    return true;
                } else {
                    System.err.println("✗ 重命名失败: " + file.getAbsolutePath());
                    return false;
                }
            } else {
                // serviceImpl 只需要重命名
                if (file.renameTo(newFile)) {
                    System.out.println("✓ " + file.getAbsolutePath() + " -> " + newFile.getName());
                    return true;
                } else {
                    System.err.println("✗ 重命名失败: " + file.getAbsolutePath());
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("✗ 处理文件失败: " + file.getAbsolutePath() + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 修改XML内容（使用字符串替换方式，保持原有格式）
     */
    private static void modifyXmlContent(File xmlFile, String type) throws Exception {
        // 读取原始文件内容
        String content = new String(Files.readAllBytes(xmlFile.toPath()), "UTF-8");
        
        // 查找 <serviceType 标签的开始位置
        int serviceTypeStart = content.indexOf("<serviceType");
        if (serviceTypeStart == -1) {
            System.out.println("  警告: 未找到 <serviceType> 节点，跳过内容修改: " + xmlFile.getName());
            return;
        }
        
        // 找到 <serviceType> 标签的结束位置（> 符号）
        int serviceTypeTagEnd = content.indexOf(">", serviceTypeStart);
        if (serviceTypeTagEnd == -1) {
            System.out.println("  警告: <serviceType> 标签格式不正确，跳过内容修改: " + xmlFile.getName());
            return;
        }
        
        // 提取 <serviceType> 标签部分
        String serviceTypeTag = content.substring(serviceTypeStart, serviceTypeTagEnd + 1);
        String modifiedTag = serviceTypeTag;
        
        // 根据类型修改属性
        if (type.equals("pbs")) {
            // pbs: kind="pbs", category="PBS_XML", outBound="false"
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pbs");
            modifiedTag = modifyAttribute(modifiedTag, "category", "PBS_XML");
            modifiedTag = modifyAttribute(modifiedTag, "outBound", "false");
        } else if (type.equals("pcs")) {
            // pcs: kind="pcs", category="PCS_XML", outBound="false"
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pcs");
            modifiedTag = modifyAttribute(modifiedTag, "category", "PCS_XML");
            modifiedTag = modifyAttribute(modifiedTag, "outBound", "false");
        } else if (type.equals("pbcb")) {
            // pbcb: kind="pbcb", 删除 category 和 outBound
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pbcb");
            modifiedTag = removeAttribute(modifiedTag, "category");
            modifiedTag = removeAttribute(modifiedTag, "outBound");
        } else if (type.equals("pbcp")) {
            // pbcp: kind="pbcp", 删除 category 和 outBound
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pbcp");
            modifiedTag = removeAttribute(modifiedTag, "category");
            modifiedTag = removeAttribute(modifiedTag, "outBound");
        } else if (type.equals("pbcc")) {
            // pbcc: kind="pbcc", 删除 category 和 outBound
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pbcc");
            modifiedTag = removeAttribute(modifiedTag, "category");
            modifiedTag = removeAttribute(modifiedTag, "outBound");
        } else if (type.equals("pbct")) {
            // pbct: kind="pbct", 删除 category 和 outBound
            modifiedTag = modifyAttribute(modifiedTag, "kind", "pbct");
            modifiedTag = removeAttribute(modifiedTag, "category");
            modifiedTag = removeAttribute(modifiedTag, "outBound");
        }
        
        // 如果标签有变化，替换原内容
        if (!modifiedTag.equals(serviceTypeTag)) {
            content = content.substring(0, serviceTypeStart) + modifiedTag + content.substring(serviceTypeTagEnd + 1);
            
            // 保存修改后的内容（保持原有格式）
            Files.write(xmlFile.toPath(), content.getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING);
            
            System.out.println("  已修改XML内容: " + xmlFile.getName());
        }
    }
    
    /**
     * 修改或添加属性
     */
    private static String modifyAttribute(String tag, String attrName, String attrValue) {
        // 使用正则表达式匹配属性（不区分大小写）
        // 匹配模式：属性名="任意值" 或 属性名='任意值'，考虑前后可能有空格
        String patternStr = "\\s+" + Pattern.quote(attrName) + "\\s*=\\s*[\"'][^\"']*[\"']";
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        
        if (pattern.matcher(tag).find()) {
            // 属性已存在，替换值（保持原有格式，但统一使用双引号）
            return tag.replaceAll("(?i)" + Pattern.quote(attrName) + "\\s*=\\s*[\"'][^\"']*[\"']", 
                                 attrName + "=\"" + attrValue + "\"");
        } else {
            // 属性不存在，在 > 之前添加
            int gtIndex = tag.lastIndexOf('>');
            if (gtIndex > 0) {
                // 在 > 之前添加属性（保持原有格式）
                String before = tag.substring(0, gtIndex);
                String after = tag.substring(gtIndex);
                // 如果标签末尾有空格，直接添加；否则先加空格
                if (before.endsWith(" ") || before.endsWith("\t")) {
                    return before + attrName + "=\"" + attrValue + "\"" + after;
                } else {
                    return before + " " + attrName + "=\"" + attrValue + "\"" + after;
                }
            } else {
                // 异常情况，在标签末尾添加
                return tag.substring(0, tag.length() - 1) + " " + attrName + "=\"" + attrValue + "\">";
            }
        }
    }
    
    /**
     * 删除属性
     */
    private static String removeAttribute(String tag, String attrName) {
        // 使用正则表达式删除属性（不区分大小写）
        // 匹配：空格 + 属性名 + = + 引号内的值 + 引号
        String patternStr = "\\s+" + Pattern.quote(attrName) + "\\s*=\\s*[\"'][^\"']*[\"']";
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        String result = pattern.matcher(tag).replaceAll("");
        
        // 清理可能出现的多余空格（两个连续空格变成一个）
        result = result.replaceAll("\\s{2,}", " ");
        
        return result;
    }
}

