package co.wethinkcode.healthsafe;

import java.util.List;

public record StaffingEvent(
        String type,
        String wardId,
        String wing,
        String department,
        int emergencyLevel,
        int requiredDoctors,
        List<String> doctors,
        int onCallWindowHours,
        String generatedAt
) {

    public static final String SCHEDULE_UPDATED = "SCHEDULE_UPDATED";

    public static StaffingEvent fromSchedule(Schedule schedule) {
        return new StaffingEvent(
                SCHEDULE_UPDATED,
                schedule.wardId(),
                schedule.wing(),
                schedule.department(),
                schedule.emergencyLevel(),
                schedule.requiredDoctors(),
                schedule.doctors(),
                schedule.onCallWindowHours(),
                schedule.generatedAt()
        );
    }
}
