package me.devtec.shared.json.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.Pair;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.JReader;
import me.devtec.shared.utility.ParseUtils;

public class CustomJsonReader implements JReader {
	static final char COLON = ':';
	static final char QUOTES = '"';
	static final char QUOTES2 = '\'';
	static final char COMMA = ',';
	static final char OPEN_BRACE = '{';
	static final char OPEN_BRACKET = '[';
	static final char CLOSED_BRACE = '}';
	static final char CLOSED_BRACKET = ']';
	static final char SKIP_CHAR = '\\';

	static final char SKIP_R = '\r';
	static final char SKIP_N = '\n';
	static final char SPACE = ' ';
	static final char TAB = '	';

	static final char T = 't';
	static final char F = 'f';
	static final char N = 'n';

	static final char DOT = '.';
	static final char SMALL_E = 'e';
	static final char BIG_E = 'E';
	static final char MINUS = '-';
	static final char PLUS = '+';
	static final char ZERO = '0';
	static final char ONE = '1';
	static final char TWO = '2';
	static final char THREE = '3';
	static final char FOUR = '4';
	static final char FIVE = '5';
	static final char SIX = '6';
	static final char SEVEN = '7';
	static final char EIGHT = '8';
	static final char NINE = '9';

	@Override
	public Object fromGson(String json, Class<?> clazz) {
		return CustomJsonReader.fromJson(json);
	}

	private static boolean isHexChar(char c) {
		return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
	}

	private static Pair readList(StringContainer text, int pos, int to, StringContainer container) {
		List<Object> listResult = new ArrayList<>();
		// 0=", [, {, numbers, true, false or null
		// 1=, or :
		// 2=numbers or ,
		// 3=regular text or ,
		// 4=none
		int awaitingAction = 0;

		for (; pos < to; ++pos) {
			char character = text.charAt(pos);
			switch (awaitingAction) {
			case 0:
				switch (character) {
				case CLOSED_BRACKET:
					if (!container.isEmpty()) {
						listResult.add(container.toString());
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case OPEN_BRACE: {
					++pos;
					Pair pair = readMap(text, pos, to, container);
					container.clear();
					listResult.add(
							pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey());
					pos = (int) pair.getValue() - 1;
					awaitingAction = 1;
				}
					continue;
				case OPEN_BRACKET:
					++pos;
					Pair pair = readList(text, pos, to, container);
					container.clear();
					listResult.add(
							pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey());
					pos = (int) pair.getValue() - 1;
					awaitingAction = 1;
					continue;
				case QUOTES:
				case QUOTES2:
					char lookingFor = character;
					++pos;
					awaitingAction = 1;
					loop: for (; pos < to; ++pos) {
						character = text.charAt(pos);
						switch (character) {
						case QUOTES:
						case QUOTES2:
							if (lookingFor == character) {
								listResult.add(container.toString());
								container.clear();
								break loop;
							}
							break;
						case SKIP_CHAR:
							if (pos + 1 < to) {
								if (readSkip(text, pos, lookingFor, container))
									++pos;
								else if (pos + 6 < to && text.charAt(pos + 1) == 'u') {
									boolean isHex = true;
									for (int j = 2; j < 6; j++) {
										char c = text.charAt(pos + j);
										if (!isHexChar(c)) {
											isHex = false;
											break;
										}
									}

									if (isHex) {
										container.deleteCharAt(container.length() - 1);
										String hex = text.substring(pos + 2, pos + 6);
										char unicodeChar = (char) Integer.parseInt(hex, 16);
										container.append(unicodeChar);
										pos += 5;
									}
								}
								continue;
							}
							break;
						}
						container.append(character);
					}
					continue;
				case ZERO:
				case ONE:
				case TWO:
				case THREE:
				case FOUR:
				case FIVE:
				case SIX:
				case SEVEN:
				case EIGHT:
				case NINE:
				case DOT:
				case MINUS:
				case PLUS:
					container.append(character);
					awaitingAction = 2;
					continue;
				case T:
					if (readTrue(text, pos, to, false)) {
						pos += 3;
						awaitingAction = 1;
						listResult.add(true);
						continue;
					}
					break;
				case F:
					if (readFalse(text, pos, to, false)) {
						pos += 4;
						awaitingAction = 1;
						listResult.add(false);
						continue;
					}
					break;
				case N:
					if (readNull(text, pos, to, false)) {
						pos += 3;
						awaitingAction = 1;
						listResult.add(null);
						continue;
					}
					break;
				case SPACE:
				case TAB:
				case SKIP_R:
				case SKIP_N:
					continue;
				}
				awaitingAction = 3;
				container.append(character);
				break;
			case 1:
				switch (character) {
				case CLOSED_BRACKET:
					if (!container.isEmpty()) {
						listResult.add(container.toString());
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						listResult.add(container.toString());
						container.clear();
					}

					// RESET
					awaitingAction = 0;
					continue;
				}
				break;
			case 2:
				switch (character) {
				case CLOSED_BRACKET:
					if (!container.isEmpty()) {
						listResult.add(ParseUtils.getNumber(container));
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						listResult.add(ParseUtils.getNumber(container));
						container.clear();
					}

					// RESET
					awaitingAction = 0;
					continue;
				case ZERO:
				case ONE:
				case TWO:
				case THREE:
				case FOUR:
				case FIVE:
				case SIX:
				case SEVEN:
				case EIGHT:
				case NINE:
				case DOT:
				case SMALL_E:
				case BIG_E:
				case MINUS:
				case PLUS:
					container.append(character);
					continue;
				}
				container.append(character);
				awaitingAction = 3;
				break;
			case 3:
				switch (character) {
				case CLOSED_BRACKET:
					if (!container.isEmpty()) {
						listResult.add(container.trim().toString());
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						listResult.add(container.trim().toString());
						container.clear();
					}

					// RESET
					awaitingAction = 0;
					continue;
				case SKIP_N:
				case SKIP_R:
					continue;
				}
				container.append(character);
				break;
			case 4:
				switch (character) {
				case CLOSED_BRACKET:
				case CLOSED_BRACE:
				case COMMA:
					return Pair.of(listResult, pos);
				case SKIP_N:
				case SKIP_R:
				case SPACE:
				case TAB:
					continue;
				}
				if (container.isEmpty())
					return Pair.of(listResult, pos);
				return Pair.of(null, pos);
			}
		}
		if (container.isEmpty())
			return Pair.of(listResult, pos);
		return Pair.of(null, pos);
	}

	private static Pair readMap(StringContainer text, int pos, int to, StringContainer container) {
		Map<Object, Object> mapResult = new LinkedHashMap<>();
		// 0=", [, {, numbers, true, false or null
		// 1=, or :
		// 2=numbers, , or :
		// 3=regular text, , or :
		// 4=none
		int awaitingAction = 0;

		// MAP KEY
		Object key = null;
		boolean anyKey = false;

		for (; pos < to; ++pos) {
			char character = text.charAt(pos);
			switch (awaitingAction) {
			case 0:
				switch (character) {
				case CLOSED_BRACE:
					if (anyKey && !container.isEmpty()) {
						Object result = container.toString();
						mapResult.put(key, result);
						key = null;
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case QUOTES:
				case QUOTES2:
					char lookingFor = character;
					++pos;
					awaitingAction = 1;
					loop: for (; pos < to; ++pos) {
						character = text.charAt(pos);
						switch (character) {
						case QUOTES:
						case QUOTES2:
							if (lookingFor == character) {
								if (!anyKey) {
									key = container.toString();
									anyKey = true;
								} else {
									mapResult.put(key, container.toString());
									key = null;
								}
								container.clear();
								break loop;
							}
							break;
						case SKIP_CHAR:
							if (pos + 1 < to) {
								if (readSkip(text, pos, lookingFor, container))
									++pos;
								else if (pos + 6 < to && text.charAt(pos + 1) == 'u') {
									boolean isHex = true;
									for (int j = 2; j < 6; j++) {
										char c = text.charAt(pos + j);
										if (!isHexChar(c)) {
											isHex = false;
											break;
										}
									}

									if (isHex) {
										container.deleteCharAt(container.length() - 1);
										String hex = text.substring(pos + 2, pos + 6);
										char unicodeChar = (char) Integer.parseInt(hex, 16);
										container.append(unicodeChar);
										pos += 5;
									}
								}
								continue;
							}
							break;
						}
						container.append(character);
					}
					continue;
				case OPEN_BRACE: {
					++pos;
					awaitingAction = 1;
					Pair pair = readMap(text, pos, to, container);
					container.clear();
					if (!anyKey) {
						key = pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey();
						anyKey = true;
					} else {
						mapResult.put(key,
								pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey());
						anyKey = false;
						key = null;
					}
					pos = (int) pair.getValue() - 1;
				}
					continue;
				case OPEN_BRACKET:
					++pos;
					awaitingAction = 1;
					Pair pair = readList(text, pos, to, container);
					container.clear();
					if (!anyKey) {
						key = pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey();
						anyKey = true;
					} else {
						mapResult.put(key,
								pair.getKey() == null ? text.substring(pos - 1, (int) pair.getValue()) : pair.getKey());
						anyKey = false;
						key = null;
					}
					pos = (int) pair.getValue() - 1;
					continue;
				case ZERO:
				case ONE:
				case TWO:
				case THREE:
				case FOUR:
				case FIVE:
				case SIX:
				case SEVEN:
				case EIGHT:
				case NINE:
				case DOT:
				case MINUS:
				case PLUS:
					container.append(character);
					awaitingAction = 2;
					continue;
				case T:
					if (readTrue(text, pos, to, true)) {
						pos += 3;
						awaitingAction = 1;
						if (!anyKey) {
							key = true;
							anyKey = true;
						} else {
							mapResult.put(key, true);
							key = null;
						}
						continue;
					}
					break;
				case F:
					if (readFalse(text, pos, to, true)) {
						pos += 4;
						awaitingAction = 1;
						if (!anyKey) {
							key = false;
							anyKey = true;
						} else {
							mapResult.put(key, false);
							key = null;
						}
						continue;
					}
					break;
				case N:
					if (readNull(text, pos, to, true)) {
						awaitingAction = 1;
						pos += 3;
						if (!anyKey) {
							key = null;
							anyKey = true;
						} else {
							mapResult.put(key, null);
							key = null;
						}
						continue;
					}
					break;
				case SPACE:
				case SKIP_N:
				case SKIP_R:
					continue;
				}
				container.append(character);
				awaitingAction = 3;
				break;
			case 1:
				switch (character) {
				case CLOSED_BRACE:
					if (anyKey && !container.isEmpty()) {
						Object result = container.toString();
						mapResult.put(key, result);
						key = null;
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case COLON:
					if (!container.isEmpty()) {
						key = container.toString();
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						mapResult.put(key, container.toString());
						container.clear();
					}
					// RESET
					awaitingAction = 0;
					key = null;
					anyKey = false;
					continue;
				}
				break;
			case 2: // numbers mode
				switch (character) {
				case CLOSED_BRACE:
					if (anyKey && !container.isEmpty()) {
						Object result = ParseUtils.getNumber(container);
						mapResult.put(key, result);
						key = null;
						container.clear();
					}
					awaitingAction = 4;
					continue;
				case COLON:
					if (!container.isEmpty()) {
						key = ParseUtils.getNumber(container);
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						mapResult.put(key, ParseUtils.getNumber(container));
						container.clear();
					}
					// RESET
					awaitingAction = 0;
					key = null;
					anyKey = false;
					continue;
				case ZERO:
				case ONE:
				case TWO:
				case THREE:
				case FOUR:
				case FIVE:
				case SIX:
				case SEVEN:
				case EIGHT:
				case NINE:
				case DOT:
				case SMALL_E:
				case BIG_E:
				case MINUS:
				case PLUS:
					container.append(character);
					continue;
				}
				container.append(character);
				awaitingAction = 3;
				break;
			case 3: // text mode
				switch (character) {
				case CLOSED_BRACE:
					if (anyKey && !container.isEmpty()) {
						Object result = container.trim().toString();
						mapResult.put(key, result);
						key = null;
					}
					awaitingAction = 4;
					continue;
				case COLON:
					if (!container.isEmpty()) {
						key = container.trim().toString();
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (!container.isEmpty()) {
						mapResult.put(key, container.trim().toString());
						container.clear();
					}
					// RESET
					awaitingAction = 0;
					key = null;
					anyKey = false;
					continue;
				case SKIP_N:
				case SKIP_R:
					continue;
				}
				container.append(character);
				break;
			case 4:
				switch (character) {
				case CLOSED_BRACE:
				case CLOSED_BRACKET:
				case COMMA:
					if (key != null)
						return Pair.of(null, pos);
					return Pair.of(mapResult, pos);
				case SKIP_N:
				case SKIP_R:
				case SPACE:
				case TAB:
					continue;
				}
				if (key == null && container.isEmpty())
					return Pair.of(mapResult, pos);
				return Pair.of(null, pos);
			}
		}
		if (key == null && container.isEmpty())
			return Pair.of(mapResult, pos);
		return Pair.of(null, pos);
	}

	private static boolean readSkip(StringContainer text, int pos, char endingChar, StringContainer container) {
		char character = text.charAt(pos + 1);
		if (character == endingChar || character == SKIP_CHAR) {
			container.append(character);
			return true;
		}
		container.append(SKIP_CHAR);
		return false;
	}

	private static boolean readTrue(StringContainer text, int pos, int to, boolean map) {
		return pos + 3 < to && text.charAt(pos + 1) == 'r' && text.charAt(pos + 2) == 'u' && text.charAt(pos + 3) == 'e'
				&& (pos + 4 >= to || pos + 4 < to && isAllowedEnding(text.charAt(pos + 4), map));
	}

	private static boolean readFalse(StringContainer text, int pos, int to, boolean map) {
		return pos + 4 < to && text.charAt(pos + 1) == 'a' && text.charAt(pos + 2) == 'l' && text.charAt(pos + 3) == 's'
				&& text.charAt(pos + 4) == 'e'
				&& (pos + 5 >= to || pos + 5 < to && isAllowedEnding(text.charAt(pos + 5), map));
	}

	private static boolean readNull(StringContainer text, int pos, int to, boolean map) {
		return pos + 3 < to && text.charAt(pos + 1) == 'u' && text.charAt(pos + 2) == 'l' && text.charAt(pos + 3) == 'l'
				&& (pos + 4 >= to || pos + 4 < to && isAllowedEnding(text.charAt(pos + 4), map));
	}

	// space, comma, tab, \r, \n, ], } or : (if field map is true)
	private static boolean isAllowedEnding(char charAt, boolean map) {
		switch (charAt) {
		case SPACE:
		case COMMA:
		case TAB:
		case SKIP_R:
		case SKIP_N:
		case CLOSED_BRACE:
		case CLOSED_BRACKET:
			return true;
		case COLON:
			return map;
		}
		return false;
	}

	public static Object fromJson(String json) {
		return CustomJsonReader.fromJson(new StringContainer(json == null ? "null" : json));
	}

	public static Object fromJson(StringContainer text) {
		char first = text.charAt(0);
		switch (first) {
		case OPEN_BRACKET: {
			Pair result = readList(text, 1, text.length(), new StringContainer());
			if (result.getKey() != null) {
				for (int pos = (int) result.getValue(); pos < text.length(); ++pos) {
					char character = text.charAt(pos);
					switch (character) {
					case SKIP_N:
					case SKIP_R:
					case SPACE:
					case TAB:
						continue;
					default:
						return text.toString();
					}
				}
				return result.getKey();
			}
			return text.toString();
		}
		case OPEN_BRACE:
			Pair result = readMap(text, 1, text.length(), new StringContainer());
			if (result.getKey() != null) {
				for (int pos = (int) result.getValue(); pos < text.length(); ++pos) {
					char character = text.charAt(pos);
					switch (character) {
					case SKIP_N:
					case SKIP_R:
					case SPACE:
					case TAB:
						continue;
					default:
						return text.toString();
					}
				}
				return result.getKey();
			}
			return text.toString();
		case T:
			if (text.length() == 4 && text.charAt(1) == 'r' && text.charAt(2) == 'u' && text.charAt(3) == 'e')
				return true;
			break;
		case F:
			if (text.length() == 5 && text.charAt(1) == 'a' && text.charAt(2) == 'l' && text.charAt(3) == 's'
					&& text.charAt(4) == 'e')
				return false;
			break;
		case N:
			if (text.length() == 4 && text.charAt(1) == 'u' && text.charAt(2) == 'l' && text.charAt(3) == 'l')
				return null;
			break;
		case ZERO:
		case ONE:
		case TWO:
		case THREE:
		case FOUR:
		case FIVE:
		case SIX:
		case SEVEN:
		case EIGHT:
		case NINE:
		case DOT:
		case SMALL_E:
		case BIG_E:
		case MINUS:
		case PLUS:
			Number number = ParseUtils.getNumber(text, 0, text.length());
			if (number == null)
				return text.toString();
			return number;
		}
		return text.toString();
	}

	@Override
	public String toString() {
		return "CustomJsonReader";
	}
}