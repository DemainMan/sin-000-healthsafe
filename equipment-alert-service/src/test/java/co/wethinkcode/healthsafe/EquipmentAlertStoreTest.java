package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAlertStoreTest {

    @Test
    void storesAlertsIdempotently() {
        EquipmentAlertStore store = new EquipmentAlertStore();
        EquipmentAlert alert = new EquipmentAlert("alert-1", "EQUIPMENT_FAILURE", "W-01",
                "EQ-1", "Ventilator stopped", "CRITICAL", "2026-01-01T00:00:00Z");

        store.save(alert);
        store.save(alert);

        assertEquals(1, store.size());
        assertTrue(store.find("alert-1").isPresent());
        assertEquals(alert, store.all().get(0));
    }
}
