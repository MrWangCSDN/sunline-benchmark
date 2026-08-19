package com.sunline.dict.service.flowchange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record FlowtransInterfaceSnapshot(
        String flowId, String flowLongname,
        Map<FieldIdentity, FieldSnapshot> fields) {
    public FlowtransInterfaceSnapshot {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public record FieldIdentity(String ioType, String fieldPath, String fieldId) {
    }

    public record FieldSnapshot(FieldIdentity identity,
                                SortedMap<String, String> attributes) {
        public FieldSnapshot {
            attributes = Collections.unmodifiableSortedMap(new TreeMap<>(attributes));
        }
    }
}
