package com.leo.project.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.leo.project.player.Player;
import com.leo.project.server.Server;

public class Game {

	public static void main(String[] args) throws InterruptedException {
		final String host = "localhost";
		final int PORT = 9999;

		Server server = new Server(PORT);
		Player initiator = new Player("initiator", host, PORT);
		Player receiver = new Player("receiver", host, PORT);

		List<String> msgList = new ArrayList<>();
		msgList.add("Hi");
		msgList.add("How are you");
		msgList.add("Are you copying me");
		msgList.add("Why are you copying me");
		msgList.add("Stop it");
		msgList.add("Stop");
		msgList.add("It's not funny");
		msgList.add("Weirdo");
		msgList.add("Imma outta here");
		msgList.add("Bye");

		Manager manager = new Manager(server, initiator, receiver);
		if (manager.init()) {
			
			// schedule initiator to send messages to server every 4 secs
			ScheduledExecutorService initiatorSendMsg = Executors.newSingleThreadScheduledExecutor();
			initiatorSendMsg.scheduleAtFixedRate(() -> {
				if (!msgList.isEmpty()) {
					String msg = msgList.get(0);
					initiator.sendMessage(msg);
					msgList.remove(0);
				} else {
					initiatorSendMsg.shutdown();
				}
			}, 0, 4000, TimeUnit.MILLISECONDS);
			
			// schedule receiver to read messages sent from server every 4 secs with 1 sec delay
			ScheduledExecutorService receiverReceiveMsg = Executors.newSingleThreadScheduledExecutor();
			receiverReceiveMsg.scheduleAtFixedRate(() -> {
				int count = receiver.getCounter();
				if (count == 10) receiverReceiveMsg.shutdown();
				else receiver.receiveMessage();
			}, 1000, 4000, TimeUnit.MILLISECONDS);
			
			// schedule receiver to send messages to server every 4 secs with 2 secs delay
			ScheduledExecutorService receiverSendMsg = Executors.newSingleThreadScheduledExecutor();
			receiverSendMsg.scheduleAtFixedRate(() -> {
				String received = receiver.getReceiveText();
				int count = receiver.getCounter();
				if (count == 10) receiverSendMsg.shutdown();
				// sent out the last message
				String msg = received + count;
				receiver.sendMessage(msg);
			}, 2000, 4000, TimeUnit.MILLISECONDS);
			
			// schedule initiator to read messages sent from server every 4 secs with 3 secs delay
			ScheduledExecutorService initiatorReceiveMsg = Executors.newSingleThreadScheduledExecutor();
			initiatorReceiveMsg.scheduleAtFixedRate(() -> {
				int count = initiator.getCounter();
				if (count == 10) initiatorReceiveMsg.shutdown();
				else initiator.receiveMessage();
			}, 3000, 4000, TimeUnit.MILLISECONDS);
			
			while (true) {
				if (initiatorSendMsg.isShutdown() && initiatorReceiveMsg.isShutdown() &&
					receiverSendMsg.isShutdown() && receiverReceiveMsg.isShutdown()) {
					System.exit(0);
					break;
				}
			}
		}
	}
}

class Manager {

	private Server server;
	private Player initiator;
	private Player receiver;

	private Semaphore sem1 = new Semaphore(1);
	private Semaphore sem2 = new Semaphore(1);

	public Manager(Server server, Player initiator, Player receiver) {
		super();
		this.server = server;
		this.initiator = initiator;
		this.receiver = receiver;
	}
	
	/**
	 * initiate server and players, ensure order
	 * @return
	 */
	public boolean init() {
		boolean success = false;

		try {
			sem1.acquire();
			sem2.acquire();
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}

		if (initServer() && initInitiator() && initReceiver()) {
			success = true;
		}

		return success;
	}

	private boolean initServer() {
		boolean success = false;

		ExecutorService serverService = Executors.newSingleThreadExecutor();
		Future<Boolean> serverStarted = serverService.submit(server);
		try {
			if (serverStarted.get()) {
				serverService.execute(() -> {
					server.listen();
				});
				sem1.release();
				success = true;
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}

		return success;
	}

	private boolean initInitiator() {
		boolean success = false;

		try {
			sem1.acquire();
			sem1.release();
			initiator.connect();
			sem2.release();
			success = true;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return success;
	}

	private boolean initReceiver() {
		boolean success = false;

		try {
			sem2.acquire();
			sem2.release();
			receiver.connect();
			success = true;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return success;
	}
}
