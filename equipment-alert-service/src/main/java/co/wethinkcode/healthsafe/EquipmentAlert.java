package co.wethinkcode.healthsafe;

public record EquipmentAlert(
        String alertId,
        String type,
        String wardId,
        String equipmentId,
        String description,
        String severity,
        String reportedAt
) {
}
