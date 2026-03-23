package com.sunline.dict.vectorization;

/**
 * Service（pcs/pbs）向量化服务接口
 */
public interface ServiceVectorizationService {

    /**
     * 根据 from_jar 来源，对该来源下的所有 service 进行向量化并写入 Qdrant
     * 在 pcs/pbs parseAndSave 成功后调用
     *
     * @param sourceInfo service.from_jar 字段值
     */
    void vectorizeBySource(String sourceInfo);

    /**
     * 根据 from_jar 来源，删除 Qdrant 中该来源下的所有 service 向量数据
     * 在 deleteBySourceInfo 后调用
     *
     * @param sourceInfo service.from_jar 字段值
     */
    void deleteBySource(String sourceInfo);
}
