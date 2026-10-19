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
package org.labkey.api.usageMetrics;

import java.util.Map;

/** Reports metric values together with their descriptions. Prefer this over a plain {@link UsageMetricsProvider}. */
@FunctionalInterface
public interface DescribedUsageMetricsProvider extends UsageMetricsProvider
{
    void addUsageMetrics(UsageMetrics metrics);

    @Override
    default Map<String, Object> getUsageMetrics()
    {
        return collectUsageMetrics().getValues();
    }

    default UsageMetrics collectUsageMetrics()
    {
        UsageMetrics metrics = new UsageMetrics();
        addUsageMetrics(metrics);
        return metrics;
    }
}
