package uorocketry.basestation.connections;

import com.fazecast.jSerialComm.SerialPort;
import uorocketry.basestation.connections.method.ConnectionMethod;
import uorocketry.basestation.connections.method.ConnectionMethodListener;
import uorocketry.basestation.connections.method.SerialConnectionMethod;
import uorocketry.basestation.connections.method.TcpConnectionMethod;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.charset.StandardCharsets;

public class DeviceConnection implements ListSelectionListener, MouseListener, ConnectionMethodListener, ActionListener {
    private DeviceConnectionHolder deviceConnectionHolder;
    private DataReceiver[] dataReceivers;

    private ConnectionMethod connectionMethod;
    private int tableIndex;
    private boolean writing;
    
    private JPanel panel;
    private ButtonGroup methodRadioGroup;
    private JPanel radioPanel;
    private JRadioButton serialRadioButton;
    private JRadioButton tcpRadioButton;
    private JList<String> comSelectorList;
    private JPanel tcpConnectionPanel;
    private JTextField hostnameField;
    private JTextField portField;
    private JButton tcpConnectButton;
    private JLabel successLabel;
    boolean ignoreNextValueChange;

    private final byte[] DELIMITER = "\n".getBytes(StandardCharsets.UTF_8);

    public DeviceConnection(DeviceConnectionHolder deviceConnectionHolder, String name) {
        this.deviceConnectionHolder = deviceConnectionHolder;

        createUI(name);
    }

    private void createUI(String name) {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(name));

        serialRadioButton = new JRadioButton();
        serialRadioButton.addActionListener(this);
        serialRadioButton.setSelected(true);
        serialRadioButton.setText("Serial");
        tcpRadioButton = new JRadioButton();
        tcpRadioButton.addActionListener(this);
        tcpRadioButton.setText("TCP");

        radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));
        radioPanel.add(serialRadioButton);
        radioPanel.add(tcpRadioButton);
        panel.add(radioPanel);

        methodRadioGroup = new ButtonGroup();
        methodRadioGroup.add(serialRadioButton);
        methodRadioGroup.add(tcpRadioButton);

        comSelectorList = new JList<>();
        comSelectorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        comSelectorList.addListSelectionListener(this);
        comSelectorList.addMouseListener(this);
        panel.add(comSelectorList);

        hostnameField = new JTextField();
        portField = new JTextField(4);
        portField.setMaximumSize(portField.getPreferredSize());
        portField.setText("8080");
        JPanel serverAddressPanel = new JPanel();
        serverAddressPanel.setLayout(new BoxLayout(serverAddressPanel, BoxLayout.X_AXIS));
        serverAddressPanel.add(hostnameField);
        serverAddressPanel.add(portField);

        tcpConnectButton = new JButton();
        tcpConnectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        tcpConnectButton.addActionListener(this);
        tcpConnectButton.setText("Connect");

        tcpConnectionPanel = new JPanel();
        tcpConnectionPanel.setVisible(false); // Default to disabled
        tcpConnectionPanel.setLayout(new BoxLayout(tcpConnectionPanel, BoxLayout.Y_AXIS));
        tcpConnectionPanel.add(serverAddressPanel);
        tcpConnectionPanel.add(tcpConnectButton);
        panel.add(tcpConnectionPanel);

        successLabel = new JLabel();
        successLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        successLabel.setHorizontalAlignment(SwingConstants.CENTER);
        successLabel.setOpaque(true);
        panel.add(successLabel);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == serialRadioButton) {
            comSelectorList.setVisible(true);
            tcpConnectionPanel.setVisible(false);

            disconnect(true);
        } else if (e.getSource() == tcpRadioButton) {
            comSelectorList.setVisible(false);
            tcpConnectionPanel.setVisible(true);

            disconnect(true);
        } else if (e.getSource() == tcpConnectButton) {
            try {
                initTcpConnection(hostnameField.getText(), Integer.parseInt(portField.getText()));
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(portField, "Port is not a number");
            }
        }
    }
    
    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        if (ignoreNextValueChange) {
            ignoreNextValueChange = false;
            return;
        }

        if (e.getSource() == comSelectorList) {
            // Find what port it was
            if (deviceConnectionHolder.getAllSerialPorts() != null) {
                if (comSelectorList.getSelectedIndex() != -1) {
                    for (int i = 0; i < deviceConnectionHolder.getAllSerialPorts().length; i++) {
                        // Check if this is the selected com selector
                        String name = deviceConnectionHolder.getAllSerialPorts()[i].getDescriptivePortName();
                        if (name.equals(comSelectorList.getSelectedValue())) {
                            final SerialPort newSerialPort = deviceConnectionHolder.getAllSerialPorts()[i];
                            
                            if (!isSerialOpen()
                                    || !newSerialPort.getDescriptivePortName().equals(getSerialConnection().getDescriptivePortName())) {
                                new Thread(() -> initSerialPort(newSerialPort)).start();
                            }
                            
                            break;
                        }
                    }
                } else if (isSerialOpen()) {
                    for (int i = 0; i < deviceConnectionHolder.getAllSerialPorts().length; i++) {
                        // Check if this is the selected com selector
                        String name = deviceConnectionHolder.getAllSerialPorts()[i].getDescriptivePortName();
                        if (name.equals(getSerialConnection().getDescriptivePortName())) {
                            comSelectorList.setSelectedIndex(i);
                            
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean isConnectionOpen() {
        return connectionMethod != null && connectionMethod.isOpen();
    }

    private boolean isConnecting() {
        return connectionMethod != null && connectionMethod.isConnecting();
    }

    private boolean isSerialConnection() {
        return connectionMethod != null && connectionMethod instanceof SerialConnectionMethod;
    }

    private boolean isTCPConnection() {
        return connectionMethod != null && connectionMethod instanceof TcpConnectionMethod;
    }

    private boolean isSerialOpen() {
        return isConnectionOpen() && isSerialConnection();
    }

    private SerialConnectionMethod getSerialConnection() {
        if (isSerialOpen()) {
            return (SerialConnectionMethod) connectionMethod;
        }

        return null;
    }

    private void initSerialPort(SerialPort newSerialPort) {
        if (newSerialPort.isOpen() || isConnecting()) return;

        successLabel.setText("Loading...");
        successLabel.setBackground(Color.yellow);

        disconnect(false);

        connectionMethod = new SerialConnectionMethod(newSerialPort);
        initConnection();
    }

    private void initTcpConnection(String hostname, int port) {
        successLabel.setText("Loading...");
        successLabel.setBackground(Color.yellow);

        disconnect(false);

        connectionMethod = new TcpConnectionMethod(hostname, port);

        initConnection();
    }

    private void initConnection() {
        connectionMethod.setConnectionMethodListener(this);

        boolean isOpen = connectionMethod.open();
        if (isOpen) {
            successLabel.setText("Connected");
            successLabel.setBackground(Color.green);
        } else {
            successLabel.setText("FAILED");
            successLabel.setBackground(Color.red);
        }
    }

    @Override
    public void receivedData(byte[] data) {
        if (dataReceivers != null) {
            for (DataReceiver dataReceiver : dataReceivers) {
                dataReceiver.receivedData(this, data);
            }
        }
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3) {
            ignoreNextValueChange = true;

            disconnect(true);
        }
    }

    private void disconnect(boolean updateUI) {
        if (isConnectionOpen() || isConnecting()) {
            if (isConnectionOpen()) connectionMethod.close();

            if (updateUI) {
                comSelectorList.clearSelection();

                successLabel.setText("Disconnected");
                successLabel.setBackground(null);
            }
        }
    }

    public void writeBytes(byte[] data) {
        if (isConnectionOpen()) {
            connectionMethod.writeBytes(data);
        }
    }
    
    public boolean bytesEqualWithoutDelimiter(byte[] a, byte[] b) {
        if (a.length == 0 || b.length == 0 || 
                a.length != b.length - DELIMITER.length) {
            return false;
        }
            
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        
        for (int i = 0; i < DELIMITER.length; i++) {
            if (DELIMITER[i] != b[a.length + i]) return false;
        }
        
        return true;
    }
    
    public void setDataReceivers(DataReceiver... dataReceivers) {
        this.dataReceivers = dataReceivers;
    }

    public int getTableIndex() {
        return tableIndex;
    }

    public void setTableIndex(int tableIndex) {
        this.tableIndex = tableIndex;
    }
    
    public boolean isWriting() {
        return writing;
    }

    public void setWriting(boolean writing) {
        this.writing = writing;
    }

    public JPanel getPanel() {
        return panel;
    }

    public JList<String> getComSelectorList() {
        return comSelectorList;
    }

    public JLabel getSuccessLabel() {
        return successLabel;
    }

    @Override
    public void mouseEntered(MouseEvent e) { }

    @Override
    public void mouseExited(MouseEvent e) { }

    @Override
    public void mousePressed(MouseEvent e) { }

    @Override
    public void mouseReleased(MouseEvent e) { }

}

