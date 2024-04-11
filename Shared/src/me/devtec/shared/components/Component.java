package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.StringContainer;

public class Component {
	public static final Component EMPTY_COMPONENT = new Component("");

	private String text;
	private List<Component> extra;

	// COLOR & FORMATS
	private String color; // #RRGGBB (1.16+) or COLOR_NAME
	private boolean bold; // l
	private boolean italic; // o
	private boolean obfuscated; // k
	private boolean underlined; // n
	private boolean strikethrough; // m

	// ADDITIONAL
	private HoverEvent hoverEvent;
	private ClickEvent clickEvent;
	private String font;
	private String insertion;

	public Component() {

	}

	public Component(String text) {
		this.text = text;
	}

	public Component setText(String value) {
		text = value;
		return this;
	}

	public String getText() {
		return text;
	}

	public boolean isBold() {
		return bold;
	}

	public boolean isItalic() {
		return italic;
	}

	public boolean isObfuscated() {
		return obfuscated;
	}

	public boolean isUnderlined() {
		return underlined;
	}

	public boolean isStrikethrough() {
		return strikethrough;
	}

	public Component setBold(boolean status) {
		bold = status;
		return this;
	}

	public Component setItalic(boolean status) {
		italic = status;
		return this;
	}

	public Component setObfuscated(boolean status) {
		obfuscated = status;
		return this;
	}

	public Component setUnderlined(boolean status) {
		underlined = status;
		return this;
	}

	public Component setStrikethrough(boolean status) {
		strikethrough = status;
		return this;
	}

	public String getFont() {
		return font;
	}

	public Component setFont(String font) {
		this.font = font;
		return this;
	}

	public HoverEvent getHoverEvent() {
		return hoverEvent;
	}

	public Component setHoverEvent(HoverEvent hoverEvent) {
		this.hoverEvent = hoverEvent;
		return this;
	}

	public ClickEvent getClickEvent() {
		return clickEvent;
	}

	public Component setClickEvent(ClickEvent clickEvent) {
		this.clickEvent = clickEvent;
		return this;
	}

	public String getInsertion() {
		return insertion;
	}

	public Component setInsertion(String insertion) {
		this.insertion = insertion;
		return this;
	}

	public List<Component> getExtra() {
		return extra;
	}

	public void setExtra(List<Component> extra) {
		this.extra = extra;
	}

	public Component append(Component comp) {
		if (extra == null)
			extra = new ArrayList<>();
		extra.add(comp);
		return this;
	}

	public String getFormats() {
		StringContainer builder = new StringContainer(10);
		if (isBold())
			builder.append('§').append('l');
		if (isItalic())
			builder.append('§').append('o');
		if (isObfuscated())
			builder.append('§').append('k');
		if (isUnderlined())
			builder.append('§').append('n');
		if (isStrikethrough())
			builder.append('§').append('m');
		return builder.toString();
	}

	@Override
	public String toString() {
		StringContainer builder = new StringContainer(getText().length() + 8);

		String colorBefore = null;

		// COLOR
		if (getColor() != null) {
			if (getColor().charAt(0) == '#')
				colorBefore = getColor();
			else
				colorBefore = "§" + colorToChar();
			builder.append(colorBefore);
		}

		// FORMATS
		String formatsBefore = getFormats();
		builder.append(formatsBefore);

		builder.append(getText());

		if (getExtra() != null)
			for (Component c : getExtra()) {
				builder.append(c.toString(colorBefore, formatsBefore));
				if (c.getColor() != null)
					if (c.getColor().charAt(0) == '#')
						colorBefore = c.getColor();
					else
						colorBefore = "§" + c.colorToChar();
				String formats = c.getFormats();
				if (!formats.isEmpty())
					formatsBefore = formats;
			}
		return builder.toString();
	}

	// Deeper toString with "anti" copying of colors & formats
	protected StringContainer toString(String parentColorBefore, String parentFormatsBefore) {
		StringContainer builder = new StringContainer(getText().length() + 8);

		String colorBefore = parentColorBefore;

		// FORMATS
		String formatsBefore = getFormats();
		// COLOR
		if (getColor() != null) {
			if (getColor().charAt(0) == '#')
				colorBefore = getColor();
			else
				colorBefore = "§" + colorToChar();
			if (!colorBefore.equals(parentColorBefore) || !formatsBefore.equals(parentFormatsBefore))
				builder.append(colorBefore);
		}

		// FORMATS
		if (!formatsBefore.equals(parentFormatsBefore))
			builder.append(formatsBefore);

		builder.append(getText());

		if (getExtra() != null)
			for (Component c : getExtra())
				builder.append(c.toString(colorBefore, formatsBefore));
		return builder;
	}

	public Component setColor(String nameOrHex) {
		color = nameOrHex != null && nameOrHex.isEmpty() ? null : nameOrHex;
		return this;
	}

	public String getColor() {
		return color;
	}

	public char colorToChar() {
		return Component.colorToChar(getColor());
	}

	protected static char colorToChar(String color) {
		if (color != null)
			switch (color) {
			// a - f
			case "green":
				return 97;
			case "aqua":
				return 98;
			case "red":
				return 99;
			case "light_purple":
				return 100;
			case "yellow":
				return 101;
			case "white":
				return 102;
			// 0 - 9
			case "black":
				return 48;
			case "dark_blue":
				return 49;
			case "dark_green":
				return 50;
			case "dark_aqua":
				return 51;
			case "dark_red":
				return 52;
			case "dark_purple":
				return 53;
			case "gold":
				return 54;
			case "gray":
				return 55;
			case "dark_gray":
				return 56;
			case "blue":
				return 57;
			default:
				break;
			}
		return 0;
	}

	public Component setColorFromChar(char character) {
		switch (character) {
		// a - f
		case 97:
			setColor("green");
			break;
		case 98:
			setColor("aqua");
			break;
		case 99:
			setColor("red");
			break;
		case 100:
			setColor("light_purple");
			break;
		case 101:
			setColor("yellow");
			break;
		case 102:
			setColor("white");
			break;
		// 0 - 9
		case 48:
			setColor("black");
			break;
		case 49:
			setColor("dark_blue");
			break;
		case 50:
			setColor("dark_green");
			break;
		case 51:
			setColor("dark_aqua");
			break;
		case 52:
			setColor("dark_red");
			break;
		case 53:
			setColor("dark_purple");
			break;
		case 54:
			setColor("gold");
			break;
		case 55:
			setColor("gray");
			break;
		case 56:
			setColor("dark_gray");
			break;
		case 57:
			setColor("blue");
			break;
		default:
			setColor(null);
			break;
		}
		return this;
	}

	public Component setFormatFromChar(char character, boolean status) {
		switch (character) {
		case 107:
			setObfuscated(status);
			break;
		case 108:
			setBold(status);
			break;
		case 109:
			setStrikethrough(status);
			break;
		case 110:
			setUnderlined(status);
			break;
		case 111:
			setItalic(status);
			break;
		default: // reset
			setBold(false);
			setItalic(false);
			setUnderlined(false);
			setStrikethrough(false);
			setObfuscated(false);
			break;
		}
		return this;
	}

	/**
	 * @apiNote Copy formats & additional settings from {@value selectedComp}
	 * @param selectedComp Component
	 * @return Component
	 */
	public Component copyOf(Component selectedComp) {
		bold = selectedComp.isBold();
		italic = selectedComp.isItalic();
		obfuscated = selectedComp.isObfuscated();
		underlined = selectedComp.isUnderlined();
		strikethrough = selectedComp.isStrikethrough();
		color = selectedComp.getColor();
		font = selectedComp.getFont();
		return this;
	}

	public Map<String, Object> toJsonMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("text", getText());
		if (getColor() != null)
			map.put("color", getColor());
		if (getClickEvent() != null)
			map.put("clickEvent", getClickEvent().toJsonMap());
		if (getHoverEvent() != null)
			map.put("hoverEvent", getHoverEvent().toJsonMap());
		if (getFont() != null)
			map.put("font", getFont());
		if (getInsertion() != null)
			map.put("insertion", getInsertion());
		if (isBold())
			map.put("bold", true);
		if (isItalic())
			map.put("italic", true);
		if (isStrikethrough())
			map.put("strikethrough", true);
		if (isObfuscated())
			map.put("obfuscated", true);
		if (isUnderlined())
			map.put("underlined", true);
		if(extra!=null&&!extra.isEmpty()){
            	    if(extra.size()==1){
                	map.put("extra",extra.get(0).toJsonMap());
		    }else {
			List<Map<String,Object>>list=new ArrayList<>();
			for(Component extras:extra){	
			    //possible cyclic error - not yet solved
                    	    list.add(extras.toJsonMap());
                	}
                	map.put("extra",list);
            	    }
                }
		return map;
	}
}
