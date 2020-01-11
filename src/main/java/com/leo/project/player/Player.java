package com.leo.project.player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class Player {
	
	private String name;
	private InetSocketAddress SERVER;
	// buffer to read messages sent from server
	private ByteBuffer rBuffer = ByteBuffer.allocate(1024);
	// buffer to send messages to server
	private ByteBuffer sBuffer = ByteBuffer.allocate(1024);
	private static Selector selector;
	// to decode and encode messages
	private Charset charset = Charset.forName("UTF-8");
	private SocketChannel socketChannel;
	// messages received in total
	private int counter = 0;
	// cache message received
	private String receiveText = "";

	public Player(String name, String host, int port) {
		this.name = name;
		SERVER = new InetSocketAddress(host, port);
	}
	
	/**
	 * connect to server
	 */
	public void connect() {
		try {
			socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			selector = Selector.open();
			socketChannel.register(selector, SelectionKey.OP_CONNECT);
			socketChannel.connect(SERVER);
			selector.select(selectionKey -> {
				if (selectionKey.isConnectable()) {
					if (socketChannel.isConnectionPending()) {
						try {
							socketChannel.finishConnect();
							System.out.println(this.getName() + " connected");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * send message to server
	 * @param message
	 */
	public void sendMessage(String message) {
		try {
			// reset interest op
			socketChannel.register(selector, SelectionKey.OP_WRITE);
			int readyChannels = selector.selectNow();
			// action not available, return immediately
			if (readyChannels == 0) return;
			
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				if (key.isWritable()) {
					try {
						sBuffer.clear();
						sBuffer.put(charset.encode(message));
						sBuffer.flip();
						socketChannel.write(sBuffer);
						// break out of the loop to prevent message being sent again
						break;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				// remove current selectionKey
				keyIterator.remove();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * read messages sent from server
	 */
	public void receiveMessage() {
		try {
			// reset interest op
			socketChannel.register(selector, SelectionKey.OP_READ);
			selector.select(selectionKey -> {
				if (selectionKey.isReadable()) {
					rBuffer.clear();
					int count = 0;
					try {
						count = socketChannel.read(rBuffer);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (count > 0) {
						receiveText = new String(rBuffer.array(), 0, count);
						System.out.println(receiveText);
						counter++;
					}
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getCounter() {
		return counter;
	}

	public String getReceiveText() {
		return receiveText;
	}
	
	public String getName() {
		return name;
	}
}
