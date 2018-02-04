import java.awt.Component;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

import javax.swing.*;

/**
 * Created by andud on 18.01.18.
 */
public class Server {

    public static void main(String[] args) {
        int speed = 0;
        int port = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-speed":
                    speed = Integer.parseInt(args[++i]);
                    break;
            }
        }
        new Server(port, speed);
    }

    private int port, expectedSequence = 0;
    private volatile int speed;
    private boolean busy = false, work = true;
    private DatagramSocket serverSocket;
    private ConcurrentHashMap<Integer, byte[]> dataBuffer;
    private FileOutputStream fos;

    public Server(int port, int speed) {
        this.port = port;
        this.speed = speed * 1024;
        dataBuffer = new ConcurrentHashMap<>();
        startGui();
        startServer();
    }

    private void log(String msg) {
        System.out.println("Server with port " + port + "\n\tMsg: " + msg);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new DatagramSocket(port, InetAddress.getLocalHost());
                log("Started working");
                while (work) {
                    byte[] sendData = new byte[1024];
                    byte[] receiveData = new byte[speed/Client.window_size];
                    DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receive);
                    String request = requestStr(receive, receiveData);
                    if (request.contains("SYN")) {
                        if (!busy)
                            requestConnection(receive.getPort(), receive.getAddress(), false);
                    } else if (request.contains("FIN")) {
                        requestConnection(receive.getPort(), receive.getAddress(), true);
                        fos.close();
                    } else if (request.contains("FILE")) {
                        createFile(request);
                        serverSocket.send(
                                new DatagramPacket(("ACK".getBytes()),
                                        ("ACK".getBytes().length),
                                        receive.getAddress(),
                                        receive.getPort()));
                    } else {
                        byte[] received_checksum = Arrays.copyOfRange(receiveData, 0, 8);
                        CRC32 checksum = new CRC32();
                        checksum.update(Arrays.copyOfRange(receiveData, 8, receive.getLength()));
                        byte[] calculated_checksum = ByteBuffer.allocate(8).putLong(checksum.getValue()).array();
                        if (Arrays.equals(received_checksum, calculated_checksum)) {
                        	int seqNum = ByteBuffer.wrap(Arrays.copyOfRange(receiveData, 8, 12)).getInt();
                            log("Received sequence number: " + seqNum);
                            serverSocket.send(
                                    new DatagramPacket(("ACK " + seqNum+"\n"+speed).getBytes(),
                                            ("ACK " + seqNum+"\n"+speed).getBytes().length,
                                            receive.getAddress(),
                                            receive.getPort()));
                            if (seqNum == expectedSequence) {
                                appendFileData(Arrays.copyOfRange(receive.getData(), 12, receive.getLength()));
                                expectedSequence += receive.getLength();
                                while (dataBuffer.containsKey(expectedSequence)) {
                                    log("Re-sending to application: " + expectedSequence);
                                    appendFileData(Arrays.copyOfRange(receive.getData(), 12, receive.getLength()));
                                    int dataLength = dataBuffer.get(expectedSequence).length;
                                    dataBuffer.remove(expectedSequence);
                                    expectedSequence += dataLength + 8 + 4;
                                }
                            } else {
                                log("Data out of order");
                                log("Data with seq: " + seqNum + " buffered");
                                dataBuffer.put(seqNum, Arrays.copyOfRange(receive.getData(), 12, receive.getLength()));
                            }
                        } else {
                        	log("Damaged file");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void requestConnection(int clientPort, InetAddress clientAddres, boolean end) throws IOException {
        log("Changing connection state");
        String header;
        if (!end)
            header = "SYN\nACK\n\n";
        else
            header = "FIN\nACK\n\n";
        String recieved;
        byte[] sendData = header.getBytes();
        byte[] receiveData = new byte[1024];
        DatagramPacket send;
        DatagramPacket receive;
        do {
            send = new DatagramPacket(sendData, sendData.length, clientAddres, clientPort);
            receive = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.send(send);
            serverSocket.setSoTimeout(200);
            try {
                serverSocket.receive(receive);
            } catch (SocketTimeoutException s) {
                serverSocket.send(send);
            }
            recieved = requestStr(receive, receiveData);
        } while (!recieved.contains("ACK"));
        serverSocket.setSoTimeout(0);
        log("Success");
        busy = !end;
        if (end) expectedSequence = 0;
    }

    private void createFile(String file) throws IOException {
        file = file.substring(file.indexOf(" ") + 1, file.length());
        File newFile = new File("./received_" + file);
        if (!newFile.exists()) newFile.createNewFile();
        fos = new FileOutputStream(newFile);
    }

    private void appendFileData(byte[] data) throws IOException {
        fos.write(data);
    }


    static String requestStr(DatagramPacket receive, byte[] receiveData) {
        String request = new String(receive.getData());
        int length = 1024;
        for (int i = 0; i < receiveData.length; i++)
            if (receiveData[i] == 0) {
                length = i;
                break;
            }
        request = request.substring(0, length);
        return request;
    }
    
    private void startGui() {
    	JFrame jframe = new JFrame("Speed Controller");
    	jframe.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    	JPanel jpanel = new JPanel();
    	jpanel.setLayout(new BoxLayout(jpanel, BoxLayout.Y_AXIS));
    	JTextField speedField = new JTextField("Speed");
    	JButton submit = new JButton("Submit");
    	jpanel.add(speedField);
    	speedField.setEditable(true);
    	jpanel.add(submit);
    	submit.setAlignmentX(Component.CENTER_ALIGNMENT);
    	submit.addActionListener(e -> {
    		try {
    			int newSpeed = Integer.parseInt(speedField.getText());
    			if (newSpeed < 1 || newSpeed>=320)
    				speedField.setText("WRONG SPEED VALUE");
    			else speed = newSpeed*1024;
    		} catch(NumberFormatException n) {
    			speedField.setText("WRONG SPEED VALUE");
    		}
    	});
    	jframe.add(jpanel);
    	jframe.setLocationRelativeTo(null);
    	jframe.pack();
    	jframe.setVisible(true);
    }

}
