package org.thoughtcrime.securesms.mesh.managers;

import org.thoughtcrime.securesms.mesh.models.SendMessageInteractor;
import org.thoughtcrime.securesms.socksserver.ProxyHandlerFactory;

import java.net.ServerSocket;

public class MeshProxyHandlerFactory implements ProxyHandlerFactory {
    private SendMessageInteractor sendMessageInteractor = null;

    MeshProxyHandlerFactory(SendMessageInteractor sendMessageInteractor) {
        this.sendMessageInteractor = sendMessageInteractor;
    }

    public MeshProxyHandler getInstance(ServerSocket listenSocket) {
        return new MeshProxyHandler(listenSocket, sendMessageInteractor);
    }

}
