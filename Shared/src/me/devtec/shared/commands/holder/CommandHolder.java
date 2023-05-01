package me.devtec.shared.commands.holder;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.devtec.shared.API;
import me.devtec.shared.commands.structures.ArgumentCommandStructure;
import me.devtec.shared.commands.structures.CallableArgumentCommandStructure;
import me.devtec.shared.commands.structures.CommandStructure;
import me.devtec.shared.utility.StringUtils;

@SuppressWarnings("unchecked")
public class CommandHolder<S> {
	private CommandStructure<S> structure;

	private Object registeredCommandObject;
	private String cmd;
	private String[] aliases;

	public CommandHolder(CommandStructure<S> structure) {
		this.structure = structure;
	}

	private String[] EMPTY_ARRAY = { "" };

	public List<String> tablist(Object obj, String[] args) {
		if (!this.structure.getSenderClass().isAssignableFrom(obj.getClass()))
			return Collections.emptyList();
		S s = (S) obj;
		if (args.length == 0)
			args = EMPTY_ARRAY;
		return StringUtils.copyPartialMatches(args[args.length - 1], lookupTab(this.structure, s, args, args[0], 0));
	}

	private List<String> lookupTab(CommandStructure<S> structure, S s, String[] args, String arg, int argPos) {
		List<String> result = new LinkedList<>();
		if (args.length >= argPos) {
			List<CommandStructure<S>> nextStructures = structure.getNextStructures(s);
			if (nextStructures.isEmpty()) {
				if (structure instanceof ArgumentCommandStructure && (((ArgumentCommandStructure<?>) structure).length() == -1 || ((ArgumentCommandStructure<?>) structure).length() >= args.length))
					return structure.tabList(s, structure, args);
				return result;
			}
			if (args.length - 1 == argPos) {
				for (CommandStructure<S> nextStructure : nextStructures)
					result.addAll(nextStructure.tabList(s, nextStructure, args));
				return result;
			}
		}
		++argPos;
		for (CommandStructure<S> sub : (List<CommandStructure<S>>) structure.findStructure(s, arg, args, true)[0])
			result.addAll(lookupTab(sub, s, args, args.length - 1 <= argPos ? args[args.length - 1] : args[argPos], argPos));
		return result;
	}

	public void execute(Object obj, String[] args) {
		if (!this.structure.getSenderClass().isAssignableFrom(obj.getClass()))
			return;
		S s = (S) obj;
		CommandStructure<S> cmd = this.structure;
		int pos = 0;
		for (String arg : args) {
			++pos;
			Object[] finder = cmd.findStructure(s, arg, args, false);
			List<CommandStructure<S>> nextStructures = (List<CommandStructure<S>>) finder[0];
			if ((boolean) finder[1]) {
				if (cmd.getFallback() != null)
					cmd.getFallback().execute(s, cmd, args);
				return;
			}
			boolean destroy = false;
			if (nextStructures.isEmpty()) {
				if (cmd.getCooldownDetection() != null && cmd.getCooldownDetection().waiting(s, cmd, args))
					;
				return;
			}
			for (CommandStructure<S> next : nextStructures) {
				if (next == null && this.maybeArgs(s, cmd, args, args.length - pos)) {
					destroy = true;
					break;
				}
				if (next != null)
					cmd = next;
			}
			if (destroy)
				break;
		}
		if (cmd.getCooldownDetection() != null && cmd.getCooldownDetection().waiting(s, cmd, args))
			return;
		cmd.getExecutor().execute(s, cmd, args);
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

	private boolean maybeArgs(S sender, CommandStructure<S> cmd, String[] args, int i) {
		if (cmd instanceof CallableArgumentCommandStructure)
			return !((CallableArgumentCommandStructure<S>) cmd).getArgs(sender, cmd, args).isEmpty() && i == 0;
		if (cmd instanceof ArgumentCommandStructure)
			return ((ArgumentCommandStructure<S>) cmd).getArgs(sender, cmd, args).isEmpty()
					&& (((ArgumentCommandStructure<S>) cmd).length() == -1 || ((ArgumentCommandStructure<S>) cmd).length() >= i);
		return false;
	}
}
