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

	private static Pair readList(StringContainer text, int pos, int to, StringContainer container) {
		List<Object> listResult = new ArrayList<>();
		// 0=", [, {, numbers, true, false or null
		// 1=, or :
		// 2=numbers, , or :
		// 3=regular text, , or :
		int awaitingAction = 0;

		for (; pos < to; ++pos) {
			char character = text.charAt(pos);
			switch (awaitingAction) {
			case 0:
				switch (character) {
				case CLOSED_BRACKET:
					if (container.length() > 0)
						listResult.add(container.toString());
					return Pair.of(listResult, pos);
				case OPEN_BRACE:
					++pos;
					Pair pair = readMap(text, pos, to, container);
					container.clear();
					listResult.add(pair.getKey());
					pos = (int) pair.getValue();
					awaitingAction = 1;
					continue;
				case OPEN_BRACKET:
					++pos;
					pair = readList(text, pos, to, container);
					container.clear();
					listResult.add(pair.getKey());
					pos = (int) pair.getValue();
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
				case SMALL_E:
				case BIG_E:
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
					if (container.length() > 0)
						listResult.add(container.toString());
					return Pair.of(listResult, pos);
				case COMMA:
					if (container.length() > 0) {
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
					if (container.length() > 0)
						listResult.add(ParseUtils.getNumber(container));
					return Pair.of(listResult, pos);
				case COMMA:
					if (container.length() > 0) {
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
				break;
			case 3:
				switch (character) {
				case CLOSED_BRACKET:
					if (container.length() > 0)
						listResult.add(container.trim().toString());
					return Pair.of(listResult, pos);
				case COMMA:
					if (container.length() > 0) {
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
			}
		}
		return Pair.of(listResult, pos);
	}

	private static Pair readMap(StringContainer text, int pos, int to, StringContainer container) {
		Map<Object, Object> mapResult = new LinkedHashMap<>();
		// 0=", [, {, numbers, true, false or null
		// 1=, or :
		// 2=numbers, , or :
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
					if (anyKey && container.length() > 0) {
						Object result = container.toString();
						mapResult.put(key, result);
					}
					return Pair.of(mapResult, pos);
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
								} else
									mapResult.put(key, container.toString());
								container.clear();
								break loop;
							}
							break;
						case SKIP_CHAR:
							if (pos + 1 < to) {
								if (readSkip(text, pos, lookingFor, container))
									++pos;
								continue;
							}
							break;
						}
						container.append(character);
					}
					continue;
				case OPEN_BRACE:
					++pos;
					awaitingAction = 1;
					Pair pair = readMap(text, pos, to, container);
					container.clear();
					pos = (int) pair.getValue();
					if (!anyKey) {
						key = pair.getKey();
						anyKey = true;
					} else {
						mapResult.put(key, pair.getKey());
						anyKey = false;
					}
					continue;
				case OPEN_BRACKET:
					++pos;
					awaitingAction = 1;
					pair = readList(text, pos, to, container);
					container.clear();
					pos = (int) pair.getValue();
					if (!anyKey) {
						key = pair.getKey();
						anyKey = true;
					} else {
						mapResult.put(key, pair.getKey());
						anyKey = false;
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
				case SMALL_E:
				case BIG_E:
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
						} else
							mapResult.put(key, true);
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
						} else
							mapResult.put(key, false);
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
						} else
							mapResult.put(key, null);
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
					if (anyKey && container.length() > 0) {
						Object result = container.toString();
						mapResult.put(key, result);
					}
					if (mapResult.isEmpty() && to > 2)
						return Pair.of('{' + container.append(CLOSED_BRACE).toString(), pos);
					return Pair.of(mapResult, pos);
				case COLON:
					if (container.length() > 0) {
						key = container.toString();
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (container.length() > 0) {
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
					if (anyKey && container.length() > 0) {
						Object result = ParseUtils.getNumber(container);
						mapResult.put(key, result);
					}
					if (mapResult.isEmpty() && to > 2)
						return Pair.of('{' + container.append(CLOSED_BRACE).toString(), pos);
					return Pair.of(mapResult, pos);
				case COLON:
					if (container.length() > 0) {
						key = ParseUtils.getNumber(container);
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (container.length() > 0) {
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
				break;
			case 3: // text mode
				switch (character) {
				case CLOSED_BRACE:
					if (anyKey && container.length() > 0) {
						Object result = container.trim().toString();
						mapResult.put(key, result);
					}
					if (mapResult.isEmpty() && to > 2)
						return Pair.of('{' + container.append(CLOSED_BRACE).toString(), pos);
					return Pair.of(mapResult, pos);
				case COLON:
					if (container.length() > 0) {
						key = container.trim().toString();
						container.clear();
						anyKey = true;
					}
					// RESET
					awaitingAction = 0;
					continue;
				case COMMA:
					if (container.length() > 0) {
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
			}
		}
		return Pair.of(mapResult, pos);
	}

	private static boolean readSkip(StringContainer text, int pos, char endingChar, StringContainer container) {
		char character = text.charAt(pos + 1);
		if (character == endingChar) {
			container.append(character);
			return true;
		}
		container.append(SKIP_CHAR);
		return false;
	}

	private static boolean readTrue(StringContainer text, int pos, int to, boolean map) {
		if (pos + 3 < to && text.charAt(pos + 1) == 'r' && text.charAt(pos + 2) == 'u' && text.charAt(pos + 3) == 'e' && (pos + 4 >= to || pos + 4 < to && isAllowedEnding(text.charAt(pos + 4), map)))
			return true;
		return false;
	}

	private static boolean readFalse(StringContainer text, int pos, int to, boolean map) {
		if (pos + 4 < to && text.charAt(pos + 1) == 'a' && text.charAt(pos + 2) == 'l' && text.charAt(pos + 3) == 's' && text.charAt(pos + 4) == 'e'
				&& (pos + 5 >= to || pos + 5 < to && isAllowedEnding(text.charAt(pos + 5), map)))
			return true;
		return false;
	}

	private static boolean readNull(StringContainer text, int pos, int to, boolean map) {
		if (pos + 3 < to && text.charAt(pos + 1) == 'u' && text.charAt(pos + 2) == 'l' && text.charAt(pos + 3) == 'l' && (pos + 4 >= to || pos + 4 < to && isAllowedEnding(text.charAt(pos + 4), map)))
			return true;
		return false;
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
		text.replace("\\u003d", "=");
		char first = text.charAt(0);
		switch (first) {
		case OPEN_BRACKET:
			return readList(text, 1, text.length(), new StringContainer()).getKey();
		case OPEN_BRACE:
			return readMap(text, 1, text.length(), new StringContainer()).getKey();
		case T:
			if (text.length() == 4 && text.charAt(1) == 'r' && text.charAt(2) == 'u' && text.charAt(3) == 'e')
				return true;
			break;
		case F:
			if (text.length() == 5 && text.charAt(1) == 'a' && text.charAt(2) == 'l' && text.charAt(3) == 's' && text.charAt(4) == 'e')
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