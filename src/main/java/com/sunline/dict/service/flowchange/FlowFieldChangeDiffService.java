package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChange;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public class FlowFieldChangeDiffService {

    private static final Comparator<FieldIdentity> IDENTITY_ORDER = Comparator
            .comparing(FieldIdentity::ioType)
            .thenComparing(FieldIdentity::fieldPath)
            .thenComparing(FieldIdentity::fieldId);

    private static final Comparator<FieldChange> DETAIL_ORDER = Comparator
            .comparing(FieldChange::identity, IDENTITY_ORDER)
            .thenComparing(change -> change.changeType().name());

    public Optional<FlowFieldChangeSet> diff(FlowtransInterfaceSnapshot before,
                                              FlowtransInterfaceSnapshot after) {
        if (before == null && after == null) {
            return Optional.empty();
        }
        if (before == null) {
            return Optional.of(changeSet(FileChangeType.ADD, after, fieldChanges(null, after)));
        }
        if (after == null) {
            return Optional.of(changeSet(FileChangeType.DELETE, before, fieldChanges(before, null)));
        }

        List<FieldChange> details = fieldChanges(before, after);
        if (details.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(changeSet(FileChangeType.MODIFY, after, details));
    }

    private List<FieldChange> fieldChanges(FlowtransInterfaceSnapshot before,
                                            FlowtransInterfaceSnapshot after) {
        TreeSet<FieldIdentity> identities = new TreeSet<>(IDENTITY_ORDER);
        if (before != null) {
            identities.addAll(before.fields().keySet());
        }
        if (after != null) {
            identities.addAll(after.fields().keySet());
        }

        List<FieldChange> details = new ArrayList<>();
        for (FieldIdentity identity : identities) {
            FieldSnapshot oldField = before == null ? null : before.fields().get(identity);
            FieldSnapshot newField = after == null ? null : after.fields().get(identity);
            if (oldField == null) {
                details.add(new FieldChange(FieldChangeType.ADD, identity, null,
                        newField.attributes(), new TreeMap<>()));
            } else if (newField == null) {
                details.add(new FieldChange(FieldChangeType.DELETE, identity,
                        oldField.attributes(), null, new TreeMap<>()));
            } else {
                SortedMap<String, ValueChange> changedAttributes = changedAttributes(oldField, newField);
                if (!changedAttributes.isEmpty()) {
                    details.add(new FieldChange(FieldChangeType.MODIFY, identity,
                            oldField.attributes(), newField.attributes(), changedAttributes));
                }
            }
        }
        details.sort(DETAIL_ORDER);
        return details;
    }

    private SortedMap<String, ValueChange> changedAttributes(FieldSnapshot before,
                                                               FieldSnapshot after) {
        TreeSet<String> attributeNames = new TreeSet<>();
        attributeNames.addAll(before.attributes().keySet());
        attributeNames.addAll(after.attributes().keySet());
        attributeNames.remove("id");

        SortedMap<String, ValueChange> changes = new TreeMap<>();
        for (String attributeName : attributeNames) {
            String oldValue = before.attributes().get(attributeName);
            String newValue = after.attributes().get(attributeName);
            if (!java.util.Objects.equals(oldValue, newValue)) {
                changes.put(attributeName, new ValueChange(oldValue, newValue));
            }
        }
        return changes;
    }

    private FlowFieldChangeSet changeSet(FileChangeType fileChangeType,
                                         FlowtransInterfaceSnapshot snapshot,
                                         List<FieldChange> details) {
        int addCount = count(details, FieldChangeType.ADD);
        int modifyCount = count(details, FieldChangeType.MODIFY);
        int removeCount = count(details, FieldChangeType.DELETE);
        int inputChangeCount = (int) details.stream()
                .filter(change -> "input".equals(change.identity().ioType()))
                .count();
        int outputChangeCount = (int) details.stream()
                .filter(change -> "output".equals(change.identity().ioType()))
                .count();
        return new FlowFieldChangeSet(fileChangeType, snapshot.flowId(), snapshot.flowLongname(),
                List.copyOf(details), addCount, modifyCount, removeCount,
                inputChangeCount, outputChangeCount);
    }

    private int count(List<FieldChange> details, FieldChangeType changeType) {
        return (int) details.stream().filter(change -> change.changeType() == changeType).count();
    }
}
