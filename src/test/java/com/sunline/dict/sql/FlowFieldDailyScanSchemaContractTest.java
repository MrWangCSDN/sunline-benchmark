package com.sunline.dict.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldDailyScanSchemaContractTest {

    @Test
    void deployment_schema_contains_complete_approved_scan_run_and_cursor_tables() throws IOException {
        String sql = normalize(resource("/sql/create_flow_field_change_tables.sql"));
        assertTrue(sql.endsWith(";"), "deployment SQL must end with a complete statement");
        List<String> statements = Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();

        String run = onlyCreateTable(statements, "flow_field_scan_run");
        assertContainsAll(run,
                "id bigint primary key auto_increment",
                "project_id bigint not null",
                "project_name varchar(255) null",
                "project_path varchar(512) null",
                "branch varchar(64) not null default 'master'",
                "window_start datetime not null",
                "window_end datetime not null",
                "status varchar(32) not null",
                "commit_count int not null default 0",
                "changed_file_count int not null default 0",
                "history_count int not null default 0",
                "failed_file_count int not null default 0",
                "skipped_count int not null default 0",
                "cursor_advanced tinyint(1) not null default 0",
                "error_message text null",
                "started_at datetime not null",
                "finished_at datetime null",
                "create_time datetime not null default current_timestamp",
                "update_time datetime not null default current_timestamp on update current_timestamp",
                "unique key uk_ffsr_project_window (project_id, branch, window_end)",
                "key idx_ffsr_status_time (status, started_at)",
                "key idx_ffsr_project_time (project_id, window_end)",
                "engine=innodb default charset=utf8mb4");

        String cursor = onlyCreateTable(statements, "flow_field_scan_cursor");
        assertContainsAll(cursor,
                "project_id bigint not null",
                "branch varchar(64) not null default 'master'",
                "project_name varchar(255) null",
                "project_path varchar(512) null",
                "last_success_end datetime not null",
                "last_run_id bigint null",
                "update_time datetime not null default current_timestamp on update current_timestamp",
                "primary key (project_id, branch)",
                "engine=innodb default charset=utf8mb4");
    }

    private static String onlyCreateTable(List<String> statements, String table) {
        List<String> matches = statements.stream()
                .filter(statement -> statement.startsWith("create table if not exists " + table + " "))
                .toList();
        assertEquals(1, matches.size(), "expected one complete definition for " + table);
        return matches.get(0);
    }

    private static void assertContainsAll(String statement, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(statement.contains(fragment), () -> "missing schema contract: " + fragment);
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
