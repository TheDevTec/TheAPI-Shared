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

	public List<String> tablist(Object obj, String[] args) {
		if (!this.structure.getSenderClass().isAssignableFrom(obj.getClass()))
			return Collections.emptyList();
		S s = (S) obj;
		int pos = 0;
		CommandStructure<S> cmd = this.structure;
		int argPos = 0;
		for (String arg : args) {
			++argPos;
			CommandStructure<S> next = (CommandStructure<S>) cmd.findStructure(s, arg, args, true)[0];
			if (next == null)
				return pos == args.length - 1 || this.maybeArgs(s, cmd, args, args.length - argPos) ? StringUtils.copyPartialMatches(args[args.length - 1], this.toList(s, args, cmd.getNextStructures(s))) : Collections.emptyList();
			cmd = next;
			++pos;
		}
		return StringUtils.copyPartialMatches(args[args.length - 1], this.toList(s, args, cmd.getParent().getNextStructures(s)));
	}

	private List<String> toList(S sender, String[] args, List<CommandStructure<S>> nextStructures) {
		List<String> cmdArgs = new LinkedList<>();
		for (CommandStructure<S> structure : nextStructures)
			cmdArgs.addAll(structure.tabList(sender, structure, args));
		return cmdArgs;
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
			CommandStructure<S> next = (CommandStructure<S>) finder[0];
			if (next == null && cmd.getFallback() != null) {
				if (cmd.getCooldownDetection() != null && cmd.getCooldownDetection().waiting(s, cmd, args))
					return;
				if (!(boolean) finder[1])
					cmd.getFallback().execute(s, cmd, args);
				return;
			}
			if (next == null && this.maybeArgs(s, cmd, args, args.length - pos))
				break;
			if (next != null)
				cmd = next;
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
		if (cmd instanceof ArgumentCommandStructure && !(cmd instanceof CallableArgumentCommandStructure))
			return ((ArgumentCommandStructure<S>) cmd).getArgs(sender, cmd, args).isEmpty() && (((ArgumentCommandStructure<S>) cmd).length() == -1 || ((ArgumentCommandStructure<S>) cmd).length() >= i);
		return false;
	}
}
