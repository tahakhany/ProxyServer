package com.taha;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;


public class Main implements Runnable {

    final int THREAD_TIMEOUT = 10000;

    static final int PORT_NUMBER = 4000;

    private ServerSocket mServerSocket;

    static HashMap<String, String> mBlockedSites;
    static HashMap<String, String> mServeLog;

    private volatile boolean isRunning = true;

    static ArrayList<Thread> mServicingThreads;

    public static void main(String[] args) {
        Main mMain = new Main(PORT_NUMBER);
        mMain.listen();
    }


    public Main(int port) {

        mBlockedSites = new HashMap<>();
        mServeLog = new HashMap<>();


        mServicingThreads = new ArrayList<>();


        new Thread(this).start();

        try {
            File mBlockedSites = new File("mBlockedSites.txt");
            if (!mBlockedSites.exists()) {
                System.out.println("Blocked Site's list does not exist...\n Creating new list...");
                mBlockedSites.createNewFile();
            } else {
                FileInputStream mFileInput = new FileInputStream(mBlockedSites);
                ObjectInputStream mObjectInput = new ObjectInputStream(mFileInput);
                Main.mBlockedSites = (HashMap<String, String>) mObjectInput.readObject();
                mFileInput.close();
                mObjectInput.close();
            }
        } catch (Exception e) {
            System.out.println("Something went wrong when loading last blocked site's list: ");
            e.printStackTrace();
        }

        try {
            File mServerLog = new File("mServerLog.txt");
            if (!mServerLog.exists()) {
                System.out.println("Server Log does not exist...\n Creating new Log...");
                mServerLog.createNewFile();
            } else {
                FileInputStream mLogFileInput = new FileInputStream(mServerLog);
                ObjectInputStream mLogObjectInput = new ObjectInputStream(mLogFileInput);
                Main.mServeLog = (HashMap<String, String>) mLogObjectInput.readObject();
                mLogFileInput.close();
                mLogObjectInput.close();
            }
        } catch (Exception e) {
            System.out.println("Something went wrong when loading last blocked site's list: ");
            e.printStackTrace();
        }

        try {
            mServerSocket = new ServerSocket(port);
            System.out.println("Listening on port: " + mServerSocket.getLocalPort() + "...");
            isRunning = true;
        } catch (SocketException se) {
            System.out.println("Something went wrong with the socket...");
            se.printStackTrace();
        } catch (SocketTimeoutException ste) {
            System.out.println("No response in the estimated time...");
        } catch (IOException io) {
            System.out.println("IO exception...");
        }
    }

    public void listen() {

        while (isRunning) {
            try {

                Socket socket = mServerSocket.accept();

                Thread thread = new Thread(new RequestResolver(socket));

                mServicingThreads.add(thread);

                thread.start();

            } catch (SocketException e) {

                System.out.println("Server closed.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void terminateServer() {
        System.out.println("\nInitiating server terminating sequence...");
        isRunning = false;
        try {
            FileOutputStream mFileOutput = new FileOutputStream("mBlockedSites.txt");
            ObjectOutputStream mObjectOutput = new ObjectOutputStream(mFileOutput);
            mObjectOutput.writeObject(mBlockedSites);
            mObjectOutput.close();
            mFileOutput.close();
            System.out.println("Blocked sites saved.");

            FileOutputStream mLogFileOutput = new FileOutputStream("mServerLog.txt");
            ObjectOutputStream mLogObjectOutput = new ObjectOutputStream(mLogFileOutput);
            mLogObjectOutput.writeObject(mServeLog);
            mLogObjectOutput.close();
            mLogFileOutput.close();
            System.out.println("Server Log Saved.");


            try {
                for (Thread thread : mServicingThreads) {
                    if (thread.isAlive()) {
                        System.out.print("Waiting for Thread: " + thread.getId() + " to terminate...");
                        thread.join(THREAD_TIMEOUT);
                        System.out.println("Thread: " + thread.getId() + " terminated.");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.out.println("Encountered Error while saving blocked sites.");
            e.printStackTrace();
        }


        try {
            System.out.println("Terminating Connection");
            mServerSocket.close();
        } catch (Exception e) {
            System.out.println("Something went wrong while closing proxy's server socket");
            e.printStackTrace();
        }

    }


    public static boolean isBlocked(String url) {
        for (Map.Entry<String, String> entry : mBlockedSites.entrySet()) {
            if (url.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void run() {
        Scanner mScanner = new Scanner(System.in);

        String mCommand;
        while (isRunning) {
            System.out.println("Enter new site name to be filtered\n" +
                    "Enter \"filtered\" to see the filtered sites list.\n" +
                    "Enter \"log\" to see the server log.\n" +
                    "Enter \"end\" to end the proxy.");
            mCommand = mScanner.nextLine();
            if (mCommand.toLowerCase().equals("filtered")) {
                System.out.println("\nFiltered Sites: ");
                for (String key : mBlockedSites.keySet()) {
                    System.out.println(key);
                }
                System.out.println();
            } else if (mCommand.equals("end")) {
                isRunning = false;
                terminateServer();
            } else if (mCommand.equals("log")) {
                for (String key : mServeLog.keySet()) {
                    System.out.println(key);
                }
                System.out.println();
            } else {
                mBlockedSites.put(mCommand, mCommand);
                System.out.println("\n" + mCommand + " was added to filtered list \n");
            }
        }
        mScanner.close();
    }

    public static String getTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        return (dtf.format(now));
    }
}
