/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import org.labkey.api.usageMetrics.UsageMetrics;
import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.util.Map;

public class FileLinkMetricsProvider implements UsageMetricsProvider
{
    private final static FileLinkMetricsProvider _instance = new FileLinkMetricsProvider();
    private volatile Map<String, Object> _metrics = Map.of("Run time", "Not run yet.");

    private FileLinkMetricsProvider()
    {
    }

    public static FileLinkMetricsProvider getInstance()
    {
        return _instance;
    }

    @Override
    public UsageMetrics addUsageMetrics(UsageMetrics metrics)
    {
        metrics.group(FileLinkMetricsMaintenanceTask.NAME, "File field files as of the last system maintenance run since startup: run time, missing files, and valid files by schema (assay batch and run files under exp)")
            .putAll(_metrics);
        return metrics;
    }

    public void updateMetrics(Map<String, Object> metrics)
    {
        _metrics = metrics;
    }
}
