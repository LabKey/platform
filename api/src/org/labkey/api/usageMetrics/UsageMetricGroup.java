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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** A described nested object; its children may omit descriptions because they inherit the group's */
public class UsageMetricGroup extends UsageMetrics
{
    UsageMetricGroup(@NotNull String path, Map<String, Object> values, List<Description> descriptions)
    {
        super(path, values, descriptions);
    }

    public UsageMetricGroup put(@NotNull String key, @Nullable Object value)
    {
        getMutableValues().put(key, value);
        return this;
    }

    public UsageMetricGroup putAll(@NotNull Map<String, ?> values)
    {
        getMutableValues().putAll(values);
        return this;
    }

    public UsageMetricGroup group(@NotNull String key)
    {
        return addGroup(key);
    }
}
