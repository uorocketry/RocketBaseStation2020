package uorocketry.basestation.connections.method;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;

import java.nio.charset.StandardCharsets;

public class SerialConnectionMethod extends AbstractConnectionMethod implements SerialPortMessageListener {

    private SerialPort serialPort;
    private boolean connecting;

    public SerialConnectionMethod(SerialPort newSerialPort) {
        serialPort = newSerialPort;
    }

    @Override
    public boolean open() {
        connecting = true;

        boolean isOpen = serialPort.openPort();
        serialPort.setBaudRate(57600);
        serialPort.addDataListener(this);

        connecting = false;

        return isOpen;
    }

    @Override
    public boolean writeBytes(byte[] data) {
        if (isOpen()) {
            int bytesWritten = serialPort.writeBytes(data, data.length);
            return bytesWritten != -1;
        }

        return false;
    }

    @Override
    public boolean close() {
        if (isOpen()) {
            serialPort.removeDataListener();
            return serialPort.closePort();
        }

        return true;
    }

    @Override
    public boolean isOpen() {
        return serialPort != null && serialPort.isOpen();
    }

    @Override
    public boolean isConnecting() {
        return connecting;
    }

    public String getDescriptivePortName() {
        return serialPort != null ? serialPort.getDescriptivePortName() : null;
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
    }

    @Override
    public byte[] getMessageDelimiter() {
        return DELIMITER;
    }

    @Override
    public boolean delimiterIndicatesEndOfMessage() {
        return true;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        try {
            if (listener != null) {
                listener.receivedData(event.getReceivedData());
            }
        } catch (Exception e) {
            // Serial event hijacks exceptions, this will cause them to still be printed
            e.printStackTrace();
        }
    }
}
