package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAlertConsumerTest {

    @Test
    void consumesAndStoresAlertsFromTheQueue() throws Exception {
        try (EmbeddedBroker broker = new EmbeddedBroker()) {
            EquipmentAlertStore store = new EquipmentAlertStore();
            try (EquipmentAlertConsumer consumer = new EquipmentAlertConsumer(broker.url(), store)) {
                consumer.start();
                send(broker.url(), alertJson("alert-1", "W-01"));

                assertTrue(awaitSize(store, 1));
                assertEquals("EQ-17", store.find("alert-1").orElseThrow().equipmentId());
            }
        }
    }

    @Test
    void recoversTheSessionWhenStorageFails() throws Exception {
        try (EmbeddedBroker broker = new EmbeddedBroker()) {
            FlakyStore store = new FlakyStore();
            try (EquipmentAlertConsumer consumer = new EquipmentAlertConsumer(broker.url(), store)) {
                consumer.start();
                send(broker.url(), alertJson("alert-1", "W-01"));

                assertTrue(awaitSize(store, 1));
                assertTrue(store.attempts.get() >= 2);
                assertEquals(1, store.size());
                assertEquals("alert-1", store.all().get(0).alertId());
            }
        }
    }

    private static String alertJson(String alertId, String wardId) {
        return """
                {"alertId":"%s","type":"EQUIPMENT_FAILURE","wardId":"%s",
                 "equipmentId":"EQ-17","description":"Ventilator stopped",
                 "severity":"CRITICAL","reportedAt":"2026-01-01T00:00:00Z"}
                """.formatted(alertId, wardId);
    }

    private static void send(String brokerUrl, String body) throws Exception {
        try (Connection connection = new ActiveMQConnectionFactory(brokerUrl).createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                MessageProducer producer = session.createProducer(session.createQueue(MqConfig.QUEUE));
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(body);
                producer.send(message);
            }
        }
    }

    private static boolean awaitSize(EquipmentAlertStore store, int expected) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (store.size() == expected) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static final class FlakyStore extends EquipmentAlertStore {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void save(EquipmentAlert alert) {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("simulated storage failure");
            }
            super.save(alert);
        }
    }
}
