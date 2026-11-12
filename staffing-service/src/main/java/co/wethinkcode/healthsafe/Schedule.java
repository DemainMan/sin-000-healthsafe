package co.wethinkcode.healthsafe;

import java.util.List;

public record Schedule(
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
