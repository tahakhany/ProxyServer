package com.taha;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;

public class RequestResolver implements Runnable {

    final int REQUEST_TIMEOUT = 2000;

    Socket mClientSocket;


    BufferedReader mProxyToClientReader;


    BufferedWriter mProxyToClientWriter;


    private Thread mHttpsClientToServer;


    public RequestResolver(Socket mClientSocket) {
        this.mClientSocket = mClientSocket;
        try {
            this.mClientSocket.setSoTimeout(REQUEST_TIMEOUT);
            mProxyToClientReader = new BufferedReader(new InputStreamReader(mClientSocket.getInputStream()));
            mProxyToClientWriter = new BufferedWriter(new OutputStreamWriter(mClientSocket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {

        String requestString;
        try {
            requestString = mProxyToClientReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Can't read request from client...");
            return;
        }


        System.out.println("Got request: " + requestString);
        Main.mServeLog.put(Main.getTime() + " :Got request: " + requestString, "Got request: " + requestString);

        String request = requestString.substring(0, requestString.indexOf(' '));


        String urlString = requestString.substring(requestString.indexOf(' ') + 1);


        urlString = urlString.substring(0, urlString.indexOf(' '));


        if (!urlString.substring(0, 4).equals("http")) {
            String temp = "http://";
            urlString = temp + urlString;
        }


        if (Main.isBlocked(urlString)) {
            System.out.println("Request for filtered site: " + urlString);
            Main.mServeLog.put(Main.getTime() + ": Request for filtered site: " + urlString, "Request for filtered site: " + urlString);
            filteredSiteRequested();
            return;
        }


        if (request.equals("CONNECT")) {
            System.out.println("HTTPS Request for: " + urlString + "\n");
            Main.mServeLog.put(Main.getTime() + " :HTTPS Request for: " + urlString + "\n", "HTTPS Request for: " + urlString + "\n");
            resolveHTTPSRequest(urlString);
        } else if (request.equals("POST")) {
            System.out.println("HTTP POST for: " + urlString + "\n");
            Main.mServeLog.put(Main.getTime() + " :HTTPS POST for: " + urlString + "\n", "HTTPS POST for: " + urlString + "\n");
            sendPostToClient(urlString);
        } else {
            System.out.println("HTTP GET for: " + urlString + "\n");
            Main.mServeLog.put(Main.getTime() + " :HTTPS GET for: " + urlString + "\n", "HTTPS GET for: " + urlString + "\n");
            sendGetToClient(urlString);
        }
    }


    private void sendGetToClient(String urlString) {

        try {

            int fileExtensionIndex = urlString.lastIndexOf(".");
            String fileExtension;


            fileExtension = urlString.substring(fileExtensionIndex);


            String fileName = urlString.substring(0, fileExtensionIndex);


            fileName = fileName.substring(fileName.indexOf('.') + 1);


            fileName = fileName.replace("/", "__");
            fileName = fileName.replace('.', '_');


            if (fileExtension.contains("/")) {
                fileExtension = fileExtension.replace("/", "__");
                fileExtension = fileExtension.replace('.', '_');
                fileExtension += ".html";
            }

            fileName = fileName + fileExtension;


            if ((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
                    fileExtension.contains(".jpeg") || fileExtension.contains(".gif")) {

                URL remoteURL = new URL(urlString);
                BufferedImage image = ImageIO.read(remoteURL);

                if (image != null) {


                    String response = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    mProxyToClientWriter.write(response);
                    mProxyToClientWriter.flush();


                    ImageIO.write(image, fileExtension.substring(1), mClientSocket.getOutputStream());


                } else {
                    System.out.println("Image wasn't received from server successfully... Sending 404 to client."
                            + fileName);
                    String errorResponse = "HTTP/1.0 404 NOT FOUND\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    mProxyToClientWriter.write(errorResponse);
                    mProxyToClientWriter.flush();
                    return;
                }
            } else {


                URL remoteURL = new URL(urlString);

                HttpURLConnection proxyToServerCon = (HttpURLConnection) remoteURL.openConnection();
                proxyToServerCon.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                proxyToServerCon.setRequestProperty("Content-Language", "en-US");
                proxyToServerCon.setUseCaches(false);
                proxyToServerCon.setDoOutput(true);


                BufferedReader proxyToServerReader = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));


                String response = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                mProxyToClientWriter.write(response);

                String line;
                while ((line = proxyToServerReader.readLine()) != null) {

                    mProxyToClientWriter.write(line);

                }


                mProxyToClientWriter.flush();


                if (proxyToServerReader != null) {
                    proxyToServerReader.close();
                }
            }


            if (mProxyToClientWriter != null) {
                mProxyToClientWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void resolveHTTPSRequest(String urlString) {

        String url = urlString.substring(7);
        String[] pieces = url.split(":");
        url = pieces[0];
        int port = Integer.valueOf(pieces[1]);

        try {


            for (int i = 0; i < 5; i++) {
                mProxyToClientReader.readLine();
            }


            InetAddress address = InetAddress.getByName(url);


            Socket proxyToServerSocket = new Socket(address, port);
            proxyToServerSocket.setSoTimeout(5000);


            String line = "HTTP/1.0 200 Connection established\r\n" +
                    "Proxy-agent: ProxyServer/1.0\r\n" +
                    "\r\n";
            mProxyToClientWriter.write(line);
            mProxyToClientWriter.flush();


            BufferedWriter proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));


            BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));


            ClientToServerHttpsConnection clientToServerHttps =
                    new ClientToServerHttpsConnection(mClientSocket.getInputStream(), proxyToServerSocket.getOutputStream());

            mHttpsClientToServer = new Thread(clientToServerHttps);
            mHttpsClientToServer.start();


            try {
                byte[] buffer = new byte[4096];
                int read;
                do {
                    read = proxyToServerSocket.getInputStream().read(buffer);
                    if (read > 0) {
                        mClientSocket.getOutputStream().write(buffer, 0, read);
                        if (proxyToServerSocket.getInputStream().available() < 1) {
                            mClientSocket.getOutputStream().flush();
                        }
                    }
                } while (read >= 0);
            } catch (SocketTimeoutException e) {

            } catch (IOException e) {
                e.printStackTrace();
            }


            if (proxyToServerSocket != null) {
                proxyToServerSocket.close();
            }

            if (proxyToServerBR != null) {
                proxyToServerBR.close();
            }

            if (proxyToServerBW != null) {
                proxyToServerBW.close();
            }

            if (mProxyToClientWriter != null) {
                mProxyToClientWriter.close();
            }


        } catch (SocketTimeoutException e) {
            String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            try {
                mProxyToClientWriter.write(line);
                mProxyToClientWriter.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("Error on HTTPS : " + urlString);
            e.printStackTrace();
        }
    }

    private void sendPostToClient(String urlString) {
        try {

            String postData = "";
            for (int i = 0; i < 5; i++) {
                postData.concat(mProxyToClientReader.readLine());
            }

            URL remoteURL = new URL(urlString);
            byte[] bytePostData = postData.getBytes(StandardCharsets.UTF_8);


            HttpURLConnection proxyToServerCon = (HttpURLConnection) remoteURL.openConnection();
            proxyToServerCon.setRequestMethod("POST");
            proxyToServerCon.setUseCaches(false);
            proxyToServerCon.setDoOutput(true);
            proxyToServerCon.setRequestProperty("Content-Length", Integer.toString(bytePostData.length));


            try (DataOutputStream proxyToServerWriter = new DataOutputStream(proxyToServerCon.getOutputStream())) {
                proxyToServerWriter.write(bytePostData);
            }


            BufferedReader proxyToServerReader = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));


            String response = "HTTP/1.0 200 OK\n" +
                    "Proxy-agent: ProxyServer/1.0\n" +
                    "\r\n";
            mProxyToClientWriter.write(response);


            String line;
            while ((line = proxyToServerReader.readLine()) != null) {

                mProxyToClientWriter.write(line);
            }


            mProxyToClientWriter.flush();


            if (proxyToServerReader != null) {
                proxyToServerReader.close();
            }
        } catch (Exception e) {

        }
    }


    class ClientToServerHttpsConnection implements Runnable {

        InputStream proxyToClientInputStream;
        OutputStream proxyToServerOutputStream;


        public ClientToServerHttpsConnection(InputStream proxyToClientInputStream, OutputStream proxyToServerOutputStream) {
            this.proxyToClientInputStream = proxyToClientInputStream;
            this.proxyToServerOutputStream = proxyToServerOutputStream;
        }

        @Override
        public void run() {
            try {

                byte[] buffer = new byte[4096];
                int read;
                do {
                    read = proxyToClientInputStream.read(buffer);
                    if (read > 0) {
                        proxyToServerOutputStream.write(buffer, 0, read);
                        if (proxyToClientInputStream.available() < 1) {
                            proxyToServerOutputStream.flush();
                        }
                    }
                } while (read >= 0);
            } catch (SocketTimeoutException ste) {

            } catch (IOException e) {
                System.out.println("HTTPS time out");
                e.printStackTrace();
            }
        }
    }


    private void filteredSiteRequested() {
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(mClientSocket.getOutputStream()));
            String response = "HTTP/1.0 403 Access Forbidden \n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            bufferedWriter.write(response);
            bufferedWriter.flush();
        } catch (IOException e) {
            System.out.println("something went wrong while writing to client when requested a blocked site");
            e.printStackTrace();
        }
    }
}




