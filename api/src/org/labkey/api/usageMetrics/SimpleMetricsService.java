package org.labkey.api.usageMetrics;

import org.labkey.api.services.ServiceRegistry;

public interface SimpleMetricsService
{
    static SimpleMetricsService get()
    {
        SimpleMetricsService result = ServiceRegistry.get().getService(SimpleMetricsService.class);
        if (result == null)
        {
            // Return a no-op implementation if the real service hasn't been registered yet
            result = (moduleName, featureArea, metricName) -> 0;
        }
        return result;
    }
    static void setInstance(SimpleMetricsService impl)
    {
        ServiceRegistry.get().registerService(SimpleMetricsService.class, impl);
    }

    long increment(String moduleName, String featureArea, String metricName);
}
