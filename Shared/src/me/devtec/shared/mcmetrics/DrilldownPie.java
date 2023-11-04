package me.devtec.shared.mcmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class DrilldownPie extends CustomChart {

	private final Callable<Map<String, Map<String, Integer>>> callable;

	/**
	 * Class constructor.
	 *
	 * @param chartId  The id of the chart.
	 * @param callable The callable which is used to request the chart data.
	 */
	public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
		super(chartId);
		this.callable = callable;
	}

	@Override
	public Map<String, Object> getChartData() throws Exception {
		Map<String, Object> valuesBuilder = new HashMap<>();

		Map<String, Map<String, Integer>> map = callable.call();
		if (map == null || map.isEmpty())
			// Null = skip the chart
			return null;
		boolean reallyAllSkipped = true;
		for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
			Map<String, Object> valueBuilder = new HashMap<>();
			boolean allSkipped = true;
			for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
				valueBuilder.put(valueEntry.getKey(), valueEntry.getValue());
				allSkipped = false;
			}
			if (!allSkipped) {
				reallyAllSkipped = false;
				valuesBuilder.put(entryValues.getKey(), valueBuilder);
			}
		}
		if (reallyAllSkipped)
			// Null = skip the chart
			return null;

		Map<String, Object> obj = new HashMap<>();
		obj.put("values", valuesBuilder);
		return obj;
	}
}