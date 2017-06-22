/*
 * Copyright (c) 2017 LabKey Corporation
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
