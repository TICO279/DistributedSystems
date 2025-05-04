import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StressSender {
    private static final String url = "tcp://localhost:61616";
    private static final String subject = "Monsters";
    private static final int WIN_CONDITION = 20;
    private static final int MAX_GAMES = 1; // Ejecuta 1 partida por run

    private ConcurrentHashMap<String, Integer> playerScore = new ConcurrentHashMap<>();
    private List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
    private List<Long> registrationTimes = Collections.synchronizedList(new ArrayList<>());
    private int successfulConnections = 0; // Conexiones que se registran correctamente

    private MessageProducer producer;
    private Session session;
    private boolean gameRunning = true;
    private boolean gameWon = false;
    private int gameCount = 0;           // Contador de partidas finalizadas

    // 🔹 Nuevo: número de clientes que esperas en esta ejecución
    private int expectedClients;

    public StressSender(int expectedClients) {
        this.expectedClients = expectedClients;  // Guarda cuántos clientes esperas
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

    // Hilo que envía monstruos mientras no se acabe la partida
    public void startGame() {
        new Thread(() -> {
            int id = 0;
            while (gameRunning) {
                try {
                    if (!gameWon) {
                        int x = (int) (Math.random() * 9);
                        int y = (int) (Math.random() * 9);
                        sendMonster(id, x, y);
                        id++;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException | JMSException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("No se enviarán más monstruos. Experimento finalizado.");
        }).start();
    }

    private void sendMonster(int id, int x, int y) throws JMSException {
        TextMessage message = session.createTextMessage(id + " " + x + " " + y);
        producer.send(message);
        System.out.println("Sending monster ID: " + id + " at position: " + x + ", " + y);
    }

    // Cuando alguien llega a WIN_CONDITION, se declara ganador
    private synchronized void sendWinner(String player) throws JMSException {
        if (!gameWon) {
            gameWon = true;
            TextMessage message = session.createTextMessage("WINNER " + player);
            producer.send(message);
            System.out.println(player + " won the game!");

            // Guardar métricas en el CSV
            saveResults(player);

            gameCount++;
            if (gameCount < MAX_GAMES) {
                resetGame();
            } else {
                gameRunning = false;
                System.out.println("Se han completado " + MAX_GAMES + " partidas. Fin del experimento.");
            }
        }
    }

    // Inicia el servidor TCP para el registro de jugadores
    public void startTCPServer(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Stress Test TCP Server started on port " + port);
                while (gameRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new PlayerHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Clase interna que maneja cada conexión de jugador
    private class PlayerHandler implements Runnable {
        private Socket socket;
        private String playerName;
        private long startTime;

        public PlayerHandler(Socket clientSocket) {
            this.socket = clientSocket;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println("WELCOME TO THE STRESS TEST");
                out.println("Enter your name:");
                playerName = in.readLine();

                if (playerName == null || playerName.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                // Calcula tiempo de registro
                long endTime = System.currentTimeMillis();
                long registrationTime = endTime - startTime;
                registrationTimes.add(registrationTime);

                successfulConnections++;

                // Registra puntaje inicial
                playerScore.putIfAbsent(playerName, 0);
                out.println("Welcome " + playerName + "! Your current score: " + playerScore.get(playerName));

                // Esperar golpes
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
        if (gameWon) return;

        String[] tokens = input.split(" ");
        if (tokens.length == 3) {
            int newScore = playerScore.compute(playerName, (key, value) -> (value == null ? 1 : value + 1));
            long reactionTime = System.currentTimeMillis() - Long.parseLong(tokens[2]);
            responseTimes.add(reactionTime);
            System.out.println(playerName + " hit a monster. Score: " + newScore + " | Reaction time: " + reactionTime + "ms");

            if (newScore >= WIN_CONDITION) {
                sendWinner(playerName);
            }
        }
    }

    // Guarda las métricas en un CSV
    private void saveResults(String winner) {
        // Copiamos listas para evitar problemas de concurrencia
        List<Long> responseCopy;
        List<Long> registrationCopy;
        synchronized (this) {
            responseCopy = new ArrayList<>(responseTimes);
            registrationCopy = new ArrayList<>(registrationTimes);
        }

        double avgResponseTime = responseCopy.stream().mapToDouble(val -> val).average().orElse(0.0);
        double stdResponseTime = calculateStdDev(responseCopy, avgResponseTime);

        double avgRegistrationTime = registrationCopy.stream().mapToDouble(val -> val).average().orElse(0.0);
        double stdRegistrationTime = calculateStdDev(registrationCopy, avgRegistrationTime);

        int numClients = successfulConnections;
        // Calculamos successRate en función de expectedClients
        double successRate = (expectedClients > 0)
                ? (successfulConnections / (double) expectedClients) * 100.0
                : 100.0; // Si no se especificó, dejamos 100%

        File file = new File("stress_results.csv");
        boolean writeHeader = !file.exists() || file.length() == 0;

        try (FileWriter writer = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(writer)) {

            // Si el archivo está vacío o no existe, escribimos el encabezado
            if (writeHeader) {
                bw.write("GameID,Winner,NumClients,AvgReactionTime,StdReactionTime,AvgRegistrationTime,StdRegistrationTime,SuccessRate");
                bw.newLine();
            }

            int gameId = gameCount + 1;
            bw.write(gameId + "," + winner + "," + numClients + "," + avgResponseTime + ","
                    + stdResponseTime + "," + avgRegistrationTime + "," + stdRegistrationTime + "," + successRate);
            bw.newLine();

            System.out.println("Resultados guardados en CSV para la partida " + gameId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double calculateStdDev(List<Long> values, double mean) {
        return Math.sqrt(values.stream().mapToDouble(val -> Math.pow(val - mean, 2)).average().orElse(0.0));
    }

    private void resetGame() {
        System.out.println("Restarting game...");
        playerScore.clear();
        responseTimes.clear();
        registrationTimes.clear();
        successfulConnections = 0;
        gameWon = false;
    }

    public static void main(String[] args) {
        // Leer número esperado de clientes desde argumentos (por defecto 50)
        int expected = 500;
        if (args.length > 0) {
            try {
                expected = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Número inválido, usando 50 por defecto.");
            }
        }

        // Instanciamos con la cantidad de clientes esperados
        StressSender sender = new StressSender(expected);
        sender.startTCPServer(5000);
        sender.startGame();
    }
}
