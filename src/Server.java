import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server.java
 * Java socket chat server with smarter question-answer chatbot.
 * Listens on port 5000, accepts clients, expects first message from each client to be their username.
 */
public class Server {
    private static final int PORT = 5000;
    private static final Set<ClientHandler> clientHandlers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void main(String[] args) {
        System.out.println("Starting Chat Server on port " + PORT + " ...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for clients...");
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clientHandlers.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Broadcast to all clients except sender
    static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) {
                ch.sendMessage(message);
            }
        }
    }

    // Remove client handler
    static void removeHandler(ClientHandler handler) {
        clientHandlers.remove(handler);
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("ClientHandler IO exception: " + e.getMessage());
            }
        }

        void sendMessage(String msg) {
            out.println(msg);
        }

        // Smarter bot logic: partial match, ignore punctuation
        private String generateBotReply(String msg) {
            msg = msg.toLowerCase().trim();
            msg = msg.replaceAll("[^a-z0-9 ]", ""); // remove punctuation

            Map<String, String> qa = new LinkedHashMap<>();
            qa.put("what is java", "Bot: Java is a high-level, object-oriented programming language.");
            qa.put("who invented java", "Bot: Java was invented by James Gosling at Sun Microsystems.");
            qa.put("what is socket programming", "Bot: Socket programming allows communication between computers using TCP/IP.");
            qa.put("what is multithreading", "Bot: Multithreading allows multiple threads to run concurrently in a program.");
            qa.put("hello", "Bot: Hello! How are you?");
            qa.put("hi", "Bot: Hello! How are you?");
            qa.put("how are you", "Bot: I'm doing great! Thanks for asking.");
            qa.put("time", "Bot: Current server time is " + new Date());
            qa.put("bye", "Bot: Goodbye! Have a nice day!");

            // Partial match
            for (Map.Entry<String, String> entry : qa.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // Fallback
            String[] fallback = {
                "Bot: Sorry, I don't understand. Can you rephrase?",
                "Bot: Hmm, I am not sure about that.",
                "Bot: Interesting! Tell me more."
            };
            return fallback[new Random().nextInt(fallback.length)];
        }

        @Override
        public void run() {
            try {
                // First message is username
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }
                System.out.println("User connected: " + username + " from " + socket.getRemoteSocketAddress());

                broadcast("[Server] " + username + " has joined the chat.", this);
                sendMessage("[Server] Welcome, " + username + "!");

                String msg;
                while ((msg = in.readLine()) != null) {
                    msg = msg.trim();
                    if (msg.equalsIgnoreCase("/quit") || msg.equalsIgnoreCase("/exit")) break;
                    if (msg.isEmpty()) continue;

                    System.out.println("[" + username + "]: " + msg);
                    broadcast(username + ": " + msg, this);

                    // Send bot reply to sender
                    String botReply = generateBotReply(msg);
                    sendMessage(botReply);
                }
            } catch (IOException e) {
                System.err.println("Connection error with user " + username + ": " + e.getMessage());
            } finally {
                try {
                    if (username != null) {
                        System.out.println("User disconnected: " + username);
                        broadcast("[Server] " + username + " has left the chat.", this);
                    }
                    removeHandler(this);
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}