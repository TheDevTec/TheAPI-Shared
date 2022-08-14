package me.devtec.shared.sockets.implementation;

import java.io.File;

public class SocketAction {
	public int action;

	public byte[] config;
	public String fileName;
	public File file;

	public SocketAction(int action, byte[] config, File file, String fileName) {
		this.action = action;
		this.config = config;
		this.fileName = fileName;
		this.file = file;
	}

}
