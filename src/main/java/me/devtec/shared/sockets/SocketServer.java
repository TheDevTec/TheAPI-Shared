package me.devtec.shared.sockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.sockets.ClientDisconnectEvent;
import me.devtec.shared.events.api.sockets.ClientPreConnectEvent;
import me.devtec.shared.events.api.sockets.ServerDataReceiveEvent;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.shared.utility.StreamUtils;

public class SocketServer {

	private final String name;
	private final int port;
	private ServerSocketChannel server;
	private final Map<String, SocketServerClient> clients = new HashMap<>();

	public SocketServer(String socketName, int port) {
		name = socketName;
		this.port = port;
	}

	@Nonnull
	public Map<String, SocketServerClient> getConnectedClients() {
		return Collections.unmodifiableMap(clients);
	}

	@Nullable
	public SocketServerClient getClient(String name) {
		return clients.get(name);
	}

	@Nonnull
	public SocketServer start(String password) {
		new Tasker() {

			@Override
			public void run() {
				try {
					server = ServerSocketChannel.open();
					server.bind(new InetSocketAddress(port));
					server.configureBlocking(false);
					Selector serverSelector = Selector.open();
					server.register(serverSelector, SelectionKey.OP_ACCEPT);
					while (server.isOpen()) {
						int serverReadyChannels = serverSelector.select();
						if (serverReadyChannels == 0) {
							continue;
						}
						Set<SelectionKey> serverSelectedKeys = serverSelector.selectedKeys();
						Iterator<SelectionKey> serverKeyIterator = serverSelectedKeys.iterator();
						while (serverKeyIterator.hasNext()) {
							SelectionKey serverKey = serverKeyIterator.next();
							if (!serverKey.isValid()) { // Close connection
								// Just closed connection
								Iterator<SocketServerClient> itr = clients.values().iterator();
								while (itr.hasNext()) {
									if (itr.next().channel.equals(serverKey.channel())) {
										itr.remove();
										break;
									}
								}
								break;
							}
							if (serverKey.isAcceptable()) {
								SocketChannel socketChannel = server.accept();
								ClientPreConnectEvent event = new ClientPreConnectEvent(SocketServer.this, socketChannel.getRemoteAddress());
								EventManager.call(event);
								if (event.isCancelled()) {
									socketChannel.close();
									System.out.println("denied");
									continue;
								}
								socketChannel.configureBlocking(false);
								Selector reader = Selector.open();
								socketChannel.register(reader, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
								SocketServerClient client = new SocketServerClient(null, socketChannel);
								new Tasker() {

									@Override
									public void run() {
										try {
											while (server.isOpen() && socketChannel.isConnected()) {
												int readyChannels = reader.select();
												if (readyChannels == 0) {
													continue;
												}
												Set<SelectionKey> selectedKeys = reader.selectedKeys();
												Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
												while (keyIterator.hasNext()) {
													SelectionKey key = keyIterator.next();
													if (key.isReadable()) {
														ByteBuffer buffer = ByteBuffer.allocate(1024);

														StringContainer container = new StringContainer(256);

														// Read all
														while (socketChannel.read(buffer) > 0) {
															buffer.flip();
															container.append(StreamUtils.decode(buffer));
														}
														if (container.isEmpty()) {
															continue;
														}

														@SuppressWarnings("unchecked")
														Map<String, Object> data = (Map<String, Object>) Json.reader().read(container.toString());
														// Process data
														if (client.socketPhase == SocketPhase.ACTIVE) { // Connected
															ServerDataReceiveEvent event = new ServerDataReceiveEvent(SocketServer.this, client, data);
															EventManager.call(event);
														} else if (client.clientName == null) {
															client.clientName = String.valueOf(data.get("name"));
															if (client.clientName == null || !password.equals(data.get("password"))) {
																socketChannel.close(); // Incorrect password
																return;
															}
															client.socketPhase = SocketPhase.ACTIVE;
															clients.put(client.clientName, client);
															client.write(new Config().set("name", name));
														}
													}
													keyIterator.remove();
												}
												try {
													Thread.sleep(10);
												} catch (InterruptedException e) {
													break;
												}
											}
											if (client.socketPhase != SocketPhase.CLOSED) {
												ClientDisconnectEvent event = new ClientDisconnectEvent(SocketServer.this, client, false);
												EventManager.call(event);
											}
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
								}.runTask();
							}
							serverKeyIterator.remove();
						}
						Thread.sleep(10);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.runTask();
		return this;
	}

	public void close() {
		try {
			server.close();
			for (SocketServerClient element : clients.values()) {
				element.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public class SocketServerClient {

		protected SocketChannel channel;
		protected String clientName;
		protected SocketPhase socketPhase = SocketPhase.LOGIN;

		public SocketServerClient(String name, SocketChannel socketChannel) {
			clientName = name;
			channel = socketChannel;
		}

		public void close() {
			socketPhase = SocketPhase.CLOSED;
			try {
				channel.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			clients.remove(clientName);
			ClientDisconnectEvent event = new ClientDisconnectEvent(SocketServer.this, SocketServerClient.this, true);
			EventManager.call(event);
		}

		public void write(@Nonnull Config config) {
			if (config == null || config.getKeys().isEmpty()) {
				return;
			}
			ByteBuffer buffer = ByteBuffer.wrap(config.toByteArray(DataType.JSON));
			while (buffer.hasRemaining()) {
				try {
					channel.write(buffer);
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
		}

		public void write(@Nonnull Map<String, Object> json) {
			if (json == null || json.isEmpty()) {
				return;
			}
			ByteBuffer buffer = ByteBuffer.wrap(StringContainer.getBytes(Json.writer().write(json)));
			while (buffer.hasRemaining()) {
				try {
					channel.write(buffer);
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
		}

		public void awaitConnection() {
			while (socketPhase == SocketPhase.LOGIN) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		@Nonnull
		public SocketChannel getChannel() {
			return channel;
		}

		@Nonnull
		public SocketPhase getPhase() {
			return socketPhase;
		}

		/**
		 * @apiNote null in the {@link SocketPhase#LOGIN} phase
		 */
		@Nullable
		public String getName() {
			return clientName;
		}

		public boolean isConnected() {
			return socketPhase == SocketPhase.ACTIVE && channel.isConnected();
		}
	}

	@Nonnull
	public String getName() {
		return name;
	}

}
