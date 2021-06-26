package uorocketry.basestation.connections.method;

import java.nio.charset.StandardCharsets;

public abstract class AbstractConnectionMethod implements ConnectionMethod {

    protected final byte[] DELIMITER = "\n".getBytes(StandardCharsets.UTF_8);

    protected ConnectionMethodListener listener;

    @Override
    public void setConnectionMethodListener(ConnectionMethodListener listener) {
        this.listener = listener;
    }
}
