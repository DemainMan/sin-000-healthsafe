package co.wethinkcode.healthsafe;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;

public final class EmbeddedBroker implements AutoCloseable {

    private final BrokerService broker;
    private final String url;

    public EmbeddedBroker() throws Exception {
        broker = new BrokerService();
        broker.setUseJmx(false);
        broker.setPersistent(false);
        broker.setBrokerName("healthsafe-test-" + System.nanoTime());
        TransportConnector connector = broker.addConnector("tcp://localhost:0");
        broker.start();
        broker.waitUntilStarted();
        url = connector.getConnectUri().toString();
    }

    public String url() {
        return url;
    }

    @Override
    public void close() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }
}
