package co.wethinkcode.healthsafe;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ScheduleStore {

    private final ConcurrentMap<String, Schedule> schedules = new ConcurrentHashMap<>();

    public void save(Schedule schedule) {
        schedules.put(WardIds.canonical(schedule.wardId()), schedule);
    }

    public Optional<Schedule> find(String rawWardId) {
        String wardId = WardIds.canonical(rawWardId);
        return wardId == null ? Optional.empty() : Optional.ofNullable(schedules.get(wardId));
    }
}
