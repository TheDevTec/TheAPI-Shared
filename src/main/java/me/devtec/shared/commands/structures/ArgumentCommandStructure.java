package me.devtec.shared.commands.structures;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandTabExecutor;

public class ArgumentCommandStructure<S> extends CommandStructure<S> {
	private final List<String> args = new LinkedList<>();
	private CommandTabExecutor<S> tabEx;
	private final int length;

	protected ArgumentCommandStructure(CommandStructure<S> parent, String argument, int length, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx, String[] aliases) {
		super(parent, ex);
		if (argument != null) {
			this.args.add(argument);
		}
		Collections.addAll(this.args, aliases);
		this.length = length;
		this.tabEx = tabEx;
	}

	@Override
	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		return tabEx != null ? tabEx.execute(sender, structure, arguments) : this.args.isEmpty() ? Collections.singletonList("<args>") : this.args;
	}

	/**
	 * @apiNote Returns arguments of this {@link ArgumentCommandStructure}
	 */
	public Collection<String> getArgs(S sender, CommandStructure<S> structure, String[] arguments) {
		return this.args;
	}

	/**
	 * @apiNote Returns custom tab executor of this {@link ArgumentCommandStructure}
	 */
	public CommandTabExecutor<S> getTabExecutor() {
		return tabEx;
	}

	/**
	 * @apiNote Set custom tab executor to this {@link ArgumentCommandStructure}
	 */
	public void setTabExecutor(CommandTabExecutor<S> tabExecutor) {
		tabEx = tabExecutor;
	}

	/**
	 * @apiNote Returns maximum length of arguments (-1 means unlimited)
	 */
	public int length() {
		return this.length;
	}
}
