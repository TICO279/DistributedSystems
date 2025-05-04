import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterSender {

    private static String url = "tcp://localhost:61616"; // IP y puerto del broker JMS
    private static String subject = "Monsters";          // Nombre del tópico
    private static final int WIN_CONDITION = 5;
    private final int k = 1000;

    private ConcurrentHashMap<String, Integer> playerScore = new ConcurrentHashMap<>();
    private MessageProducer producer;
    private Session session;
    private boolean gameRunning = true;

    public MonsterSender() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
            Connection connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createTopic(subject);
            producer = session.createProducer(destination);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void startGame() {
        new Thread(() -> {
            int id = 0;
            while (gameRunning) {
                try {
                    int x = (int) (Math.random() * 9);
                    int y = (int) (Math.random() * 9);
                    sendMonster(id, x, y);
                    id++;
                    Thread.sleep(k);
                } catch (InterruptedException | JMSException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMonster(int id, int x, int y) throws JMSException {
        TextMessage message = session.createTextMessage(id + " " + x + " " + y);
        producer.send(message);
        System.out.println("Sending monster ID: " + id + " at position: " + x + ", " + y);
    }

    private void sendWinner(String player) throws JMSException {
        TextMessage message = session.createTextMessage("WINNER " + player);
        producer.send(message);
        System.out.println(player + " won the game!");
        resetGame();
    }

    public void startTCPServer(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("TCP Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new PlayerHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private class PlayerHandler implements Runnable {
        private Socket socket;
        private String playerName;

        public PlayerHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                // Mensajes de bienvenida y solicitud de nombre
                out.println("WELCOME TO MONSTERS");
                out.println("Enter your name:");

                // Leer el nombre
                playerName = in.readLine();
                if (playerName == null || playerName.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                // Registrar al jugador si no existe
                playerScore.putIfAbsent(playerName, 0);

                // Respuesta de registro: incluye info del juego
                out.println("Welcome " + playerName + "! Your current score: " + playerScore.get(playerName));
                // Enviamos información necesaria para jugar:
                out.println("INFO BROKER_URL=" + url + " TOPIC=" + subject);

                // Leer y procesar golpes
                String input;
                while ((input = in.readLine()) != null) {
                    if (input.equalsIgnoreCase("exit")) break;
                    if (input.startsWith("hit")) {
                        processHit(playerName, input);
                    }
                }
                socket.close();
            } catch (IOException | JMSException e) {
                e.printStackTrace();
            }
        }
    }

    private void processHit(String playerName, String input) throws JMSException {
        String[] tokens = input.split(" ");
        if (tokens.length == 3) {
            int x = Integer.parseInt(tokens[1]);

            int newScore = playerScore.compute(playerName, (key, value) -> (value == null ? 1 : value + 1));
            System.out.println(playerName + " hit monster at " + x + ". Score: " + newScore);

            if (newScore >= WIN_CONDITION) {
                sendWinner(playerName);
            }
        }
    }

    private void resetGame() {
        playerScore.replaceAll((player, score) -> 0);
        System.out.println("Restarting game...");
    }

    public static void main(String[] args) {
        MonsterSender sender = new MonsterSender();
        sender.startTCPServer(50000);
        sender.startGame();
    }
}
