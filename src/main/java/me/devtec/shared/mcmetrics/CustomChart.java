package me.devtec.shared.mcmetrics;

import java.util.HashMap;
import java.util.Map;

public abstract class CustomChart {

	private final String chartId;

	protected CustomChart(String chartId) {
		if (chartId == null)
			throw new IllegalArgumentException("chartId must not be null");
		this.chartId = chartId;
	}

	public Map<String, Object> getRequestJsonObject() {
		Map<String, Object> builder = new HashMap<>();
		builder.put("chartId", chartId);
		try {
			Map<String, Object> data = getChartData();
			if (data == null)
				// If the data is null we don't send the chart.
				return null;
			builder.put("data", data);
		} catch (Throwable t) {
			return null;
		}
		return builder;
	}

	protected abstract Map<String, Object> getChartData() throws Exception;

}