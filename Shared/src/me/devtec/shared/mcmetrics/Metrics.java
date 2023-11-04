package me.devtec.shared.mcmetrics;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import me.devtec.shared.Ref;
import me.devtec.shared.Ref.ServerType;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Tasker;

public class Metrics {

	public static GatheringInfoManager gatheringInfoManager;
	public static final String METRICS_VERSION = "3.0.3-SNAPSHOT";

	private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";

	// The uuid of the server
	private static final String serverUUID;
	static {
		Config c = new Config("plugins/bStats/config.yml");
		serverUUID = c.getString("serverUuid", UUID.randomUUID().toString());
		c.set("serverUuid", serverUUID).save(DataType.YAML);
	}

	// The plugin
	private final String pluginVersion;
	private final String platform;
	private final Set<CustomChart> customCharts = new HashSet<>();

	// The plugin id
	private final int pluginId;

	public Metrics(String pluginVersion, int pluginId) {
		this.pluginVersion = pluginVersion;
		this.pluginId = pluginId;
		platform = Ref.serverType().isBukkit() ? "bukkit" : Ref.serverType() == ServerType.BUNGEECORD ? "bungeecord" : Ref.serverType() == ServerType.VELOCITY ? "velocity" : "bukkit";
		long initialDelay = (long) (20 * 60 * (3 + Math.random() * 3));
		long secondDelay = (long) (20 * 60 * (Math.random() * 30));
		new Tasker() {

			@Override
			public void run() {
				submitData();
			}
		}.runRepeating(initialDelay + secondDelay, 20 * 60 * 30);
	}

	public void addCustomChart(CustomChart chart) {
		customCharts.add(chart);
	}

	private Map<String, Object> getServerData() {
		// platform
		Map<String, Object> data = new HashMap<>();
		if (platform.equals("bukkit")) {
			data.put("playerAmount", gatheringInfoManager.getPlayers());
			data.put("onlineMode", gatheringInfoManager.getOnlineMode());
			data.put("bukkitVersion", gatheringInfoManager.getServerVersion());
			data.put("bukkitName", gatheringInfoManager.getServerName());
		} else if (platform.equals("bungeecord")) {
			data.put("playerAmount", gatheringInfoManager.getPlayers());
			data.put("managedServers", gatheringInfoManager.getManagedServers());
			data.put("onlineMode", gatheringInfoManager.getOnlineMode());
			data.put("bungeecordVersion", gatheringInfoManager.getServerVersion());
			data.put("bungeecordName", gatheringInfoManager.getServerName());
		} else if (platform.equals("velocity")) {
			data.put("playerAmount", gatheringInfoManager.getPlayers());
			data.put("managedServers", gatheringInfoManager.getManagedServers());
			data.put("onlineMode", gatheringInfoManager.getOnlineMode());
			data.put("velocityVersionVersion", gatheringInfoManager.getServerVersion());
			data.put("velocityVersionName", gatheringInfoManager.getServerName());
			data.put("velocityVersionVendor", gatheringInfoManager.getServerVersionVendor());
		}
		data.put("javaVersion", System.getProperty("java.version"));
		data.put("osName", System.getProperty("os.name"));
		data.put("osArch", System.getProperty("os.arch"));
		data.put("osVersion", System.getProperty("os.version"));
		data.put("coreCount", Runtime.getRuntime().availableProcessors());

		// service
		Map<String, Object> pluginData = new HashMap<>();
		pluginData.put("pluginVersion", pluginVersion);
		pluginData.put("id", pluginId);
		Map<String, Object>[] chartData = customCharts.stream().map(CustomChart::getRequestJsonObject).filter(t -> t != null).toArray(new IntFunction<Map<String, Object>[]>() {

			@SuppressWarnings("unchecked")
			@Override
			public Map<String, Object>[] apply(int value) {
				return new HashMap[value];
			}
		});
		pluginData.put("customCharts", chartData);

		data.put("service", pluginData);
		data.put("serverUUID", Metrics.serverUUID);
		data.put("metricsVersion", METRICS_VERSION);
		return data;
	}

	private void submitData() {
		try {
			String url = String.format(REPORT_URL, platform);
			HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();

			// Compress the data to save bandwidth
			byte[] compressedData = compress(Json.writer().simpleWrite(getServerData()));

			connection.setRequestMethod("POST");
			connection.addRequestProperty("Accept", "application/json");
			connection.addRequestProperty("Connection", "close");
			connection.addRequestProperty("Content-Encoding", "gzip");
			connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("User-Agent", "Metrics-Service/1");

			connection.setDoOutput(false);
			try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
				outputStream.write(compressedData);
			}
		} catch (Exception e) {
		}
	}

	/**
	 * Gzips the given string.
	 *
	 * @param str The string to gzip.
	 * @return The gzipped string.
	 */
	private static byte[] compress(final String str) throws IOException {
		if (str == null)
			return null;
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
			gzip.write(str.getBytes(StandardCharsets.UTF_8));
		}
		return outputStream.toByteArray();
	}
}