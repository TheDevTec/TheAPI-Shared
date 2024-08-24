package me.devtec.shared.commands.manager;

import me.devtec.shared.commands.holder.CommandHolder;

public interface CommandsRegister {

	void register(CommandHolder<?> commandHolder, String command, String[] aliases);

	void unregister(CommandHolder<?> commandHolder);

}
