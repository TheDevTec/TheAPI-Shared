package me.devtec.shared.sockets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.ByteLoader;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.ClientResponde;
import me.devtec.shared.events.api.ServerClientPreReceiveFileEvent;
import me.devtec.shared.events.api.ServerClientReceiveDataEvent;
import me.devtec.shared.events.api.ServerClientReceiveFileEvent;
import me.devtec.shared.events.api.ServerClientRespondeEvent;
import me.devtec.shared.sockets.implementation.SocketAction;

public class SocketUtils {
	private static final int READ_LIMIT = Integer.MAX_VALUE - 8;
	private static final int READ_TEXT_LIMIT = 256;
	private static final int READ_FILE_LIMIT = 16 * 1024;

	public static synchronized Config readConfig(DataInputStream in) throws IOException {
		byte[] path = new byte[in.readInt()];
		in.read(path);
		return new Config(ByteLoader.fromBytes(path));
	}

	public static synchronized String readText(DataInputStream in) throws IOException {
		return readText(in, READ_LIMIT);
	}

	public static synchronized String readText(DataInputStream in, int readLimit) throws IOException {
		int size = in.readInt();
		if (size >= readLimit || size >= READ_LIMIT)
			return "";
		StringContainer builder = new StringContainer(size);
		while (size > READ_TEXT_LIMIT) {
			size -= READ_TEXT_LIMIT;
			byte[] path = new byte[READ_TEXT_LIMIT];
			in.read(path);
			CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(path));
			char[] set = new char[decoded.capacity()];
			decoded.get(set);
			builder.append(new String(set));
		}
		if (size > 0) {
			byte[] path = new byte[size];
			in.read(path);
			CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(path));
			char[] set = new char[decoded.capacity()];
			decoded.get(set);
			builder.append(new String(set));
		}
		return builder.toString();
	}

	public static synchronized boolean readFile(DataInputStream in, FileOutputStream out, File file) {
		int bytes;
		long origin;
		try {
			long size = in.readLong();
			origin = size;
			byte[] buffer = new byte[READ_FILE_LIMIT];
			long total = 0;
			while (total < size && (bytes = in.read(buffer, 0, size - total > buffer.length ? buffer.length : (int) (size - total))) > 0) {
				out.write(buffer, 0, bytes);
				total += bytes;
			}
			out.close();
		} catch (Exception err) {
			err.printStackTrace();
			return false;
		}
		return origin == file.length();
	}

	public static synchronized void postAction(SocketClient client, int actionId) {
		SocketAction path = client.getWriteActions().get(actionId);
		if (path == null)
			return;
		DataOutputStream out = client.getOutputStream();
		if (path.file != null && path.file.exists()) {
			byte[] data = path.config;
			File file = path.file;
			String fileName = path.fileName;
			try {
				if (data != null) {
					out.writeInt(ClientResponde.RECEIVE_DATA_AND_FILE.getResponde());
					out.flush();
					// data
					out.writeInt(data.length);
					out.write(data);
				} else
					out.writeInt(ClientResponde.RECEIVE_FILE.getResponde());
				out.flush();
				// file
				byte[] bytesData = fileName.getBytes();
				out.writeInt(bytesData.length);
				out.write(bytesData);
				out.flush();
				ClientResponde responde = client.readUntilFind(ClientResponde.ACCEPTED_FILE, ClientResponde.REJECTED_FILE);
				ServerClientRespondeEvent crespondeEvent = new ServerClientRespondeEvent(client, responde.getResponde());
				EventManager.call(crespondeEvent);

				if (responde == ClientResponde.ACCEPTED_FILE) {
					long size = file.length();
					out.writeLong(size);
					out.flush();
					FileInputStream fileInputStream = new FileInputStream(file);
					int bytes = 0;
					byte[] buffer = new byte[READ_FILE_LIMIT];
					long total = 0;
					while (total < size && (bytes = fileInputStream.read(buffer, 0, size - total > buffer.length ? buffer.length : (int) (size - total))) > 0) {
						total += bytes;
						out.write(buffer, 0, bytes);
					}
					out.flush();
					fileInputStream.close();
					responde = client.readUntilFind(ClientResponde.SUCCESSFULLY_DOWNLOADED_FILE, ClientResponde.FAILED_DOWNLOAD_FILE);
					crespondeEvent = new ServerClientRespondeEvent(client, responde.getResponde());
					EventManager.call(crespondeEvent);
					if (responde != ClientResponde.FAILED_DOWNLOAD_FILE)
						client.getWriteActions().remove(actionId);
				} else
					client.getWriteActions().remove(actionId);
			} catch (Exception e) {
				client.stop();
				if (client.canReconnect())
					try {
						client.start();
					} catch (SocketException e1) {
					}
			}
			return;
		}
		if (path.config == null)
			return; // Invalid action
		try {
			byte[] data = path.config;
			out.writeInt(ClientResponde.RECEIVE_DATA.getResponde());
			out.writeInt(data.length);
			out.write(data);
			out.flush();
			client.getWriteActions().remove(actionId);
		} catch (Exception e) {
			client.stop();
			if (client.canReconnect())
				try {
					client.start();
				} catch (SocketException e1) {
				}
		}
	}

	public static synchronized void process(SocketClient client, int actionUid) throws IOException {
		client.getOutputStream().writeInt(ClientResponde.READ_ACTION.getResponde());
		client.getOutputStream().writeInt(actionUid);
		client.getOutputStream().flush();

		DataInputStream in = client.getInputStream();
		ClientResponde respondeRead = client.readUntilFind(ClientResponde.RECEIVE_DATA, ClientResponde.RECEIVE_DATA_AND_FILE, ClientResponde.RECEIVE_FILE);
		Config data = null;
		switch (respondeRead) {
		case RECEIVE_DATA: {
			ServerClientReceiveDataEvent event = new ServerClientReceiveDataEvent(client, SocketUtils.readConfig(in));
			EventManager.call(event);
			break;
		}
		case RECEIVE_DATA_AND_FILE:
			data = SocketUtils.readConfig(in);
		case RECEIVE_FILE:
			ServerClientPreReceiveFileEvent event = new ServerClientPreReceiveFileEvent(client, data, SocketUtils.readText(in));
			EventManager.call(event);
			if (event.isCancelled()) {
				client.getOutputStream().writeInt(ClientResponde.REJECTED_FILE.getResponde());
				client.getOutputStream().flush();
				break;
			}
			client.getOutputStream().writeInt(ClientResponde.ACCEPTED_FILE.getResponde());
			client.getOutputStream().flush();
			String folder = event.getFileDirectory();
			if (!folder.isEmpty() && !folder.endsWith("/"))
				folder += "/";
			File createdFile = SocketUtils.findUsableName(folder + event.getFileName());
			FileOutputStream out = new FileOutputStream(createdFile);
			if (!SocketUtils.readFile(in, out, createdFile)) {
				createdFile.delete(); // Failed to download file! Repeat.
				client.getOutputStream().writeInt(ClientResponde.FAILED_DOWNLOAD_FILE.getResponde());
				client.getOutputStream().flush();
				break;
			}
			client.getOutputStream().writeInt(ClientResponde.SUCCESSFULLY_DOWNLOADED_FILE.getResponde());
			client.getOutputStream().flush();
			ServerClientReceiveFileEvent fileEvent = new ServerClientReceiveFileEvent(client, data, createdFile);
			EventManager.call(fileEvent);
			break;
		default:
			break;
		}
	}

	private static File findUsableName(String fileName) {
		File file = new File(fileName);
		if (file.exists()) {
			String end = fileName.split("\\.")[fileName.split("\\.").length - 1];
			return SocketUtils.findUsableName(fileName.substring(0, fileName.length() - (end.length() + 1)) + "-copy." + end);
		}
		if (file.getParentFile() != null)
			file.getParentFile().mkdirs();
		try {
			file.createNewFile();
		} catch (Exception e) {
		}
		return file;
	}
}
