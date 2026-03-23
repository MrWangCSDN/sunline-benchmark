package com.sunline.dict.vectorization;

/**
 * Uschema（u_schema.xml）限制类型/基本类型向量化服务接口
 */
public interface UschemaVectorizationService {

    void vectorizeBySource(String sourceInfo);

    void deleteBySource(String sourceInfo);
}
