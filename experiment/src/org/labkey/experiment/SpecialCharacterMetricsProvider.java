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

public class SpecialCharacterMetricsProvider implements DescribedUsageMetricsProvider
{
    public static final String METRIC_KEY = "specialCharacterFields";

    private final static SpecialCharacterMetricsProvider _instance = new SpecialCharacterMetricsProvider();
    private volatile Map<String, Object> _metrics = Map.of("Run time", "Not run yet.");

    private SpecialCharacterMetricsProvider()
    {
    }

    public static SpecialCharacterMetricsProvider getInstance()
    {
        return _instance;
    }

    @Override
    public void addUsageMetrics(UsageMetrics metrics)
    {
        metrics.group(METRIC_KEY, "Semicolons, commas, newlines, and double quotes as of the last system maintenance run since startup. " +
            "TextChoice/MVTC: choice fields with such a choice; Text/MultiLine: columns with such a value; dataName: sample type and data class tables with such a name; objectStringValue: non-provisioned properties with such a value")
            .putAll(_metrics);
    }

    public void updateMetrics(Map<String, Object> metrics)
    {
        _metrics = metrics;
    }
}