package org.labkey.core.metrics;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.usageMetrics.ClientSideMetricService;
import org.labkey.api.usageMetrics.UsageMetricsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSideMetricServiceImpl implements ClientSideMetricService
{
    private static final Map<String, Map<String, AtomicInteger>> FEATURE_AREA_METRIC_COUNTS = new ConcurrentHashMap<>();

    static void setInstance(ClientSideMetricService impl)
    {
        ServiceRegistry.get().registerService(ClientSideMetricService.class, impl);
    }

    @Override
    public int increment(String featureArea, String metricName)
    {
        FEATURE_AREA_METRIC_COUNTS.computeIfAbsent(featureArea, k -> new HashMap<>());
        return FEATURE_AREA_METRIC_COUNTS.get(featureArea).computeIfAbsent(metricName, s -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public void registerUsageMetrics(String moduleName)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(moduleName, () -> Collections.singletonMap("clientSideMetricCounts", FEATURE_AREA_METRIC_COUNTS));
        }
    }
}
