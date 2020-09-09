package org.thoughtcrime.securesms.socksserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public interface ProxyHandler extends Runnable {
	void setLock(Object lock);
	void run();
	void close();
	void sendToClient(byte[] buffer);
	void sendToClient(byte[] buffer, int len);
	void sendToServer(byte[] buffer, int len);
	boolean isActive();
	void connectToServer(String server, int port) throws IOException;
	void prepareServer() throws IOException;
	boolean prepareClient();
	void processRelay();
	byte getByteFromClient() throws Exception;
	void relay();
	int checkClientData();
	int checkServerData();
	int getPort();
	InetAddress getClientInetAddress();
	int getClientPort();
	Socket getServerSocket();
	Socket getClientSocket();
	void setServerSocket(Socket serverSocket);
	byte[] getBuffer();
	void setBuffer(byte[] buffer);
}
