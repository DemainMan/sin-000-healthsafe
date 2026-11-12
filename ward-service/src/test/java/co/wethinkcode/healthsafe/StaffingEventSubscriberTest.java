package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingEventSubscriberTest {

    @Test
    void storesTheLatestStaffingEventPerWard() throws Exception {
        try (EmbeddedBroker broker = new EmbeddedBroker()) {
            StaffingUpdateStore store = new StaffingUpdateStore();
            try (StaffingEventSubscriber subscriber = new StaffingEventSubscriber(broker.url(), store)) {
                subscriber.start();

                try (Connection connection = new ActiveMQConnectionFactory(broker.url()).createConnection()) {
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    MessageProducer producer = session.createProducer(session.createTopic(MqConfig.TOPIC));
                    producer.send(session.createTextMessage("""
                            {"type":"SCHEDULE_UPDATED","wardId":"w-05","wing":"East Wing",
                             "department":"Paediatrics","emergencyLevel":5,"requiredDoctors":6,
                             "doctors":["DOC-001"],"onCallWindowHours":12,"generatedAt":"now"}
                            """));
                    producer.send(session.createTextMessage("""
                            {"type":"SCHEDULE_UPDATED","wardId":"W-05","wing":"East Wing",
                             "department":"Paediatrics","emergencyLevel":6,"requiredDoctors":7,
                             "doctors":["DOC-001"],"onCallWindowHours":12,"generatedAt":"later"}
                            """));
                }

                Optional<StaffingEvent> update = await(store, "W-05", 6);
                assertTrue(update.isPresent());
                assertEquals("W-05", update.orElseThrow().wardId());
                assertEquals(6, update.orElseThrow().emergencyLevel());
                assertEquals("later", update.orElseThrow().generatedAt());
                assertEquals(1, store.all().size());
            }
        }
    }

    private static Optional<StaffingEvent> await(StaffingUpdateStore store, String wardId, int level)
            throws InterruptedException {
        Optional<StaffingEvent> latest = Optional.empty();
        for (int i = 0; i < 40; i++) {
            latest = store.find(wardId);
            if (latest.isPresent() && latest.orElseThrow().emergencyLevel() == level) {
                return latest;
            }
            Thread.sleep(100);
        }
        return latest;
    }
}
