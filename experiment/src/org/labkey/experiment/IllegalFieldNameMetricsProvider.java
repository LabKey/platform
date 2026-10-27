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

import org.labkey.api.usageMetrics.DescribedUsageMetricsProvider;
import org.labkey.api.usageMetrics.UsageMetrics;

import java.util.Map;

public class IllegalFieldNameMetricsProvider implements DescribedUsageMetricsProvider
{
    public static final String METRIC_KEY = "illegalFieldNames";

    private final static IllegalFieldNameMetricsProvider _instance = new IllegalFieldNameMetricsProvider();
    private volatile Map<String, Object> _metrics = Map.of("Run time", "Not run yet.");

    private IllegalFieldNameMetricsProvider()
    {
    }

    public static IllegalFieldNameMetricsProvider getInstance()
    {
        return _instance;
    }

    @Override
    public UsageMetrics addUsageMetrics(UsageMetrics metrics)
    {
        metrics.group(METRIC_KEY, "Stored domains whose field names break a current naming rule, as of the last system maintenance run since startup. " +
            "Each rule maps to a count per domain kind. These configurations predate the rule that now forbids them, so they are not blocked at save time. " +
            "parentAliasConflict counts affected sample types and data classes rather than individual conflicts, because parent alias validation stops at the first problem")
            .putAll(_metrics);
        return metrics;
    }

    public void updateMetrics(Map<String, Object> metrics)
    {
        _metrics = metrics;
    }
}
