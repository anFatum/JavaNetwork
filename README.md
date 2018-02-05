# JavaNetwork
The first Agent/Monitor classes represents TCP connection basis. 

Agent
The agents deal with counters and connections. Each agent is a separate node, identified by IP address and port number. The port is used by the agent to communicate with the other agents and the monitor. Each agent has a single counter.
Additionally, every agent knows the full structure of network, i.e., knows IP adresses and communication ports of all the remaining agents. Each of the agents can be requested to provide the current value of the counter and the description of the network by sending the communicates to his listening port.

Monitor
The monitor is a simple program that displays the state of the network of counter. It presents a table with the list of agents (i.e., pairs IP:port) together with their counter values. The table is dynamically updated.

------

The second Agent/Controller classes represents UDP connection basis

Agent
The agents deal with counters and connections. Each agent is a separate node, identified by its IP address. The IP of an agent depends on the node on which the agent runs. The port number is in range of 10. This port is used by an agent for communication.
Each agent has a single counter.
Each agent periodically, every specified time period, synchronizes its cunter value with values of counters of other agents.

The controller
An additional application is used as a controller of the network of agents. Using it, one can read or change the value of the counter or the synchronization period of each agent separately. The application as its first parameter acepts the the IP address of an agent.

------

The third Client/Server represents UDP transaction with additional options to save reliability of this protocol.
The client is a process, which sends the data. It accepts the following parameters at
runtime (in any order):
• -server <address> – specifies an address, at which the server application works.
• -port <port numer> – specifies a UDP port number, at which the server application works.
• -file <file name> – a the name of a file for transmission.
  
During tranfer, the client prints out the following information about the transmission:
• te number of delivered bytes and the number of bytes left,
• current average speed computed from the beginning of the transfer,
• current average speed computed during recent 10s,
• current average speed computed during recent 1s r information about data transfer errors, if they appear.

The server’s goal is to accept a connection from a client and to receive a file delivered by this client. At execution, the server accepts the following parameters (in any order):
• -port <port numer> – specifies a UDP port number, at which the server application works.
• -speed <initial speed>–initialdatatransferspeedexpressedinKB/s(1KB=1024B).
  
During work, the sever may read from the keyboard a new data transfer speed value (in KB/s). If such value is read, the server sends it to the client, which is obliged to adjust to the new value. Such changes can be done at any moment and many times during the transfer.
