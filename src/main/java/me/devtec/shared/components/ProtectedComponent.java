package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProtectedComponent extends Component {

	public ProtectedComponent(Component component) {
		super(component.getText());
		copyOf(component);
		hoverEvent = component.getHoverEvent();
		clickEvent = component.getClickEvent();
		insertion = component.getInsertion();
		if (component.getExtra() != null && !component.getExtra().isEmpty()) {
			List<Component> protectedExtras = new ArrayList<>();
			for (Component extra : component.getExtra())
				protectedExtras.add(new ProtectedComponent(extra));
			extra = Collections.unmodifiableList(protectedExtras);
		}
	}

	public ProtectedComponent(String text) {
		super(text);
	}

	@Override
	public Component append(Component comp) {
		return this;
	}

	@Override
	public Component setText(String value) {
		return this;
	}

	@Override
	public Component setBold(boolean status) {
		return this;
	}

	@Override
	public Component setClickEvent(ClickEvent clickEvent) {
		return this;
	}

	@Override
	public Component setColor(String nameOrHex) {
		return this;
	}

	@Override
	public Component setColorFromChar(char character) {
		return this;
	}

	@Override
	public void setExtra(List<Component> extra) {

	}

	@Override
	public Component setFont(String font) {
		return this;
	}

	@Override
	public Component setHoverEvent(HoverEvent hoverEvent) {
		return this;
	}

	@Override
	public Component setInsertion(String insertion) {
		return this;
	}

	@Override
	public Component setItalic(boolean status) {
		return this;
	}

	@Override
	public Component setObfuscated(boolean status) {
		return this;
	}

	@Override
	public Component setStrikethrough(boolean status) {
		return this;
	}

	@Override
	public Component setUnderlined(boolean status) {
		return this;
	}
}
