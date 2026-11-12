package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

public final class StaffingEventPublisher implements SchedulePublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String brokerUrl;

    public StaffingEventPublisher(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    @Override
    public void publish(Schedule schedule) {
        try (Connection connection = new ActiveMQConnectionFactory(brokerUrl).createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Topic topic = session.createTopic(MqConfig.TOPIC);
                try (MessageProducer producer = session.createProducer(topic)) {
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    TextMessage message = session.createTextMessage(
                            MAPPER.writeValueAsString(StaffingEvent.fromSchedule(schedule)));
                    producer.send(message);
                }
            }
        } catch (Exception ex) {
            System.err.println("Could not publish staffing update: " + ex.getMessage());
        }
    }
}
