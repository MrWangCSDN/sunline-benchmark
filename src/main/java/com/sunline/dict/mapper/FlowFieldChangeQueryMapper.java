package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowData;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlowFieldChangeQueryMapper {

    Page<FieldChangeRowData> selectFieldChanges(
            @Param("page") Page<FieldChangeRowData> page,
            @Param("query") FieldChangeQuery query);

    Page<ScanRunView> selectScanRuns(
            @Param("page") Page<ScanRunView> page,
            @Param("query") ScanRunQuery query);
}
