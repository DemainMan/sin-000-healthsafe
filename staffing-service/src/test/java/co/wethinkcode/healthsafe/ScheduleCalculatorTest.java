package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleCalculatorTest {

    @Test
    void usesOneDoctorPlusTheEmergencyLevel() {
        Schedule schedule = new ScheduleCalculator().calculate(
                new Ward("w-05", "East Wing", "Paediatrics"), 3);

        assertEquals("W-05", schedule.wardId());
        assertEquals(3, schedule.emergencyLevel());
        assertEquals(4, schedule.requiredDoctors());
        assertEquals(List.of("DOC-001", "DOC-002", "DOC-003", "DOC-004"), schedule.doctors());
        assertEquals(12, schedule.onCallWindowHours());
    }

    @Test
    void supportsTheFullAlertRange() {
        ScheduleCalculator calculator = new ScheduleCalculator();
        Ward ward = new Ward("W-01", "East Wing", "Cardiology");

        assertEquals(1, calculator.calculate(ward, 0).requiredDoctors());
        assertEquals(9, calculator.calculate(ward, 8).requiredDoctors());
    }
}
