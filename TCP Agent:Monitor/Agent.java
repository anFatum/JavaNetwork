import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent is class, created to represent using of TCP protocol.
 * It uses two threads: one for incrementing counter and another for serverSocket, that listening to requests.
 */
public class Agent {
    public static String[] requests = {"NEW", "ADD", "NET", "CLOSE", "CLK", "SYN", "DEL", "DELAGENT"};
    private String ip;
    private int port;
    private String monitorIp;
    private int monitorPort;
    private ConcurrentHashMap<Integer, String> portIp;
    private int counter;
    private Thread serverThread;
    private Thread counterThread;
    private boolean work = true;


    /**
     * There are two ways of creating Agents:
     * One is using only one parameter - the initial port of the first Agent
     * The other is using two parameters - the initial port of the agent and the port of already existed one
     * For ip they all use localhost that is possible to change on your needs.
     */

    public static void main(String[] args) throws Exception {
        if (args.length == 1)
            new Agent(Integer.parseInt(args[0]), 0);
        else
            new Agent(Integer.parseInt(args[1]), 0, "localhost", Integer.parseInt(args[0]));
        Thread.sleep(1000);
    }

    /**
     * First constructor gets two values: initial value of @param counter and @param port should be used for creating socket
     * In this constructor is created ConcurrentHashMap portIp which will store all other sockets' values
     * When initiating Agent connect to Monitor: class that manage other agents
     */

    public Agent(int port, int counter) {

        portIp = new ConcurrentHashMap<>();
        this.port = port;
        this.counter = counter;
        monitorPort = 9090;
        monitorIp = "localhost";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        portIp.put(port, ip);

        log("Added agent with ip: " + ip + " and port: " + port);

        createThreads();
        serverThread.start();
        counterThread.start();
        try {
            connectToMonitor();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * The second creates agent basing on the ip and port of the one that has already existed.
     * Does all the same the previous constructor does, except connecting to existing agent and receiving the table of
     * values ip-port all agents in the network.
     *
     * @param newport     - port of the current Agent
     * @param counter     - value that will be increasing
     * @param existedIp   - agent's ip this agent will be connected to
     * @param existedPort - agent's port this agent will be connected to
     */

    public Agent(int newport, int counter, String existedIp, int existedPort) {
        this.port = newport;
        monitorPort = 9090;
        monitorIp = "localhost";
        try {
            this.ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.counter = counter;

        log("Added new agent with ip: " + ip + " and port: " + port);

        createThreads();
        connectWithOther(existedIp, existedPort);
        serverThread.start();
        counterThread.start();
        try {
            connectToMonitor();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Method that write a message to BufferedWriter
     *
     * @param msg - message that has to be written
     * @param bw  - BufferedWriter that writes a message
     * @throws IOException
     */
    public static void msg(String msg, BufferedWriter bw) throws IOException {
        bw.write(msg);
        bw.newLine();
        bw.flush();

    }

    public void log(String msg) {
        System.out.println("AGENT LOG: " + port + " " + msg);
    }

    /**
     * Method that initializes threads for receiving messages and increasing counter value ones per ms
     * For correct work using static string tab that represents list of requests that can be
     * processed by agent
     */
    private void createThreads() {
        serverThread = new Thread(() -> {
            try {
                while (work) {
                    ServerSocket thisAgentSocket = new ServerSocket(port);
                    log("Started working");
                    log("KNOWN AGENTS: " + portIp);
                    Socket otherAgentSocket = thisAgentSocket.accept();
                    log("Agent connected");
                    BufferedReader br = new BufferedReader(new InputStreamReader(otherAgentSocket.getInputStream(), "UTF-8"));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(otherAgentSocket.getOutputStream()));

                    String request = br.readLine();
                    while (!request.equals(requests[3])) {

                        if (request.equals(requests[0]))
                            newAgent(bw, br);
                        else if (request.equals(requests[1]))
                            addAgent(bw, br);
                        else if (request.equals(requests[2]))
                            net(bw);
                        else if (request.equals(requests[4]))
                            clk(bw);
                        else if (request.equals(requests[5])) {
                            syn();
                            msg("NEW VALUE: " + counter, bw);
                        } else if (request.equals(requests[6])) {
                            del(ip, port);
                            work = false;
                            counterThread.interrupt();
                            msg("200", bw);
                            log("DELETED");
                            break;
                        } else if (request.equals(requests[7]))
                            del(br, bw);
                        request = br.readLine();
                    }

                    thisAgentSocket.close();
                }


            } catch (IOException e) {
                e.printStackTrace();
            }

        });

        counterThread = new Thread(() -> {
            boolean run = true;
            while (run) {
                try {
                    counter++;
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    run = false;
                }
            }
        });

    }

    /**
     * Mathod that adds new agent to the ip-port map with known agents to communication with it in the future
     *
     * @param bw - BufferedWriter got from clientSocket to communicate with connected socket
     * @param br - BufferedReader got from clientSocket to get requests from connected socket
     * @throws IOException
     */

    private void newAgent(BufferedWriter bw, BufferedReader br) throws IOException {

        msg("PORT", bw);
        int agentPort = Integer.parseInt(br.readLine());
        msg("IP", bw);
        String agentIp = br.readLine();

        if (!portIp.containsKey(agentPort)) {
            addToEveryone(agentPort, agentIp);
            msg("SUCCESS", bw);
        } else
            msg("FAILED, AGENT ALREADY EXISTS", bw);

    }

    /**
     * Method that is used to connecting to all known agents and adding the new one to their ip-port map
     *
     * @param port - new Agent's port that should be added
     * @param ip   - new Agent's ip that should be added
     * @throws IOException
     */
    private void addToEveryone(int port, String ip) throws IOException {
        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {
            if (entry.getKey() == this.port && entry.getValue().equals(this.ip)) {
                portIp.put(port, ip);
            } else if (entry.getKey() == port && entry.getValue().equals(ip)) {
                continue;
            } else {
                Socket agentSocket = new Socket(entry.getValue(), entry.getKey());
                BufferedReader br = new BufferedReader(new InputStreamReader(agentSocket.getInputStream(), "UTF-8"));
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(agentSocket.getOutputStream()));
                msg(requests[1], bw);
                msg(port + "", bw);
                msg(ip, bw);
                msg(requests[3], bw);
            }
        }

    }

    /**
     * Method that is used for connection with existed agent for adding current Agent to the network
     *
     * @param ip   - existed Agent's ip
     * @param port - existed Agent's port
     */
    private void connectWithOther(String ip, int port) {

        try {

            log("STARTED CONNECTION ");
            Socket agentSocket = new Socket(ip, port);
            BufferedReader br = new BufferedReader(new InputStreamReader(agentSocket.getInputStream(), "UTF-8"));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(agentSocket.getOutputStream()));

            msg(requests[0], bw);
            String response = br.readLine();
            msg(this.port + "", bw);
            br.readLine();
            msg(this.ip, bw);
            msg(requests[2], bw);
            portIp = new ConcurrentHashMap<>();
            response = br.readLine();
            response = br.readLine();
            while (!response.equals("101")) {
                int np = Integer.parseInt(response);
                String nip = br.readLine();
                portIp.put(np, nip);
                response = br.readLine();
            }
            msg(requests[3], bw);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    /**
     * This method is used for getting whole ip-port map and sending it to the connected client
     *
     * @param bw - BufferedWriter of the current Agent's serverSocket
     * @throws IOException
     */
    private void net(BufferedWriter bw) throws IOException {

        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {

            msg(entry.getKey() + "", bw);
            msg(entry.getValue(), bw);

        }

        msg("101", bw);


    }

    /**
     * Method for sending current counter value
     *
     * @param bw - BufferedWriter of the current Agent's serverSocket
     * @throws IOException
     */
    private void clk(BufferedWriter bw) throws IOException {

        log("SENDING COUNTER VALUE");

        msg(counter + "", bw);

    }

    /**
     * Method for adding port-ip values to the current known ip-port map
     *
     * @param bw - BufferedWriter got from clientSocket to communicate with connected socket
     * @param br - BufferedReader got from clientSocket to get requests from connected socket
     * @throws IOException
     */
    private void addAgent(BufferedWriter bw, BufferedReader br) throws IOException {
        msg("WAITING FOR PORT", bw);
        int portNum = Integer.parseInt(br.readLine());
        msg("WAITING FOR IP", bw);
        String nip = br.readLine();
        portIp.put(portNum, nip);
    }

    /**
     * Method for synchronize current counter value with each known Agent
     *
     * @throws IOException
     */
    private void syn() throws IOException {
        int synch = counter;
        BufferedWriter bw = null;
        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {
            if (!(entry.getKey() == this.port && entry.getValue().equals(this.ip))) {
                Socket agentSocket = new Socket(entry.getValue(), entry.getKey());
                BufferedReader br = new BufferedReader(new InputStreamReader(agentSocket.getInputStream(), "UTF-8"));
                bw = new BufferedWriter(new OutputStreamWriter(agentSocket.getOutputStream()));
                msg(requests[4], bw);
                synch += Integer.parseInt(br.readLine());
                msg(requests[3], bw);
            }
        }
        counter = synch / portIp.size();
    }

    /**
     * Method for deleting specific Agent from the ip-port map
     *
     * @param bw - BufferedWriter got from clientSocket to communicate with connected socket
     * @param br - BufferedReader got from clientSocket to get requests from connected socket
     * @throws IOException
     */
    private void del(BufferedReader br, BufferedWriter bw) throws IOException {
        msg("WAITING IP:", bw);
        String ipToDelete = br.readLine();
        msg("WAITING PORT:", bw);
        int portToDelete = Integer.parseInt(br.readLine());
        portIp.remove(portToDelete, ipToDelete);
    }

    /**
     * Method for sending requests to all known Agents to delete specific Agent from the ip-port map
     *
     * @param ip   - Agent's ip tha should be deleted
     * @param port - Agent's ip tha should be deleted
     * @throws IOException
     */
    private void del(String ip, int port) throws IOException {
        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {
            Socket otherSocket = new Socket(entry.getValue(), entry.getKey());
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(otherSocket.getOutputStream()));
            msg(requests[7], bw);
            msg(ip, bw);
            msg(port + "", bw);
            msg(requests[3], bw);
        }
    }

    /**
     * Method for connecting to the Monitor - special class created to control Agents
     *
     * @throws IOException
     */
    private void connectToMonitor() throws IOException {
        Socket monitorSocket = new Socket(monitorIp, monitorPort);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
        msg(requests[1], bw);
        msg(ip, bw);
        msg(port + "", bw);
        msg(requests[3], bw);

    }
}
