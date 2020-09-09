package org.thoughtcrime.securesms.socksserver;

import java.net.ServerSocket;

public class BasicProxyHandlerFactory implements ProxyHandlerFactory {
	public BasicProxyHandler getInstance(ServerSocket listenSocket) {
		return new BasicProxyHandler(listenSocket);
	}
}
