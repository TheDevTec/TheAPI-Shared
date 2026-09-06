package me.devtec.shared.commands.holder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.devtec.shared.API;
import me.devtec.shared.commands.structures.ArgumentCommandStructure;
import me.devtec.shared.commands.structures.CommandStructure;
import me.devtec.shared.commands.structures.CommandStructure.CooldownDetection;
import me.devtec.shared.commands.structures.CommandStructure.LookupResult;
import me.devtec.shared.utility.StringUtils;

@SuppressWarnings("unchecked")
public class CommandHolder<S> {

	private static final String[] EMPTY_ARRAY = { "" };

	private final CommandStructure<S> structure;

	private Object registeredCommandObject;
	private String cmd;
	private String[] aliases;

	public CommandHolder(CommandStructure<S> structure) {
		this.structure = structure;
	}

	public Collection<String> tablist(Object obj, String[] args) {
		if (!structure.getSenderClass().isInstance(obj))
			return Collections.emptyList();

		S sender = (S) obj;

		if (args == null || args.length == 0)
			args = EMPTY_ARRAY;

		String prefix = args[args.length - 1];
		List<String> result = new ArrayList<>(8);

		lookupTab(structure, sender, args, 0, prefix, result);

		return result.isEmpty() ? Collections.<String>emptyList() : result;
	}

	private void lookupTab(CommandStructure<S> current, S sender, String[] args, int argPos,
			String prefix, List<String> result) {

		int target = args.length - 1;

		if (argPos > target)
			return;

		if (argPos == target) {
			if (!current.hasChildStructures())
				return;

			List<CommandStructure<S>> next = new ArrayList<>(4);
			current.getNextStructures(sender, next);

			for (CommandStructure<S> nextStructure : next) {
				Collection<String> values = nextStructure.tabList(sender, nextStructure, args);

				if (values != null && !values.isEmpty())
					StringUtils.copyPartialMatches(prefix, values, result);
			}

			return;
		}

		if (!current.hasChildStructures())
			return;

		List<CommandStructure<S>> matches = new ArrayList<>(4);

		current.findStructures(sender, args[argPos], args, argPos, true, matches);

		for (CommandStructure<S> sub : matches) {
			int nextPos = argPos + 1;

			if (sub instanceof ArgumentCommandStructure) {
				ArgumentCommandStructure<S> argument = (ArgumentCommandStructure<S>) sub;
				int length = argument.length();

				if (length == -1) {
					Collection<String> values = argument.tabList(sender, argument, args);

					if (values != null && !values.isEmpty())
						StringUtils.copyPartialMatches(prefix, values, result);

					continue;
				}

				if (length > 1) {
					int end = argPos + length;

					if (target < end) {
						Collection<String> values = argument.tabList(sender, argument, args);

						if (values != null && !values.isEmpty())
							StringUtils.copyPartialMatches(prefix, values, result);

						continue;
					}

					nextPos = end;
				}
			}

			lookupTab(sub, sender, args, nextPos, prefix, result);
		}
	}

	public void execute(Object obj, String[] args) {
		if (!structure.getSenderClass().isInstance(obj))
			return;

		S sender = (S) obj;
		CommandStructure<S> current = structure;
		CommandStructure<S> lastFallback = current.getFallback() != null ? current : null;

		if (args == null)
			args = new String[0];

		if (args.length == 0) {
			executeCurrent(sender, current, args);
			return;
		}

		LookupResult<S> lookup = new LookupResult<>();

		int validDepth = 0;
		boolean hasValidChildren = false;
		int i = 0;

		while (i < args.length) {
			if (current.getFallback() != null)
				lastFallback = current;

			current.findFirstStructure(sender, args[i], args, i, false, lookup);

			CommandStructure<S> next = lookup.getStructure();
			hasValidChildren = next != null;

			if (lookup.hasNoPermission()) {
				if (current.getFallback() != null)
					current.getFallback().execute(sender, current, args);

				return;
			}

			if (next == null)
				break;

			int remaining = args.length - i;
			int consumed = 1;

			if (next instanceof ArgumentCommandStructure) {
				int length = ((ArgumentCommandStructure<S>) next).length();

				if (length == -1)
					consumed = remaining;
				else if (length > 0)
					consumed = Math.min(length, remaining);
			}

			current = next;
			i += consumed;
			validDepth = i;
		}

		if (args.length > validDepth && !current.hasChildStructures() && lastFallback != null) {
			if (current.getExecutor() != null)
				current.getExecutor().execute(sender, current, args);
			else if (current.getFallback() != null)
				current.getFallback().execute(sender, current, args);

			return;
		}

		if (args.length > validDepth && !hasValidChildren && lastFallback != null) {
			lastFallback.getFallback().execute(sender, lastFallback, args);
			return;
		}

		executeCurrent(sender, current, args);
	}

	private void executeCurrent(S sender, CommandStructure<S> current, String[] args) {
		CooldownDetection<S> cooldown = current.getCooldownDetection();

		if (cooldown != null && cooldown.waiting(sender, current, args))
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
		return structure;
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