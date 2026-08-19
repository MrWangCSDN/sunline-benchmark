package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;

import java.util.List;
import java.util.SortedMap;

public record FlowFieldChangeSet(
        FileChangeType fileChangeType, String flowId, String flowLongname,
        List<FieldChange> details,
        int addCount, int modifyCount, int removeCount,
        int inputChangeCount, int outputChangeCount) {
    public enum FileChangeType { ADD, MODIFY, DELETE }
    public enum FieldChangeType { ADD, MODIFY, DELETE }
    public record ValueChange(String oldValue, String newValue) {}
    public record FieldChange(
            FieldChangeType changeType, FieldIdentity identity,
            SortedMap<String, String> oldSnapshot,
            SortedMap<String, String> newSnapshot,
            SortedMap<String, ValueChange> changedAttributes) {}
}
