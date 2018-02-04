import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class Monitor is created to control Agents' communication in the network
 * Represented as HTTP server.
 * Could also be used with direct connection.
 */
public class Monitor {

    private ConcurrentHashMap<Integer, String> portIp;
    private Thread serverThread;
    private String ip;
    private int port;

    /**
     * To run Monitor it should get port on which it will work
     *
     * @param args - program arguments
     */
    public static void main(String[] args) {
        if (args.length == 1)
            new Monitor(Integer.parseInt(args[0]));
    }

    /**
     * Monitor has port-ip Map as Agent does. It contains ports and ip all existing Agents in the network.
     *
     * @param port - port at which Monitor will work
     */
    public Monitor(int port) {
        portIp = new ConcurrentHashMap<>();
        this.port = port;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        createThreads();
        serverThread.start();
    }

    public static void log(String msg) {
        System.out.println("MONITOR LOG: " + msg);
    }

    /**
     * Method is used to initialize Thread for server
     */
    private void createThreads() {
        serverThread = new Thread(() -> {
            try {
                while (true) {
                    ServerSocket thisAgentSocket = new ServerSocket(port);
                    log("Started working");
                    log("KNOWN AGENTS: " + portIp);
                    Socket otherAgentSocket = thisAgentSocket.accept();
                    log("Agent connected");
                    BufferedReader br = new BufferedReader(new InputStreamReader(otherAgentSocket.getInputStream(), "UTF-8"));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(otherAgentSocket.getOutputStream()));
                    String request = br.readLine();
                    while (!request.equals(Agent.requests[3])) {
                        log(request);
                        if (request.equals(Agent.requests[1])) {
                            addAgent(br);
                        } else if (request.equals(Agent.requests[5])) {
                            allKnown(bw);
                            synchronize(br, bw);
                        } else if (request.equals(Agent.requests[6]))
                            delete(br);
                        else if (request.equals("GET /favicon.ico HTTP/1.1")) {
                            while (true) {
                                if (request == null || request.trim().length() == 0)
                                    break;
                                request = br.readLine();
                            }
                            String tresponse = "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: " + htmlresp().length() + "\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Server: Apache/2.4.6 (CentOS)\r\n" +
                                    "Date: Tue, 04 May 2010 22:30:36 GMT\r\n" +
                                    "Connection: close\r\n\r\n" + htmlresp();
                            Agent.msg(tresponse, bw);
                            break;
                        } else if (request.contains("GET")) {
                            getResponse(request, bw, br);
                            break;

                        } else if (request.contains("POST")) {
                            postResponse(request, bw, br);
                            break;
                        }
                        request = br.readLine();
                    }
                    thisAgentSocket.close();
                    log("CLOSING CONNECTION");
                }
            } catch (IOException e) {
                log(e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Method to response all known agents, which are in th port-ip map
     *
     * @param bw - BufferedWriter to connect with client
     */
    private void allKnown(BufferedWriter bw) {
        try {
            Agent.msg("Port-Ip List: ", bw);
            for (Map.Entry<Integer, String> entry : portIp.entrySet())
                Agent.msg(entry.getKey() + " " + entry.getValue(), bw);

        } catch (IOException e) {
            log(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Method that send request to specific Agent to synchronize counter value
     *
     * @param br - BufferedReader to receive data from client
     * @param bw - BufferedWriter to connect with client
     * @throws IOException
     */
    private void synchronize(BufferedReader br, BufferedWriter bw) throws IOException {
        int portToConnect = Integer.parseInt(br.readLine());
        String ipToConnect = br.readLine();
        Socket synSocket = new Socket(ipToConnect, portToConnect);
        BufferedReader sockbr = new BufferedReader(new InputStreamReader(synSocket.getInputStream(), "UTF-8"));
        BufferedWriter sockbw = new BufferedWriter(new OutputStreamWriter(synSocket.getOutputStream()));
        Agent.msg(Agent.requests[5], sockbw);
        String response = sockbr.readLine();
        Agent.msg(Agent.requests[3], sockbw);
        Agent.msg(response, bw);
    }

    /**
     * Method is identical to the previous one, except it uses as parameters ip and port of Agent that should be synchronized
     *
     * @param ip   - Agent's ip that should be synchronized
     * @param port - Agent's port that should be synchronized
     * @throws IOException
     */
    private void synchronize(String ip, int port) throws IOException {
        Socket synSocket = new Socket(ip, port);
        BufferedReader sockbr = new BufferedReader(new InputStreamReader(synSocket.getInputStream(), "UTF-8"));
        BufferedWriter sockbw = new BufferedWriter(new OutputStreamWriter(synSocket.getOutputStream()));
        Agent.msg(Agent.requests[5], sockbw);
        sockbr.readLine();
        Agent.msg(Agent.requests[3], sockbw);
    }

    /**
     * Method to delete specific Agent, that client request
     *
     * @param br - BufferedReader to receive data from client
     * @throws IOException
     */
    private void delete(BufferedReader br) throws IOException {
        int portToConnect = Integer.parseInt(br.readLine());
        String ipToConnect = br.readLine();
        Socket synSocket = new Socket(ipToConnect, portToConnect);
        portIp.remove(portToConnect);
        BufferedWriter sockbw = new BufferedWriter(new OutputStreamWriter(synSocket.getOutputStream()));
        Agent.msg(Agent.requests[6], sockbw);
    }

    /**
     * Method is identical to the previous one, except it uses as parameters ip and port of Agent that should be deleted
     *
     * @param ip   - Agent's ip that should be deleted
     * @param port - Agent's port that should be deleted
     * @throws IOException
     */
    private void delete(String ip, int port) throws IOException {
        Socket synSocket = new Socket(ip, port);
        portIp.remove(port);
        BufferedReader sockbr = new BufferedReader(new InputStreamReader(synSocket.getInputStream(), "UTF-8"));
        BufferedWriter sockbw = new BufferedWriter(new OutputStreamWriter(synSocket.getOutputStream()));
        Agent.msg(Agent.requests[6], sockbw);
        log(sockbr.readLine());
        log("DELETED: " + portIp);
        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {
            synchronize(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Method that reads HTTP GET request till the end
     *
     * @param request - first line of HTTP GET request
     * @param bw      - BufferedWriter that response on HTTP GET request
     * @param br      -  BufferedReader that gets every next line of HTTP GET request
     * @throws IOException
     */
    private void getResponse(String request, BufferedWriter bw, BufferedReader br) throws IOException {
        while (true) {
            if (request == null || request.trim().length() == 0)
                break;
            request = br.readLine();
        }

        Agent.msg(htmlresp(), bw);
    }

    /**
     * This method is processing HTTP POST request (which can be to delete or to synchronize agents)
     *
     * @param request - first line of HTTP POST request
     * @param bw      - BufferedWriter that response on HTTP POST request
     * @param br      -  BufferedReader that gets every next line of HTTP POST request
     * @throws IOException
     */
    private void postResponse(String request, BufferedWriter bw, BufferedReader br) throws IOException {
        String tres = request.substring(request.indexOf('/'));
        tres = tres.substring(1, tres.indexOf(' '));
        while (true) {
            if (request == null || request.trim().length() == 0) {
                break;
            }
            request = br.readLine();
        }
        String post[] = tres.split("/");
        if (post[0].equals("delete")) {
            delete(portIp.get(Integer.parseInt(post[1])), Integer.parseInt(post[1]));
            Agent.msg(htmlresp(), bw);
        } else if (post[0].equals("synchronize")) {
            synchronize(portIp.get(Integer.parseInt(post[1])), Integer.parseInt(post[1]));
            Agent.msg(htmlresp(), bw);
        }

    }

    /**
     * Method that receives counter value of the specific Agent and returns it
     *
     * @param ip   - Agent's ip that should send counter value
     * @param port - Agent's port that should send counter value
     * @return the counter value Monitor receives
     * @throws IOException
     */
    private String getClkValue(String ip, int port) throws IOException {
        Socket synSocket = new Socket(ip, port);
        BufferedReader sockbr = new BufferedReader(new InputStreamReader(synSocket.getInputStream(), "UTF-8"));
        BufferedWriter sockbw = new BufferedWriter(new OutputStreamWriter(synSocket.getOutputStream()));
        Agent.msg(Agent.requests[4], sockbw);
        String value = sockbr.readLine();
        Agent.msg(Agent.requests[3], sockbw);
        return value;
    }

    /**
     * Method that creates property HTTP response either for GET or POST request
     *
     * @return the body of HTTP response
     * @throws IOException
     */
    private String htmlresp() throws IOException {
        String s = ("\n<html>\n<body>");
        for (Map.Entry<Integer, String> entry : portIp.entrySet()) {
            s += "<p>" + entry.getValue() + ":";
            s += entry.getKey() + " = " + getClkValue(entry.getValue(), entry.getKey()) + "</p>\n";
            s += "<form action=\"\\delete\\" + entry.getKey() + "\" method=\"post\">\n" +
                    "    <input type=\"submit\" name=\"" + entry.getKey() + "\" value=\"DELETE\" />\n" +
                    "</form>\n";
            s += "<form action=\"\\synchronize\\" + entry.getKey() + "\" method=\"post\">\n" +
                    "    <input type=\"submit\" name=\"" + entry.getKey() + "\" value=\"SYNCHRONIZE\" />\n" +
                    "</form>\n";

        }
        s += "</body></html>\n";
        String tresponse = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Server: Apache/2.4.6 (CentOS)\r\n" +
                "Content-Length: " + s.length() + "\r\n" +
                "Content-Type: text/html\r\n\r\n" + s;
        return tresponse;
    }

    /**
     * Method which adds the Agent to the ip-port map of the known agents
     *
     * @param br - BufferedReader to receive data from client (Agent's port and ip that should be added)
     * @throws IOException
     */
    private void addAgent(BufferedReader br) throws IOException {
        String agentIp = br.readLine();
        int agentPort = Integer.parseInt(br.readLine());
        portIp.put(agentPort, agentIp);
    }

}
