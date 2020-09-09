package org.thoughtcrime.securesms.socksserver;

import java.net.ServerSocket;

public interface ProxyHandlerFactory {
	ProxyHandler getInstance(ServerSocket listenSocket);
}

