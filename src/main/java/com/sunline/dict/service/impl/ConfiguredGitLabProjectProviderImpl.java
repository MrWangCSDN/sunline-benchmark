package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.ConfiguredGitLabProjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Parses the explicit GitLab project list without broadening the scan scope. */
@Service
public class ConfiguredGitLabProjectProviderImpl implements ConfiguredGitLabProjectProvider {

    private final List<Long> projectIds;

    public ConfiguredGitLabProjectProviderImpl(@Value("${git.projects.list:}") String configuredProjects) {
        this.projectIds = parse(configuredProjects);
    }

    @Override
    public List<Long> projectIds() {
        return projectIds;
    }

    private static List<Long> parse(String configuredProjects) {
        if (configuredProjects == null || configuredProjects.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String item : configuredProjects.split(",", -1)) {
            try {
                long projectId = Long.parseLong(item.trim());
                if (projectId <= 0) {
                    throw new NumberFormatException();
                }
                ids.add(projectId);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("git.projects.list must contain positive numeric project IDs");
            }
        }
        return List.copyOf(new ArrayList<>(ids));
    }
}
