package com.sunline.dict.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldChangeSchemaContractTest {

    @Test
    void schema_matches_the_approved_mariadb_mysql_contract() throws IOException {
        String sql = resource("/sql/create_flow_field_change_tables.sql").toLowerCase();

        assertTrue(sql.contains("create table if not exists flow_field_change_log"));
        assertTrue(sql.contains("create table if not exists flow_field_change_detail"));
        assertTrue(sql.contains("dedup_key            char(64)      not null"));
        assertTrue(sql.contains("unique key uk_ffcl_dedup (dedup_key)"));
        assertTrue(sql.contains("file_change_type     varchar(16)   not null"));
        assertTrue(sql.contains("capture_status       varchar(16)   not null"));
        assertTrue(sql.contains("error_message        text          null"));
        assertTrue(sql.contains("before_sha           varchar(64)   null"));
        assertTrue(sql.contains("after_sha            varchar(64)   null"));
        assertTrue(sql.contains("add_count            int           not null default 0"));
        assertTrue(sql.contains("modify_count         int           not null default 0"));
        assertTrue(sql.contains("remove_count         int           not null default 0"));
        assertTrue(sql.contains("input_change_count   int           not null default 0"));
        assertTrue(sql.contains("output_change_count  int           not null default 0"));
        assertTrue(sql.contains("field_path           varchar(1024) not null"));
        assertTrue(sql.contains("old_snapshot         longtext      null"));
        assertTrue(sql.contains("new_snapshot         longtext      null"));
        assertTrue(sql.contains("changed_attributes   longtext      null"));
        assertTrue(sql.contains("engine=innodb default charset=utf8mb4"));
        assertFalse(sql.contains("foreign key"));
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
