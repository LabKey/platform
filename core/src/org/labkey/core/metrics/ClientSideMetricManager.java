package org.labkey.core.metrics;

import org.labkey.api.usageMetrics.UsageMetricsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSideMetricManager
{
    private static final ClientSideMetricManager instance = new ClientSideMetricManager();
    private static final Map<String, Map<String, AtomicInteger>> FEATURE_AREA_METRIC_COUNTS = new ConcurrentHashMap<>();

    public static ClientSideMetricManager get()
    {
        return instance;
    }

    public int increment(String featureArea, String metricName)
    {
        FEATURE_AREA_METRIC_COUNTS.computeIfAbsent(featureArea, k -> new HashMap<>());
        return FEATURE_AREA_METRIC_COUNTS.get(featureArea).computeIfAbsent(metricName, s -> new AtomicInteger(0)).incrementAndGet();
    }

    public void registerUsageMetrics(String moduleName)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(moduleName, () -> Collections.singletonMap("clientSideMetricCounts", FEATURE_AREA_METRIC_COUNTS));
        }
    }
}
