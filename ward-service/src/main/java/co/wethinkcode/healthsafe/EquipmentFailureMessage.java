package co.wethinkcode.healthsafe;

public record EquipmentFailureMessage(
        String alertId,
        String type,
        String wardId,
        String equipmentId,
        String description,
        String severity,
        String reportedAt
) {
}
