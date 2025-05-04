import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class MonsterReceiver {
    // Por defecto, sabemos el IP/puerto del servidor de registro
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 50000;

    // Inicialmente, podemos dejar estos en null/valores por defecto;
    // luego los rellenamos con lo que envíe el servidor.
    private String brokerUrl = "tcp://localhost:61616";
    private String topicName = "Monsters";

    private JFrame frame;
    private JButton[][] buttons = new JButton[9][9];
    private String playerName;
    private Socket socket;
    private PrintWriter out;

    public MonsterReceiver() {
        playerName = JOptionPane.showInputDialog("Enter Player Name:");
        if (playerName == null || playerName.isEmpty()) {
            System.exit(0);
        }
        connectToServer();
        createUI();
        // Suscripción al tópico la haremos DESPUÉS de leer la info del servidor
        // Si ya tenemos brokerUrl y topicName, podemos suscribir
        subscribeToTopic();
    }

    /**
     * Conecta el jugador al servidor TCP y recibe su puntaje e información del broker.
     */
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Leer bienvenida
            System.out.println("Server: " + in.readLine()); // WELCOME TO MONSTERS
            System.out.println("Server: " + in.readLine()); // Enter your name:

            // Enviar el nombre
            out.println(playerName);
            System.out.println("Connected as: " + playerName);

            // Leer mensaje de bienvenida y puntaje
            String welcomeMsg = in.readLine();
            System.out.println("Server: " + welcomeMsg);

            // Leer la línea con "INFO BROKER_URL=... TOPIC=..."
            String infoLine = in.readLine();
            if (infoLine != null && infoLine.startsWith("INFO ")) {
                parseGameInfo(infoLine.substring("INFO ".length()));
                // parseGameInfo("BROKER_URL=tcp://localhost:61616 TOPIC=Monsters");
            }

            // Hilo para leer mensajes "WINNER" u otros
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("WINNER")) {
                            JOptionPane.showMessageDialog(frame, "Winner: " + message.split(" ")[1] + "!");
                            resetBoard();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Could not connect to server!");
            System.exit(1);
        }
    }

    /**
     * Parsea la línea de info enviada por el servidor, p.ej:
     * "BROKER_URL=tcp://localhost:61616 TOPIC=Monsters"
     */
    private void parseGameInfo(String data) {
        // data: "BROKER_URL=tcp://localhost:61616 TOPIC=Monsters"
        String[] parts = data.split(" ");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                switch (kv[0]) {
                    case "BROKER_URL":
                        brokerUrl = kv[1];
                        break;
                    case "TOPIC":
                        topicName = kv[1];
                        break;
                }
            }
        }
        System.out.println("🔹 Received from server: brokerUrl=" + brokerUrl + ", topicName=" + topicName);
    }

    /**
     * Crea la UI con una cuadrícula de botones que representan el área de juego.
     */
    private void createUI() {
        frame = new JFrame("Hit the Monsters - " + playerName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 500);
        frame.setLayout(new GridLayout(9, 9));

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                final int row = i;
                final int col = j;
                buttons[i][j] = new JButton();
                buttons[i][j].setEnabled(false);
                buttons[i][j].addActionListener(e -> {
                    out.println("hit " + row + " " + System.currentTimeMillis());
                    buttons[row][col].setEnabled(false);
                });
                frame.add(buttons[i][j]);
            }
        }
        frame.setVisible(true);
    }

    /**
     * Se suscribe al tópico en ActiveMQ (brokerUrl, topicName) para recibir eventos de aparición de monstruos.
     */
    private void subscribeToTopic() {
        new Thread(() -> {
            try {
                // brokerUrl y topicName se obtienen de parseGameInfo
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
                Connection connection = connectionFactory.createConnection();
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(topicName);
                MessageConsumer consumer = session.createConsumer(destination);

                consumer.setMessageListener(message -> {
                    if (message instanceof TextMessage) {
                        try {
                            String text = ((TextMessage) message).getText();
                            SwingUtilities.invokeLater(() -> processMessage(text));
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    }
                });

            } catch (JMSException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Procesa los mensajes recibidos en el tópico de ActiveMQ.
     */
    private void processMessage(String text) {
        if (text.equals("The game is over!")) {
            JOptionPane.showMessageDialog(frame, "The game is over!");
            resetBoard();
        } else if (text.startsWith("WINNER")) {
            JOptionPane.showMessageDialog(frame, "Winner: " + text.split(" ")[1] + "!");
            resetBoard();
        } else {
            String[] parts = text.split(" ");
            int id = Integer.parseInt(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            showMonster(x, y);
        }
    }

    /**
     * Muestra un monstruo en la posición (x, y).
     */
    private void showMonster(int x, int y) {
        SwingUtilities.invokeLater(() -> {
            buttons[x][y].setText("👾");
            buttons[x][y].setEnabled(true);

            new Timer(1000,e ->hideMonster(x, y)).start();
        });
    }

    /**
     * Oculta un monstruo en la posición (x, y).
     */
    private void hideMonster(int x, int y) {
        SwingUtilities.invokeLater(() -> {
            buttons[x][y].setText("");
            buttons[x][y].setEnabled(false);
        });
    }

    /**
     * Reinicia la cuadrícula cuando se termina una partida.
     */
    private void resetBoard() {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    buttons[i][j].setText("");
                    buttons[i][j].setEnabled(false);
                }
            }
        });
    }

    public static void main(String[] args) {
        new MonsterReceiver();
    }
}
