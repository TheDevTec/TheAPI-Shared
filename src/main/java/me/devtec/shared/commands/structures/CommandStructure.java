package me.devtec.shared.commands.structures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.devtec.shared.API;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.commands.holder.CommandExecutor;
import me.devtec.shared.commands.holder.CommandHolder;
import me.devtec.shared.commands.holder.CommandTabExecutor;
import me.devtec.shared.commands.manager.PermissionChecker;
import me.devtec.shared.commands.selectors.Selector;
import me.devtec.shared.commands.structures.CallableArgumentCommandStructure.CallableArgument;

public class CommandStructure<S> {

	private CommandExecutor<S> executor;
	private String permission;
	protected int priority;

	private PermissionChecker<S> permissionChecker;
	private final CommandStructure<S> parent;
	private final CommandStructure<S> root;

	private final Map<Selector, SelectorCommandStructure<S>> selectors = new ConcurrentHashMap<>();
	private final List<ArgumentCommandStructure<S>> arguments = new ArrayList<>();

	private CommandExecutor<S> fallback;
	private CooldownDetection<S> detection;
	private Class<S> senderClass;

	protected CommandStructure(CommandStructure<S> parent, CommandExecutor<S> executor) {
		this.parent = parent;
		this.root = parent == null ? this : parent.root;
		this.executor = executor;
	}

	public interface CooldownDetection<T> {
		boolean waiting(T sender, CommandStructure<T> structure, String[] args);
	}

	public static final class LookupResult<T> {

		private CommandStructure<T> structure;
		private boolean noPermission;

		public CommandStructure<T> getStructure() {
			return structure;
		}

		public boolean hasNoPermission() {
			return noPermission;
		}

		private void reset() {
			structure = null;
			noPermission = false;
		}
	}

	public CommandStructure<S> cooldownDetection(CooldownDetection<S> detection) {
		this.detection = detection;
		return this;
	}

	public CooldownDetection<S> getCooldownDetection() {
		CommandStructure<S> current = this;

		while (current != null) {
			if (current.detection != null)
				return current.detection;

			current = current.parent;
		}

		return null;
	}

	public static <T> CommandStructure<T> create(@Nonnull Class<T> executorClass, @Nonnull PermissionChecker<T> perm,
			@Nonnull CommandExecutor<T> executor) {

		Checkers.nonNull(executorClass, "Executor class");
		Checkers.nonNull(perm, "Permission Checker");
		Checkers.nonNull(executor, "Command Executor");

		CommandStructure<T> structure = new CommandStructure<>(null, executor);
		structure.permissionChecker = perm;
		structure.senderClass = executorClass;
		return structure;
	}

	public PermissionChecker<S> getPermissionChecker() {
		return root.permissionChecker;
	}

	public SelectorCommandStructure<S> selector(Selector selector, CommandExecutor<S> ex) {
		SelectorCommandStructure<S> sub = new SelectorCommandStructure<>(this, selector, ex, null);
		selectors.put(sub.getSelector(), sub);
		return sub;
	}

	public SelectorCommandStructure<S> selector(Selector selector, CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {
		SelectorCommandStructure<S> sub = new SelectorCommandStructure<>(this, selector, ex, tabEx);
		selectors.put(sub.getSelector(), sub);
		return sub;
	}

	public CommandStructure<S> fallback(CommandExecutor<S> ex) {
		fallback = ex;
		return this;
	}

	public CommandExecutor<S> getFallback() {
		return fallback;
	}

	public CommandExecutor<S> getExecutor() {
		return executor;
	}

	public CommandStructure<S> setExecutor(CommandExecutor<S> executor) {
		this.executor = executor;
		return this;
	}

	public ArgumentCommandStructure<S> argument(String argument, CommandExecutor<S> ex, String... aliases) {
		return argument(argument, 0, ex, aliases);
	}

	public ArgumentCommandStructure<S> argument(String argument, int length, CommandExecutor<S> ex, String... aliases) {
		ArgumentCommandStructure<S> sub = new ArgumentCommandStructure<>(this, argument, length, ex, null, aliases);
		arguments.add(sub);
		return sub;
	}

	public ArgumentCommandStructure<S> argument(String argument, CommandExecutor<S> ex, CommandTabExecutor<S> tab,
			String... aliases) {
		return argument(argument, 0, ex, tab, aliases);
	}

	public ArgumentCommandStructure<S> argument(String argument, int length, CommandExecutor<S> ex,
			CommandTabExecutor<S> tab, String... aliases) {

		ArgumentCommandStructure<S> sub = new ArgumentCommandStructure<>(this, argument, length, ex, tab, aliases);
		arguments.add(sub);
		return sub;
	}

	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, CommandExecutor<S> ex) {
		return callableArgument(future, 0, ex);
	}

	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, int length,
			CommandExecutor<S> ex) {

		CallableArgumentCommandStructure<S> sub = new CallableArgumentCommandStructure<>(
				this, length, ex, null, future);

		arguments.add(sub);
		return sub;
	}

	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, CommandExecutor<S> ex,
			CommandTabExecutor<S> tabEx) {
		return callableArgument(future, 0, ex, tabEx);
	}

	public CallableArgumentCommandStructure<S> callableArgument(CallableArgument<S> future, int length,
			CommandExecutor<S> ex, CommandTabExecutor<S> tabEx) {

		CallableArgumentCommandStructure<S> sub = new CallableArgumentCommandStructure<>(
				this, length, ex, tabEx, future);

		arguments.add(sub);
		return sub;
	}

	public CommandStructure<S> priority(int level) {
		priority = level;
		return this;
	}

	public int getPriority() {
		return priority;
	}

	public CommandStructure<S> permission(String permission) {
		this.permission = permission;
		return this;
	}

	public String getPermission() {
		CommandStructure<S> current = this;

		while (current != null) {
			if (current.permission != null)
				return current.permission;

			current = current.parent;
		}

		return null;
	}

	public CommandStructure<S> first() {
		return root;
	}

	public CommandStructure<S> firstParent() {
		return root;
	}

	public CommandStructure<S> getParent() {
		return parent;
	}

	public CommandStructure<S> parent() {
		return parent;
	}

	public CommandStructure<S> getParent(int jumps) {
		return parent(jumps);
	}

	public CommandStructure<S> parent(int jumps) {
		CommandStructure<S> current = this;

		while (jumps-- > 0 && current.parent != null)
			current = current.parent;

		return current;
	}

	public Collection<String> tabList(S sender, CommandStructure<S> structure, String[] arguments) {
		return Collections.emptyList();
	}

	public Class<S> getSenderClass() {
		return root.senderClass;
	}

	public CommandHolder<S> build() {
		return new CommandHolder<>(root);
	}

	@Override
	public String toString() {
		return getClass().getCanonicalName() + ":" + tabList(null, null, null);
	}

	public final Object[] findStructure(S sender, String arg, String[] args, int currentDepth, boolean tablist) {
		List<CommandStructure<S>> result = new ArrayList<>(4);
		boolean noPerms = findStructures(sender, arg, args, currentDepth, tablist, result);
		return new Object[] { result, Boolean.valueOf(noPerms) };
	}

	@SuppressWarnings("unchecked")
	public final boolean findStructures(S sender, String arg, String[] args, int currentDepth, boolean tablist,
			List<CommandStructure<S>> result) {

		PermissionChecker<S> checker = root.permissionChecker;

		boolean denied = false;
		int highestDenied = Integer.MIN_VALUE;
		int highestAllowed = Integer.MIN_VALUE;

		for (ArgumentCommandStructure<S> sub : arguments) {
			String permission = sub.getPermission();
			boolean allowed = permission == null || checker.has(sender, permission, tablist);

			/*
			 * Callable argument se bez permission nevyhodnocuje.
			 * Může obsahovat drahý/dynamický callback.
			 */
			if (!allowed && sub instanceof CallableArgumentCommandStructure)
				continue;

			Collection<String> values = sub.getArgs(sender, sub, args);

			if (!contains(sub, values, arg))
				continue;

			if (!allowed) {
				denied = true;

				if (sub.priority > highestDenied)
					highestDenied = sub.priority;

				continue;
			}

			if (sub.priority > highestAllowed)
				highestAllowed = sub.priority;

			insertByPriority(result, sub);
		}

		for (SelectorCommandStructure<S> sub : selectors.values()) {
			if (!API.selectorUtils.check(sender, sub.getSelector(), arg))
				continue;

			String permission = sub.getPermission();

			if (permission != null && !checker.has(sender, permission, tablist)) {
				denied = true;

				if (sub.priority > highestDenied)
					highestDenied = sub.priority;

				continue;
			}

			if (sub.priority > highestAllowed)
				highestAllowed = sub.priority;

			insertByPriority(result, sub);
		}

		return denied && (result.isEmpty() || highestDenied > highestAllowed);
	}

	@SuppressWarnings("unchecked")
	public final void findFirstStructure(S sender, String arg, String[] args, int currentDepth, boolean tablist,
			LookupResult<S> result) {

		result.reset();

		PermissionChecker<S> checker = root.permissionChecker;

		CommandStructure<S> best = null;
		int bestPriority = Integer.MIN_VALUE;

		boolean denied = false;
		int deniedPriority = Integer.MIN_VALUE;

		for (ArgumentCommandStructure<S> sub : arguments) {
			String permission = sub.getPermission();
			boolean allowed = permission == null || checker.has(sender, permission, tablist);

			if (!allowed && sub instanceof CallableArgumentCommandStructure)
				continue;

			Collection<String> values = sub.getArgs(sender, sub, args);

			if (!contains(sub, values, arg))
				continue;

			if (!allowed) {
				denied = true;

				if (sub.priority > deniedPriority)
					deniedPriority = sub.priority;

				continue;
			}

			if (best == null || sub.priority > bestPriority) {
				best = sub;
				bestPriority = sub.priority;
			}
		}

		for (SelectorCommandStructure<S> sub : selectors.values()) {
			if (!API.selectorUtils.check(sender, sub.getSelector(), arg))
				continue;

			String permission = sub.getPermission();

			if (permission != null && !checker.has(sender, permission, tablist)) {
				denied = true;

				if (sub.priority > deniedPriority)
					deniedPriority = sub.priority;

				continue;
			}

			if (best == null || sub.priority > bestPriority) {
				best = sub;
				bestPriority = sub.priority;
			}
		}

		result.structure = best;
		result.noPermission = denied && (best == null || deniedPriority > bestPriority);
	}

	public final boolean hasChildStructures() {
		return !arguments.isEmpty() || !selectors.isEmpty();
	}

	public final List<CommandStructure<S>> getNextStructures(S sender) {
		if (!hasChildStructures())
			return Collections.emptyList();

		List<CommandStructure<S>> result = new ArrayList<>(arguments.size() + selectors.size());
		getNextStructures(sender, result);
		return result;
	}

	public final void getNextStructures(S sender, List<CommandStructure<S>> result) {
		PermissionChecker<S> checker = root.permissionChecker;

		for (ArgumentCommandStructure<S> sub : arguments) {
			String permission = sub.getPermission();

			if (permission == null || checker.has(sender, permission, true))
				insertByPriority(result, sub);
		}

		for (SelectorCommandStructure<S> sub : selectors.values()) {
			String permission = sub.getPermission();

			if (permission == null || checker.has(sender, permission, true))
				insertByPriority(result, sub);
		}
	}

	private static <T> void insertByPriority(List<CommandStructure<T>> result, CommandStructure<T> structure) {
		int priority = structure.priority;
		int pos = result.size();

		while (pos > 0 && result.get(pos - 1).priority < priority)
			--pos;

		result.add(pos, structure);
	}

	public static boolean contains(ArgumentCommandStructure<?> sub, Collection<String> list, String arg) {
		if (list == null)
			return false;

		if (!(sub instanceof CallableArgumentCommandStructure) && list.isEmpty())
			return true;

		for (String value : list)
			if (value != null && value.equalsIgnoreCase(arg))
				return true;

		return false;
	}
}