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
}
