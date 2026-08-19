package com.sunline.dict.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguredGitLabProjectProviderImplTest {

    @Test
    void parsesDeduplicatesAndPreservesConfiguredProjectOrder() {
        assertEquals(List.of(42L, 7L), provider(" 42,7,42 ").projectIds());
    }

    @Test
    void blankProjectConfigurationMeansNoProjects() {
        assertEquals(List.of(), provider(" ").projectIds());
    }

    @Test
    void rejectsZeroNegativeAndNonNumericProjectIdsBeforeAnyApiAccess() {
        for (String configuredProjects : List.of("0", "-7", "42,seven")) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> provider(configuredProjects));

            assertEquals("git.projects.list must contain positive numeric project IDs", exception.getMessage());
        }
    }

    private static ConfiguredGitLabProjectProviderImpl provider(String configuredProjects) {
        return new ConfiguredGitLabProjectProviderImpl(configuredProjects);
    }
}
