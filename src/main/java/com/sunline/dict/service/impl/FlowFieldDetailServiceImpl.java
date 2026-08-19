package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.mapper.FlowFieldDetailMapper;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.FlowFieldExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlowFieldDetailServiceImpl implements FlowFieldDetailService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldDetailServiceImpl.class);

    @Autowired
    private FlowFieldDetailMapper flowFieldDetailMapper;

    /** 共用一个 extractor 实例（纯函数，线程安全） */
    private final FlowFieldExtractor extractor = new FlowFieldExtractor();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> extractAndSave(Element flowtranRoot, String flowId, String sourceInfo) {
        FlowFieldExtractor.ExtractResult parsed = extractor.extract(flowtranRoot);

        // 先删后插：用 QueryWrapper 替代自定义 mapper XML（与 sibling FlowStep 等一致）
        QueryWrapper<FlowFieldDetail> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("flow_id", flowId);
        flowFieldDetailMapper.delete(deleteWrapper);

        LocalDateTime now = LocalDateTime.now();
        for (FlowFieldExtractor.FieldRow row : parsed.inputs) {
            flowFieldDetailMapper.insert(toEntity(row, flowId, "input", sourceInfo, now));
        }
        for (FlowFieldExtractor.FieldRow row : parsed.outputs) {
            flowFieldDetailMapper.insert(toEntity(row, flowId, "output", sourceInfo, now));
        }

        log.info("flow_field_detail 入库 flowId={} input={} output={}",
                flowId, parsed.inputs.size(), parsed.outputs.size());

        Map<String, Integer> ret = new HashMap<>();
        ret.put("inputCount",  parsed.inputs.size());
        ret.put("outputCount", parsed.outputs.size());
        return ret;
    }

    @Override
    public List<FlowFieldDetail> getByFlowIdAndIoType(String flowId, String ioType) {
        QueryWrapper<FlowFieldDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("flow_id", flowId)
               .eq("io_type", ioType)
               .orderByAsc("id");
        return flowFieldDetailMapper.selectList(wrapper);
    }

    @Override
    public List<FlowFieldDetail> getBySourceInfo(String sourceInfo) {
        QueryWrapper<FlowFieldDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("source_info", sourceInfo).orderByAsc("id");
        return flowFieldDetailMapper.selectList(wrapper);
    }

    @Override
    public int deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<FlowFieldDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("source_info", sourceInfo);
        return flowFieldDetailMapper.delete(wrapper);
    }

    private FlowFieldDetail toEntity(FlowFieldExtractor.FieldRow row, String flowId,
                                      String ioType, String sourceInfo, LocalDateTime now) {
        FlowFieldDetail e = new FlowFieldDetail();
        e.setFlowId(flowId);
        e.setIoType(ioType);
        e.setFieldId(row.fieldId);
        e.setFieldType(row.type);
        e.setLongname(row.longname);
        e.setRef(row.ref);
        e.setRequired(row.required);
        e.setMulti(row.multi);
        e.setArrayFlag(row.arrayFlag);
        e.setSourceInfo(sourceInfo);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        return e;
    }
}
