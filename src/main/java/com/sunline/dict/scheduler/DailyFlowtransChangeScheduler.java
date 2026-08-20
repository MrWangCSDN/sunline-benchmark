package com.sunline.dict.scheduler;

import com.sunline.dict.service.FlowFieldDailyScanService;
import com.sunline.dict.service.FlowFieldDailyScanService.BatchScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(
        prefix = "flow-field-change.scan",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DailyFlowtransChangeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyFlowtransChangeScheduler.class);

    private final FlowFieldDailyScanService dailyScanService;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public DailyFlowtransChangeScheduler(
            FlowFieldDailyScanService dailyScanService,
            ObjectProvider<Clock> clockProvider,
            @Value("${flow-field-change.scan.zone:Asia/Shanghai}") String zone) {
        this(dailyScanService, clockProvider.getIfAvailable(Clock::systemDefaultZone),
                ZoneId.of(zone));
    }

    public DailyFlowtransChangeScheduler(FlowFieldDailyScanService dailyScanService,
                                         Clock clock,
                                         ZoneId zone) {
        this.dailyScanService = dailyScanService;
        this.clock = clock;
        this.zone = zone;
    }

    @Scheduled(
            cron = "${flow-field-change.scan.cron:0 0 22 * * ?}",
            zone = "${flow-field-change.scan.zone:Asia/Shanghai}")
    public void scanDailyChanges() {
        BatchScanResult result = dailyScanService.scanAll(
                LocalDate.now(clock.withZone(zone)).atTime(22, 0));
        if (result.attemptedProjects() == 0 || result.errorProjects() > 0) {
            log.warn("Daily flowtrans scan completed; attemptedProjects={}, "
                            + "successfulProjects={}, errorProjects={}",
                    result.attemptedProjects(), result.successfulProjects(), result.errorProjects());
        } else {
            log.info("Daily flowtrans scan completed; attemptedProjects={}, "
                            + "successfulProjects={}, errorProjects={}",
                    result.attemptedProjects(), result.successfulProjects(), result.errorProjects());
        }
    }
}
