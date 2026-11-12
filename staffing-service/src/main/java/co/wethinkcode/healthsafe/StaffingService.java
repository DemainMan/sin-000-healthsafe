package co.wethinkcode.healthsafe;

public final class StaffingService {

    private final WardClient wardClient;
    private final AlertLevelClient alertLevelClient;
    private final ScheduleCalculator calculator;
    private final ScheduleStore store;

    public StaffingService(WardClient wardClient, AlertLevelClient alertLevelClient,
                           ScheduleCalculator calculator, ScheduleStore store) {
        this.wardClient = wardClient;
        this.alertLevelClient = alertLevelClient;
        this.calculator = calculator;
        this.store = store;
    }

    public Schedule calculate(String wardId) {
        Ward ward = wardClient.fetch(wardId);
        AlertLevel level = alertLevelClient.fetchLevel();
        return calculator.calculate(ward, level.level());
    }

    public Schedule store(String wardId) {
        Schedule schedule = calculate(wardId);
        store.save(schedule);
        return schedule;
    }

    public ScheduleStore store() {
        return store;
    }
}
