package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardCsvCleanerTest {

    @Test
    void cleansPlaceholdersDuplicatesDatesAndBooleans() throws Exception {
        String csv = """
                ward_id, Wing ,department,beds_available,last_inspection_date,operational,extra
                W-01, East  Wing ,cardiology,3,2024-01-05,yes,ignored
                w-01,east wing,Cardiology,5,01/06/2024,NO,ignored
                W-02,West Wing,paediatrics,-1,2023-02-30,maybe,ignored
                bad,North Wing,Oncology,4,2024-03-01,true,ignored
                """;

        IngestionResult result = new WardCsvCleaner().clean(new StringReader(csv));

        assertEquals(4, result.report().totalRows());
        assertEquals(3, result.report().acceptedRows());
        assertEquals(2, result.report().uniqueWards());
        assertEquals(1, result.report().mergedDuplicates());
        assertEquals(1, result.report().rejectedRows());
        assertEquals(2, result.wards().size());

        Ward first = result.wards().get(0);
        assertEquals("W-01", first.wardId());
        assertEquals("East Wing", first.wing());
        assertEquals("Cardiology", first.department());
        assertEquals(3, first.bedsAvailable());
        assertEquals("2024-01-05", first.lastInspectionDate());
        assertEquals(Boolean.TRUE, first.operational());
        assertTrue(first.notes().stream().anyMatch(note -> note.contains("conflicted on bedsAvailable")));

        Ward second = result.wards().get(1);
        assertEquals("W-02", second.wardId());
        assertEquals("Paediatrics", second.department());
        assertNull(second.bedsAvailable());
        assertNull(second.lastInspectionDate());
        assertNull(second.operational());
    }

    @Test
    void canonicalisesWardIds() {
        assertEquals("W-05", WardCsvCleaner.canonicalWardId("w-05"));
        assertEquals("W-05", WardCsvCleaner.canonicalWardId("W5"));
        assertEquals("W-05", WardCsvCleaner.canonicalWardId("Ward_05"));
        assertEquals("W-123", WardCsvCleaner.canonicalWardId("123"));
        assertNull(WardCsvCleaner.canonicalWardId("not-a-ward"));
        assertNull(WardCsvCleaner.canonicalWardId(null));
    }
}
