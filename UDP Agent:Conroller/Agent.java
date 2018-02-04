import java.io.*;
import java.net.*;
import java.util.Enumeration;

/**
 * Created by andud on 08.12.17.
 */
public class Agent {
    private int startPortUdp;
    private int portUdp;
    private DatagramSocket serverUDP = null;
    private int timeRepeat;
    private int licznik;
    private boolean work = true;

    public Agent(int licznik, int timeRepeat, int startPortUdp, int ports) {

        this.licznik = licznik;
        this.timeRepeat = timeRepeat*1000;
        this.portUdp = startPortUdp+ports;
        this.startPortUdp=startPortUdp;

        createThreads();
        threadToRepeat();
    }

    public static void main(String[] args) {
        new Agent(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
    }

    public void log(String msg) throws UnknownHostException {
        System.out.println("Agent " + InetAddress.getLocalHost() + ":" +portUdp + " : "+ msg);
    }

    private void createThreads() {
        Thread counterThread = new Thread(() -> {
            try {
                while (work) {
                    Thread.sleep(1);
                    this.licznik++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        counterThread.start();
        Thread serverThread = new Thread(() -> setServerUDP());
        serverThread.start();
    }


    private void sendResponce(String req, byte[] sendData, DatagramPacket receive) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteArrayOutputStream);
        switch (req) {
            case "counter":
                dataOut.writeInt(licznik);
                break;
            case "period":
                dataOut.writeInt(timeRepeat);
                break;
            default:
                dataOut.writeBytes(req);
        }
        dataOut.close();
        sendData = byteArrayOutputStream.toByteArray();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, receive.getAddress(), receive.getPort());
        serverUDP.send(send);
        log("RESPONSE SENT: "+req);
    }

    private String requestStr(DatagramPacket receive, byte[] receiveData) {
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

    private void setServerUDP() {
        try {
            serverUDP = new DatagramSocket(portUdp, InetAddress.getLocalHost());
            log("CREATED");
            while (work) {
                byte[] sendData = new byte[1024];
                byte[] receiveData = new byte[1024];
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                log("WAITING FOR REQUEST");
                serverUDP.receive(receive);
                String request = requestStr(receive, receiveData);
                    System.out.println("REQUEST: " + request);
                if (request.contains("GET")){
                    if (request.contains("period"))
                        sendResponce("period", sendData, receive);
                    else
                        sendResponce("counter", sendData, receive);
                } else if (request.contains("SET")) {
                    String tmp[] = request.split(" ");
                    switch (tmp[1]) {
                        case "counter":
                            try {
                                licznik = Integer.parseInt(tmp[2]);
                                log("SET counter AT: "+Integer.parseInt(tmp[2]));
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception){
                                log("Wrong counter value, value hasn't been changed");
                            }
                            break;
                        case "period":
                            try {
                                timeRepeat = Integer.parseInt(tmp[2]);
                                log("SET period AT: "+Integer.parseInt(tmp[2]));
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception){
                                log("Wrong period value, value hasn't been changed");
                            }
                            break;
                    }
                    sendResponce("OK", sendData, receive);
                } else if (request.contains("STOP")) {
                    log("STOPPED");
                    sendResponce("OK", sendData, receive);
                    work = false;
                }

            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverUDP.close();
        }
    }


    private void threadToRepeat() {
        Thread repeatThread = new Thread(() -> {
            while (work) {
                try {
                    Thread.sleep(timeRepeat);
                    Enumeration<NetworkInterface> interfaces =
                            NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface networkInterface = interfaces.nextElement();
                        if (networkInterface.isLoopback())
                            continue;
                        for (InterfaceAddress interfaceAddress :
                                networkInterface.getInterfaceAddresses()) {
                            InetAddress broadcast = interfaceAddress.getBroadcast();
                            if (broadcast == null)
                                continue;
                            for (int i = startPortUdp; i<startPortUdp+10 && work; i++)
                                sendPacket(broadcast, i);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        repeatThread.start();
    }

    private void sendPacket(InetAddress ip, int portUdp) throws IOException {
        byte[] sendData;
        byte[] receiveData = new byte[1024];
        String msg = "GET";
        sendData = msg.getBytes();
        DatagramSocket clientSocket = new DatagramSocket();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, ip, portUdp);
        DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.send(send);
        int counterToDivide = licznik;
        int numberOfSockets = 1;
        boolean work = true;
        log("SEND PACKET TO: " + ip);
        try {
            while (work) {
                clientSocket.setSoTimeout(500);
                clientSocket.receive(receive);
                if (receive == null || receive.getData().length == 0)
                    work = false;
                else {
                    if (!receive.getAddress().equals(InetAddress.getLocalHost()) || !(receive.getPort()==this.portUdp)) {
                        ByteArrayInputStream byteIn = new ByteArrayInputStream(receive.getData());
                        DataInputStream dataIn = new DataInputStream(byteIn);
                        int integ = dataIn.readInt();
                        counterToDivide += integ;
                        numberOfSockets++;
                    }
                }
            }
        } catch (SocketTimeoutException ex) {
            // System.out.println("TIMEOUT");
        } finally {
            clientSocket.close();
        }
        if (numberOfSockets > 1) {
            licznik = counterToDivide / numberOfSockets;
            log("NEW LICZNIK: " + licznik);
        }
    }

}
