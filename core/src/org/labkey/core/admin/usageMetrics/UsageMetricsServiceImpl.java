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
package org.labkey.core.admin.usageMetrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ExceptionUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by Tony on 2/14/2017.
 */
public class UsageMetricsServiceImpl implements UsageMetricsService
{
    private static final Logger LOG = LogManager.getLogger(UsageMetricsServiceImpl.class);

    private final Map<String, Set<UsageMetricsProvider>> moduleUsageReports = new ConcurrentHashMap<>();

    @Override
    public void registerUsageMetrics(String moduleName, UsageMetricsProvider metrics)
    {
        moduleUsageReports.computeIfAbsent(moduleName, k -> new ConcurrentHashSet<>()).add(metrics);
    }

    @Override
    @Nullable
    public Map<String, Map<String, Object>> getModuleUsageMetrics()
    {
        Map<String, Map<String, Object>> allModulesMetrics = new HashMap<>();
        moduleUsageReports.forEach((moduleName, providers) ->
        {
            try
            {
                Map<String, Object> moduleMetrics = allModulesMetrics.computeIfAbsent(moduleName, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                providers.forEach(provider ->
                {
                    Map<String, Object> providerMetrics = provider.getUsageMetrics();
                    Set<String> duplicateKeys = providerMetrics.keySet()
                            .stream()
                            .filter(moduleMetrics::containsKey)
                            .collect(Collectors.toSet());
                    if (duplicateKeys.isEmpty())
                        moduleMetrics.putAll(providerMetrics);
                    else
                    {
                        String message = (moduleName + " module has duplicate metric names registered by multiple UsageMetricProviders. Duplicate names: " + duplicateKeys);
                        LOG.error(message);
                    }
                });
            }
            catch (Exception e)
            {
                allModulesMetrics.computeIfAbsent(ERRORS, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).put(moduleName, e.getMessage());
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        });

        return allModulesMetrics;
    }
}
