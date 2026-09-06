package me.devtec.shared.dataholder.store;

import java.util.List;

/** Lexical compatibility information; never the canonical logical value. */
public final class NodeMetadata {
	public String writtenValue;
	public String commentAfterValue;
	public List<String> comments;

	public NodeMetadata() {
	}

	public NodeMetadata(String raw, String after, List<String> before) {
		writtenValue = raw;
		commentAfterValue = after;
		comments = before;
	}
}