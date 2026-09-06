package me.devtec.shared.commands.structures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandTabExecutor;

public class ArgumentCommandStructure<S> extends CommandStructure<S> {

	private static final List<String> DEFAULT_TAB = Collections.singletonList("<args>");

	private final List<String> args;
	private CommandTabExecutor<S> tabEx;
	private final int length;

	protected ArgumentCommandStructure(CommandStructure<S> parent, String argument, int length,
			CommandExecutor<S> ex, CommandTabExecutor<S> tabEx, String[] aliases) {

		super(parent, ex);

		int size = aliases == null ? 0 : aliases.length;

		if (argument != null)
			++size;

		args = new ArrayList<>(size);

		if (argument != null)
			args.add(argument);

		if (aliases != null)
			Collections.addAll(args, aliases);

		this.length = length;
		this.tabEx = tabEx;
	}

	@Override
	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		if (tabEx != null)
			return tabEx.execute(sender, structure, arguments);

		return args.isEmpty() ? DEFAULT_TAB : args;
	}

	public Collection<String> getArgs(S sender, CommandStructure<S> structure, String[] arguments) {
		return args;
	}

	public CommandTabExecutor<S> getTabExecutor() {
		return tabEx;
	}

	public void setTabExecutor(CommandTabExecutor<S> tabExecutor) {
		tabEx = tabExecutor;
	}

	public int length() {
		return length;
	}
}