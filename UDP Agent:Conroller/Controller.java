import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by andud on 13.12.17.
 */
public class Controller {

    private static JPanel jPanel;
    static JFrame jframe;
    static JTextField value, ip, field, port;
    static JButton jButton;
    static JComboBox option;


    public static void main(String[] args) {
        start();
    }

    private static void start() {
        jframe = new JFrame("Controller");
        jframe.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jPanel = new JPanel();
        fillPanels(jPanel);
        listenerButton(jButton);
        jframe.add(jPanel);
        jframe.setLocationRelativeTo(null);
        jframe.pack();
        jframe.setVisible(true);
    }

    private static void fillPanels(JPanel jPanel) {
        ip = new JTextField("IP");
        port = new JTextField("PORT");
        jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));
        jPanel.add(ip);
        jPanel.add(port);
        String[] options = {"STOP", "SET", "GET"};
        option = new JComboBox(options);
        setList(option);
        jPanel.add(option);
        field = new JTextField("FIELD");
        //jPanel.add(field);
        jButton = new JButton("OK");
        jButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        jPanel.add(jButton);
        value = new JTextField("VALUE");
    }

    private static void setList(JComboBox option) {
        option.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                if (itemEvent.getItem().toString().equals("SET")) {
                    jPanel.remove(jButton);
                    jPanel.add(field);
                    jPanel.add(value);
                    jPanel.add(jButton);
                    jframe.pack();
                } else if (itemEvent.getItem().toString().equals("GET")) {
                    jPanel.remove(jButton);
                    jPanel.remove(value);
                    jPanel.add(field);
                    jPanel.add(jButton);
                    jframe.pack();
                } else {
                    jPanel.remove(field);
                    jPanel.remove(value);
                    jframe.pack();
                }
            }
        });
    }

    private static void listenerButton(JButton jButton) {
        jButton.addActionListener(e -> {
            switch (option.getSelectedItem().toString()) {
                case "STOP":
                    sendReq(ip.getText(), "STOP", Integer.parseInt(port.getText()));
                    break;
                case "SET":
                    sendReq(ip.getText(), "SET " + field.getText() + " " + value.getText(), Integer.parseInt(port.getText()));
                    break;
                case "GET":
                    sendReq(ip.getText(), "GET "+field.getText(), Integer.parseInt(port.getText()));
                    break;
            }
        });
    }

    private static void sendReq(String ip, String msg, int port) {
        byte[] sendData;
        byte[] receiveData = new byte[1024];
        sendData = msg.getBytes();
        boolean work = true;
        try {
            while (work) {
                DatagramSocket clientSocket = new DatagramSocket();
                DatagramPacket send = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ip), port);
                System.out.println("SENT: "+ send.getSocketAddress());
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.send(send);
                clientSocket.receive(receive);
                if (receive.getData().length>0) {
                    work = false;
                    if (msg.startsWith("GET")) {
                        ByteArrayInputStream byteIn = new ByteArrayInputStream(receive.getData());
                        DataInputStream dataIn = new DataInputStream(byteIn);
                        int integ = dataIn.readInt();
                        System.out.println(integ);
                    }
                    else {
                        String request = new String(receive.getData());
                        int length = 1024;
                        for (int i = 0; i < receiveData.length; i++)
                            if (receiveData[i] == 0) {
                                length = i;
                                break;
                            }
                        request = request.substring(0, length);
                        System.out.println(request);
                    }
                }
                Thread.sleep(3000);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

}
