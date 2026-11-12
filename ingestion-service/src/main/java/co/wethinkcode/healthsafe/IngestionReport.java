package co.wethinkcode.healthsafe;

import java.util.List;

public record IngestionReport(
        int totalRows,
        int acceptedRows,
        int uniqueWards,
        int mergedDuplicates,
        int rejectedRows,
        int flaggedWards,
        List<RejectedRow> rejected
) {
}
