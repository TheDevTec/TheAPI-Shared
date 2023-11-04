package me.devtec.shared.mcmetrics;

public interface GatheringInfoManager {

	public int getPlayers();

	public int getOnlineMode();

	public String getServerVersion();

	public String getServerName();

	// BungeeCord & Velocity
	public int getManagedServers();

	// Velocity
	public String getServerVersionVendor();
}
