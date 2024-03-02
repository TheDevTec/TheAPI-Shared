package me.devtec.shared.commands.structures;

import java.util.Collection;

import me.devtec.shared.API;
import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandTabExecutor;
import me.devtec.shared.commands.selectors.Selector;

public class SelectorCommandStructure<S> extends CommandStructure<S> {
	private Selector selector;
	private CommandTabExecutor<S> tabEx;

	protected SelectorCommandStructure(CommandStructure<S> parent, Selector selector, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {
		super(parent, ex);
		this.selector = selector;
		this.tabEx = tabEx;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		return tabEx != null ? tabEx.execute(sender, structure, arguments) : API.selectorUtils.build(sender, this.selector);
	}

	/**
	 * @apiNote Returns selector of this {@link SelectorCommandStructure}
	 */
	public Selector getSelector() {
		return this.selector;
	}

	/**
	 * @apiNote Returns custom tab executor of this {@link SelectorCommandStructure}
	 */
	public CommandTabExecutor<S> getTabExecutor() {
		return tabEx;
	}

	/**
	 * @apiNote Set custom tab executor to this {@link SelectorCommandStructure}
	 */
	public void setTabExecutor(CommandTabExecutor<S> tabExecutor) {
		tabEx = tabExecutor;
	}
}
