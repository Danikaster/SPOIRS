package com.spoirs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {
    private static final int PORT = 5000;
    private static final String SERVER_DIR = "server_files";

    public static void main(String[] args) {
        File dir = new File(SERVER_DIR);
        if (!dir.exists()) dir.mkdir();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен. Ожидание клиентов...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Подключился клиент: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private static final String SERVER_DIRECTORY = "server_files";

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                File dir = new File(SERVER_DIRECTORY);
                if (!dir.exists()) dir.mkdir();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String command = in.readUTF();
                    System.out.println("Получена команда: " + command);

                    if (command.equals("SERVER_FILES")) {
                        listServerFiles();
                    } else if (command.startsWith("UPLOAD ")) {
                        receiveFile(command.substring(7));
                    } else if (command.startsWith("DOWNLOAD ")) {
                        sendFile(command.substring(9));
                    } else if (command.equals("EXIT")) {
                        System.out.println("Клиент отключился.");
                        break;
                    } else if (command.startsWith("ECHO ")) {
                        out.writeUTF(command.substring(5)); // Отправляем обратно сообщение
                    } else if (command.equals("TIME")) {
                        String serverTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        out.writeUTF(serverTime);
                    }
                }
            } catch (IOException e) {
                System.out.println("Соединение с клиентом разорвано.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void listServerFiles() throws IOException {
            File folder = new File(SERVER_DIRECTORY);
            File[] files = folder.listFiles();

            if (files != null && files.length > 0) {
                StringBuilder fileList = new StringBuilder("Содержимое папки сервера:\n");
                for (File file : files) {
                    fileList.append(file.getName()).append("\n");
                }
                out.writeUTF(fileList.toString());
            } else {
                out.writeUTF("Папка сервера пуста.");
            }
        }

        private void receiveFile(String fileName) throws IOException {
            File file = new File(SERVER_DIRECTORY, fileName);

            long alreadyReceived = file.exists() ? file.length() : 0;
            out.writeUTF(String.valueOf(alreadyReceived));

            long fileSize = in.readLong();

            if (alreadyReceived >= fileSize) {
                System.out.println("Файл уже полностью загружен.");
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(alreadyReceived);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalReceived = alreadyReceived;

                while (totalReceived < fileSize) {
                    bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        System.out.println("\nОшибка: клиент закрыл соединение раньше времени!");
                        break;
                    }

                    raf.write(buffer, 0, bytesRead);
                    totalReceived += bytesRead;

                    int progress = (int) ((totalReceived * 100) / fileSize);
                    System.out.print("\rПолучение: " + progress + "%");

                }
            }

            System.out.println("\nФайл " + fileName + " успешно загружен.");
            out.writeUTF("Файл " + fileName + " успешно загружен.");
        }

        private void sendFile(String fileName) throws IOException {
            File file = new File(SERVER_DIRECTORY, fileName);
            if (!file.exists()) {
                out.writeUTF("Файл не найден.");
                return;
            }

            out.writeUTF("OK");
            out.writeLong(file.length());

            long alreadySent = Long.parseLong(in.readUTF());
            if (alreadySent >= file.length()) {
                System.out.println("Клиент уже скачал файл.");
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(alreadySent);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = raf.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            }

    }
}