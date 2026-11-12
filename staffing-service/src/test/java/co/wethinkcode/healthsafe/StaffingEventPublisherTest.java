package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingEventPublisherTest {

    @Test
    void publishesScheduleUpdatesToTheTopic() throws Exception {
        try (EmbeddedBroker broker = new EmbeddedBroker();
             Connection consumerConnection = new ActiveMQConnectionFactory(broker.url()).createConnection()) {
            consumerConnection.start();
            Session session = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createConsumer(topic);

            Schedule schedule = new Schedule(
                    "W-05", "East Wing", "Paediatrics", 5, 6,
                    List.of("DOC-001", "DOC-002", "DOC-003", "DOC-004", "DOC-005", "DOC-006"),
                    12, "2026-01-01T00:00:00Z");

            new StaffingEventPublisher(broker.url()).publish(schedule);

            TextMessage message = (TextMessage) consumer.receive(3000);
            assertNotNull(message);
            assertTrue(message.getText().contains("\"type\":\"SCHEDULE_UPDATED\""));
            assertTrue(message.getText().contains("\"wardId\":\"W-05\""));
            assertTrue(message.getText().contains("\"requiredDoctors\":6"));
        }
    }
}
