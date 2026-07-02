/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches special-character field metrics (GitHub Issue 1086) computed asynchronously by
 * {@link SpecialCharacterMetricsMaintenanceTask}. Following the {@link FileLinkMetricsProvider} pattern, the expensive
 * scan runs on the System Maintenance schedule and stores its result here in memory; the daily usage-metrics
 * collection just reads the cached value. The cache reverts to "Not run yet." on server restart until the task runs.
 */
public class SpecialCharacterMetricsProvider implements UsageMetricsProvider
{
    public static final String METRIC_KEY = "specialCharacterFields";

    private final static SpecialCharacterMetricsProvider _instance = new SpecialCharacterMetricsProvider();
    private final Map<String, Object> _metrics;

    private SpecialCharacterMetricsProvider()
    {
        _metrics = new HashMap<>();
        Map<String, Object> initial = new HashMap<>();
        initial.put("Run time", "Not run yet.");
        _metrics.put(METRIC_KEY, initial);
    }

    public static SpecialCharacterMetricsProvider getInstance()
    {
        return _instance;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        return _metrics;
    }

    public void updateMetrics(Map<String, Object> metrics)
    {
        _metrics.putAll(metrics);
    }
}