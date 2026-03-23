package com.sunline.dict.vectorization;

/**
 * Dict（d_schema.xml）字典类型向量化服务接口
 */
public interface DictVectorizationService {

    void vectorizeBySource(String sourceInfo);

    void deleteBySource(String sourceInfo);
}
