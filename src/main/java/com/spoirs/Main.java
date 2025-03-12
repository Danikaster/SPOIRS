package com.spoirs;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    private static final String SERVER_IP = "192.168.1.104";
    private static final int SERVER_PORT = 5000;
    private static final String CLIENT_DIR = "client_files";

    public static void main(String[] args) {
        File dir = new File(CLIENT_DIR);
        if (!dir.exists()) dir.mkdir();

        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Подключено к серверу. Введите команду (UPLOAD/DOWNLOAD/EXIT/CLIENT(SERVER)_FILES):");

            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine();
                out.writeUTF(command);

                if (command.equals("EXIT")) {
                    System.out.println("Отключение от сервера.");
                    break;
                } else if (command.equals("CLIENT_FILES")) {
                    listClientFiles();
                } else if (command.equals("SERVER_FILES")) {
                    System.out.println("Запрос списка файлов на сервере...");
                    System.out.println(in.readUTF());
                } else if (command.startsWith("UPLOAD ")) {
                    sendFile(out, in, command.substring(7));
                } else if (command.startsWith("DOWNLOAD ")) {
                    receiveFile(in, out, command.substring(9));
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка подключения: " + e.getMessage());
        }
    }

    private static void listClientFiles() {
        File folder = new File(CLIENT_DIR);
        File[] files = folder.listFiles();

        if (files != null && files.length > 0) {
            System.out.println("Содержимое папки клиента:");
            for (File file : files) {
                System.out.println(file.getName());
            }
        } else {
            System.out.println("Папка клиента пуста.");
        }
    }

    private static void sendFile(DataOutputStream out, DataInputStream in, String fileName) throws IOException {
        File file = new File(CLIENT_DIR, fileName);
        if (!file.exists()) {
            System.out.println("Файл не найден.");
            return;
        }

        out.writeLong(file.length());
        long fileSize = file.length();
        long startTime = System.nanoTime();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                int progress = (int) ((totalSent * 100) / fileSize);
                System.out.print("\rОтправка: " + progress + "%");
            }
            out.flush();
        }

        long endTime = System.nanoTime();
        double seconds = (endTime - startTime) / 1e9;
        double bitrate = (fileSize / 1024.0) / seconds;

        System.out.println("\nПередача завершена. Скорость: " + String.format("%.2f", bitrate) + " КБ/с");
        System.out.println(in.readUTF());
    }

    private static void receiveFile(DataInputStream in, DataOutputStream out, String fileName) throws IOException {
        String response = in.readUTF();
        if (!response.equals("OK")) {
            System.out.println(response);
            return;
        }

        long fileSize = in.readLong();
        File file = new File(CLIENT_DIR, fileName);
        long startTime = System.nanoTime();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalReceived = 0;

            while (totalReceived < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
                int progress = (int) ((totalReceived * 100) / fileSize);
                System.out.print("\rЗагрузка: " + progress + "%");
            }
        }

        long endTime = System.nanoTime();
        double seconds = (endTime - startTime) / 1e9;
        double bitrate = (fileSize / 1024.0) / seconds;

        System.out.println("\nЗагрузка завершена. Скорость: " + String.format("%.2f", bitrate) + " КБ/с");
        System.out.println(in.readUTF());
    }
}
