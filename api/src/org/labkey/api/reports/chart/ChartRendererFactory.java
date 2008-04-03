package org.labkey.api.reports.chart;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 19, 2007
 */
public class ChartRendererFactory
{
    private static final ChartRendererFactory instance = new ChartRendererFactory();
    private static Map<String, ChartRenderer> _chartRenderers = new HashMap<String, ChartRenderer>();


    public static ChartRendererFactory get()
    {
        return instance;
    }

    private ChartRendererFactory(){}

    public synchronized void addChartRenderer(ChartRenderer renderer)
    {
        _chartRenderers.put(renderer.getType(), renderer);
    }

    public synchronized ChartRenderer getChartRenderer(String type)
    {
        return _chartRenderers.get(type);
    }

    public synchronized ChartRenderer[] getChartRenderers()
    {
        return _chartRenderers.values().toArray(new ChartRenderer[0]);
    }
}
