/*
 * Copyright (c) 2017-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.usageMetrics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.UsageReportingLevel;

import java.util.Map;

/**
 * Created by Tony on 2/14/2017.
 *
 * Service for modules to register their own set of metrics to include in mothership usage reports
 * Example usage from DataIntegrationModule.doStartup():
 *         UsageMetricsService svc = UsageMetricsService.get();
 *         if (null != svc)
 *         {
 *             svc.registerUsageMetrics(UsageReportingLevel.MEDIUM, NAME, () -> {
 *                 Map<String, Object> metric = new HashMap<>();
 *                 metric.put("etlRunCount", new SqlSelector(DbSchema.get("dataintegration", DbSchemaType.Module), "SELECT COUNT(*) FROM dataintegration.TransformRun").getObject(Long.class));
 *                 return metric;
 *             });
 *         }
 */
public interface UsageMetricsService
{
    String ERRORS = "_ModuleUsageReportingErrors";

    static UsageMetricsService get()
    {
        return ServiceRegistry.get().getService(UsageMetricsService.class);
    }

    static void setInstance(UsageMetricsService impl)
    {
        ServiceRegistry.get().registerService(UsageMetricsService.class, impl);
    }

    /** @return map of module name to a map of metric names/values */
    @Nullable Map<String, Map<String, Object>> getModuleUsageMetrics(UsageReportingLevel level);

    /**
     *  Method to register a module's metrics. Call from doStartup() / startupAfterSpringConfiguration()
     *  Metrics can be included at UsageReportingLevel LOW or MEDIUM. Note that metrics reported at MEDIUM level
     *  are a superset of what is reported at LOW level.
     *  If a given module has one set of metrics which should be included at LOW level, and an additional set
     *  to report at MEDIUM level, call this method twice.
     *
     * @param level The UsageReportingLevel at which to include these metrics, LOW or MEDIUM.
     * @param moduleName The name of the module
     * @param metrics Implementation of the functional interface UsageMetricsProvider.getUsageMetrics() method. Typically
     *                this will return a map of metric name -> value pairs.
     */
    void registerUsageMetrics(UsageReportingLevel level, String moduleName, UsageMetricsProvider metrics);
}
