package org.labkey.experiment;

import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.util.HashMap;
import java.util.Map;

public class FileLinkMetricsProvider implements UsageMetricsProvider
{
    private final static FileLinkMetricsProvider _instance = new FileLinkMetricsProvider();
    private final Map<String, Object> _metrics;

    private FileLinkMetricsProvider()
    {
        _metrics = new HashMap<>();
        Map<String, Object> missingFilesMetrics = new HashMap<>();
        missingFilesMetrics.put("Run time", "Not run yet.");
        _metrics.put(FileLinkMetricsMaintenanceTask.NAME, missingFilesMetrics);
    }

    public static FileLinkMetricsProvider getInstance()
    {
        return _instance;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        return _metrics;
    }

    public void updateMetrics(Map<String, Object> metrics)
    {
        _metrics.putAll(metrics);
    }
}
