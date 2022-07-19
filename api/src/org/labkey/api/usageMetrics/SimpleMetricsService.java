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

    /**
     * Increment the persistent counter associated with the given module, feature area, and metric name combination.
     * The total will be reported to mothership in this module's "simpleMetricCounts" node. Isn't that simple?
     * @param moduleName Module name. Must match a currently deployed module's name, though we'll grudgingly accept
     *                   casing differences vs. the module's canonical name. But just use the module's name constant so
     *                   you don't have to worry about it.
     * @param featureArea Your name for the feature area. Needs to be unique within this module's simple metrics. By
     *                    convention, we typically use camel case with initial lowercase letter (like method names).
     * @param metricName Your name for the specific metric within this feature area. Same convention as above (camel
     *                   case with initial lowercase letter). These are all counts, so no need to include "count" in
     *                   the name.
     * @return new value for this counter
     */
    long increment(String moduleName, String featureArea, String metricName);
}
