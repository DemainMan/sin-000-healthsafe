package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public final class EquipmentAlertConsumer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String brokerUrl;
    private final EquipmentAlertStore store;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public EquipmentAlertConsumer(String brokerUrl, EquipmentAlertStore store) {
        this.brokerUrl = brokerUrl;
        this.store = store;
    }

    public void start() throws Exception {
        connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
        connection.start();
        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = session.createQueue(MqConfig.QUEUE);
        consumer = session.createConsumer(queue);
        consumer.setMessageListener(this::onMessage);
    }

    private void onMessage(Message message) {
        if (!(message instanceof TextMessage text)) {
            return;
        }
        try {
            EquipmentAlert alert = MAPPER.readValue(text.getText(), EquipmentAlert.class);
            store.save(alert);
            message.acknowledge();
        } catch (Exception ex) {
            System.err.println("Could not store equipment alert; recovering session for redelivery: "
                    + ex.getMessage());
            try {
                session.recover();
            } catch (Exception recoveryFailure) {
                System.err.println("Could not recover equipment alert session: "
                        + recoveryFailure.getMessage());
            }
        }
    }

    @Override
    public void close() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ex) {
            System.err.println("Could not close equipment alert consumer: " + ex.getMessage());
        }
    }
}
