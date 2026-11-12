package co.wethinkcode.healthsafe;

public record RejectedRow(int rowNumber, String rawWardId, String reason) {
}
