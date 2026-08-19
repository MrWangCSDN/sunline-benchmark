package com.sunline.dict.integration;

import com.sunline.dict.controller.WebhookController;
import com.sunline.dict.scheduler.DailyFlowtransChangeScheduler;
import com.sunline.dict.service.FlowFieldDailyScanService;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService;
import com.sunline.dict.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "flow-field-change.scan.enabled=false",
        "git.gitlab.url=https://gitlab.example.test",
        "git.projects.list="
})
class FlowFieldDailyScanArchitectureTest {

    private static final String API_BASE = "/api/flow-field-change";
    private static final Set<RequestMethod> WRITE_METHODS =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void disabled_daily_scan_keeps_services_but_omits_the_property_gated_scheduler()
            throws NoSuchMethodException {
        assertEquals("false", environment.getProperty("flow-field-change.scan.enabled"));
        assertEquals("https://gitlab.example.test", environment.getProperty("git.gitlab.url"));
        assertTrue(context.getBeansOfType(DailyFlowtransChangeScheduler.class).isEmpty());
        assertNotNull(context.getBean(FlowFieldDailyScanService.class));
        assertNotNull(context.getBean(FlowFieldScanStateService.class));

        ConditionalOnProperty gate =
                DailyFlowtransChangeScheduler.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(gate);
        assertEquals("flow-field-change.scan", gate.prefix());
        assertArrayEquals(new String[] {"enabled"}, gate.name());
        assertEquals("true", gate.havingValue());
        assertTrue(gate.matchIfMissing());

        Method scheduledMethod =
                DailyFlowtransChangeScheduler.class.getDeclaredMethod("scanDailyChanges");
        Scheduled schedule = scheduledMethod.getAnnotation(Scheduled.class);
        assertNotNull(schedule);
        assertEquals("${flow-field-change.scan.cron:0 0 22 * * ?}", schedule.cron());
        assertEquals("${flow-field-change.scan.zone:Asia/Shanghai}", schedule.zone());
    }

    @Test
    void webhook_components_have_no_capture_service_dependency() {
        assertNoCaptureDependency(WebhookController.class);
        assertNoCaptureDependency(WebhookServiceImpl.class);
    }

    @Test
    void flow_field_change_api_exposes_only_the_three_read_endpoints() {
        Set<String> getPaths = new TreeSet<>();

        for (RequestMappingInfo mapping : flowFieldChangeMappings().keySet()) {
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            assertFalse(methods.isEmpty(),
                    () -> "unrestricted HTTP mapping exposed under read-only API: " + mapping);
            if (methods.contains(RequestMethod.GET)) {
                getPaths.addAll(mapping.getPatternValues());
            }
            assertTrue(methods.stream().noneMatch(WRITE_METHODS::contains),
                    () -> "write mapping exposed under read-only API: " + mapping);
        }

        assertEquals(3, getPaths.size());
        assertEquals(Set.of(
                API_BASE + "/list",
                API_BASE + "/detail/{logId}",
                API_BASE + "/scan-runs"), getPaths);
    }

    private Map<RequestMappingInfo, ?> flowFieldChangeMappings() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().stream()
                        .anyMatch(path -> path.startsWith(API_BASE)))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void assertNoCaptureDependency(Class<?> componentType) {
        for (Field field : componentType.getDeclaredFields()) {
            assertFalse(isCaptureType(field.getType()),
                    () -> componentType.getSimpleName() + " depends on " + field.getType().getName());
        }
        for (Constructor<?> constructor : componentType.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertFalse(isCaptureType(parameterType),
                        () -> componentType.getSimpleName() + " depends on " + parameterType.getName());
            }
        }
        for (Method method : componentType.getDeclaredMethods()) {
            assertFalse(isCaptureType(method.getReturnType()),
                    () -> componentType.getSimpleName() + " depends on "
                            + method.getReturnType().getName());
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertFalse(isCaptureType(parameterType),
                        () -> componentType.getSimpleName() + " depends on "
                                + parameterType.getName());
            }
        }
    }

    private static boolean isCaptureType(Class<?> type) {
        return type.getName().contains("FlowFieldChangeCapture");
    }
}
