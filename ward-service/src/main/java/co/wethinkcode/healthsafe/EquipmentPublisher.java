package co.wethinkcode.healthsafe;

@FunctionalInterface
public interface EquipmentPublisher {

    void publish(EquipmentFailureMessage message);
}
