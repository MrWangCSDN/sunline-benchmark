package com.sunline.dict.vectorization;

/**
 * MetadataTables（tables.xml）向量化服务接口
 */
public interface MetadataTablesVectorizationService {

    /**
     * 根据 from_jar 来源，对该来源下的所有 table 进行向量化并写入 Qdrant
     * 向量化粒度：以 table（table_id）为单位，每张表生成一个 Point
     *
     * @param sourceInfo metadata_tables.from_jar 字段值
     */
    void vectorizeBySource(String sourceInfo);

    /**
     * 根据 from_jar 来源，删除 Qdrant 中该来源下的所有 table 向量数据
     *
     * @param sourceInfo metadata_tables.from_jar 字段值
     */
    void deleteBySource(String sourceInfo);
}
