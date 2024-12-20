package me.devtec.shared.sockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.sockets.ClientClosedEvent;
import me.devtec.shared.events.api.sockets.ClientDataReceiveEvent;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.shared.utility.StreamUtils;

public class SocketClient {

	private String clientName;
	private String ip;
	private int port;

	private SocketChannel channel;
	private String serverName;

	private SocketPhase socketPhase = SocketPhase.WAITING;

	private final Processor READ_STATE = stateProcessor = (channel1, input1) -> {
		ClientDataReceiveEvent event = new ClientDataReceiveEvent(SocketClient.this, input1);
		EventManager.call(event);
		return true;
	};
	private final Processor LOGIN_STATE = (channel, input) -> {
		this.serverName = (String) input.get("name");
		if (serverName == null) {
			return false;
		}
		socketPhase = SocketPhase.ACTIVE;
		stateProcessor = READ_STATE;
		return true;
	};
	private Processor stateProcessor = LOGIN_STATE;

	public interface Processor {
		boolean process(SocketChannel channel, Map<String, Object> input);
	}

	public SocketClient(String socketName, String ip, int port) {
		clientName = socketName;
		this.ip = ip;
		this.port = port;
	}

	@Nonnull
	public String getName() {
		return clientName;
	}

	public void close() {
		reset();
		socketPhase = SocketPhase.CLOSED;
		try {
			channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		ClientClosedEvent event = new ClientClosedEvent(SocketClient.this, true);
		EventManager.call(event);
	}

	public void write(@Nonnull Config config) {
		if (config == null || config.getKeys().isEmpty() || channel == null || !channel.isConnected()) {
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
		if (json == null || json.isEmpty() || channel == null || !channel.isConnected()) {
			return;
		}
		ByteBuffer buffer = ByteBuffer.wrap(new StringContainer(Json.writer().simpleWrite(json)).getBytes());
		while (buffer.hasRemaining()) {
			try {
				channel.write(buffer);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	@Nullable
	public SocketChannel getChannel() {
		return channel;
	}

	public boolean isConnected() {
		return socketPhase == SocketPhase.ACTIVE && channel != null && channel.isConnected();
	}

	@Nullable
	public String getServerName() {
		return serverName;
	}

	private void reset() {
		socketPhase = SocketPhase.WAITING;
		stateProcessor = LOGIN_STATE;
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
	public SocketClient connect(String password, boolean reconnectIfOffline) {
		if (socketPhase != SocketPhase.WAITING && socketPhase != SocketPhase.CLOSED) {
			return this;
		}
		socketPhase = SocketPhase.LOGIN;
		new Tasker() {

			@Override
			public void run() {
				try {
					channel = SocketChannel.open();
					channel.configureBlocking(false);
					channel.connect(new InetSocketAddress(ip, port));

					try {
						while (!channel.finishConnect()) {
							;
						}
					} catch (Exception e) {
						boolean connected = false;
						if (e.getMessage() != null && "Connection refused: no further information".equals(e.getMessage())) {
							if (reconnectIfOffline) {
								try {
									Thread.sleep(10000);
								} catch (InterruptedException err) {
									return;
								}
								reset();
								SocketClient.this.connect(password, true);
								return;
							}
							int tries = 3;
							while (--tries > 0) {
								try {
									Thread.sleep(5000);
								} catch (InterruptedException err) {
									return;
								}
								channel.connect(new InetSocketAddress(ip, port));

								try {
									while (!channel.finishConnect()) {
										;
									}
									connected = true;
									break;
								} catch (Exception r) {
									if (r.getMessage() != null && "Connection refused: no further information".equals(r.getMessage())) {

									}
								}
							}
							if (!connected) {
								reset();
								return;
							}
						}

					}
					if (!channel.isConnected()) {
						try {
							Thread.sleep(10000);
						} catch (InterruptedException err) {
							return;
						}
						reset();
						SocketClient.this.connect(password, true);
						return;
					}

					Selector selector = Selector.open();
					channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					new Tasker() {
						boolean providedLogin = false;

						@Override
						public void run() {
							socketPhaseLoop: while (socketPhase != SocketPhase.WAITING) {
								try {
									int readyChannels = selector.select();
									if (readyChannels == 0) {
										continue;
									}
									Set<SelectionKey> selectedKeys = selector.selectedKeys();
									Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
									while (keyIterator.hasNext()) {
										SelectionKey key = keyIterator.next();
										if (key.isWritable() && socketPhase == SocketPhase.LOGIN && !providedLogin) {
											Config settings = new Config().set("name", clientName).set("password", password);
											write(settings);
											providedLogin = true;
										}
										if (key.isReadable()) {
											ByteBuffer buffer = ByteBuffer.allocate(1024);

											StringContainer container = new StringContainer(256);

											// Read all
											while (channel.read(buffer) > 0) {
												buffer.flip();
												container.append(StreamUtils.decode(buffer));
											}
											if (container.isEmpty()) {
												continue;
											}

											@SuppressWarnings("unchecked")
											Map<String, Object> data = (Map<String, Object>) Json.reader().read(container.toString());

											// Process data
											if (!stateProcessor.process(channel, data)) {
												break socketPhaseLoop;
											}
										}
										keyIterator.remove();
									}
									Thread.sleep(10);
								} catch (Exception e) {
									if (e.getMessage() != null && ("Connection reset by peer".equals(e.getMessage()) || "Connection refused: no further information".equals(e.getMessage())
											|| "Connection reset".equals(e.getMessage()))) {
										break;
									}
									e.printStackTrace();
								}
							}
							if (socketPhase != SocketPhase.CLOSED) {
								ClientClosedEvent event = new ClientClosedEvent(SocketClient.this, false);
								EventManager.call(event);
							}
							if (reconnectIfOffline) {
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e) {
									return;
								}
								reset();
								SocketClient.this.connect(password, true);
								return;
							}
							reset();
						}
					}.runTask();
				} catch (Exception err) {
					err.printStackTrace();
				}
			}
		}.runTask();
		return this;
	}

}
