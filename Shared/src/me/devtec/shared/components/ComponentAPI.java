package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.Json;
import me.devtec.shared.utility.ColorUtils;

public class ComponentAPI {
	static Map<String, ComponentTransformer<?>> transformers = new HashMap<>();
	static Boolean hexModeEnabled;

	public static ComponentTransformer<?> transformer(String name) {
		return ComponentAPI.transformers.get(name.toUpperCase());
	}

	public static ComponentTransformer<?> registerTransformer(String name, ComponentTransformer<?> transformer) {
		if (ComponentAPI.transformers.put(name.toUpperCase(), transformer) != null)
			Logger.getGlobal().warning("[TheAPI/ComponentAPI] Overriding " + name.toUpperCase() + " component transformer.");
		return transformer;
	}

	public static ComponentTransformer<?> unregisterTransformer(String name) {
		return ComponentAPI.transformers.remove(name.toUpperCase());
	}

	public static ComponentTransformer<?> bungee() {
		return ComponentAPI.transformer("BUNGEECORD");
	}

	public static ComponentTransformer<?> adventure() {
		return ComponentAPI.transformer("ADVENTURE");
	}

	public static String toString(Component input) {
		if (input == null)
			return null;
		return input.toString(); // Are you lazy or stupid?
	}

	public static Component fromString(String input) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(input,
				/* Depends on version & software */ hexModeEnabled == null ? (hexModeEnabled = !Ref.serverType().isBukkit() || Ref.serverType().isBukkit() && Ref.isNewerThan(15)) : hexModeEnabled,
				true);
	}

	public static Component fromString(String input, boolean hexMode) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(input,
				hexMode ? hexModeEnabled == null ? (hexModeEnabled = !Ref.serverType().isBukkit() || Ref.serverType().isBukkit() && Ref.isNewerThan(15)) : hexModeEnabled : false, true);
	}

	public static Component fromString(String input, boolean hexMode, boolean urlMode) {
		if (input == null || input.isEmpty())
			return Component.EMPTY_COMPONENT;

		Component main = new Component("");
		Component component = new Component();
		main.append(component);

		// UrlFinder
		int componentPos = 0;
		int componentStartAt = 0;
		int spaceFinder = 0;

		int privatePos = 0;
		byte lookingMode = 0;
		int prevCount = 0;
		int count = 0;

		int initAt = 0;
		StringContainer container = new StringContainer(input.length());
		charLoop: for (int i = 0; i < input.length(); ++i) {
			char c = input.charAt(i);
			if (c == '§' && i + 1 < input.length()) {
				char afterSymbol = input.charAt(++i);
				if (hexMode && afterSymbol == 'x' && i + 12 < input.length())
					for (int ic = 1; ic < 13; ++ic) {
						afterSymbol = input.charAt(i + ic);
						if (ic % 2 == 1) {
							if (afterSymbol == '§')
								continue;
							container.append('§').append('x');
							continue charLoop;
						}
						if (!isColorChar(afterSymbol)) {
							container.append('§').append('x');
							continue charLoop;
						}
						if (ic == 12) {
							if (!container.isEmpty()) {
								privatePos = 0;
								component.setText(container.toString());
								container.clear();
								component = new Component();
								main.append(component);
								++componentPos;
							}
							component.setColor(
									new String(new char[] { '#', input.charAt(i + 2), input.charAt(i + 4), input.charAt(i + 6), input.charAt(i + 8), input.charAt(i + 10), input.charAt(i + 12) }));
							i += 12;
							continue charLoop;
						}
						continue;
					}
				else if (isColorChar(afterSymbol)) {
					if (component.colorToChar() == afterSymbol)
						continue;
					if (!container.isEmpty()) {
						privatePos = 0;
						component.setText(container.toString());
						container.clear();
						component = new Component();
						main.append(component);
						++componentPos;
					}
					component.setColorFromChar(afterSymbol);
					continue;
				} else if (isFormat(afterSymbol)) {
					if (hasFormat(component, afterSymbol))
						continue;
					if (!container.isEmpty()) {
						privatePos = 0;
						component.setText(container.toString());
						container.clear();
						component = new Component().copyOf(component);
						main.append(component);
						++componentPos;
					}
					component.setFormatFromChar(afterSymbol, true);
					continue;
				}
				container.append('§').append(afterSymbol);
				continue charLoop;
			}
			container.append(c);
			if (urlMode)
				switchCase: switch (lookingMode) {
				case 0:
					initAt = i;
					componentStartAt = componentPos;
					if (c == 'h' && i + 11 < input.length()) {
						if (input.charAt(i + 1) == 't' && input.charAt(i + 2) == 't' || input.charAt(i + 3) == 'p') {
							container.append('t').append('t').append('p');
							if (input.charAt(i + 4) == 's' && input.charAt(i + 5) == ':' && input.charAt(i + 6) == '/' && input.charAt(i + 7) == '/') {
								container.append('s').append(':').append('/').append('/');
								i += 7;
								c = input.charAt(i + 1);
								if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
									container.append(c);
									++i;
									lookingMode = 1;
									count = 1;
									spaceFinder = privatePos == 0 || container.charAt(privatePos - 1) == ' ' ? privatePos : privatePos - 1;
								}
							} else if (input.charAt(i + 4) == ':' && input.charAt(i + 5) == '/' && input.charAt(i + 6) == '/') {
								container.append(':').append('/').append('/');
								i += 6;
								c = input.charAt(i + 1);
								if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
									container.append(c);
									++i;
									lookingMode = 1;
									count = 1;
									spaceFinder = privatePos == 0 || container.charAt(privatePos - 1) == ' ' ? privatePos : privatePos - 1;
								}
							}
						}
						break;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
						lookingMode = 1;
						count = 1;
						break;
					}
					break;
				case 1:
					if (c == '.') { // xxx.
						if (count >= 2) {
							prevCount = count;
							count = 0;
							lookingMode = 2;
						} else {
							lookingMode = 0;
							spaceFinder = 0;
						}
						break switchCase;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
						++count;
						break switchCase;
					}
					if (c == ' ')
						spaceFinder = privatePos + 1;
					else
						spaceFinder = 0;
					lookingMode = 0;
					break;
				case 2: // xxx.(lookingForPossibleEnding/xxx)
					if (c >= '0' && c <= '9') {
						lookingMode = 0; // Start
						spaceFinder = 0;
						count = 0;
						break switchCase;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
						if (++count == 1 && prevCount >= 4)
							switch (c) {
							case 'a':
								if (i + 2 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'p' || input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 't')) {
									container.append(input.charAt(i + 1)).append(input.charAt(i + 2));
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'g':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'g') {
									container.append('g');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'm':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'c') {
									container.append('c');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'o' && input.charAt(i + 2) == 'n' && input.charAt(i + 3) == 's' && input.charAt(i + 4) == 't'
										&& input.charAt(i + 5) == 'e' && input.charAt(i + 6) == 'r') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'c':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'z') {
									container.append('z');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 2 < input.length() && input.charAt(i + 1) == 'o' && input.charAt(i + 2) == 'm') {
									container.append('o').append('m');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'u' && input.charAt(i + 4) == 'd') {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'n':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 't') {
									container.append('e').append('t');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'o':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 'g') {
									container.append('r').append('g');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 5 < input.length() && input.charAt(i + 1) == 'n' && input.charAt(i + 2) == 'l' && input.charAt(i + 3) == 'i' && input.charAt(i + 4) == 'n'
										&& input.charAt(i + 5) == 'e') {
									for (int ic = 1; ic < 6; ++ic)
										container.append(input.charAt(i + ic));
									i += 5;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'i':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'o') {
									container.append('o');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'n' && input.charAt(i + 2) == 'f' && input.charAt(i + 3) == 'o') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'u':
								if (i + 1 < input.length() && (input.charAt(i + 1) == 's' || input.charAt(i + 1) == 'k')) {
									container.append(input.charAt(i + 1));
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'd':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'e') {
									container.append('e');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'b':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'g') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 't':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'c' && input.charAt(i + 3) == 'h') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 's':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'k') {
									container.append('k');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 't' && input.charAt(i + 3) == 'e') {
									container.append('i').append('t').append('e');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'a' && input.charAt(i + 3) == 'c' && input.charAt(i + 4) == 'e'
										|| input.charAt(i + 1) == 't' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'r' && input.charAt(i + 4) == 'e')) {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'p':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'l') {
									container.append('l');
									i += 1;
									lookingMode = 4; // Read until space
								}
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 'o') {
									container.append('r').append('o');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'e':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'r':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'f':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'u' && input.charAt(i + 2) == 'n') {
									container.append('u').append('n');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'x':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'y' && input.charAt(i + 2) == 'z') {
									container.append('y').append('z');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'w':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 'k' && input.charAt(i + 3) == 'i') {
									container.append('i').append('k').append('i');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'b' && input.charAt(i + 3) == 's' && input.charAt(i + 4) == 'i'
										&& input.charAt(i + 5) == 't' && input.charAt(i + 6) == 'e') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							}
						break switchCase;
					}
					if (c == '.') { // xxx.
						if (count >= 4) {
							count = 0;
							lookingMode = 3;
						} else {
							lookingMode = 0;
							spaceFinder = 0;
						}
						break switchCase;
					}
					lookingMode = 0;
					spaceFinder = 0;
					break;
				case 3:
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
						if (++count == 1)
							switch (c) {
							case 'a':
								if (i + 2 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'p' || input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 't')) {
									container.append(input.charAt(i + 1)).append(input.charAt(i + 2));
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'g':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'g') {
									container.append('g');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'm':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'c') {
									container.append('c');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'o' && input.charAt(i + 2) == 'n' && input.charAt(i + 3) == 's' && input.charAt(i + 4) == 't'
										&& input.charAt(i + 5) == 'e' && input.charAt(i + 6) == 'r') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'c':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'z') {
									container.append('z');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 2 < input.length() && input.charAt(i + 1) == 'o' && input.charAt(i + 2) == 'm') {
									container.append('o').append('m');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'u' && input.charAt(i + 4) == 'd') {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'n':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 't') {
									container.append('e').append('t');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'o':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 'g') {
									container.append('r').append('g');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 5 < input.length() && input.charAt(i + 1) == 'n' && input.charAt(i + 2) == 'l' && input.charAt(i + 3) == 'i' && input.charAt(i + 4) == 'n'
										&& input.charAt(i + 5) == 'e') {
									for (int ic = 1; ic < 6; ++ic)
										container.append(input.charAt(i + ic));
									i += 5;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'i':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'o') {
									container.append('o');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'n' && input.charAt(i + 2) == 'f' && input.charAt(i + 3) == 'o') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'u':
								if (i + 1 < input.length() && (input.charAt(i + 1) == 's' || input.charAt(i + 1) == 'k')) {
									container.append(input.charAt(i + 1));
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'd':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'e') {
									container.append('e');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'b':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'g') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 't':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'c' && input.charAt(i + 3) == 'h') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 's':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'k') {
									container.append('k');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 't' && input.charAt(i + 3) == 'e') {
									container.append('i').append('t').append('e');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'a' && input.charAt(i + 3) == 'c' && input.charAt(i + 4) == 'e'
										|| input.charAt(i + 1) == 't' && input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'r' && input.charAt(i + 4) == 'e')) {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'p':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'l') {
									container.append('l');
									i += 1;
									lookingMode = 4; // Read until space
								}
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 'o') {
									container.append('r').append('o');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'e':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'r':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'f':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'u' && input.charAt(i + 2) == 'n') {
									container.append('u').append('n');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'x':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'y' && input.charAt(i + 2) == 'z') {
									container.append('y').append('z');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'w':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 'k' && input.charAt(i + 3) == 'i') {
									container.append('i').append('k').append('i');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'b' && input.charAt(i + 3) == 's' && input.charAt(i + 4) == 'i'
										&& input.charAt(i + 5) == 't' && input.charAt(i + 6) == 'e') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							}
						break switchCase;
					}
					switch (c) {
					case '/':
						lookingMode = 4; // Read until space
						break switchCase;
					default:
						lookingMode = 0;
						spaceFinder = 0;
						break;
					}
					break;
				case 4:
					if (c == ' ') {
						ClickEvent event = new ClickEvent(ClickEvent.Action.OPEN_URL, input.substring(initAt, i).replace('§', '&'));
						Component start = componentStartAt == componentPos ? component : main.getExtra().get(componentStartAt);
						if (componentStartAt == componentPos) { // Start & end are same
							Component withUrl = new Component(container.substring(spaceFinder, container.length() - 1)).copyOf(start);
							withUrl.setClickEvent(event);
							main.append(withUrl);
							++componentPos;
							start.setText(container.substring(0, spaceFinder));
							container.clear();
							container.append(' ');
							component = new Component().copyOf(start);
						} else {

							for (int middle = componentStartAt + 1; middle < componentPos; ++middle)
								main.getExtra().get(middle).setClickEvent(event);

							// start
							Component withUrl = new Component(start.getText().substring(spaceFinder, start.getText().length())).copyOf(start);
							withUrl.setClickEvent(event);
							main.getExtra().add(componentStartAt + 1, withUrl);
							start.setText(start.getText().substring(0, spaceFinder));
							++componentPos;

							container.deleteCharAt(container.length() - 1);

							// end
							Component end = main.getExtra().get(componentPos);
							end.setText(container.toString());
							end.setClickEvent(event);
							++componentPos;
							container.clear();
							container.append(' ');
							component = new Component().copyOf(end);
						}
						main.append(component);
						privatePos = 0;
						++componentPos;
						spaceFinder = 0;
						lookingMode = 0;
						count = 0;
					}
					break;
				}
			++privatePos;
		}
		if (lookingMode == 4) {
			ClickEvent event = new ClickEvent(ClickEvent.Action.OPEN_URL, input.substring(initAt).replace('§', '&'));
			Component start = componentStartAt == componentPos ? component : main.getExtra().get(componentStartAt);

			if (componentStartAt == componentPos) { // Start & end are same
				Component withUrl = new Component(container.substring(spaceFinder)).copyOf(start);
				withUrl.setClickEvent(event);
				main.append(withUrl);
				++componentPos;
				start.setText(container.substring(0, spaceFinder));
			} else {

				for (int middle = componentStartAt + 1; middle < componentPos; ++middle)
					main.getExtra().get(middle).setClickEvent(event);

				// start
				Component withUrl = new Component(start.getText().substring(spaceFinder, start.getText().length())).copyOf(start);
				withUrl.setClickEvent(event);
				main.getExtra().add(componentStartAt + 1, withUrl);
				start.setText(start.getText().substring(0, spaceFinder));
				++componentPos;

				// end
				Component end = main.getExtra().get(componentPos);
				end.setText(container.toString());
				end.setClickEvent(event);
			}
		} else if (!container.isEmpty())
			component.setText(container.toString());
		return main;
	}

	public static List<Map<String, Object>> toJsonList(Component component) {
		List<Map<String, Object>> list = new LinkedList<>();
		list.add(component.toJsonMap());
		if (component.getExtra() != null)
			ComponentAPI.toJsonListAll(list, component.getExtra());
		return list;
	}

	private static void toJsonListAll(List<Map<String, Object>> list, List<Component> extra) {
		for (Component c : extra) {
			list.add(c.toJsonMap());
			if (c.getExtra() != null)
				ComponentAPI.toJsonListAll(list, c.getExtra());
		}
	}

	public static List<Map<String, Object>> toJsonList(String text) {
		return ComponentAPI.toJsonList(ComponentAPI.fromString(text));
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> fixJsonList(List<Map<String, Object>> lists) { // usable for ex. chat format
		if (lists == null || lists.isEmpty())
			return lists;
		List<Map<String, Object>> list = new ArrayList<>();

		Iterator<Map<String, Object>> it = lists.listIterator();
		while (it.hasNext()) {
			Map<String, Object> text = it.next();

			if (text.get("text") == null || text.get("text").toString().isEmpty())
				continue; // fast skip

			Map<String, Object> hover = convertMapValues("hoverEvent", (Map<String, Object>) text.get("hoverEvent"));
			Map<String, Object> click = convertMapValues("clickEvent", (Map<String, Object>) text.get("clickEvent"));
			String insertion = (String) text.get("insertion");

			String stext = (String) text.get("text");
			Component c = ComponentAPI.fromString(stext);
			if (c != null) {
				if (!c.getText().isEmpty()) {
					Map<String, Object> json = c.toJsonMap();
					if (hover != null)
						json.put("hoverEvent", hover);
					if (click != null && c.getClickEvent() == null) // Propably URL
						json.put("clickEvent", click);
					if (insertion != null)
						json.put("insertion", insertion);
					list.add(json);
				}
				if (c.getExtra() != null)
					for (Component extras : c.getExtra())
						addExtras(extras, list, hover, click, insertion);
			}
			Object extra = text.get("extra");
			if (extra != null) {
				List<Map<String, Object>> extras = new ArrayList<>();
				if (extra instanceof Map)
					extras.addAll(fixJsonList(Arrays.asList((Map<String, Object>) extra)));
				else if (extra instanceof List)
					extras.addAll(fixJsonList((List<Map<String, Object>>) extra));
				list.get(list.size() - 1).put("extra", extras);
			}
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	public static String listToString(List<?> list) {
		StringContainer builder = new StringContainer(list.size() * 20);
		for (Object text : list)
			if (text instanceof Map)
				builder.append(ComponentAPI.getColor(((Map<String, Object>) text).get("color"))).append(String.valueOf(((Map<String, Object>) text).get("text")));
			else
				builder.append(ColorUtils.colorize(text + ""));
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	public static Component fromJson(String json) {
		Object obj = Json.reader().simpleRead(json);
		if (obj instanceof Map)
			return fromJson((Map<String, Object>) obj);
		if (!(obj instanceof Collection))
			return fromString(json);
		return fromJson((Collection<?>) obj);
	}

	@SuppressWarnings("unchecked")
	public static Component fromJson(Collection<?> collection) {
		Component component = new Component("");
		for (Object val : collection)
			if (val instanceof Map)
				component.append(fromJson((Map<String, Object>) val));
		return component;
	}

	@SuppressWarnings("unchecked")
	public static Component fromJson(Map<String, Object> map) {
		if (map.containsKey("id") && map.containsKey("type"))
			return ComponentEntity.fromJson(map);
		if (map.containsKey("id"))
			return ComponentItem.fromJson(map);
		Component component = new Component(map.get("text") + "");
		if (map.containsKey("color"))
			component.setColor(map.get("color") + "");
		if (map.containsKey("font"))
			component.setFont(map.get("font") + "");
		if (map.containsKey("bold"))
			component.setBold((boolean) map.get("bold"));
		if (map.containsKey("italic"))
			component.setItalic((boolean) map.get("italic"));
		if (map.containsKey("obfuscated"))
			component.setObfuscated((boolean) map.get("obfuscated"));
		if (map.containsKey("underlined"))
			component.setUnderlined((boolean) map.get("underlined"));
		if (map.containsKey("strikethrough"))
			component.setStrikethrough((boolean) map.get("strikethrough"));
		if (map.containsKey("hoverEvent")) {
			Map<String, String> value = (Map<String, String>) map.get("hoverEvent");
			Object val = value.getOrDefault("value", value.get("contents"));
			component.setHoverEvent(new HoverEvent(HoverEvent.Action.valueOf((value.get("action") + "").toUpperCase()),
					val instanceof Collection ? fromJson((Collection<?>) val) : val instanceof Map ? fromJson((Map<String, Object>) val) : fromString(val + "")));
		}
		if (map.containsKey("clickEvent")) {
			Map<String, String> value = (Map<String, String>) map.get("clickEvent");
			component.setClickEvent(new ClickEvent(ClickEvent.Action.valueOf((value.get("action") + "").toUpperCase()), value.get("value") + ""));
		}
		if (map.containsKey("insertion"))
			component.setInsertion(map.get("insertion") + "");
		if (map.containsKey("extra")) {
			Object extra = map.get("extra");
			if (extra instanceof Map)
				component.append(fromJson((Map<String, Object>) extra));
			else if (extra instanceof Collection)
				for (Object val : (Collection<?>) extra)
					if (val instanceof Map)
						component.append(fromJson((Map<String, Object>) val));
		}
		return component;
	}

	private static boolean hasFormat(Component component, char afterSymbol) {
		switch (afterSymbol) {
		case 'k':
		case 'K':
			return component.isObfuscated();
		case 'l':
		case 'L':
			return component.isBold();
		case 'm':
		case 'M':
			return component.isStrikethrough();
		case 'n':
		case 'N':
			return component.isUnderlined();
		case 'o':
		case 'O':
			return component.isItalic();
		case 'r':
		case 'R':
			return !(component.isObfuscated() || component.isBold() || component.isStrikethrough() || component.isUnderlined() || component.isItalic());
		}
		return false;
	}

	private static boolean isColorChar(char afterSymbol) {
		return afterSymbol >= '0' && afterSymbol <= '9' || afterSymbol >= 'a' && afterSymbol <= 'f' || afterSymbol >= 'A' && afterSymbol <= 'F';
	}

	private static boolean isFormat(char afterSymbol) {
		return afterSymbol >= 'r' && afterSymbol <= 'R' || afterSymbol >= 'k' && afterSymbol <= 'o' || afterSymbol >= 'K' && afterSymbol <= 'O';
	}

	private static void addExtras(Component extras, List<Map<String, Object>> list, Map<String, Object> hover, Map<String, Object> click, String insertion) {
		Map<String, Object> jsons = extras.toJsonMap();
		if (hover != null)
			jsons.put("hoverEvent", hover);
		if (click != null && extras.getClickEvent() == null) // Propably URL
			jsons.put("clickEvent", click);
		if (insertion != null)
			jsons.put("insertion", insertion);
		list.add(jsons);
		if (extras.getExtra() != null)
			for (Component c : extras.getExtra())
				addExtras(c, list, hover, click, insertion);
	}

	private static String getColor(Object color) {
		if (color == null)
			return "";
		if (color.toString().startsWith("#"))
			return ColorUtils.color.replaceHex(color.toString());
		return "§" + Component.colorToChar(color.toString());
	}

	private static Map<String, Object> convertMapValues(String key, Map<String, Object> hover) {
		if (hover == null || hover.isEmpty())
			return null;
		Object val = hover.getOrDefault("value", hover.getOrDefault("content", hover.getOrDefault("contents", null)));
		if (val == null)
			hover.put("value", "");
		else if (key.equalsIgnoreCase("hoverEvent")) {
			if (val instanceof Collection || val instanceof Map) {
				Object ac = hover.get("action");
				hover.clear();
				hover.put("action", ac);
				hover.put("value", val);
			} else {
				Object ac = hover.get("action");
				hover.clear();
				hover.put("action", ac);
				hover.put("value", ComponentAPI.toJsonList(val + ""));
			}
		} else if (val instanceof Collection || val instanceof Map) {
			Object ac = hover.get("action");
			hover.clear();
			hover.put("action", ac);
			hover.put("value", val);
		} else {
			Object ac = hover.get("action");
			hover.clear();
			hover.put("action", ac);
			hover.put("value", val + "");
		}
		return hover;
	}
}
