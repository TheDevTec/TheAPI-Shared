package me.devtec.shared.commands.manager;

import java.util.Collection;

import me.devtec.shared.commands.selectors.Selector;

public interface SelectorUtils<S> {
	Collection<String> build(S sender, Selector selector);

	boolean check(S sender, Selector selector, String value);

}
