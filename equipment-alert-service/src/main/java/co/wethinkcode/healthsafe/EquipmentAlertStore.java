package co.wethinkcode.healthsafe;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EquipmentAlertStore {

    private final ConcurrentMap<String, EquipmentAlert> alerts = new ConcurrentHashMap<>();

    public void save(EquipmentAlert alert) {
        alerts.putIfAbsent(alert.alertId(), alert);
    }

    public List<EquipmentAlert> all() {
        return alerts.values().stream()
                .sorted(Comparator.comparing(EquipmentAlert::reportedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EquipmentAlert::alertId))
                .toList();
    }

    public Optional<EquipmentAlert> find(String alertId) {
        return alertId == null ? Optional.empty() : Optional.ofNullable(alerts.get(alertId));
    }

    public int size() {
        return alerts.size();
    }

    public Map<String, EquipmentAlert> snapshot() {
        return Map.copyOf(alerts);
    }
}
