package com.mycompany.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class ChatServer {

    // Danh sách các luồng dữ liệu của tất cả Client
    private static final Set<ClientConnection> clients = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("[SERVER] Chat Server is running on port 6666...");
        try (ServerSocket listener = new ServerSocket(6666)) {
            while (true) {
                Socket socket = listener.accept();
                ClientConnection connection = new ClientConnection(socket);
                clients.add(connection);
                new Thread(connection).start();
            }
        }
    }

    // Lớp chứa các luồng đọc/ghi của một Client cụ thể
    private static class ClientConnection implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private DataInputStream dataIn;
        private DataOutputStream dataOut;

        public ClientConnection(Socket socket) {
            this.socket = socket;
            try {
                // Setup luồng Text (có UTF-8 để hỗ trợ Emoji)
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                
                // Setup luồng Byte (để chuyển File)
                this.dataIn = new DataInputStream(socket.getInputStream());
                this.dataOut = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String header;
                // Liên tục đọc tín hiệu từ Client
                while ((header = in.readLine()) != null) {
                    
                    if (header.startsWith("TEXT:")) {
                        // Nếu là tin nhắn Text thường
                        System.out.println("[LOG Text] " + header);
                        broadcastText(header);
                        
                    } else if (header.startsWith("FILE:")) {
                        // Nếu là File, chuẩn bị đọc luồng Byte
                        int fileLength = dataIn.readInt();
                        System.out.println("[LOG File] " + header + " | Kích thước: " + (fileLength/1024) + " KB");
                        
                        if (fileLength > 0) {
                            byte[] fileData = new byte[fileLength];
                            dataIn.readFully(fileData, 0, fileData.length);
                            broadcastFile(header, fileData);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[DISCONNECT] Một client đã thoát.");
            } finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException e) {}
            }
        }

        // Hàm phát tin nhắn Text cho toàn bộ Client
        private void broadcastText(String message) {
            synchronized (clients) {
                for (ClientConnection client : clients) {
                    client.out.println(message);
                }
            }
        }

        // Hàm phát dữ liệu File cho toàn bộ Client
        private void broadcastFile(String header, byte[] fileData) {
            synchronized (clients) {
                for (ClientConnection client : clients) {
                    try {
                        client.out.println(header);
                        client.dataOut.writeInt(fileData.length);
                        client.dataOut.write(fileData);
                        client.dataOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}