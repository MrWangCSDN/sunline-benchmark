/**
 * XmlFileRenamer 配置示例
 * 
 * 复制以下代码到 XmlFileRenamer.java 的 getPathTypeMapping() 方法中
 */

// ========== 示例1：单个路径对应一种类型 ==========
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
    pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
    pathTypeMapping.put("D:\\code\\project3\\src", "pbcb");
    
    return pathTypeMapping;
}

// ========== 示例2：多个路径对应同一种类型 ==========
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // 多个路径都处理为 pbs 类型
    String[] pbsPaths = {
        "D:\\code\\project1\\src",
        "D:\\code\\project2\\src",
        "D:\\code\\project3\\src"
    };
    for (String path : pbsPaths) {
        pathTypeMapping.put(path, "pbs");
    }
    
    return pathTypeMapping;
}

// ========== 示例3：混合配置（推荐） ==========
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // 单个路径配置
    pathTypeMapping.put("D:\\code\\project1\\src", "pbs");
    pathTypeMapping.put("D:\\code\\project2\\src", "pcs");
    
    // 多个路径对应 pbcb 类型
    String[] pbcbPaths = {
        "D:\\code\\project3\\src",
        "D:\\code\\project4\\src",
        "D:\\code\\project5\\src"
    };
    for (String path : pbcbPaths) {
        pathTypeMapping.put(path, "pbcb");
    }
    
    // 多个路径对应 pbct 类型
    String[] pbctPaths = {
        "D:\\code\\module1\\src",
        "D:\\code\\module2\\src"
    };
    for (String path : pbctPaths) {
        pathTypeMapping.put(path, "pbct");
    }
    
    return pathTypeMapping;
}

// ========== 示例4：实际项目配置 ==========
private static Map<String, String> getPathTypeMapping() {
    Map<String, String> pathTypeMapping = new HashMap<>();
    
    // PBS 相关项目
    String[] pbsProjects = {
        "D:\\workspace\\ccbs-online-dist\\ccbs-dept-impl\\src\\main\\resources",
        "D:\\workspace\\ccbs-online-dist\\ccbs-ap\\src\\main\\resources"
    };
    for (String path : pbsProjects) {
        pathTypeMapping.put(path, "pbs");
    }
    
    // PCS 相关项目
    String[] pcsProjects = {
        "D:\\workspace\\ccbs-online-dist\\ccbs-sett-impl\\src\\main\\resources"
    };
    for (String path : pcsProjects) {
        pathTypeMapping.put(path, "pcs");
    }
    
    // PBCB 相关项目
    String[] pbcbProjects = {
        "D:\\workspace\\ccbs-online-dist\\ccbs-loan-impl\\src\\main\\resources",
        "D:\\workspace\\ccbs-online-dist\\ccbs-comm-impl\\src\\main\\resources"
    };
    for (String path : pbcbProjects) {
        pathTypeMapping.put(path, "pbcb");
    }
    
    return pathTypeMapping;
}

