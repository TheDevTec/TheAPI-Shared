package me.devtec.shared.commands.structures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import me.devtec.shared.API;
import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandHolder;
import me.devtec.shared.commands.holder.CommandTabExecutor;
import me.devtec.shared.commands.manager.PermissionChecker;
import me.devtec.shared.commands.selectors.Selector;
import me.devtec.shared.commands.structures.CallableArgumentCommandStructure.CallableArgument;

public class CommandStructure<S> {
	private CommandExecutor<S> executor;
	private String permission;
	private int priority;

	private PermissionChecker<S> permissionChecker;
	private final CommandStructure<S> parent;

	private final Map<Selector, SelectorCommandStructure<S>> selectors = new ConcurrentHashMap<>();
	private final List<ArgumentCommandStructure<S>> arguments = new ArrayList<>();
	private CommandExecutor<S> fallback;
	private CooldownDetection<S> detection;
	private Class<S> senderClass;

	protected CommandStructure(CommandStructure<S> parent, CommandExecutor<S> executor) {
		this.setExecutor(executor);
		this.parent = parent;
	}

	public interface CooldownDetection<T> {
		boolean waiting(T sender, CommandStructure<T> structure, String[] args);
	}

	public CommandStructure<S> cooldownDetection(CooldownDetection<S> detection) {
		this.detection = detection;
		return this;
	}

	public CooldownDetection<S> getCooldownDetection() {
		if (detection == null && getParent() != null)
			return getParent().getCooldownDetection();
		return detection;
	}

	/**
	 * @apiNote Creates new {@link CommandStructure}
	 *
	 */
	public static <T> CommandStructure<T> create(Class<T> executorClass, PermissionChecker<T> perm, CommandExecutor<T> executor) {
		CommandStructure<T> structure = new CommandStructure<>(null, executor);
		structure.permissionChecker = perm;
		structure.senderClass = executorClass;
		return structure;
	}

	/**
	 * @apiNote Add selector argument to current {@link CommandStructure}
	 *
	 */
	public SelectorCommandStructure<S> selector(Selector selector, CommandExecutor<S> ex) {
		SelectorCommandStructure<S> sub = new SelectorCommandStructure<>(this, selector, ex, null);
		this.selectors.put(sub.getSelector(), sub);
		return sub;
	}

	/**
	 * @apiNote Add selector argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public SelectorCommandStructure<S> selector(Selector selector, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {
		SelectorCommandStructure<S> sub = new SelectorCommandStructure<>(this, selector, ex, tabEx);
		this.selectors.put(sub.getSelector(), sub);
		return sub;
	}

	/**
	 * @apiNote Fallback executor when every try to find selector / argument
	 *          structure fail
	 *
	 */
	public CommandStructure<S> fallback(CommandExecutor<S> ex) { // Everything failed? Don't worry! This will be executed
		this.fallback = ex;
		return this;
	}

	/**
	 * @apiNote Returns fallback executor
	 *
	 */
	public CommandExecutor<S> getFallback() {
		return this.fallback;
	}

	/**
	 * @apiNote Returns command executor
	 *
	 */
	public CommandExecutor<S> getExecutor() {
		return this.executor;
	}

	/**
	 * @apiNote Override current command executor
	 *
	 */
	public CommandStructure<S> setExecutor(CommandExecutor<S> executor) {
		this.executor = executor;
		return this;
	}

	/**
	 * @apiNote Add string/s argument to current {@link CommandStructure}
	 *
	 */
	public ArgumentCommandStructure<S> argument(String argument, CommandExecutor<S> ex, String... aliases) {
		return this.argument(argument, 0, ex, aliases);
	}

	/**
	 * @apiNote Add string/s argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public ArgumentCommandStructure<S> argument(String argument, int length, CommandExecutor<S> ex, String... aliases) {
		ArgumentCommandStructure<S> sub = new ArgumentCommandStructure<>(this, argument, length, ex, null, aliases);
		this.arguments.add(sub);
		return sub;
	}

	/**
	 * @apiNote Add string/s argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public ArgumentCommandStructure<S> argument(String argument, CommandExecutor<S> ex, CommandTabExecutor<S> tab, String... aliases) {
		return this.argument(argument, 0, ex, tab, aliases);
	}

	/**
	 * @apiNote Add string/s argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public ArgumentCommandStructure<S> argument(String argument, int length, CommandExecutor<S> ex, CommandTabExecutor<S> tab, String... aliases) {
		ArgumentCommandStructure<S> sub = new ArgumentCommandStructure<>(this, argument, length, ex, tab, aliases);
		this.arguments.add(sub);
		return sub;
	}

	/**
	 * @apiNote Add string/s argument to current {@link CommandStructure}
	 *
	 */
	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, CommandExecutor<S> ex) {
		return this.callableArgument(future, 0, ex);
	}

	/**
	 * @apiNote Add string/s argument to current {@link CommandStructure}
	 *
	 */
	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, int length, CommandExecutor<S> ex) {
		CallableArgumentCommandStructure<S> sub = new CallableArgumentCommandStructure<>(this, length, ex, null, future);
		this.arguments.add(sub);
		return sub;
	}

	/**
	 * @apiNote Add string/s argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {
		return this.callableArgument(future, 0, ex, tabEx);
	}

	/**
	 * @apiNote Add string/s argument with own tab executor to current
	 *          {@link CommandStructure}
	 *
	 */
	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, int length, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {
		CallableArgumentCommandStructure<S> sub = new CallableArgumentCommandStructure<>(this, length, ex, tabEx, future);
		this.arguments.add(sub);
		return sub;
	}

	/**
	 * @apiNote Higher number means higher priority in lookup
	 */
	public CommandStructure<S> priority(int level) {
		this.priority = level;
		return this;
	}

	/**
	 * @apiNote Returns priority
	 */
	public int getPriority() {
		return this.priority;
	}

	/**
	 * @apiNote Permission to use this and other sub-commands
	 */
	public CommandStructure<S> permission(String permission) {
		this.permission = permission;
		return this;
	}

	/**
	 * @apiNote Returns permission
	 */
	public String getPermission() {
		if (permission == null && getParent() != null)
			return getParent().getPermission();
		return permission;
	}

	/**
	 * @apiNote Returns original {@link CommandStructure}
	 */
	public CommandStructure<S> first() {
		return this.getParent() == null ? this : this.getParent().first();
	}

	/**
	 * @apiNote Returns original {@link CommandStructure}
	 */
	public CommandStructure<S> firstParent() {
		return first();
	}

	/**
	 * @apiNote @Nullable Returns parent of this {@link CommandStructure}
	 *
	 */
	public CommandStructure<S> getParent() {
		return this.parent;
	}

	/**
	 * @apiNote @Nullable Returns parent of this
	 *          {@link CommandStructure#getParent()}
	 *
	 */
	public CommandStructure<S> parent() {
		return this.getParent();
	}

	/**
	 * @apiNote Jump X times down to parent of this
	 *          {@link CommandStructure#getParent()}
	 *
	 */
	public CommandStructure<S> getParent(int jumps) {
		return parent(jumps);
	}

	/**
	 * @apiNote Jump X times down to parent of this
	 *          {@link CommandStructure#getParent()}
	 *
	 */
	public CommandStructure<S> parent(int jumps) {
		if (jumps <= 0 || getParent() == null)
			return this;
		return getParent().parent(--jumps);
	}

	/**
	 * @apiNote Returns tab completer values of this {@link CommandStructure}
	 *
	 */
	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		return Collections.emptyList();
	}

	/**
	 * @apiNote Returns executor's class
	 *
	 */
	public Class<S> getSenderClass() {
		return this.first().senderClass;
	}

	/**
	 * @apiNote Build and convert to {@link CommandHolder}
	 *
	 */
	public CommandHolder<S> build() {
		return new CommandHolder<>(this.first());
	}

	@Override
	public String toString() {
		return this.getClass().getCanonicalName() + ":" + this.tabList(null, null, null);
	}

	// Special utils to make this structure working!

	@SuppressWarnings("unchecked")
	public final Object[] findStructure(S s, String arg, String[] args, boolean tablist) {
		Map<Integer, List<CommandStructure<S>>> structures = new TreeMap<>();
		boolean noPerms = false;

		PermissionChecker<S> permsChecker = first().permissionChecker;

		for (ArgumentCommandStructure<S> sub : this.arguments)
			if (CommandStructure.contains(sub, sub.getArgs(s, sub, args), arg)) {
				String perm = sub.getPermission();
				if (perm != null && !permsChecker.has(s, perm, tablist)) {
					noPerms = true;
					continue;
				}
                List<CommandStructure<S>> list = structures.computeIfAbsent(sub.getPriority(), k -> new LinkedList<>());
                list.add(sub);
			}
		for (SelectorCommandStructure<S> sub : this.selectors.values())
			if (API.selectorUtils.check(s, sub.getSelector(), arg)) {
				String perm = sub.getPermission();
				if (perm != null && !permsChecker.has(s, perm, tablist)) {
					noPerms = true;
					continue;
				}
                List<CommandStructure<S>> list = structures.computeIfAbsent(sub.getPriority(), k -> new LinkedList<>());
                list.add(sub);
			}
		List<CommandStructure<S>> list = new LinkedList<>();
		for (Entry<Integer, List<CommandStructure<S>>> entry : structures.entrySet())
			list.addAll(entry.getValue());
		return new Object[] { list, noPerms };
	}

	public final List<CommandStructure<S>> getNextStructures(S s) {
		Map<Integer, List<CommandStructure<S>>> structures = new TreeMap<>();
		for (ArgumentCommandStructure<S> sub : this.arguments)
			if (sub.getPermission() == null || sub.first().permissionChecker.has(s, sub.getPermission(), true)) {
                List<CommandStructure<S>> list = structures.computeIfAbsent(sub.getPriority(), k -> new LinkedList<>());
                list.add(sub);
			}
		for (SelectorCommandStructure<S> sub : this.selectors.values())
			if (sub.getPermission() == null || sub.first().permissionChecker.has(s, sub.getPermission(), true)) {
                List<CommandStructure<S>> list = structures.computeIfAbsent(sub.getPriority(), k -> new LinkedList<>());
                list.add(sub);
			}
		List<CommandStructure<S>> list = new LinkedList<>();
		for (Entry<Integer, List<CommandStructure<S>>> entry : structures.entrySet())
			list.addAll(entry.getValue());
		return list;
	}

	public static boolean contains(ArgumentCommandStructure<?> sub, Collection<String> list, String arg) {
		if (!(sub instanceof CallableArgumentCommandStructure) && list.isEmpty())
			return true;
		for (String value : list)
			if (value.equalsIgnoreCase(arg))
				return true;
		return false;
	}
}
