package me.devtec.shared.commands.structures;

import java.util.Collections;
import java.util.List;

import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandTabExecutor;

public class CallableArgumentCommandStructure<S> extends ArgumentCommandStructure<S> {
	private static String[] EMPTY_STRING = {};
	private CallableArgument<S> futureArgs;

	protected CallableArgumentCommandStructure(CommandStructure<S> parent, int length, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx, CallableArgument<S> future) {
		super(parent, null, length, ex, tabEx, CallableArgumentCommandStructure.EMPTY_STRING);
		this.futureArgs = future;
	}

	@Override
	public List<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		return getTabExecutor() != null ? getTabExecutor().execute(sender, structure, arguments) : this.getArgs(sender, structure, arguments);
	}

	/**
	 * @apiNote Returns arguments of this {@link ArgumentCommandStructure}
	 */
	@Override
	public List<String> getArgs(S sender, CommandStructure<S> structure, String[] arguments) {
		try {
			return this.futureArgs.call(sender, structure, arguments);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Collections.emptyList();
	}

	public interface CallableArgument<S> {
		public List<String> call(S sender, CommandStructure<S> structure, String[] args);
	}
}
