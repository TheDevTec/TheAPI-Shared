package me.devtec.shared.mcmetrics;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface GatheringInfoManager {

	public Consumer<String> getInfoLogger();

	public BiConsumer<String, Throwable> getErrorLogger();

	public int getPlayers();

	public int getOnlineMode();

	public String getServerVersion();

	public String getServerName();

	// BungeeCord & Velocity
	public int getManagedServers();

	// Velocity
	public String getServerVersionVendor();
}
