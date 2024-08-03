package me.devtec.shared.mcmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class AdvancedBarChart extends CustomChart {

	private final Callable<Map<String, int[]>> callable;

	/**
	 * Class constructor.
	 *
	 * @param chartId  The id of the chart.
	 * @param callable The callable which is used to request the chart data.
	 */
	public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
		super(chartId);
		this.callable = callable;
	}

	@Override
	protected Map<String, Object> getChartData() throws Exception {
		Map<String, Object> valuesBuilder = new HashMap<>();
		Map<String, int[]> map = callable.call();
		if (map == null || map.isEmpty())
			// Null = skip the chart
			return null;
		boolean allSkipped = true;
		for (Map.Entry<String, int[]> entry : map.entrySet()) {
			if (entry.getValue().length == 0)
				continue; // Skip this invalid
			allSkipped = false;
			valuesBuilder.put(entry.getKey(), entry.getValue());
		}
		if (allSkipped)
			// Null = skip the chart
			return null;

		Map<String, Object> obj = new HashMap<>();
		obj.put("values", valuesBuilder);
		return obj;
	}
}