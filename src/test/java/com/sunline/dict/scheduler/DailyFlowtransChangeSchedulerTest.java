package com.sunline.dict.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sunline.dict.DictManagerApplication;
import com.sunline.dict.service.FlowFieldDailyScanService;
import com.sunline.dict.service.FlowFieldDailyScanService.BatchScanResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyFlowtransChangeSchedulerTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerTestConfiguration.class);

    @Test
    void scheduled_scan_uses_the_shanghai_calendar_day_and_fixed_2200_window_end() {
        FlowFieldDailyScanService scanService = mock(FlowFieldDailyScanService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T14:00:00Z"), ZoneId.of("UTC"));
        DailyFlowtransChangeScheduler scheduler =
                new DailyFlowtransChangeScheduler(scanService, clock, SHANGHAI);
        when(scanService.scanAll(LocalDateTime.of(2026, 8, 19, 22, 0)))
                .thenReturn(new BatchScanResult(1, 1, 0));

        scheduler.scanDailyChanges();

        verify(scanService).scanAll(LocalDateTime.of(2026, 8, 19, 22, 0));
    }

    @Test
    void empty_project_scan_logs_warning_summary_with_exact_counts() {
        assertSummary(new BatchScanResult(0, 0, 0), Level.WARN);
    }

    @Test
    void scan_with_project_errors_logs_warning_summary_with_exact_counts() {
        assertSummary(new BatchScanResult(3, 1, 2), Level.WARN);
    }

    @Test
    void successful_scan_logs_info_summary_with_exact_counts() {
        assertSummary(new BatchScanResult(3, 3, 0), Level.INFO);
    }

    @Test
    void scheduling_annotations_define_the_exact_cron_zone_and_enable_gate() throws Exception {
        Method method = DailyFlowtransChangeScheduler.class.getDeclaredMethod("scanDailyChanges");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals("${flow-field-change.scan.cron:0 0 22 * * ?}", scheduled.cron());
        assertEquals("${flow-field-change.scan.zone:Asia/Shanghai}", scheduled.zone());

        ConditionalOnProperty condition =
                DailyFlowtransChangeScheduler.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition);
        assertEquals("flow-field-change.scan", condition.prefix());
        assertArrayEquals(new String[] {"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertTrue(condition.matchIfMissing());

        assertNotNull(DictManagerApplication.class.getAnnotation(EnableScheduling.class));
    }

    @Test
    void disabled_scan_property_omits_the_scheduler_bean() {
        contextRunner
                .withPropertyValues("flow-field-change.scan.enabled=false")
                .run(context -> assertTrue(
                        context.getBeansOfType(DailyFlowtransChangeScheduler.class).isEmpty()));
    }

    @Test
    void enabled_scheduler_requires_daily_scan_infrastructure() {
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulerOnlyTestConfiguration.class)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(failure.getMessage().contains("FlowFieldDailyScanService"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DailyFlowtransChangeScheduler.class)
    static class SchedulerTestConfiguration {

        @Bean
        FlowFieldDailyScanService flowFieldDailyScanService() {
            return windowEnd -> new BatchScanResult(0, 0, 0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DailyFlowtransChangeScheduler.class)
    static class SchedulerOnlyTestConfiguration {
    }

    private void assertSummary(BatchScanResult result, Level expectedLevel) {
        FlowFieldDailyScanService scanService = windowEnd -> result;
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T14:00:00Z"), ZoneId.of("UTC"));
        DailyFlowtransChangeScheduler scheduler =
                new DailyFlowtransChangeScheduler(scanService, clock, SHANGHAI);

        List<ILoggingEvent> events = captureLogs(scheduler::scanDailyChanges);

        assertEquals(1, events.size());
        assertEquals(expectedLevel, events.get(0).getLevel());
        assertEquals("Daily flowtrans scan completed; attemptedProjects="
                        + result.attemptedProjects() + ", successfulProjects="
                        + result.successfulProjects() + ", errorProjects="
                        + result.errorProjects(),
                events.get(0).getFormattedMessage());
    }

    private List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(DailyFlowtransChangeScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
