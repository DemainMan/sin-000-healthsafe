package co.wethinkcode.healthsafe;

@FunctionalInterface
public interface SchedulePublisher {

    void publish(Schedule schedule);
}
