package me.devtec.shared.mcmetrics;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface GatheringInfoManager {

	Consumer<String> getInfoLogger();

	BiConsumer<String, Throwable> getErrorLogger();

	int getPlayers();

	int getOnlineMode();

	String getServerVersion();

	String getServerName();

	// BungeeCord & Velocity
    int getManagedServers();

	// Velocity
    String getServerVersionVendor();
}
