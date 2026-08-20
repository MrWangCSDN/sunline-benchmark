package com.sunline.dict.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ApplicationLoggingConfigurationTest {

    @Test
    void default_application_resource_does_not_enable_mybatis_stdout_sql_logging()
            throws IOException {
        String defaultApplication = Files.readString(
                Path.of("src/main/resources/application.yml"));

        assertFalse(defaultApplication.contains("org.apache.ibatis.logging.stdout.StdOutImpl"));
    }
}
