package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

public final class StaffingEventSubscriber implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String brokerUrl;
    private final StaffingUpdateStore store;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public StaffingEventSubscriber(String brokerUrl, StaffingUpdateStore store) {
        this.brokerUrl = brokerUrl;
        this.store = store;
    }

    public void start() throws Exception {
        connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination topic = session.createTopic(MqConfig.TOPIC);
        consumer = session.createConsumer(topic);
        consumer.setMessageListener(this::onMessage);
    }

    private void onMessage(Message message) {
        if (!(message instanceof TextMessage text)) {
            return;
        }
        try {
            StaffingEvent event = MAPPER.readValue(text.getText(), StaffingEvent.class);
            store.save(event);
        } catch (Exception ex) {
            System.err.println("Ignoring unparseable staffing event: " + ex.getMessage());
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
            System.err.println("Could not close staffing subscriber: " + ex.getMessage());
        }
    }
}
