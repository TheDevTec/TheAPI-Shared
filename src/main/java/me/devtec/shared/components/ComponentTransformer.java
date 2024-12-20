package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.List;

public interface ComponentTransformer<T> {
	default T fromString(String string) {
		return this.fromComponent(ComponentAPI.fromString(string));
	}

	default T[] fromStringArray(String string) {
		return this.fromComponents(ComponentAPI.fromString(string));
	}

	Component toComponent(T value);

	default Component toComponent(T[] value) {
		Component comp = new Component("");
		List<Component> extra = new ArrayList<>();
		for (T t : value) {
			if (comp.getText().isEmpty()) {
				comp = this.toComponent(t);
			} else {
				extra.add(this.toComponent(t));
			}
		}
		comp.setExtra(extra);
		return comp;
	}

	T fromComponent(Component component);

	T fromComponent(List<Component> components);

	T[] fromComponents(Component component);

	T[] fromComponents(List<Component> components);
}
