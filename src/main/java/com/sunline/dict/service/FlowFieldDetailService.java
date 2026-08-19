package com.sunline.dict.service;

import com.sunline.dict.entity.FlowFieldDetail;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

/**
 * 交易字段明细服务（薄壳）：调用 FlowFieldExtractor 提取，再写 mapper 落库。
 */
public interface FlowFieldDetailService {

    /**
     * 解析 .flowtrans.xml 根节点，把 input/output 字段平铺入库。
     * 先 DELETE BY flowId 再 batch INSERT，幂等。
     *
     * @return { "inputCount": N, "outputCount": M }
     */
    Map<String, Integer> extractAndSave(Element flowtranRoot, String flowId, String sourceInfo);

    /**
     * 按 flow_id + io_type 查询字段清单
     */
    List<FlowFieldDetail> getByFlowIdAndIoType(String flowId, String ioType);

    /**
     * 按来源标识（source_info，如 projectName:master:filePath）查询字段清单。
     * 用于 Webhook 变更 diff 时读取「修改前」的字段集合。
     */
    List<FlowFieldDetail> getBySourceInfo(String sourceInfo);

    /**
     * 按来源标识删除字段清单（文件被删除时清理孤儿数据）。
     * @return 删除的条数
     */
    int deleteBySourceInfo(String sourceInfo);
}
