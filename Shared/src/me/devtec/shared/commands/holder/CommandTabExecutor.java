package me.devtec.shared.commands.holder;

import java.util.List;

import me.devtec.shared.commands.structures.CommandStructure;

public interface CommandTabExecutor<S> {
	public List<String> execute(S sender, CommandStructure<S> structure, String[] args);
}
