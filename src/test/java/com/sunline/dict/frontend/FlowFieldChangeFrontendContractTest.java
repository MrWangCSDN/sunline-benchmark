package com.sunline.dict.frontend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldChangeFrontendContractTest {

    private static final Pattern INLINE_SCRIPT = Pattern.compile(
            "<script>\\s*(.*?)\\s*</script>", Pattern.DOTALL);

    @Test
    void index_wires_history_menu_permission_iframe_and_title() throws IOException {
        String index = resource("/static/index.html");

        assertTrue(index.contains("hasMenuPermission('flow-field-change-history')"));
        assertTrue(index.contains("switchView('flow-field-change-history')"));
        assertTrue(index.contains("src=\"/flow-field-change-history.html\""));
        assertTrue(index.contains("'flow-field-change-history': '📜 交易接口变动历史'"));
    }

    @Test
    void page_exposes_complete_field_day_filters_rows_and_commit_metadata() throws IOException {
        String page = resource("/static/flow-field-change-history.html");

        assertTrue(page.contains("v-model=\"historyFilters.projectName\""));
        assertTrue(page.contains("v-model=\"historyFilters.projectId\""));
        assertTrue(page.contains("v-model=\"historyFilters.filePath\""));
        assertTrue(page.contains("v-model=\"historyFilters.flowId\""));
        assertTrue(page.contains("v-model=\"historyFilters.ioType\""));
        assertTrue(page.contains("v-model=\"historyFilters.fieldId\""));
        assertTrue(page.contains("v-model=\"historyFilters.changeType\""));
        assertTrue(page.contains("v-model=\"historyFilters.commitAuthor\""));
        assertTrue(page.contains("v-model=\"historyFilters.captureStatus\""));
        assertTrue(page.contains("value=\"ADD\""));
        assertTrue(page.contains("value=\"MODIFY\""));
        assertTrue(page.contains("value=\"DELETE\""));
        assertTrue(page.contains("value=\"SUCCESS\""));
        assertTrue(page.contains("value=\"FAILED\""));

        assertTrue(page.contains("v-for=\"(record, index) in historyRecords\""));
        assertTrue(page.contains("record.projectName"));
        assertTrue(page.contains("record.projectId"));
        assertTrue(page.contains("record.filePath"));
        assertTrue(page.contains("record.flowId"));
        assertTrue(page.contains("record.ioType || '—'"));
        assertTrue(page.contains("record.fieldPath || '—'"));
        assertTrue(page.contains("record.fieldId || '—'"));
        assertTrue(page.contains("attributeEntries(record.changedAttributes)"));
        assertTrue(page.contains("record.errorMessage || '采集失败，未生成字段详情'"));
        assertTrue(page.contains("record.commitSha"));
        assertTrue(page.contains("record.commitAuthor"));
        assertTrue(page.contains("record.commitEmail"));
        assertTrue(page.contains("record.commitMessage"));
        assertTrue(page.contains("record.commitTime"));
        assertTrue(page.contains("record.captureStatus !== 'FAILED'"));
    }

    @Test
    void page_renders_scan_status_window_counters_and_independent_states() throws IOException {
        String page = resource("/static/flow-field-change-history.html");

        assertTrue(page.contains("/api/flow-field-change/scan-runs"));
        assertTrue(page.contains("v-model=\"scanFilters.projectId\""));
        assertTrue(page.contains("v-model=\"scanFilters.status\""));
        assertTrue(page.contains("RUNNING"));
        assertTrue(page.contains("SUCCESS"));
        assertTrue(page.contains("COMPLETED_WITH_ERRORS"));
        assertTrue(page.contains("FAILED"));
        assertTrue(page.contains("run.windowStart"));
        assertTrue(page.contains("run.windowEnd"));
        assertTrue(page.contains("run.commitCount"));
        assertTrue(page.contains("run.changedFileCount"));
        assertTrue(page.contains("run.historyCount"));
        assertTrue(page.contains("run.failedFileCount"));
        assertTrue(page.contains("run.skippedCount"));
        assertTrue(page.contains("run.cursorAdvanced"));
        assertTrue(page.contains("run.errorMessage"));

        assertTrue(page.contains("historyLoading"));
        assertTrue(page.contains("historyError"));
        assertTrue(page.contains("historyRecords.length === 0"));
        assertTrue(page.contains("scanLoading"));
        assertTrue(page.contains("scanError"));
        assertTrue(page.contains("scanRuns.length === 0"));
        assertTrue(page.contains("changeHistoryPage"));
        assertTrue(page.contains("changeScanPage"));
        assertTrue(page.contains("resetHistoryFilters"));
        assertTrue(page.contains("resetScanFilters"));
        assertTrue(page.contains("正在加载字段变动"));
        assertTrue(page.contains("正在加载扫描状态"));
        assertTrue(page.contains("暂无匹配的字段变动"));
        assertTrue(page.contains("暂无匹配的扫描记录"));
    }

    @Test
    void page_keeps_detail_accessible_safe_and_read_only() throws IOException {
        String page = resource("/static/flow-field-change-history.html");
        String lower = page.toLowerCase();

        assertTrue(page.contains("ref=\"workspace\""));
        assertTrue(page.contains("ref=\"detailDrawer\""));
        assertTrue(page.contains("aria-modal=\"true\""));
        assertTrue(page.contains("aria-labelledby=\"detail-title\""));
        assertTrue(page.contains("@click.self=\"closeDetail\""));
        assertTrue(page.contains("detail.log.commitSha"));
        assertTrue(page.contains("detail.log.commitAuthor"));
        assertTrue(page.contains("detail.log.commitEmail"));
        assertTrue(page.contains("detail.log.commitMessage"));
        assertTrue(page.contains("detail.log.commitTime"));
        assertTrue(page.contains("detail.oldSnapshot"));
        assertTrue(page.contains("detail.newSnapshot"));
        assertTrue(page.contains("detail.changedAttributes"));
        assertTrue(page.contains("aria-live=\"polite\""));
        assertFalse(lower.contains("v-html"));
        assertFalse(lower.contains("axios.post"));
        assertFalse(lower.contains("axios.put"));
        assertFalse(lower.contains("axios.patch"));
        assertFalse(lower.contains("axios.delete"));
        assertEquals(3, countOccurrences(page, "/api/flow-field-change/"));
        assertTrue(page.contains("<script src=\"/js/vue.global.js\"></script>"));
        assertTrue(page.contains("<script src=\"/js/axios.min.js\"></script>"));
    }

    @Test
    void page_contains_a_390px_safe_layout_with_internal_table_scrolling() throws IOException {
        String page = resource("/static/flow-field-change-history.html");
        String compact = page.replaceAll("\\s+", " ");

        assertTrue(compact.contains("body { overflow-x: hidden;"));
        assertTrue(compact.contains(".workspace { width: 100%; max-width: 100%;"));
        assertTrue(compact.contains(".table-scroll { width: 100%; max-width: 100%; overflow-x: auto;"));
        assertTrue(page.contains("@media (max-width: 390px)"));
        assertTrue(page.contains("@media (prefers-reduced-motion: reduce)"));
    }

    @Test
    void component_methods_enforce_dates_filters_race_guards_resets_and_modal_focus() throws Exception {
        String page = resource("/static/flow-field-change-history.html");
        String script = inlineScript(page);
        Path harness = Files.createTempFile("flow-field-change-page-", ".js");
        try {
            Files.writeString(harness, NODE_PRELUDE + script + NODE_ASSERTIONS, StandardCharsets.UTF_8);
            ProcessBuilder processBuilder = new ProcessBuilder("node", harness.toString());
            processBuilder.environment().put("TZ", "Asia/Shanghai");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(finished, "Node harness timed out:\n" + output);
            assertEquals(0, process.exitValue(), "Node harness failed:\n" + output);
            assertTrue(output.contains("NODE_CONTRACT_OK"), output);
        } finally {
            Files.deleteIfExists(harness);
        }
    }

    @Test
    void both_menu_scripts_define_the_same_enabled_idempotent_child() throws IOException {
        assertHistoryMenuSql(resource("/sql/create_menu_table.sql"));
        assertHistoryMenuSql(resource("/sql/add_flow_field_change_history_menu.sql"));
    }

    private String inlineScript(String page) {
        Matcher matcher = INLINE_SCRIPT.matcher(page);
        assertTrue(matcher.find(), "missing inline page script");
        return matcher.group(1);
    }

    private int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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

    private static final String NODE_PRELUDE = """
            'use strict';
            let component;
            const calls = [];
            const pending = [];
            const axios = {
              get(url, options = {}) {
                calls.push({ url, params: { ...(options.params || {}) } });
                return new Promise((resolve, reject) => pending.push({ resolve, reject }));
              }
            };
            const Vue = {
              createApp(definition) {
                component = definition;
                return { mount() { return definition; } };
              }
            };
            const window = { addEventListener() {}, removeEventListener() {} };
            const document = { activeElement: null };
            function assert(condition, message) {
              if (!condition) throw new Error(message);
            }
            function pagePayload(records = [], total = records.length) {
              return { data: { code: 200, data: { current: 1, size: 20, total, records } } };
            }
            function resolveRequest(index, payload) { pending[index].resolve(payload); }
            function rejectRequest(index, message) { pending[index].reject(new Error(message)); }
            function tick() { return new Promise(resolve => setImmediate(resolve)); }
            function focusable(name) {
              return { name, focused: false, focus() { document.activeElement = this; this.focused = true; } };
            }
            function inertTarget() {
              return {
                inert: false,
                attributes: {},
                setAttribute(name, value) { this.attributes[name] = value; },
                removeAttribute(name) { delete this.attributes[name]; }
              };
            }
            function createVm() {
              const vm = {
                $refs: {},
                $nextTick(callback) { if (callback) callback(); return Promise.resolve(); }
              };
              for (const [name, method] of Object.entries(component.methods)) {
                vm[name] = method.bind(vm);
              }
              Object.assign(vm, component.data.call(vm));
              for (const [name, getter] of Object.entries(component.computed || {})) {
                Object.defineProperty(vm, name, { get: getter.bind(vm) });
              }
              return vm;
            }
            """;

    private static final String NODE_ASSERTIONS = """
            ;(async () => {
              const vm = createVm();
              const today = vm.todayDate();
              assert(/^\\d{4}-\\d{2}-\\d{2}$/.test(today), 'today must be a local date-only value');

              const firstHistory = vm.loadHistory();
              const firstScan = vm.loadScanRuns();
              assert(calls[0].url === '/api/flow-field-change/list', 'history must use list GET');
              assert(calls[1].url === '/api/flow-field-change/scan-runs', 'scan must use scan-runs GET');
              for (const call of calls.slice(0, 2)) {
                assert(call.params.startDate === today, 'initial startDate must be today');
                assert(call.params.endDate === today, 'initial endDate must be today');
              }
              resolveRequest(0, pagePayload());
              resolveRequest(1, pagePayload());
              await Promise.all([firstHistory, firstScan]);

              vm.historyFilters = {
                startDate: '2026-08-01', endDate: '2026-08-20', projectName: ' dept ',
                projectId: ' 123 ', filePath: ' TC045.flowtrans.xml ', flowId: ' TC045 ',
                ioType: 'input', fieldId: ' acctBalance ', changeType: 'MODIFY',
                commitAuthor: ' 张三 ', captureStatus: 'SUCCESS'
              };
              const params = vm.buildHistoryParams();
              for (const key of ['startDate', 'endDate', 'projectName', 'projectId', 'filePath',
                'flowId', 'ioType', 'fieldId', 'changeType', 'commitAuthor', 'captureStatus']) {
                assert(Object.hasOwn(params, key), 'missing history filter ' + key);
              }
              assert(params.projectName === 'dept' && params.projectId === '123', 'filters must trim text');

              vm.historyCurrent = 4;
              vm.scanCurrent = 3;
              let historyReloads = 0;
              let scanReloads = 0;
              vm.loadHistory = () => { historyReloads++; };
              vm.loadScanRuns = () => { scanReloads++; };
              vm.resetHistoryFilters();
              assert(vm.historyCurrent === 1 && vm.historyFilters.startDate === today
                && vm.historyFilters.endDate === today, 'history reset must restore today and page 1');
              assert(historyReloads === 1 && scanReloads === 0, 'history reset must be independent');
              vm.resetScanFilters();
              assert(vm.scanCurrent === 1 && vm.scanFilters.startDate === today
                && vm.scanFilters.endDate === today, 'scan reset must restore today and page 1');
              assert(scanReloads === 1, 'scan reset must reload scan only');

              vm.historyCurrent = 1;
              vm.historyTotal = 60;
              vm.size = 20;
              vm.changeHistoryPage(2);
              assert(vm.historyCurrent === 2 && historyReloads === 2 && vm.scanCurrent === 1,
                'history pagination must not move scan pagination');
              vm.scanTotal = 60;
              vm.changeScanPage(2);
              assert(vm.scanCurrent === 2 && scanReloads === 2 && vm.historyCurrent === 2,
                'scan pagination must not move history pagination');

              const race = createVm();
              const historyOffset = pending.length;
              const oldHistory = race.loadHistory();
              const newHistory = race.loadHistory();
              resolveRequest(historyOffset + 1, pagePayload([{ logId: 2, fieldId: 'new' }]));
              await newHistory;
              resolveRequest(historyOffset, pagePayload([{ logId: 1, fieldId: 'old' }]));
              await oldHistory;
              assert(race.historyRecords[0].logId === 2, 'stale history response overwrote new state');
              assert(race.historyLoading === false && race.historyError === '', 'history final state is wrong');

              const scanOffset = pending.length;
              const oldScan = race.loadScanRuns();
              const newScan = race.loadScanRuns();
              resolveRequest(scanOffset + 1, pagePayload([{ id: 22, status: 'SUCCESS' }]));
              await newScan;
              rejectRequest(scanOffset, 'old scan failed');
              await oldScan;
              assert(race.scanRuns[0].id === 22, 'stale scan response overwrote new state');
              assert(race.scanLoading === false && race.scanError === '', 'stale scan error leaked into new state');

              const detailOffset = pending.length;
              race.detailOpen = true;
              const oldDetail = race.loadDetail(10);
              const newDetail = race.loadDetail(20);
              resolveRequest(detailOffset + 1, { data: { code: 200, data: { log: { id: 20 }, details: [] } } });
              await newDetail;
              resolveRequest(detailOffset, { data: { code: 200, data: { log: { id: 10 }, details: [] } } });
              await oldDetail;
              assert(race.detail.log.id === 20, 'stale detail response overwrote selected detail');

              const closeOffset = pending.length;
              const closingDetail = race.loadDetail(30);
              race.closeDetail();
              resolveRequest(closeOffset, { data: { code: 200, data: { log: { id: 30 }, details: [] } } });
              await closingDetail;
              assert(race.detailOpen === false && race.detail === null && race.detailLoading === false,
                'closed detail accepted a stale response');

              const failed = createVm();
              const callCount = calls.length;
              await failed.openDetail({ logId: 99, captureStatus: 'FAILED' }, null);
              assert(calls.length === callCount && failed.detailOpen === false,
                'failed history row must not open or request detail');

              const modal = createVm();
              const workspace = inertTarget();
              const closeButton = focusable('close');
              const first = focusable('first');
              const last = focusable('last');
              const drawer = { querySelectorAll() { return [first, last]; } };
              modal.$refs = { workspace, detailClose: closeButton, detailDrawer: drawer };
              const trigger = focusable('trigger');
              document.activeElement = trigger;
              const modalOffset = pending.length;
              const opening = modal.openDetail({ logId: 77, captureStatus: 'SUCCESS' }, { currentTarget: trigger });
              assert(workspace.inert === true && workspace.attributes['aria-hidden'] === 'true',
                'open detail must make background inert');
              assert(closeButton.focused, 'detail close button must receive focus');
              resolveRequest(modalOffset, { data: { code: 200, data: { log: { id: 77 }, details: [] } } });
              await opening;

              document.activeElement = last;
              let prevented = false;
              modal.onGlobalKeydown({ key: 'Tab', shiftKey: false, preventDefault() { prevented = true; } });
              assert(prevented && first.focused, 'Tab must wrap focus to first drawer control');
              document.activeElement = first;
              prevented = false;
              modal.onGlobalKeydown({ key: 'Tab', shiftKey: true, preventDefault() { prevented = true; } });
              assert(prevented && last.focused, 'Shift+Tab must wrap focus to last drawer control');
              modal.closeDetail();
              await tick();
              assert(workspace.inert === false && !Object.hasOwn(workspace.attributes, 'aria-hidden'),
                'closing detail must restore background interaction');
              assert(trigger.focused, 'closing detail must restore trigger focus');

              const attributes = vm.attributeEntries({
                safe: { old: '<img src=x onerror=alert(1)>', new: null },
                malformed: 'not-an-object'
              });
              assert(attributes.length === 2, 'attribute renderer must keep every own attribute');
              assert(attributes[0][1].old === 'not-an-object' || attributes[1][1].old === 'not-an-object',
                'malformed changed attribute must be normalized safely');
              for (const status of ['RUNNING', 'SUCCESS', 'COMPLETED_WITH_ERRORS', 'FAILED']) {
                assert(vm.scanStatusText(status) !== status, 'missing scan status label for ' + status);
              }
              assert(vm.formatDateTime('2026-08-20T10:11:12').includes('2026'),
                'date display must use a readable Intl formatter');

              console.log('NODE_CONTRACT_OK');
            })().catch(error => {
              console.error(error && error.stack || error);
              process.exitCode = 1;
            });
            """;
}
