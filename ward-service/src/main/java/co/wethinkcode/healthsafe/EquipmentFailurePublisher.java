package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public final class EquipmentFailurePublisher implements EquipmentPublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String brokerUrl;

    public EquipmentFailurePublisher(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    @Override
    public void publish(EquipmentFailureMessage failure) {
        try (Connection connection = new ActiveMQConnectionFactory(brokerUrl).createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue queue = session.createQueue(MqConfig.QUEUE);
                try (MessageProducer producer = session.createProducer(queue)) {
                    producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                    TextMessage message = session.createTextMessage(
                            MAPPER.writeValueAsString(failure));
                    producer.send(message);
                }
            }
        } catch (Exception ex) {
            throw new EquipmentPublishException("could not publish equipment failure", ex);
        }
    }
}
