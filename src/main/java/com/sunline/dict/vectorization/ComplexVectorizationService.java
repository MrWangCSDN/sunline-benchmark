package com.sunline.dict.vectorization;

/**
 * Complex（c_schema.xml）复合类型向量化服务接口
 */
public interface ComplexVectorizationService {

    /**
     * 根据 from_jar 来源，对该来源下的所有 complex 进行向量化并写入 Qdrant
     * 向量化粒度：以 complex_pojo（complex_pojo_id）为单位，每个对象生成一个 Point
     *
     * @param sourceInfo complex.from_jar 字段值
     */
    void vectorizeBySource(String sourceInfo);

    /**
     * 根据 from_jar 来源，删除 Qdrant 中该来源下的所有 complex 向量数据
     *
     * @param sourceInfo complex.from_jar 字段值
     */
    void deleteBySource(String sourceInfo);
}
