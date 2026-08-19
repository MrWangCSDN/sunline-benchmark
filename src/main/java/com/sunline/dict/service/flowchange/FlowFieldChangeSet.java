package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public record FlowFieldChangeSet(
        FileChangeType fileChangeType, String flowId, String flowLongname,
        List<FieldChange> details,
        int addCount, int modifyCount, int removeCount,
        int inputChangeCount, int outputChangeCount) {
    public FlowFieldChangeSet {
        details = List.copyOf(details);
    }

    public enum FileChangeType { ADD, MODIFY, DELETE }
    public enum FieldChangeType { ADD, MODIFY, DELETE }
    public record ValueChange(String oldValue, String newValue) {}
    public record FieldChange(
            FieldChangeType changeType, FieldIdentity identity,
            SortedMap<String, String> oldSnapshot,
            SortedMap<String, String> newSnapshot,
            SortedMap<String, ValueChange> changedAttributes) {
        public FieldChange {
            oldSnapshot = immutableCopy(oldSnapshot);
            newSnapshot = immutableCopy(newSnapshot);
            changedAttributes = immutableCopy(changedAttributes);
        }

        private static <V> SortedMap<String, V> immutableCopy(SortedMap<String, V> source) {
            return source == null ? null : Collections.unmodifiableSortedMap(new TreeMap<>(source));
        }
    }
}
