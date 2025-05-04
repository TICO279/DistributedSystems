import java.io.*;
import java.net.Socket;
import java.util.Random;

public class StressReceiver {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int NUM_CLIENTS = 500; // 🔹 Número de clientes a simular

    public static void main(String[] args) {
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientId = i;
            new Thread(() -> simulateClient(clientId)).start();
        }
    }

    private static void simulateClient(int clientId) {
        try {
            long startTime = System.currentTimeMillis();
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String playerName = "Player_" + clientId;
            out.println(playerName);

            long endTime = System.currentTimeMillis();
            System.out.println(playerName + " registered in " + (endTime - startTime) + "ms");

            Random random = new Random();
            boolean gameOver = false;

            while (!gameOver) {
                int x = random.nextInt(9);
                long reactionTime = System.currentTimeMillis();
                out.println("hit " + x + " " + reactionTime);

                // 🔹 Verifica si el servidor envió un mensaje de "WINNER"
                if (in.ready()) {
                    String response = in.readLine();
                    if (response.startsWith("WINNER")) {
                        System.out.println("Game over! " + response);
                        gameOver = true;
                    }
                }

                Thread.sleep(random.nextInt(500)); // 🔹 Simula tiempo de reacción
            }

            socket.close();
            System.out.println("🔹 " + playerName + " finished and is exiting.");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
