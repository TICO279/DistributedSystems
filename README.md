# Distributed Systems Game  
### Real-Time Stress-Resilient Multiplayer Game using JMS and TCP

This project implements a **distributed monster-hunting game** designed to test **scalability, fault tolerance, and latency handling** using **Java Sockets and JMS (ActiveMQ)**. The system features a centralized monster broadcaster and multiple clients competing in real-time, with stress-testing modules simulating hundreds of concurrent players.

## Features

- **Real-Time Multiplayer Game**:
  - Players register over TCP and attempt to hit monsters sent via JMS.
  - Fastest reactions earn points. First to 20 hits wins.
- **Message-Based Distribution**:
  - Monsters are broadcast via **JMS topics** to all subscribed players.
- **Player Registration via TCP**:
  - Initial client-server handshake and score tracking occur over sockets.
- **Performance Metrics Logging**:
  - Average registration and reaction times are logged for each session.
- **Scalable Stress Testing**:
  - Simulates hundreds of clients using multi-threading for performance analysis.
- **CSV Output**:
  - All results are saved into structured `.csv` logs for review.

## Architecture Overview

The project consists of **two major execution modes**:

1. **Game Mode**:
   - `MonsterSender.java`: Publishes monster positions via JMS (ActiveMQ).
   - `MonsterReceiver.java`: Subscribed clients receive monster messages and respond if they hit.
   - `Main.java`: Entry point that launches sender and receivers.

2. **Stress Testing Mode**:
   - `StressSender.java`: Manages game logic and player registration via TCP, sends monsters via JMS.
   - `StressReceiver.java`: Simulates hundreds of players connecting, reacting, and trying to win.

## Setup Instructions

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YourUsername/DistributedGameSystem.git
   cd DistributedGameSystem
2. **Install ActiveMQ**:
   Download and install Apache ActiveMQ, then run it locally:
   ```bash
   ./bin/activemq start
3. **Compile Java files**:
   Ensure you have jakarta.jms-api.jar and activemq-all-x.x.x.jar in your classpath:
   ```bash
   javac -cp .:lib/* *.java
4. **Run the game**:
   Launch sender and a few receivers to play manually:
   ```bash
   java -cp .:lib/* MonsterSender
   java -cp .:lib/* MonsterReceiver Player_A
   java -cp .:lib/* MonsterReceiver Player_B
  
5. **(Optional) Run stress tests**:
   Launch the stress test server and simulate 500 players and monitor results:
   ```bash
   java -cp .:lib/* StressSender 500
   java -cp .:lib/* StressReceiver


## Notes and Limitations

- The system relies on **Apache ActiveMQ** for message passing between components. Ensure ActiveMQ is running before starting the game or stress tests.
- **Message topics and structure** must remain consistent across senders and receivers. Any structural mismatch can result in dropped or unprocessed messages.
- The game logic assumes **correct message formatting** and **unique player identifiers**. Duplicate IDs or malformed messages may cause inconsistent behavior.
- The **stress test results** are written to `stress_results.csv`. Ensure write permissions are granted in the execution directory.
- The **number of virtual clients** in stress testing should reflect realistic loads. Excessive client counts may overwhelm slower machines or improperly tuned brokers.
- This project does not include a **graphical interface**; all interactions occur via the terminal.
- The system assumes **a single game round** per execution. Expanding to support continuous sessions or dynamic player entry would require architectural changes.
- The current implementation lacks **message authentication or encryption**. For secure environments, additional layers should be added to validate and protect data.



















   
