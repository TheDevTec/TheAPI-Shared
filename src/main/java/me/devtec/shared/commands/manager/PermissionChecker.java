package me.devtec.shared.commands.manager;

public interface PermissionChecker<S> {
	boolean has(S sender, String perm, boolean isTablist);
}
