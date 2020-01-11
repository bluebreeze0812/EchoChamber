package com.leo.project.server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;

public class Server implements Callable<Boolean> {
	
	int port;
	// charset used to decode and encode messages
	private Charset charset = Charset.forName("UTF-8");
	// buffer to read message
	private ByteBuffer rBuffer = ByteBuffer.allocate(1024);
	// buffer to send message
	private ByteBuffer sBuffer = ByteBuffer.allocate(1024);
	// stores all the incoming sockets
	private Set<SocketChannel> socketSet = new HashSet<>();
	private static Selector selector;

	public Server(int port) {
		this.port = port;
	}

	@Override
	public Boolean call() {
		try {
			// initiating the server
			ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			ServerSocket serverSocket = serverSocketChannel.socket();
			serverSocket.bind(new InetSocketAddress(port));
			
			// registering server to selector
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			System.out.println("Server started, port：" + port);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * blocking, handle each action with corresponding functions
	 */
	public void listen() {
		while (true) {
			try {
				selector.select();
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				selectionKeys.forEach(selectionKey -> handle(selectionKey));
				selectionKeys.clear();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private void handle(SelectionKey selectionKey) {
		try {
			// add incoming sockets to the map
			if (selectionKey.isAcceptable()) {
				ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
				SocketChannel socket = server.accept();
				socket.configureBlocking(false);
				socket.register(selector, SelectionKey.OP_READ);
				socketSet.add(socket);
			} 
			// read messages sent from the sockets
			else if (selectionKey.isReadable()) {
				SocketChannel socket = (SocketChannel) selectionKey.channel();
				rBuffer.clear();
				int bytes = socket.read(rBuffer);
				if (bytes > 0) {
					rBuffer.flip();
					String receiveText = String.valueOf(charset.decode(rBuffer));
					
					// send message received to all other sockets
					dispatch(socket, receiveText);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * dispatch received message to all sockets except sender
	 * @param player, sender
	 * @param info, message received
	 * @throws IOException
	 */
	private void dispatch(SocketChannel player, String info) throws IOException {
		if (!socketSet.isEmpty()) {
			Iterator<SocketChannel> iterator = socketSet.iterator();
			while (iterator.hasNext()) {
				SocketChannel temp = iterator.next();
				if (!player.equals(temp)) {
					sBuffer.clear();
					sBuffer.put(charset.encode(info));
					sBuffer.flip();
					temp.write(sBuffer);
				}
			}
		}
	}
}
