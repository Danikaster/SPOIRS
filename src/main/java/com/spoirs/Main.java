package com.spoirs;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    private static final String SERVER_IP = "192.168.1.106";
    private static final int SERVER_PORT = 5000;
    private static final String CLIENT_DIR = "client_files";

    public static void main(String[] args) {
        File dir = new File(CLIENT_DIR);
        if (!dir.exists()) dir.mkdir();

        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Подключено к серверу. Введите команду (UPLOAD/DOWNLOAD/EXIT/CLIENT_FILES/SERVER_FILES/ECHO/TIME):");

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
                } else if (command.startsWith("ECHO ")) {
                    System.out.println(in.readUTF());
                } else if (command.equals("TIME")) {
                    System.out.println(in.readUTF());
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

        long fileSize = file.length();
        out.writeLong(fileSize);
        System.out.println(fileSize);
        long alreadySent = Long.parseLong(in.readUTF());
        if (alreadySent >= fileSize) {
            System.out.println("Файл уже полностью загружен.");
            return;
        }

        long startTime = System.nanoTime();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(alreadySent);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalSent = alreadySent;

            while (totalSent < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalSent);
                bytesRead = raf.read(buffer, 0, bytesToRead);

                if (bytesRead == -1) {
                    throw new IOException("Ошибка чтения файла.");
                }

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
        long alreadyDownloaded = file.exists() ? file.length() : 0;

        // Сообщаем серверу, сколько уже скачали
        out.writeUTF(String.valueOf(alreadyDownloaded));

        if (alreadyDownloaded >= fileSize) {
            System.out.println("Файл уже полностью скачан.");
            return;
        }

        long startTime = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(alreadyDownloaded); // Перемещаемся к позиции возобновления
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalReceived = alreadyDownloaded;

            while (totalReceived < fileSize) {
                // Определяем, сколько байт можно прочитать за один раз
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalReceived);
                bytesRead = in.read(buffer, 0, bytesToRead);

                if (bytesRead == -1) {
                    throw new IOException("Соединение закрыто сервером во время передачи.");
                }

                // Записываем данные в файл
                raf.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;

                // Выводим прогресс
                int progress = (int) ((totalReceived * 100) / fileSize);
                System.out.print("\rЗагрузка: " + progress + "%");
            }
        }

        long endTime = System.nanoTime();
        double seconds = (endTime - startTime) / 1e9;
        double bitrate = (fileSize / 1024.0) / seconds;

        System.out.println("\nЗагрузка завершена. Скорость: " + String.format("%.2f", bitrate) + " КБ/с");


    }



}
