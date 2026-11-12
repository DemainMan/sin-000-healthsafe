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
}
