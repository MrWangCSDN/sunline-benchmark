package com.sunline.dict.frontend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldChangeFrontendContractTest {

    @Test
    void index_wires_history_menu_permission_iframe_and_title() throws IOException {
        String index = resource("/static/index.html");

        assertTrue(index.contains("hasMenuPermission('flow-field-change-history')"));
        assertTrue(index.contains("switchView('flow-field-change-history')"));
        assertTrue(index.contains("src=\"/flow-field-change-history.html\""));
        assertTrue(index.contains("'flow-field-change-history': '📜 交易接口变动历史'"));
    }

    @Test
    void page_exposes_filters_and_read_only_api_contract() throws IOException {
        String page = resource("/static/flow-field-change-history.html");

        assertTrue(page.contains("/api/flow-field-change/list"));
        assertTrue(page.contains("/api/flow-field-change/detail/"));
        assertTrue(page.contains("交易码"));
        assertTrue(page.contains("文件路径"));
        assertTrue(page.contains("提交人"));
        assertTrue(page.contains("文件变动"));
        assertTrue(page.contains("采集状态"));
        assertTrue(page.contains("开始时间"));
        assertTrue(page.contains("结束时间"));
        assertTrue(page.contains("queryHistory"));
        assertTrue(page.contains("resetFilters"));
        assertTrue(page.contains("changePage"));
        assertTrue(page.contains("value.trim() !== ''"));
        assertFalse(page.contains("删除历史"));
    }

    @Test
    void page_keeps_loading_empty_error_failure_and_keyboard_states_visible() throws IOException {
        String page = resource("/static/flow-field-change-history.html");

        assertTrue(page.contains("正在加载变动历史"));
        assertTrue(page.contains("暂无匹配的变动记录"));
        assertTrue(page.contains("加载失败"));
        assertTrue(page.contains("采集失败"));
        assertTrue(page.contains("record.errorMessage"));
        assertTrue(page.contains("captureStatus === 'FAILED'"));
        assertTrue(page.contains("activeIo: 'input'"));
        assertTrue(page.contains("@keyup.esc=\"closeDetail\""));
        assertTrue(page.contains("aria-modal=\"true\""));
        assertTrue(page.contains("aria-live=\"polite\""));
    }

    @Test
    void page_groups_io_and_renders_semantic_complete_or_changed_snapshots() throws IOException {
        String page = resource("/static/flow-field-change-history.html");

        assertTrue(page.contains("activeIo === 'input'"));
        assertTrue(page.contains("activeIo === 'output'"));
        assertTrue(page.contains("detail.ioType === activeIo"));
        assertTrue(page.contains("changeType === 'ADD'"));
        assertTrue(page.contains("changeType === 'MODIFY'"));
        assertTrue(page.contains("changeType === 'DELETE'"));
        assertTrue(page.contains("新增"));
        assertTrue(page.contains("修改"));
        assertTrue(page.contains("删除"));
        assertTrue(page.contains("diff-badge--add"));
        assertTrue(page.contains("diff-badge--modify"));
        assertTrue(page.contains("diff-badge--delete"));
        assertTrue(page.contains("detail.newSnapshot"));
        assertTrue(page.contains("detail.oldSnapshot"));
        assertTrue(page.contains("detail.changedAttributes"));
        assertTrue(page.contains("属性"));
        assertTrue(page.contains("原值"));
        assertTrue(page.contains("新值"));
    }

    @Test
    void both_menu_scripts_define_the_same_enabled_idempotent_child() throws IOException {
        assertHistoryMenuSql(resource("/sql/create_menu_table.sql"));
        assertHistoryMenuSql(resource("/sql/add_flow_field_change_history_menu.sql"));
    }

    private void assertHistoryMenuSql(String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("'flow-field-change-history', '交易接口变动历史'"));
        assertTrue(normalized.contains("FROM sys_menu WHERE menu_code = 'dict-management'"));
        assertTrue(normalized.contains("id, 2, '📜', 5, 1"));
        assertTrue(normalized.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(normalized.contains("menu_name = '交易接口变动历史'"));
        assertTrue(normalized.contains("parent_id = VALUES(parent_id)"));
        assertTrue(normalized.contains("status = 1"));
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
