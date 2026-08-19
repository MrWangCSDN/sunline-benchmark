package com.sunline.dict.sql;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowData;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.mapper.FlowFieldChangeQueryMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldChangeSchemaContractTest {

    @Test
    void clean_install_schema_matches_the_approved_daily_history_model_and_preserves_scan_state() throws IOException {
        String sql = normalizedResource("/sql/create_flow_field_change_tables.sql");

        assertTrue(sql.contains("create table if not exists flow_field_change_log"));
        assertTrue(sql.contains("scan_run_id bigint not null"));
        assertTrue(sql.contains("change_date date not null"));
        assertTrue(sql.contains("project_path varchar(512) null"));
        assertTrue(sql.contains("parent_sha varchar(64) null"));
        assertTrue(sql.contains("commit_sha varchar(64) not null"));
        assertTrue(sql.contains("commit_time datetime not null"));
        assertTrue(sql.contains("update_time datetime not null default current_timestamp on update current_timestamp"));
        assertTrue(sql.contains("unique key uk_ffcl_dedup (dedup_key)"));
        assertTrue(sql.contains("key idx_ffcl_change_date (change_date)"));
        assertTrue(sql.contains("key idx_ffcl_commit (project_id, commit_sha)"));
        assertTrue(sql.contains("key idx_ffcl_scan_run (scan_run_id)"));
        assertTrue(sql.contains("create table if not exists flow_field_change_detail"));
        assertTrue(sql.contains("old_snapshot longtext null"));
        assertTrue(sql.contains("new_snapshot longtext null"));
        assertTrue(sql.contains("changed_attributes longtext null"));
        assertFalse(sql.contains("foreign key"));

        assertTrue(sql.contains("create table if not exists flow_field_scan_run"));
        assertTrue(sql.contains("unique key uk_ffsr_project_window (project_id, branch, window_end)"));
        assertTrue(sql.contains("create table if not exists flow_field_scan_cursor"));
        assertTrue(sql.contains("primary key (project_id, branch)"));
    }

    @Test
    void migration_adds_nullable_compatibility_columns_and_backfills_only_the_derived_date() throws IOException {
        String sql = normalizedResource("/sql/migrate_flow_field_change_daily_scan.sql");

        assertTrue(sql.contains("alter table flow_field_change_log"));
        assertTrue(sql.contains("add column scan_run_id bigint null"));
        assertTrue(sql.contains("add column change_date date null"));
        assertTrue(sql.contains("add column project_path varchar(512) null"));
        assertTrue(sql.contains("add column parent_sha varchar(64) null"));
        assertTrue(sql.contains("add column update_time datetime not null default current_timestamp on update current_timestamp"));
        assertTrue(sql.contains("set change_date = date(coalesce(commit_time, create_time))"));
        assertTrue(sql.contains("where change_date is null"));
        assertTrue(sql.contains("add index idx_ffcl_change_date (change_date)"));
        assertTrue(sql.contains("add index idx_ffcl_commit (project_id, commit_sha)"));
        assertTrue(sql.contains("add index idx_ffcl_scan_run (scan_run_id)"));
        assertTrue(sql.contains("create table if not exists flow_field_scan_run"));
        assertTrue(sql.contains("create table if not exists flow_field_scan_cursor"));

        assertGuardedColumn(sql, "scan_run_id");
        assertGuardedColumn(sql, "change_date");
        assertGuardedColumn(sql, "project_path");
        assertGuardedColumn(sql, "parent_sha");
        assertGuardedColumn(sql, "update_time");
        assertGuardedIndex(sql, "idx_ffcl_change_date");
        assertGuardedIndex(sql, "idx_ffcl_commit");
        assertGuardedIndex(sql, "idx_ffcl_scan_run");
        assertEquals(8, occurrences(sql, "prepare ffcd_ddl from @ffcd_ddl_sql"));
        assertEquals(8, occurrences(sql, "execute ffcd_ddl"));
        assertEquals(8, occurrences(sql, "deallocate prepare ffcd_ddl"));

        assertFalse(sql.contains("drop column webhook_uuid"));
        assertFalse(sql.contains("drop column before_sha"));
        assertFalse(sql.contains("drop column after_sha"));
        assertFalse(sql.contains("set scan_run_id ="));
        assertFalse(sql.contains("set commit_sha ="));
        assertFalse(sql.contains("set commit_time ="));
    }

    @Test
    void field_query_emits_left_join_daily_only_filters_case_sensitive_search_and_stable_order() throws IOException {
        Configuration configuration = mapperConfiguration();
        FieldChangeQuery query = new FieldChangeQuery(
                1, 20, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                42L, "payments", "src/T001", "TC001", "input", "Acct",
                "MODIFY", "张三", "SUCCESS");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", new Page<FieldChangeRowData>(1, 20));
        parameters.put("query", query);

        BoundSql bound = configuration.getMappedStatement(
                FlowFieldChangeQueryMapper.class.getName() + ".selectFieldChanges")
                .getBoundSql(parameters);
        String sql = normalize(bound.getSql());
        List<String> properties = bound.getParameterMappings().stream()
                .map(ParameterMapping::getProperty).toList();

        assertTrue(sql.contains("from flow_field_change_log l left join flow_field_change_detail d on d.log_id = l.id"));
        assertTrue(sql.contains("l.scan_run_id is not null"));
        assertTrue(sql.contains("l.project_id = ?"));
        assertTrue(sql.contains("l.project_name like concat('%', ?, '%')"));
        assertTrue(sql.contains("l.file_path like concat('%', ?, '%')"));
        assertTrue(sql.contains("l.flow_id = ?"));
        assertTrue(sql.contains("d.io_type = ?"));
        assertTrue(sql.contains("binary d.field_id like concat('%', ?, '%')"));
        assertTrue(sql.contains("coalesce(d.change_type, l.file_change_type) = ?"));
        assertTrue(sql.contains("l.commit_author like concat('%', ?, '%')"));
        assertTrue(sql.contains("l.capture_status = ?"));
        assertTrue(sql.contains("l.change_date >= ?"));
        assertTrue(sql.contains("l.change_date <= ?"));
        assertTrue(sql.endsWith("order by l.commit_time desc, l.id desc, d.id asc"));
        assertTrue(properties.containsAll(List.of(
                "query.projectId", "query.projectName", "query.filePath", "query.flowId",
                "query.ioType", "query.fieldId", "query.changeType", "query.commitAuthor",
                "query.captureStatus", "query.startDate", "query.endDate")));
    }

    @Test
    void scan_run_query_emits_inclusive_window_date_and_exact_status_filters() throws IOException {
        Configuration configuration = mapperConfiguration();
        ScanRunQuery query = new ScanRunQuery(
                1, 20, 42L, "FAILED", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", new Page<>(1, 20));
        parameters.put("query", query);

        BoundSql bound = configuration.getMappedStatement(
                FlowFieldChangeQueryMapper.class.getName() + ".selectScanRuns")
                .getBoundSql(parameters);
        String sql = normalize(bound.getSql());

        assertTrue(sql.contains("from flow_field_scan_run r"));
        assertTrue(sql.contains("r.project_id = ?"));
        assertTrue(sql.contains("r.status = ?"));
        assertTrue(sql.contains("date(r.window_end) >= ?"));
        assertTrue(sql.contains("date(r.window_end) <= ?"));
        assertTrue(sql.endsWith("order by r.window_end desc, r.id desc"));
    }

    @Test
    void field_count_sql_retains_left_join_so_details_and_file_only_rows_share_page_cardinality()
            throws IOException {
        Configuration configuration = mapperConfiguration();
        FieldChangeQuery query = new FieldChangeQuery(
                1, 20, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19),
                42L, null, null, null, null, null, null, null, "SUCCESS");
        Page<FieldChangeRowData> page = new Page<>(1, 20);
        page.setOptimizeJoinOfCountSql(false);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", page);
        parameters.put("query", query);
        String rowSql = configuration.getMappedStatement(
                        FlowFieldChangeQueryMapper.class.getName() + ".selectFieldChanges")
                .getBoundSql(parameters).getSql();

        String countSql = normalize(new CountSqlProbe().countSql(page, rowSql));

        assertTrue(countSql.startsWith("select count(*) as total"));
        assertTrue(countSql.contains(
                "from flow_field_change_log l left join flow_field_change_detail d on d.log_id = l.id"));
        assertTrue(countSql.contains("l.scan_run_id is not null"));
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        String resource = "mapper/FlowFieldChangeQueryMapper.xml";
        try (InputStream input = getClass().getResourceAsStream("/" + resource)) {
            if (input == null) {
                throw new IOException("missing resource: " + resource);
            }
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalizedResource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing resource: " + path);
            }
            return normalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static void assertGuardedColumn(String sql, String column) {
        assertTrue(sql.contains("from information_schema.columns where table_schema = database() "
                        + "and table_name = 'flow_field_change_log' and column_name = '" + column + "'"),
                "missing retry guard for column " + column);
    }

    private static void assertGuardedIndex(String sql, String index) {
        assertTrue(sql.contains("from information_schema.statistics where table_schema = database() "
                        + "and table_name = 'flow_field_change_log' and index_name = '" + index + "'"),
                "missing retry guard for index " + index);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static final class CountSqlProbe extends PaginationInnerInterceptor {
        String countSql(Page<?> page, String sql) {
            return autoCountSql(page, sql);
        }
    }
}
