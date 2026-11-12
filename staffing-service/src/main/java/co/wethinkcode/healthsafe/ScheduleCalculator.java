package co.wethinkcode.healthsafe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ScheduleCalculator {

    public static final int ON_CALL_WINDOW_HOURS = 12;

    public Schedule calculate(Ward ward, int emergencyLevel) {
        int requiredDoctors = 1 + emergencyLevel;
        List<String> doctors = new ArrayList<>();
        for (int i = 1; i <= requiredDoctors; i++) {
            doctors.add(String.format("DOC-%03d", i));
        }
        return new Schedule(
                WardIds.canonical(ward.wardId()),
                ward.wing(),
                ward.department(),
                emergencyLevel,
                requiredDoctors,
                List.copyOf(doctors),
                ON_CALL_WINDOW_HOURS,
                Instant.now().toString()
        );
    }
}
