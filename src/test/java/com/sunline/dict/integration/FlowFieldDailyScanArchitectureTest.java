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
import org.springframework.http.server.PathContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "flow-field-change.scan.enabled=false",
        "git.gitlab.url=https://gitlab.example.test",
        "git.projects.list="
})
class FlowFieldDailyScanArchitectureTest {

    private static final String API_BASE = "/api/flow-field-change";
    private static final List<String> API_NAMESPACE_PREFIX =
            List.of("api", "flow-field-change");
    private static final byte[] CAPTURE_TYPE_TOKEN =
            "FlowFieldChangeCapture".getBytes(StandardCharsets.UTF_8);
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
    void capture_dependency_guard_detects_generic_inherited_and_bytecode_only_references()
            throws NoSuchFieldException, NoSuchMethodException {
        Type genericDependency = GenericCaptureDependency.class
                .getDeclaredField("dependencies").getGenericType();
        Type inheritedDependency = InheritedCaptureDependency.class.getGenericSuperclass();
        Type bytecodeOnlyReturn = BytecodeCaptureDependency.class
                .getDeclaredMethod("captureTypeOnlyInMethodBody").getGenericReturnType();

        assertAll(
                () -> {
                    assertTrue(containsCaptureType(genericDependency, new HashSet<>()));
                    assertThrows(AssertionError.class,
                            () -> assertNoCaptureDependency(GenericCaptureDependency.class));
                },
                () -> {
                    assertTrue(containsCaptureType(inheritedDependency, new HashSet<>()));
                    assertThrows(AssertionError.class,
                            () -> assertNoCaptureDependency(InheritedCaptureDependency.class));
                },
                () -> {
                    assertFalse(containsCaptureType(bytecodeOnlyReturn, new HashSet<>()));
                    assertThrows(AssertionError.class,
                            () -> assertNoCaptureDependency(BytecodeCaptureDependency.class));
                });
    }

    @Test
    void removed_capture_types_are_not_loadable() {
        assertAll(
                () -> assertThrows(ClassNotFoundException.class,
                        () -> Class.forName("com.sunline.dict.service.FlowFieldChangeCaptureService")),
                () -> assertThrows(ClassNotFoundException.class,
                        () -> Class.forName("com.sunline.dict.service.impl.FlowFieldChangeCaptureServiceImpl")),
                () -> assertThrows(ClassNotFoundException.class,
                        () -> Class.forName("com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta")),
                () -> assertThrows(ClassNotFoundException.class,
                        () -> Class.forName("com.sunline.dict.service.flowchange.FlowFieldChangeCaptureResult")));
    }

    @Test
    void read_only_route_guard_recognizes_template_routes_that_can_reach_the_api() {
        assertAll(
                () -> assertTrue(canReachFlowFieldChangeApi("/api/{resource}")),
                () -> assertTrue(canReachFlowFieldChangeApi("/api/{resource}/{action}")),
                () -> assertTrue(canReachFlowFieldChangeApi("/api/{resource}/{action}/{id}")),
                () -> assertTrue(canReachFlowFieldChangeApi("/api/{resource}/admin")),
                () -> assertTrue(canReachFlowFieldChangeApi("/{root}/flow-field-change/admin")),
                () -> assertTrue(canReachFlowFieldChangeApi("/api/**")),
                () -> assertFalse(canReachFlowFieldChangeApi("/api/other/**")));
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
                        .anyMatch(FlowFieldDailyScanArchitectureTest::canReachFlowFieldChangeApi))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean canReachFlowFieldChangeApi(String path) {
        PathPatternParser.defaultInstance.parse(path);
        List<String> patternSegments = List.of(path.substring(1).split("/", -1));
        Set<Integer> states = catchAllClosure(Set.of(0), patternSegments);

        for (String namespaceSegment : API_NAMESPACE_PREFIX) {
            Set<Integer> nextStates = new HashSet<>();
            for (int state : states) {
                if (state >= patternSegments.size()) {
                    continue;
                }
                String patternSegment = patternSegments.get(state);
                if (isCatchAll(patternSegment)) {
                    nextStates.add(state);
                } else if (segmentMatches(patternSegment, namespaceSegment)) {
                    nextStates.add(state + 1);
                }
            }
            states = catchAllClosure(nextStates, patternSegments);
            if (states.isEmpty()) {
                return false;
            }
        }

        // Once the fixed namespace prefix is consumable, the namespace's arbitrary
        // suffix can conservatively satisfy every remaining valid PathPattern segment.
        return true;
    }

    private static Set<Integer> catchAllClosure(
            Set<Integer> initialStates, List<String> patternSegments) {
        Set<Integer> closure = new HashSet<>(initialStates);
        boolean changed;
        do {
            changed = false;
            for (int state : Set.copyOf(closure)) {
                if (state < patternSegments.size()
                        && isCatchAll(patternSegments.get(state))
                        && closure.add(state + 1)) {
                    changed = true;
                }
            }
        } while (changed);
        return closure;
    }

    private static boolean isCatchAll(String patternSegment) {
        return "**".equals(patternSegment)
                || patternSegment.startsWith("{*") && patternSegment.endsWith("}");
    }

    private static boolean segmentMatches(String patternSegment, String pathSegment) {
        PathPattern segmentPattern =
                PathPatternParser.defaultInstance.parse("/" + patternSegment);
        return segmentPattern.matches(PathContainer.parsePath("/" + pathSegment));
    }

    private static void assertNoCaptureDependency(Class<?> componentType) {
        for (Class<?> current = componentType;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            assertNoCaptureType(componentType, current.getGenericSuperclass());
            for (Type interfaceType : current.getGenericInterfaces()) {
                assertNoCaptureType(componentType, interfaceType);
            }
            for (Field field : current.getDeclaredFields()) {
                assertNoCaptureType(componentType, field.getGenericType());
            }
            for (Constructor<?> constructor : current.getDeclaredConstructors()) {
                for (Type parameterType : constructor.getGenericParameterTypes()) {
                    assertNoCaptureType(componentType, parameterType);
                }
                for (Type exceptionType : constructor.getGenericExceptionTypes()) {
                    assertNoCaptureType(componentType, exceptionType);
                }
                for (TypeVariable<?> typeVariable : constructor.getTypeParameters()) {
                    assertNoCaptureType(componentType, typeVariable);
                }
            }
            for (Method method : current.getDeclaredMethods()) {
                assertNoCaptureType(componentType, method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertNoCaptureType(componentType, parameterType);
                }
                for (Type exceptionType : method.getGenericExceptionTypes()) {
                    assertNoCaptureType(componentType, exceptionType);
                }
                for (TypeVariable<Method> typeVariable : method.getTypeParameters()) {
                    assertNoCaptureType(componentType, typeVariable);
                }
            }
            assertNoCaptureBytecodeReference(componentType, current);
        }
    }

    private static void assertNoCaptureType(Class<?> componentType, Type type) {
        assertFalse(containsCaptureType(type, new HashSet<>()),
                () -> componentType.getSimpleName() + " depends on " + type.getTypeName());
    }

    private static boolean containsCaptureType(Type type, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return false;
        }
        if (type instanceof Class<?> classType) {
            return classType.getName().contains("FlowFieldChangeCapture")
                    || classType.isArray()
                    && containsCaptureType(classType.getComponentType(), visited);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (containsCaptureType(parameterizedType.getRawType(), visited)
                    || containsCaptureType(parameterizedType.getOwnerType(), visited)) {
                return true;
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsCaptureType(argument, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsCaptureType(arrayType.getGenericComponentType(), visited);
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            for (Type bound : typeVariable.getBounds()) {
                if (containsCaptureType(bound, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                if (containsCaptureType(bound, visited)) {
                    return true;
                }
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                if (containsCaptureType(bound, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertNoCaptureBytecodeReference(
            Class<?> componentType, Class<?> declaringType) {
        String resourceName = declaringType.getName().replace('.', '/') + ".class";
        ClassLoader classLoader = declaringType.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resourceName)) {
            assertNotNull(input, () -> "missing compiled class resource " + resourceName);
            byte[] bytecode = input.readAllBytes();
            assertFalse(containsBytes(bytecode, CAPTURE_TYPE_TOKEN),
                    () -> componentType.getSimpleName()
                            + " bytecode references a removed capture type in " + resourceName);
        } catch (IOException exception) {
            throw new AssertionError("failed to inspect compiled class " + resourceName, exception);
        }
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            int offset = 0;
            while (offset < needle.length && haystack[start + offset] == needle[offset]) {
                offset++;
            }
            if (offset == needle.length) {
                return true;
            }
        }
        return false;
    }

    private static class FlowFieldChangeCaptureMarker {
    }

    private static class GenericCaptureDependency {
        private List<FlowFieldChangeCaptureMarker> dependencies;
    }

    private static class InheritedCaptureDependency extends FlowFieldChangeCaptureMarker {
    }

    private static class BytecodeCaptureDependency {
        Object captureTypeOnlyInMethodBody() {
            return FlowFieldChangeCaptureMarker.class;
        }
    }
}
