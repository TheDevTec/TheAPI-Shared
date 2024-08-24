package me.devtec.shared.sockets.implementation;

import java.io.File;

public class SocketAction {
	public final int action;

	public final byte[] config;
	public final String fileName;
	public final File file;

	public SocketAction(int action, byte[] config, File file, String fileName) {
		this.action = action;
		this.config = config;
		this.fileName = fileName;
		this.file = file;
	}

}
