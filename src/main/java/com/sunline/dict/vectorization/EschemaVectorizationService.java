package com.sunline.dict.vectorization;

/**
 * Eschema（e_schema.xml）枚举类型向量化服务接口
 */
public interface EschemaVectorizationService {

    void vectorizeBySource(String sourceInfo);

    void deleteBySource(String sourceInfo);
}
