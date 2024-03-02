package me.devtec.shared.commands.holder;

import java.util.Collection;

import me.devtec.shared.commands.structures.CommandStructure;

public interface CommandTabExecutor<S> {
	public Collection<String> execute(S sender, CommandStructure<S> structure, String[] args);
}
