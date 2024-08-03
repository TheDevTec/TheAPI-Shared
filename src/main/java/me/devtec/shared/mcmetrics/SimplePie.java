package me.devtec.shared.mcmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class SimplePie extends CustomChart {

	private final Callable<String> callable;

	/**
	 * Class constructor.
	 *
	 * @param chartId  The id of the chart.
	 * @param callable The callable which is used to request the chart data.
	 */
	public SimplePie(String chartId, Callable<String> callable) {
		super(chartId);
		this.callable = callable;
	}

	@Override
	protected Map<String, Object> getChartData() throws Exception {
		String value = callable.call();
		if (value == null || value.isEmpty())
			// Null = skip the chart
			return null;
		Map<String, Object> obj = new HashMap<>();
		obj.put("value", value);
		return obj;
	}
}