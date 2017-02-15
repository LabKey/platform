package org.labkey.api.usageMetrics;

import java.util.Map;

/**
 * Created by Tony on 2/14/2017.
 *
 * Service for modules to register their own set of metrics to include in mothership usage reports
 */
public interface UsageMetricsService
{
    String ERRORS = "ModuleUsageReportingErrors";

    void registerUsageMetrics(String moduleName, UsageMetricsProvider metrics);

    Map<String, Map<String, Object>> getModuleUsageMetrics();
}
