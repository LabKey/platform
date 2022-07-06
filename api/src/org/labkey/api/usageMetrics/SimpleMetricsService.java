package org.labkey.api.usageMetrics;

import org.labkey.api.services.ServiceRegistry;

public interface SimpleMetricsService
{
    static SimpleMetricsService get()
    {
        return ServiceRegistry.get().getService(SimpleMetricsService.class);
    }
    static void setInstance(SimpleMetricsService impl)
    {
        ServiceRegistry.get().registerService(SimpleMetricsService.class, impl);
    }

    long increment(String moduleName, String featureArea, String metricName);
}
