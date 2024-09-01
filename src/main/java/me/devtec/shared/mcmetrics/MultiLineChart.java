package me.devtec.shared.mcmetrics;

import java.util.Map;
import java.util.concurrent.Callable;

public class MultiLineChart extends CustomChart {

	private final Callable<Map<String, Integer>> callable;

	/**
	 * Class constructor.
	 *
	 * @param chartId  The id of the chart.
	 * @param callable The callable which is used to request the chart data.
	 */
	public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
		super(chartId);
		this.callable = callable;
	}

	@Override
	protected Map<String, Object> getChartData() throws Exception {
		return AdvancedPie.getStringObjectMap(callable);
	}

}
