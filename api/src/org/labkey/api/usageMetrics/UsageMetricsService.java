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

import java.util.Map;

/**
 * Created by Tony on 2/14/2017.
 *
 * Service for modules to register their own set of metrics to include in mothership usage reports
 * Example usage from DataIntegrationModule.doStartup():
 *         UsageMetricsService svc = UsageMetricsService.get();
 *         if (null != svc)
 *         {
 *             svc.registerUsageMetrics(DataIntegrationModule.NAME, () -> {
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
    @Nullable Map<String, Map<String, Object>> getModuleUsageMetrics();

    /**
     *  Method to register a module's metrics. Call from doStartup() / startupAfterSpringConfiguration()
     *  Metrics can be included at UsageReportingLevel OFF or ON. Usage metrics will only be sent for ON.
     *
     * @param moduleName The name of the module
     * @param metrics Implementation of the functional interface UsageMetricsProvider.getUsageMetrics() method. Typically,
     *                this will return a map of metric name -> value pairs.
     * @throws IllegalArgumentException if moduleName doesn't correspond to an existing module
     */
    void registerUsageMetrics(String moduleName, UsageMetricsProvider metrics);
}
