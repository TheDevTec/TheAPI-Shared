package me.devtec.shared.mcmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class SingleLineChart extends CustomChart {

	private final Callable<Integer> callable;

	/**
	 * Class constructor.
	 *
	 * @param chartId  The id of the chart.
	 * @param callable The callable which is used to request the chart data.
	 */
	public SingleLineChart(String chartId, Callable<Integer> callable) {
		super(chartId);
		this.callable = callable;
	}

	@Override
	protected Map<String, Object> getChartData() throws Exception {
		int value = callable.call();
		if (value == 0)
			// Null = skip the chart
			return null;
		Map<String, Object> obj = new HashMap<>();
		obj.put("value", value);
		return obj;
	}

}