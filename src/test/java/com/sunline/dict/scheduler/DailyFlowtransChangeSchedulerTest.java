package com.sunline.dict.scheduler;

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

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

        scheduler.scanDailyChanges();

        verify(scanService).scanAll(LocalDateTime.of(2026, 8, 19, 22, 0));
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
    void enabled_scheduler_does_not_break_contexts_without_gitlab_scan_infrastructure() {
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulerOnlyTestConfiguration.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    DailyFlowtransChangeScheduler scheduler =
                            context.getBean(DailyFlowtransChangeScheduler.class);
                    assertDoesNotThrow(scheduler::scanDailyChanges);
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
}
