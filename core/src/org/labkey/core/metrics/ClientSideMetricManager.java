package org.labkey.core.metrics;

import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.UsageReportingLevel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientSideMetricManager
{
    private static final ClientSideMetricManager instance = new ClientSideMetricManager();
    private static final Map<String, Integer> METRIC_COUNTS = new HashMap<>();

    public static ClientSideMetricManager get()
    {
        return instance;
    }

    public Integer increment(String metricName)
    {
        if (!METRIC_COUNTS.containsKey(metricName))
            METRIC_COUNTS.put(metricName, 0);

        Integer count = METRIC_COUNTS.get(metricName) + 1;
        METRIC_COUNTS.put(metricName, count);
        return count;
    }

    public void registerUsageMetrics(String moduleName)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(UsageReportingLevel.MEDIUM, moduleName, () -> Collections.singletonMap("clientSideMetricCounts", METRIC_COUNTS));
        }
    }
}
