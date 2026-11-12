package co.wethinkcode.healthsafe;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class StaffingUpdateStore {

    private final ConcurrentMap<String, StaffingEvent> updates = new ConcurrentHashMap<>();

    public void save(StaffingEvent event) {
        String wardId = WardIds.canonical(event.wardId());
        if (wardId != null) {
            updates.put(wardId, event);
        }
    }

    public java.util.List<StaffingEvent> all() {
        return updates.values().stream()
                .sorted(Comparator.comparing(event -> WardIds.canonical(event.wardId())))
                .toList();
    }

    public Optional<StaffingEvent> find(String rawWardId) {
        String wardId = WardIds.canonical(rawWardId);
        return wardId == null ? Optional.empty() : Optional.ofNullable(updates.get(wardId));
    }
}
