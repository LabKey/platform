package org.labkey.core.admin.usageMetrics;

import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.usageMetrics.UsageMetricsService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by Tony on 2/14/2017.
 */
public class UsageMetricsServiceImpl implements UsageMetricsService
{
    private final Map<String, UsageMetricsProvider> moduleUsageReports = new ConcurrentSkipListMap<>();

    @Override
    public void registerUsageMetrics(String moduleName, UsageMetricsProvider metrics)
    {
        moduleUsageReports.put(moduleName, metrics);
    }

    @Override
    public Map<String, Map<String, Object>> getModuleUsageMetrics()
    {
        Map<String, Map<String, Object>> allModulesMetrics = new HashMap<>();
        moduleUsageReports.forEach((moduleName, provider) ->
        {
            try
            {
                allModulesMetrics.put(moduleName, provider.getUsageMetrics());
            }
            catch (Exception e)
            {
                Map<String, Object> errors = allModulesMetrics.computeIfAbsent(ERRORS , k -> new HashMap<>());
                errors.put(moduleName, e.getMessage());
            }
        });

        return allModulesMetrics;
    }
}
