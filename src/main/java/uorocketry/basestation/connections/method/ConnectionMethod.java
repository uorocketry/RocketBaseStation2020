package uorocketry.basestation.connections.method;

public interface ConnectionMethod {

    boolean open();

    boolean close();

    void setConnectionMethodListener(ConnectionMethodListener listener);

    boolean writeBytes(byte[] data);

    boolean isOpen();

    boolean isConnecting();
}
