package me.devtec.shared.dataholder.codec;

import java.io.IOException;

public final class ConfigParseException extends IOException {
	private static final long serialVersionUID = 1;
	public final String format;
	public final long offset, line, column;

	public ConfigParseException(String format, long offset, long line, long column, String reason) {
		super(format + " at line " + line + ", column " + column + " (offset " + offset + "): " + reason);
		this.format = format;
		this.offset = offset;
		this.line = line;
		this.column = column;
	}
}