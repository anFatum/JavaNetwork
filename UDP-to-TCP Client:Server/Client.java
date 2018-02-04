import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.CRC32;

public class Client {

    private InetAddress ip;
    private int port;
    private volatile int initSize;
    private File fileToSend;
    private boolean work = true;
    private volatile int currentSend = 0, sequenceNumber = 0, sentPackets = 0;
    private FileInputStream fis;
    private ConcurrentLinkedDeque<Integer> pakets, paketsRecieved;

    public static final int window_size = 5;

    public Client(int port, String ip, String fileName) {
        fileToSend = new File("./" + fileName);
        try {
            fis = new FileInputStream(fileToSend);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        initSize = 1024;
        this.port = port;
        try {
			this.ip = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
        pakets = new ConcurrentLinkedDeque<>();
        paketsRecieved = new ConcurrentLinkedDeque<>();
        sendUdp();
    }

    public static void main(String[] args) {
        String ip = "";
        String fileName = "";
        int port = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-server":
                    ip = args[++i];
                    break;
                case "-port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-file":
                    fileName = args[++i];
                    break;
            }
        }
        new Client(port, ip, fileName);
    }

    private void sendUdp() {
        new Thread(() -> {
            try {
                log("Establishing connection");
                stateConnection(false);
                log("Connection established");
                log("Send file name");
                sendNameFile();
                log("Success");
                log("Start sending");
                new Thread(() -> {
                    int seconds = 0;
                    int forTen = 0;
                    while (work) {
                        try {
                        	int init = sequenceNumber;
                            Thread.sleep(1000);
                            init = sequenceNumber - init;
                            forTen+=init;
                            seconds++;
                            log("Average speed during 1 second: " + init/1024 + "Kb/s ("+ init + " B/s)");
                            int left = 0;
                            try {
                            left = Files.readAllBytes(Paths.get(fileToSend.getAbsolutePath())).length - sequenceNumber + 12*sentPackets;
                            }catch(IOException n) {
                            	n.printStackTrace();
                            }
                            log ("Total average speed: " + sequenceNumber/seconds/1024 + "Kb/s (" + sequenceNumber/seconds + " B/s)");
                            log("Left to send: " + left/1024 + "Kb (" + left + " b)" );
                            if (seconds % 10 == 0) {
                                log("Average speed during 10 second: " + forTen/10/1024 + "Kb/s (" + forTen/10 + " B/s)");
                                forTen = 0;
                            }
                        } catch (InterruptedException a) {
                            a.printStackTrace();
                        }
                    }
                }).start();
                while (work) {
                    if (pakets.size() < window_size) {
                        currentSend++;
                        new Thread(() -> {
                            try {
                                sendData();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }).start();
                        Thread.sleep(200);
                    }
                    if(pakets.size()==paketsRecieved.size()) {
                    	pakets.clear();
                    	paketsRecieved.clear();
                    }
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void sendData() throws IOException {
        int actualSize = initSize - 8 - 4; // 8 - checkSum, 4 - sequence number
        byte[] sendData;
        byte[] receiveData = new byte[actualSize];
        byte[] dataToSend = new byte[actualSize];
        int dataLength = fis.read(dataToSend, 0, actualSize);
        if (dataLength == -1){
        	if (pakets.size()==0 && paketsRecieved.size()==0) {
		        stateConnection(true);
		        work = false;
        	}
        }
        else {
            byte[] dataBytes = Arrays.copyOfRange(dataToSend, 0, dataLength);
            sendData = prepareFragment(sequenceNumber, dataBytes);
            int seqNum = sequenceNumber;
            sequenceNumber = sequenceNumber + (sendData.length);
            pakets.push(seqNum);
            log("Sending data with sequence number: " + seqNum);
            DatagramSocket clientSocket = new DatagramSocket();
            DatagramPacket send = new DatagramPacket(sendData, sendData.length, ip, port);
            DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
            String received = "";
            while (!received.contains("ACK " + seqNum)) {
                clientSocket.send(send);
                clientSocket.setSoTimeout(50);
                try {
                	clientSocket.receive(receive);
                }catch (SocketTimeoutException ex) {
                }
                received = Server.requestStr(receive, receive.getData());
            }
            initSize = Integer.parseInt(received.substring(received.indexOf('\n')+1, received.length()))/window_size;
            paketsRecieved.push(seqNum);
            currentSend--;
            sentPackets++;
            clientSocket.close();
        }
    }

    private void log(String msg) {
        System.out.println("Client with port 8080\n\t" + " Msg: " + msg);
    }

    public byte[] prepareFragment(int seqNum, byte[] dataBytes) {
        byte[] seqNumBytes = ByteBuffer.allocate(4).putInt(seqNum).array();
        CRC32 checksum = new CRC32();
        checksum.update(seqNumBytes);
        checksum.update(dataBytes);
        byte[] checksumBytes = ByteBuffer.allocate(8).putLong(checksum.getValue()).array();
        byte[] overall = new byte[seqNumBytes.length + checksumBytes.length + dataBytes.length];
        for (int i = 0; i < overall.length; i++) {
            if (i < checksumBytes.length)
                overall[i] = checksumBytes[i];
            else if (i < seqNumBytes.length + checksumBytes.length)
                overall[i] = seqNumBytes[i - checksumBytes.length];
            else
                overall[i] = dataBytes[i - seqNumBytes.length - checksumBytes.length];
        }
        return overall;
    }

    private void stateConnection(boolean end) throws IOException {
        String header;
        if (!end)
            header = "8080\n" + port + "\nSYN\n\n";
        else
            header = "FIN\n\n";
        String recieved = "";
        byte[] sendData = header.getBytes();
        byte[] receiveData = new byte[1024];
        DatagramPacket send;
        DatagramSocket clientSocket = new DatagramSocket();
        DatagramPacket receive;
        do {
            send = new DatagramPacket(sendData, sendData.length, ip, port);
            receive = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.send(send);
            clientSocket.setSoTimeout(1000);
            try {
                clientSocket.receive(receive);
            } catch (SocketTimeoutException s) {
                clientSocket.send(send);
            }
            recieved = Server.requestStr(receive, receiveData);
        } while (!recieved.contains("ACK"));
        sendData = "ACK".getBytes();
        send = new DatagramPacket(sendData, sendData.length, InetAddress.getLocalHost(), port);
        clientSocket.send(send);
        clientSocket.setSoTimeout(300);
        try {
            clientSocket.receive(receive);
        } catch (SocketTimeoutException ex) {
            log("Success");
        }
        clientSocket.close();
    }

    private void sendNameFile() throws IOException {
        String msg = "FILE " + fileToSend.getName();
        String received = "";
        DatagramSocket clientSocket = new DatagramSocket();
        do {
            byte[] sendData = msg.getBytes();
            byte[] receiveData = new byte[1024];
            DatagramPacket send = new DatagramPacket(sendData, sendData.length, ip, port);
            DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.send(send);
            clientSocket.setSoTimeout(300);
            try {
                clientSocket.receive(receive);
            } catch (SocketTimeoutException e) {
                log("Re-sending file name");
            }
            received = Server.requestStr(receive, receiveData);
        } while (!received.contains("ACK"));
        clientSocket.close();
    }

}
