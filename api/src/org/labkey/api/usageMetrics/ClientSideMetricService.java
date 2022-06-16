package org.labkey.api.usageMetrics;

import org.labkey.api.services.ServiceRegistry;

public interface ClientSideMetricService
{
    static ClientSideMetricService get()
    {
        return ServiceRegistry.get().getService(ClientSideMetricService.class);
    }
    static void setInstance(ClientSideMetricService impl)
    {
        ServiceRegistry.get().registerService(ClientSideMetricService.class, impl);
    }

    int increment(String featureArea, String metricName);

    void registerUsageMetrics(String moduleName);
}
