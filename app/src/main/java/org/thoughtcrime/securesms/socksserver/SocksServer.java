package org.thoughtcrime.securesms.socksserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

	protected int port;
	ProxyHandlerFactory proxyHandlerFactory;
	protected boolean stopping = false;

	public int getPort() {
		return port;
	}

	public synchronized void start(int listenPort, ProxyHandlerFactory proxyHandlerFactory) throws NoSuchMethodException {
		this.stopping = false;
		this.port = listenPort;
		this.proxyHandlerFactory = proxyHandlerFactory;
		new Thread(new ServerProcess()).start();
	}

	public synchronized void stop() {
		stopping = true;
	}

	private class ServerProcess implements Runnable {

		@Override
		public void run() {
			LOGGER.debug("SOCKS server started...");
			try {
				handleClients(port);
				LOGGER.debug("SOCKS server stopped...");
			} catch (IOException e) {
				LOGGER.debug("SOCKS server crashed...");
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				// ignore
			}
		}

		protected void handleClients(int port) throws IOException, Exception {
			final ServerSocket listenSocket = new ServerSocket(port);
			listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);
			SocksServer.this.port = listenSocket.getLocalPort();
			LOGGER.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());

			while (true) {
				synchronized (SocksServer.this) {
					if (stopping) {
						break;
					}
				}
				handleNextClient(listenSocket);
			}

			try {
				listenSocket.close();
			} catch (IOException e) {
				// ignore
			}
		}

		private void handleNextClient(ServerSocket listenSocket) {
			try {
				ProxyHandler proxyHandler = proxyHandlerFactory.getInstance(listenSocket);
				new Thread(proxyHandler).start();
			} catch(Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}