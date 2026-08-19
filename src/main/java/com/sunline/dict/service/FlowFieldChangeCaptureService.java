package com.sunline.dict.service;

import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureResult;

import java.util.Map;

public interface FlowFieldChangeCaptureService {

    FlowFieldChangeCaptureResult capture(Map<String, Object> payload, String eventUuid);
}
