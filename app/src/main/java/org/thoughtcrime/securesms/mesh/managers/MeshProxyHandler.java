package org.thoughtcrime.securesms.mesh.managers;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mesh.models.SendMessageInteractor;
import org.thoughtcrime.securesms.socksserver.BasicProxyHandler;
import org.thoughtcrime.securesms.socksserver.SocksConstants;
import org.thoughtcrime.securesms.socksserver.Utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.SocketException;

class MeshProxyHandler extends BasicProxyHandler {

    // mesh socket server
    private MeshSocketServer meshSocketServer = null;
    private SendMessageInteractor sendMessageInteractor = null;
    static private int meshSocketServerPort = 1337;
    private static final int SOCKET_TIMEOUT = 600000;

    public MeshProxyHandler(ServerSocket listenSocket, SendMessageInteractor sendMessageInteractor) {
        super(listenSocket);
        this.sendMessageInteractor = sendMessageInteractor;
        try {
            getClientSocket().setSoTimeout(SOCKET_TIMEOUT);
            int timeout = getClientSocket().getSoTimeout();
        } catch (Exception e) {
            // could not change timeout
        }
    }

    @Override
    public void connectToServer(String server, int port) throws IOException {
        // TODO: fix race condition incrementing port used by MeshSocketServer..
        int meshPort = meshSocketServerPort++;
        meshSocketServer = new MeshSocketServer(meshPort, server, port, sendMessageInteractor);
        meshSocketServer.startServer();

        super.connectToServer("127.0.0.1", meshPort);
        int timeout = getServerSocket().getSoTimeout();
        getServerSocket().setSoTimeout(SOCKET_TIMEOUT);

    }

    @Override
    public void close() {
        if (meshSocketServer != null) {
            meshSocketServer.stopServer();
        }
    }
}

