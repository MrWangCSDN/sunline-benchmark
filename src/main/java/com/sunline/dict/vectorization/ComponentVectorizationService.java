package com.sunline.dict.vectorization;

/**
 * Component（pbcb/pbcp/pbcc/pbct）向量化服务接口
 */
public interface ComponentVectorizationService {

    /**
     * 根据 from_jar 来源，对该来源下的所有 component 进行向量化并写入 Qdrant
     *
     * @param sourceInfo component.from_jar 字段值
     */
    void vectorizeBySource(String sourceInfo);

    /**
     * 根据 from_jar 来源，删除 Qdrant 中该来源下的所有 component 向量数据
     *
     * @param sourceInfo component.from_jar 字段值
     */
    void deleteBySource(String sourceInfo);
}
