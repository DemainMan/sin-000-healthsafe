package co.wethinkcode.healthsafe;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WardStore {

    private volatile Map<String, Ward> wards = Map.of();

    public void replaceAll(List<Ward> incoming) {
        Map<String, Ward> replacement = new LinkedHashMap<>();
        for (Ward ward : incoming) {
            String id = WardIds.canonical(ward.wardId());
            if (id != null) {
                replacement.put(id, ward);
            }
        }
        wards = Map.copyOf(replacement);
    }

    public List<Ward> all() {
        return wards.values().stream()
                .sorted(Comparator.comparing(ward -> WardIds.canonical(ward.wardId())))
                .toList();
    }

    public Optional<Ward> find(String rawId) {
        String id = WardIds.canonical(rawId);
        return id == null ? Optional.empty() : Optional.ofNullable(wards.get(id));
    }

    public List<String> departments() {
        return wards.values().stream()
                .map(Ward::department)
                .filter(department -> department != null && !department.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
