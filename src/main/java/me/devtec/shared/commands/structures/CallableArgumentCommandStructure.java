package me.devtec.shared.commands.structures;

import java.util.Collection;
import java.util.Collections;

import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandTabExecutor;

public class CallableArgumentCommandStructure<S> extends ArgumentCommandStructure<S> {

	private static final String[] EMPTY_STRING = {};

	private final CallableArgument<S> futureArgs;

	protected CallableArgumentCommandStructure(CommandStructure<S> parent, int length, CommandExecutor<S> ex,
			CommandTabExecutor<S> tabEx, CallableArgument<S> future) {

		super(parent, null, length, ex, tabEx, EMPTY_STRING);
		futureArgs = future;
	}

	@Override
	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		if (getTabExecutor() != null) {
			Collection<String> result = getTabExecutor().execute(sender, structure, arguments);
			return result == null ? Collections.emptyList() : result;
		}

		return getArgs(sender, structure, arguments);
	}

	@Override
	public Collection<String> getArgs(S sender, CommandStructure<S> structure, String[] arguments) {
		try {
			Collection<String> result = futureArgs.call(sender, structure, arguments);
			return result == null ? Collections.emptyList() : result;
		} catch (Exception e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

	public interface CallableArgument<S> {
		Collection<String> call(S sender, CommandStructure<S> structure, String[] args);
	}
}