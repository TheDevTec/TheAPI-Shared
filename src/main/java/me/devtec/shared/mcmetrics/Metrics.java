package me.devtec.shared.mcmetrics;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import me.devtec.shared.Ref;
import me.devtec.shared.Ref.ServerType;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class Metrics {

	public static GatheringInfoManager gatheringInfoManager;
	public static final String METRICS_VERSION = "3.0.2";

	private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";

	// The uuid of the server
	private static final String serverUUID;
	private static final boolean enabled;
	private static final boolean logErrors;
	private static final boolean logSentData;
	private static final boolean logResponseStatusText;
	static {
		Config config = new Config("plugins/bStats/config.yml");
		if (!config.existsKey("serverUuid")) {
			config.setIfAbsent("enabled", true);
			config.setIfAbsent("serverUuid", UUID.randomUUID().toString());
			config.setIfAbsent("logFailedRequests", false);
			config.setIfAbsent("logSentData", false);
			config.setIfAbsent("logResponseStatusText", false);

			// Inform the server owners about bStats
			config.setHeader(Arrays.asList("# bStats (https://bStats.org) collects some basic information for plugin authors, like how",
					"# many people use their plugin and their total player count. It's recommended to keep bStats",
					"# enabled, but if you're not comfortable with this, you can turn this setting off. There is no",
					"# performance penalty associated with having metrics enabled, and data sent to bStats is fully", "# anonymous."));
			config.save("yaml");
		}
		serverUUID = config.getString("serverUuid");

		// Load the data
		enabled = config.getBoolean("enabled", true);
		logErrors = config.getBoolean("logFailedRequests", false);
		logSentData = config.getBoolean("logSentData", false);
		logResponseStatusText = config.getBoolean("logResponseStatusText", false);

	}

	// The plugin
	private final String pluginVersion;
	private final String platform;
	private final Set<CustomChart> customCharts = new HashSet<>();

	// The plugin id
	private final int pluginId;
	private int taskId;

	public Metrics(String pluginVersion, int pluginId) {
		this.pluginVersion = pluginVersion;
		this.pluginId = pluginId;
		if (Ref.serverType().isBukkit())
			platform = "bukkit";
		else if (Ref.serverType() == ServerType.BUNGEECORD)
			platform = "bungeecord";
		else if (Ref.serverType() == ServerType.VELOCITY)
			platform = "velocity";
		else {
			platform = "uknown";
			return;
		}
		long initialDelay = (long) (20 * 60 * (3 + Math.random() * 3));
		long secondDelay = (long) (20 * 60 * (Math.random() * 30));
		if (enabled)
			taskId = new Tasker() {

				@Override
				public void run() {
					submitData();
				}
			}.runRepeating(initialDelay + secondDelay, 20 * 60 * 30);
	}

	public void shutdown() {
		if (taskId == 0)
			return;
		Scheduler.cancelTask(taskId);
		taskId = 0;
	}

	public void addCustomChart(CustomChart chart) {
		customCharts.add(chart);
	}

	private Map<String, Object> getServerData() {
		// platform
		Map<String, Object> data = new HashMap<>();
        switch (platform) {
            case "bukkit":
                data.put("playerAmount", gatheringInfoManager.getPlayers());
                data.put("onlineMode", gatheringInfoManager.getOnlineMode());
                data.put("bukkitVersion", gatheringInfoManager.getServerVersion());
                data.put("bukkitName", gatheringInfoManager.getServerName());
                break;
            case "bungeecord":
                data.put("playerAmount", gatheringInfoManager.getPlayers());
                data.put("managedServers", gatheringInfoManager.getManagedServers());
                data.put("onlineMode", gatheringInfoManager.getOnlineMode());
                data.put("bungeecordVersion", gatheringInfoManager.getServerVersion());
                data.put("bungeecordName", gatheringInfoManager.getServerName());
                break;
            case "velocity":
                data.put("playerAmount", gatheringInfoManager.getPlayers());
                data.put("managedServers", gatheringInfoManager.getManagedServers());
                data.put("onlineMode", gatheringInfoManager.getOnlineMode());
                data.put("velocityVersionVersion", gatheringInfoManager.getServerVersion());
                data.put("velocityVersionName", gatheringInfoManager.getServerName());
                data.put("velocityVersionVendor", gatheringInfoManager.getServerVersionVendor());
                break;
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
		Map<String, Object>[] chartData = customCharts.stream().map(CustomChart::getRequestJsonObject).filter(Objects::nonNull).toArray((IntFunction<Map<String, Object>[]>) HashMap[]::new);
		pluginData.put("customCharts", chartData);

		data.put("service", pluginData);
		data.put("serverUUID", serverUUID);
		data.put("metricsVersion", METRICS_VERSION);
		return data;
	}

	private void submitData() {
		try {

			String data = Json.writer().simpleWrite(getServerData());
			// Compress the data to save bandwidth
			if (logSentData)
				gatheringInfoManager.getInfoLogger().accept("Sent bStats metrics data: " + data);

			String url = String.format(REPORT_URL, platform);
			HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();

			// Compress the data to save bandwidth
			byte[] compressedData = compress(data);

			connection.setRequestMethod("POST");
			connection.addRequestProperty("Accept", "application/json");
			connection.addRequestProperty("Connection", "close");
			connection.addRequestProperty("Content-Encoding", "gzip");
			connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("User-Agent", "Metrics-Service/1");

			connection.setDoOutput(true);
			try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
				outputStream.write(compressedData);
			}
			StringBuilder builder = new StringBuilder();
			try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String line;
				while ((line = bufferedReader.readLine()) != null)
					builder.append(line);
			}

			if (logResponseStatusText)
				gatheringInfoManager.getInfoLogger().accept("Sent data to bStats and received response: " + builder);
		} catch (Exception e) {
			if (logErrors)
				gatheringInfoManager.getErrorLogger().accept("Could not submit bStats metrics data", e);
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