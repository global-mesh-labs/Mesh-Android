package org.thoughtcrime.securesms.mesh.managers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thoughtcrime.securesms.mesh.models.SendMessageInteractor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mesh.models.Message;
import org.thoughtcrime.securesms.mesh.models.SocketMessage;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.socksserver.ProxyHandler;
import org.thoughtcrime.securesms.socksserver.SocksConstants;

// test with `$ nc <device ip address> 1337
class SocketRequestHandler extends Thread  implements GTMeshManager.IncomingMessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketRequestHandler.class);

    private static final String TAG = SocketRequestHandler.class.getSimpleName();
    private static final int MESH_PAYLOAD_SIZE = 150;
    private Socket socket;
    private String remoteHost;
    private Integer remotePort;
    private SendMessageInteractor sendMessageInteractor;
    private Boolean isQuitting;
    private DataInputStream reader;
    private DataOutputStream writer;
    byte[] writeBuffer = new byte[0];
    byte[] socketId = null;


    SocketRequestHandler(Socket socket, String remoteHost, Integer remotePort, SendMessageInteractor sendMessageInteractor) {
        this.socket = socket;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.sendMessageInteractor = sendMessageInteractor;
        this.isQuitting = false;
        try {
            socket.setSoTimeout(600000);
            int timeout = socket.getSoTimeout();
            Log.i(TAG, "SocketRequestHandler: socket.getSoTimeout="+ String.valueOf(timeout));
        } catch (SocketException e) {
            LOGGER.error("Socket Exception during setting Timeout.");
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Received a connection");
            Date localDateTime = new Date();
            byte[] read_buffer = new byte[SocksConstants.DEFAULT_BUF_SIZE];
            reader = new DataInputStream(socket.getInputStream());
            writer = new DataOutputStream(socket.getOutputStream());

            // register to process incoming mesh messages
            GTMeshManager.getInstance().addIncomingMessageListener(this);

            while(!Thread.interrupted() && !isQuitting) {
                int availableBytes = reader.available();
                if (availableBytes > 0) {
                    Log.i(TAG, "run(), reader.available() = " + String.valueOf(availableBytes));
                    int bytesRead = reader.read(read_buffer);
                    byte count = (byte) Math.ceil((float)bytesRead / MESH_PAYLOAD_SIZE);
                    byte[] payload_buffer = Arrays.copyOfRange(read_buffer, 0, Math.min(MESH_PAYLOAD_SIZE, bytesRead));
                    SocketMessage message;

                    if (this.socketId == null) {
                        // first message includes host:port information
                        message = new SocketMessage(46725174784L, 555555555L, localDateTime, payload_buffer,
                                this.remoteHost, this.remotePort, (byte) count, Message.MessageStatus.SENDING, "");
                        this.socketId = message.getSocketId().clone();
                    }
                    else {
                        // subsequent messages only include socketId
                        message = new SocketMessage(46725174784L, 555555555L, localDateTime, payload_buffer,
                                socketId, (byte) 0, count, Message.MessageStatus.SENDING, "");
                    }
                    this.sendMessageInteractor.sendMessage(message, false,
                            new SendMessageInteractor.SendMessageListener() {
                                @Override
                                public void onMessageResponseReceived() {
                                }
                            });

                    for (byte index = 1; index < count; index++) {
                        int offset = index * MESH_PAYLOAD_SIZE;
                        payload_buffer = Arrays.copyOfRange(read_buffer, offset, Math.min(offset + MESH_PAYLOAD_SIZE, bytesRead));

                        // subsequent messages only include socketId
                        message = new SocketMessage(46725174784L, 555555555L, localDateTime, payload_buffer,
                                socketId, index, count, Message.MessageStatus.SENDING, "");
                        this.sendMessageInteractor.sendMessage(message, false,
                                new SendMessageInteractor.SendMessageListener() {
                                    @Override
                                    public void onMessageResponseReceived() {
                                    }
                                });
                    }
                }
                sleep(5000);
            }

            // unregister to receive incoming mesh messages
            GTMeshManager.getInstance().removeIncomingMessageListener(this);

            // Close our connection
            reader.close();
            this.socket.close();

            System.out.println("Connection closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIncomingMessage(Message incomingMessage)  {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    if (incomingMessage instanceof SocketMessage) {
                        byte[] buffer = ((SocketMessage)incomingMessage).getBuffer();
                        byte index = ((SocketMessage)incomingMessage).getIndex();
                        byte count = ((SocketMessage)incomingMessage).getCount();
                        if (buffer.length == 0) {
                            // close the request handler
                            isQuitting = true;
                        } else if (index < count) {
                            // append to write buffer
                            byte[] result = new byte[writeBuffer.length + buffer.length];
                            System.arraycopy(writeBuffer, 0, result, 0, writeBuffer.length);
                            System.arraycopy(buffer, 0, result, writeBuffer.length, buffer.length);
                            writeBuffer = result;
                        }
                        if (index == count - 1) {
                            // write buffer to socket
                            Log.i(TAG, "onIncomingMessage: [" + new String(writeBuffer, StandardCharsets.UTF_8) + "]");
                            // TODO: use socketId to select which port to write to
                            writer.write(writeBuffer);
                            writer.flush();
                            writeBuffer = new byte[0];
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(r).start();
    }
}

public class MeshSocketServer extends Thread {
    private static final int SOCKET_TIMEOUT = 600000;
    private ServerSocket serverSocket;
    private Integer port;
    private String remoteHost;
    private Integer remotePort;
    private SendMessageInteractor sendMessageInteractor;
    private boolean running = false;

    public MeshSocketServer(Integer port, String remoteHost, Integer remotePort, SendMessageInteractor sendMessageInteractor) {
        this.port = port;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.sendMessageInteractor = sendMessageInteractor;
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(SOCKET_TIMEOUT);
            this.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        running = false;
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                System.out.println("Listening for a connection");

                // Call accept() to receive the next connection
                Socket socket = serverSocket.accept();

                // Pass the socket to the RequestHandler thread for processing
                SocketRequestHandler socketRequestHandler = new SocketRequestHandler(socket, remoteHost, remotePort, this.sendMessageInteractor);
                socketRequestHandler.start();
                while (running) {
                        sleep(100);
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
