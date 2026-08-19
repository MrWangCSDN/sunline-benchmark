package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChange;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldChangeDiffServiceTest {

    private final FlowFieldChangeDiffService service = new FlowFieldChangeDiffService();

    @Test
    void one_field_with_two_changed_attributes_counts_as_one_modify() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T1", "required", "false")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T2", "required", "true")));

        FlowFieldChangeSet change = service.diff(before, after).orElseThrow();

        assertEquals(FileChangeType.MODIFY, change.fileChangeType());
        assertEquals(1, change.modifyCount());
        assertEquals(2, change.details().get(0).changedAttributes().size());
    }

    @Test
    void empty_file_add_and_delete_still_produce_history() {
        FlowtransInterfaceSnapshot empty = snapshot("TC000");

        assertEquals(FileChangeType.ADD, service.diff(null, empty).orElseThrow().fileChangeType());
        assertEquals(FileChangeType.DELETE, service.diff(empty, null).orElseThrow().fileChangeType());
    }

    @Test
    void added_and_deleted_fields_have_snapshots_and_directional_counts() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/", "old", attrs("type", "T1")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "output", "/", "new", attrs("type", "T2")));

        FlowFieldChangeSet change = service.diff(before, after).orElseThrow();

        assertEquals(1, change.addCount());
        assertEquals(1, change.removeCount());
        assertEquals(1, change.inputChangeCount());
        assertEquals(1, change.outputChangeCount());
        assertEquals(FieldChangeType.DELETE, change.details().get(0).changeType());
        assertEquals(FieldChangeType.ADD, change.details().get(1).changeType());
        assertEquals(attrs("id", "old", "type", "T1"), change.details().get(0).oldSnapshot());
        assertNull(change.details().get(0).newSnapshot());
        assertNull(change.details().get(1).oldSnapshot());
        assertEquals(attrs("id", "new", "type", "T2"), change.details().get(1).newSnapshot());
    }

    @Test
    void input_and_output_fields_with_same_id_are_isolated() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T1")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "output", "/", "amount", attrs("type", "T1")));

        FlowFieldChangeSet change = service.diff(before, after).orElseThrow();

        assertEquals(1, change.addCount());
        assertEquals(1, change.removeCount());
        assertEquals(0, change.modifyCount());
    }

    @Test
    void path_or_id_movement_is_a_delete_and_add() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/fields[accounts]", "amount", attrs("type", "T1")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "input", "/fields[balances]", "total", attrs("type", "T1")));

        FlowFieldChangeSet change = service.diff(before, after).orElseThrow();

        assertEquals(List.of(FieldChangeType.DELETE, FieldChangeType.ADD),
                change.details().stream().map(FieldChange::changeType).toList());
    }

    @Test
    void attribute_add_and_remove_use_null_values() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T1", "required", "true")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T1", "precision", "2")));

        SortedMap<String, ValueChange> changedAttributes = service.diff(before, after).orElseThrow()
                .details().get(0).changedAttributes();

        assertEquals(new ValueChange(null, "2"), changedAttributes.get("precision"));
        assertEquals(new ValueChange("true", null), changedAttributes.get("required"));
    }

    @Test
    void order_only_changes_return_empty() {
        FlowtransInterfaceSnapshot first = snapshot("TC045",
                field("input", "/", "one", attrs("type", "T1")),
                field("output", "/", "two", attrs("type", "T2")));
        FlowtransInterfaceSnapshot reordered = snapshot("TC045",
                field("output", "/", "two", attrs("type", "T2")),
                field("input", "/", "one", attrs("type", "T1")));

        Optional<FlowFieldChangeSet> change = service.diff(first, reordered);

        assertTrue(change.isEmpty());
    }

    @Test
    void case_changes_are_significant() {
        FlowtransInterfaceSnapshot before = snapshot("TC045", field(
                "input", "/", "amount", attrs("type", "T1")));
        FlowtransInterfaceSnapshot after = snapshot("TC045", field(
                "input", "/", "amount", attrs("Type", "T1")));

        SortedMap<String, ValueChange> changedAttributes = service.diff(before, after).orElseThrow()
                .details().get(0).changedAttributes();

        assertEquals(new ValueChange(null, "T1"), changedAttributes.get("Type"));
        assertEquals(new ValueChange("T1", null), changedAttributes.get("type"));
    }

    @Test
    void details_are_sorted_by_identity() {
        FlowtransInterfaceSnapshot before = snapshot("TC045");
        FlowtransInterfaceSnapshot after = snapshot("TC045",
                field("output", "/", "z", attrs()),
                field("input", "/fields[b]", "a", attrs()),
                field("input", "/", "z", attrs()));

        List<FieldIdentity> identities = service.diff(before, after).orElseThrow().details().stream()
                .map(FieldChange::identity).toList();

        assertEquals(List.of(
                new FieldIdentity("input", "/", "z"),
                new FieldIdentity("input", "/fields[b]", "a"),
                new FieldIdentity("output", "/", "z")), identities);
    }

    private FlowtransInterfaceSnapshot snapshot(String flowId, FieldSnapshot... fields) {
        Map<FieldIdentity, FieldSnapshot> snapshots = new LinkedHashMap<>();
        for (FieldSnapshot field : fields) {
            snapshots.put(field.identity(), field);
        }
        return new FlowtransInterfaceSnapshot(flowId, flowId + " name", snapshots);
    }

    private FieldSnapshot field(String ioType, String path, String id, SortedMap<String, String> attributes) {
        FieldIdentity identity = new FieldIdentity(ioType, path, id);
        SortedMap<String, String> attributesWithId = new TreeMap<>(attributes);
        attributesWithId.put("id", id);
        return new FieldSnapshot(identity, attributesWithId);
    }

    private SortedMap<String, String> attrs(String... values) {
        SortedMap<String, String> attributes = new TreeMap<>();
        for (int index = 0; index < values.length; index += 2) {
            attributes.put(values[index], values[index + 1]);
        }
        return attributes;
    }
}
