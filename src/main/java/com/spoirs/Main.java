package com.spoirs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

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

                (new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private static final String SERVER_DIRECTORY = "server_files";  // Папка для файлов

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                File dir = new File(SERVER_DIRECTORY);
                if (!dir.exists()) dir.mkdir(); // Создаем папку, если её нет

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

            long fileSize = in.readLong();

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                while (totalRead < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                if (totalRead != fileSize) {
                    throw new IOException("Прочитано меньше байтов, чем ожидалось: " + totalRead + " из " + fileSize);
                }
            }
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

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            out.writeUTF("Файл " + fileName + " успешно отправлен.");
        }

    }
}