package me.devtec.shared.commands.holder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.devtec.shared.API;
import me.devtec.shared.commands.structures.ArgumentCommandStructure;
import me.devtec.shared.commands.structures.CommandStructure;
import me.devtec.shared.utility.StringUtils;

@SuppressWarnings("unchecked")
public class CommandHolder<S> {
	private final CommandStructure<S> structure;

	private Object registeredCommandObject;
	private String cmd;
	private String[] aliases;

	public CommandHolder(CommandStructure<S> structure) {
		this.structure = structure;
	}

	private final String[] EMPTY_ARRAY = { "" };

	public Collection<String> tablist(Object obj, String[] args) {
		if (!this.structure.getSenderClass().isAssignableFrom(obj.getClass()))
			return Collections.emptyList();
		S s = (S) obj;
		if (args.length == 0)
			args = EMPTY_ARRAY;
		return StringUtils.copyPartialMatches(args[args.length - 1], lookupTab(this.structure, s, args, args[0], 0));
	}

	private Collection<String> lookupTab(CommandStructure<S> structure, S sender, String[] args, String arg,
			int argPos) {
		List<String> result = new ArrayList<>();

		if (args.length < argPos)
			return result;

		List<CommandStructure<S>> next = structure.getNextStructures(sender);

		if (next.isEmpty()) {
			if (structure instanceof ArgumentCommandStructure) {
				ArgumentCommandStructure<S> argumentStructure = (ArgumentCommandStructure<S>) structure;
				int len = argumentStructure.length();
				if (len == -1 || len >= args.length)
					return argumentStructure.tabList(sender, structure, args);
			}
			return result;
		}

		if (args.length - 1 == argPos) {
			for (CommandStructure<S> nextStructure : next)
				result.addAll(nextStructure.tabList(sender, nextStructure, args));
			return result;
		}

		argPos++;
		String nextArg = args.length <= argPos ? args[args.length - 1] : args[argPos];

		Object[] resolved = structure.findStructure(sender, arg, args, argPos, true);
		if (resolved == null || resolved.length == 0)
			return result;

		List<CommandStructure<S>> subs = (List<CommandStructure<S>>) resolved[0];
		for (CommandStructure<S> sub : subs)
			result.addAll(lookupTab(sub, sender, args, nextArg, argPos));
		return result;
	}

	public void execute(Object obj, String[] args) {
		if (!structure.getSenderClass().isAssignableFrom(obj.getClass()))
			return;

		S sender = (S) obj;
		CommandStructure<S> current = structure;
		CommandStructure<S> lastFallback = current.getFallback() != null ? current : null;
		int validDepth = 0;

		if (args.length == 0) {
			if (current.getCooldownDetection() != null && current.getCooldownDetection().waiting(sender, current, args))
				return;

			if (current.getExecutor() != null)
				current.getExecutor().execute(sender, current, args);
			else if (current.getFallback() != null)
				current.getFallback().execute(sender, current, args);
			return;
		}

		boolean hasValidChildren = false;
		int i = 0;
		while (i < args.length) {
			String arg = args[i];
			Object[] result = current.findStructure(sender, arg, args, i, false);
			List<CommandStructure<S>> nextList = (List<CommandStructure<S>>) result[0];
			boolean noPerms = (boolean) result[1];
			hasValidChildren = !nextList.isEmpty();

			if (current.getFallback() != null)
				lastFallback = current;

			if (noPerms) {
				if (current.getFallback() != null)
					current.getFallback().execute(sender, current, args);
				return;
			}

			if (nextList.isEmpty())
				break;

			CommandStructure<S> next = null;
			for (CommandStructure<S> n : nextList)
				if (n != null) {
					next = n;
					break;
				}

			if (next == null)
				break;

			int lengthConsumed = 1;

			if (next instanceof ArgumentCommandStructure) {
				int len = ((ArgumentCommandStructure<S>) next).length();
				if (len > 0) {
					if (i + len > args.length)
						len = args.length - i;
					lengthConsumed = len;
				}
			}

			current = next;
			validDepth = i + lengthConsumed;
			i += lengthConsumed;
		}

		if (args.length > validDepth && !current.hasChildStructures() && lastFallback != null) {
			current.getExecutor().execute(sender, current, args);
			return;
		}

		if (args.length > validDepth && !hasValidChildren && lastFallback != null) {
			lastFallback.getFallback().execute(sender, lastFallback, args);
			return;
		}

		if (current.getCooldownDetection() != null && current.getCooldownDetection().waiting(sender, current, args))
			return;

		if (current.getExecutor() != null)
			current.getExecutor().execute(sender, current, args);
		else if (current.getFallback() != null)
			current.getFallback().execute(sender, current, args);
	}

	public CommandHolder<S> register(String command, String... aliases) {
		API.commandsRegister.register(this, command, aliases);
		return this;
	}

	public void unregister() {
		API.commandsRegister.unregister(this);
	}

	public CommandStructure<S> getStructure() {
		return this.structure;
	}

	public Object getRegisteredCommand() {
		return registeredCommandObject;
	}

	public String getCommandName() {
		return cmd;
	}

	public String[] getCommandAliases() {
		return aliases;
	}

	public void setRegisteredCommand(Object registeredCommandObject, String cmd, String... aliases) {
		this.registeredCommandObject = registeredCommandObject;
		this.cmd = cmd;
		this.aliases = aliases;
	}
}
