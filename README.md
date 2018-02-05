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
