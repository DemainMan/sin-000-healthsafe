package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentFailurePublisherTest {

    @Test
    void publishesAPersistentQueueMessage() throws Exception {
        try (EmbeddedBroker broker = new EmbeddedBroker()) {
            EquipmentFailureMessage failure = new EquipmentFailureMessage(
                    "alert-1", "EQUIPMENT_FAILURE", "W-01", "EQ-17",
                    "Ventilator stopped", "CRITICAL", "2026-01-01T00:00:00Z");

            try (Connection connection = new ActiveMQConnectionFactory(broker.url()).createConnection()) {
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(MqConfig.QUEUE));

                new EquipmentFailurePublisher(broker.url()).publish(failure);

                Message message = consumer.receive(5000);
                assertNotNull(message);
                assertEquals(DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
                String body = ((TextMessage) message).getText();
                assertTrue(body.contains("\"alertId\":\"alert-1\""));
                assertTrue(body.contains("\"wardId\":\"W-01\""));
                assertTrue(body.contains("\"equipmentId\":\"EQ-17\""));
            }
        }
    }
}
