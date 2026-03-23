package com.sunline.dict.vectorization;

/**
 * Flowtran 向量化服务接口
 */
public interface FlowtranVectorizationService {

    /**
     * 根据 from_jar 来源，对该来源下的所有 flowtran 进行向量化并写入 Qdrant
     * 在 flowtransXml parseAndSave 成功后调用
     *
     * @param sourceInfo flowtran.from_jar 字段值（如 "ccbs-dept-impl:master:dept-pbf/..."）
     */
    void vectorizeBySource(String sourceInfo);

    /**
     * 根据 from_jar 来源，删除 Qdrant 中该来源下的所有 flowtran 向量数据
     * 在 deleteBySourceInfo 调用后同步删除
     *
     * @param sourceInfo flowtran.from_jar 字段值
     */
    void deleteBySource(String sourceInfo);
}
