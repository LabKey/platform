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
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects a provider's metric values together with their descriptions. Only the values are sent to mothership;
 * descriptions are exposed separately to document what each metric means.
 * Every key added here needs a description; children of a described {@link #group} inherit it.
 */
public class UsageMetrics
{
    public enum Kind
    {
        /** Recomputed from current data at each report */
        CURRENT,
        /** Running total that only grows, such as a count of times a feature was used */
        CUMULATIVE
    }

    /** @param path dot-separated path relative to the module's metrics; children of a group share its description */
    public record Description(@NotNull String path, @Nullable String description, @Nullable Kind kind) {}

    private final String _path;
    private final Map<String, Object> _values;
    private final List<Description> _descriptions;

    public UsageMetrics()
    {
        this(null, new LinkedHashMap<>(), new ArrayList<>());
    }

    UsageMetrics(@Nullable String path, Map<String, Object> values, List<Description> descriptions)
    {
        _path = path;
        _values = values;
        _descriptions = descriptions;
    }

    public UsageMetrics put(@NotNull String key, @Nullable Object value, @NotNull String description)
    {
        return put(key, value, description, Kind.CURRENT);
    }

    public UsageMetrics put(@NotNull String key, @Nullable Object value, @NotNull String description, @NotNull Kind kind)
    {
        _descriptions.add(new Description(describedPath(key), description, kind));
        _values.put(key, value);
        return this;
    }

    /** Adds a nested object whose children, including keys only known at runtime, share this description */
    public UsageMetricGroup group(@NotNull String key, @NotNull String description)
    {
        return group(key, description, Kind.CURRENT);
    }

    public UsageMetricGroup group(@NotNull String key, @NotNull String description, @NotNull Kind kind)
    {
        _descriptions.add(new Description(describedPath(key), description, kind));
        return addGroup(key);
    }

    UsageMetricGroup addGroup(@NotNull String key)
    {
        Map<String, Object> child = new LinkedHashMap<>();
        _values.put(key, child);
        return new UsageMetricGroup(childPath(key), child, _descriptions);
    }

    /** A '.' in a described key would make its jsonKey indistinguishable from a nested path */
    private String describedPath(String key)
    {
        if (key.contains("."))
            throw new IllegalArgumentException("Described usage metric keys can't contain '.': " + key);
        return childPath(key);
    }

    String childPath(String key)
    {
        return _path == null ? key : _path + "." + key;
    }

    Map<String, Object> getMutableValues()
    {
        return _values;
    }

    public Map<String, Object> getValues()
    {
        return Collections.unmodifiableMap(_values);
    }

    public List<Description> getDescriptions()
    {
        return Collections.unmodifiableList(_descriptions);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testValuesAndDescriptions()
        {
            UsageMetrics metrics = new UsageMetrics();
            metrics.put("jobCount", 3L, "Jobs");
            metrics.put("clicks", 7L, "Clicks", Kind.CUMULATIVE);
            UsageMetricGroup actions = metrics.group("actionTypes", "Actions by type");
            actions.put("AssayImport", 2L);
            actions.group("nested").put("leaf", 1L);
            actions.put("described", 5L, "Overrides the group's description");

            assertEquals(Map.of(
                "jobCount", 3L,
                "clicks", 7L,
                "actionTypes", Map.of(
                    "AssayImport", 2L,
                    "nested", Map.of("leaf", 1L),
                    "described", 5L)),
                metrics.getValues());

            assertEquals(List.of(
                new Description("jobCount", "Jobs", Kind.CURRENT),
                new Description("clicks", "Clicks", Kind.CUMULATIVE),
                new Description("actionTypes", "Actions by type", Kind.CURRENT),
                new Description("actionTypes.described", "Overrides the group's description", Kind.CURRENT)),
                metrics.getDescriptions());
        }

        @Test
        public void testRejectsDottedKeys()
        {
            UsageMetrics metrics = new UsageMetrics();
            assertThrows(IllegalArgumentException.class, () -> metrics.put("a.b", 1L, "Dotted"));
            assertThrows(IllegalArgumentException.class, () -> metrics.group("a.b", "Dotted"));
            metrics.group("files", "Files by name").put("report.xlsx", 1L);
        }
    }
}
