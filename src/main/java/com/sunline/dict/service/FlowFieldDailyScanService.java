package com.sunline.dict.service;

import java.time.LocalDateTime;

/** Coordinates the daily commit-by-commit Flowtrans interface scan. */
public interface FlowFieldDailyScanService {

    BatchScanResult scanAll(LocalDateTime windowEnd);

    record BatchScanResult(int attemptedProjects, int successfulProjects, int errorProjects) {
    }
}
