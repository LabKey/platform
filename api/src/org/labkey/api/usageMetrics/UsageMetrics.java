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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
    private final Map<String, Description> _descriptions;

    public UsageMetrics()
    {
        this(null, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /** @param values null for a group that's only described */
    UsageMetrics(@Nullable String path, @Nullable Map<String, Object> values, Map<String, Description> descriptions)
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
        describe(key, description, kind);
        getMutableValues().put(key, value);
        return this;
    }

    /** Calls the supplier only when values are reported, so a {@code reportedIf(false)} view skips the work */
    public UsageMetrics put(@NotNull String key, @NotNull Supplier<?> value, @NotNull String description)
    {
        return put(key, value, description, Kind.CURRENT);
    }

    public UsageMetrics put(@NotNull String key, @NotNull Supplier<?> value, @NotNull String description, @NotNull Kind kind)
    {
        describe(key, description, kind);
        if (_values != null)
            _values.put(key, value.get());
        return this;
    }

    /** @return this, or if not reported, a view at the same path that only describes what's put into it, groups included */
    public UsageMetrics reportedIf(boolean reported)
    {
        return reported ? this : new UsageMetrics(_path, null, _descriptions);
    }

    /** Describes a key without reporting it, for keys this server omits, such as ones only reported under some condition */
    public UsageMetrics describe(@NotNull String key, @NotNull String description)
    {
        return describe(key, description, Kind.CURRENT);
    }

    public UsageMetrics describe(@NotNull String key, @NotNull String description, @NotNull Kind kind)
    {
        Description added = new Description(describedPath(key), description, kind);
        Description existing = _descriptions.putIfAbsent(added.path(), added);
        if (existing != null && !existing.equals(added))
            throw new IllegalStateException("Usage metric " + added.path() + " is described inconsistently: " + existing + " vs. " + added);
        return this;
    }

    /** Adds a nested object whose children, including keys only known at runtime, share this description */
    public UsageMetricGroup group(@NotNull String key, @NotNull String description)
    {
        return group(key, description, Kind.CURRENT);
    }

    public UsageMetricGroup group(@NotNull String key, @NotNull String description, @NotNull Kind kind)
    {
        describe(key, description, kind);
        return addGroup(key);
    }

    /** Describes a nested object without reporting it. Its children can be described, but not given values. */
    public UsageMetricGroup describeGroup(@NotNull String key, @NotNull String description)
    {
        return describeGroup(key, description, Kind.CURRENT);
    }

    public UsageMetricGroup describeGroup(@NotNull String key, @NotNull String description, @NotNull Kind kind)
    {
        describe(key, description, kind);
        return new UsageMetricGroup(childPath(key), null, _descriptions);
    }

    UsageMetricGroup addGroup(@NotNull String key)
    {
        if (_values == null)
            return new UsageMetricGroup(childPath(key), null, _descriptions);
        Map<String, Object> child = new LinkedHashMap<>();
        getMutableValues().put(key, child);
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
        if (_values == null)
            throw new IllegalStateException("Usage metric group " + _path + " is only described, so it can't hold values");
        return _values;
    }

    public Map<String, Object> getValues()
    {
        return _values == null ? Map.of() : Collections.unmodifiableMap(_values);
    }

    public List<Description> getDescriptions()
    {
        return List.copyOf(_descriptions.values());
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
            assertThrows(IllegalArgumentException.class, () -> metrics.describe("a.b", "Dotted"));
            metrics.group("files", "Files by name").put("report.xlsx", 1L);
        }

        @Test
        public void testDescribeWithoutReporting()
        {
            UsageMetrics metrics = new UsageMetrics();
            metrics.describe("optional", "Only reported sometimes");
            metrics.group("byType", "Counts by type").describe("rare", "Rarely present type", Kind.CUMULATIVE);

            assertEquals(Map.of("byType", Map.of()), metrics.getValues());
            assertEquals(List.of(
                new Description("optional", "Only reported sometimes", Kind.CURRENT),
                new Description("byType", "Counts by type", Kind.CURRENT),
                new Description("byType.rare", "Rarely present type", Kind.CUMULATIVE)),
                metrics.getDescriptions());
        }

        @Test
        public void testRepeatedDescriptions()
        {
            UsageMetrics metrics = new UsageMetrics();
            metrics.describe("count", "Things");
            metrics.put("count", 1L, "Things");
            assertEquals(List.of(new Description("count", "Things", Kind.CURRENT)), metrics.getDescriptions());

            assertThrows(IllegalStateException.class, () -> metrics.put("count", 1L, "Other things"));
            assertThrows(IllegalStateException.class, () -> metrics.describe("count", "Things", Kind.CUMULATIVE));
        }

        @Test
        public void testDescribeGroupWithoutReporting()
        {
            UsageMetrics metrics = new UsageMetrics();
            UsageMetricGroup absent = metrics.describeGroup("perFolder", "Folder counts", Kind.CURRENT);
            absent.describe("runs", "Runs");

            assertEquals(Map.of(), metrics.getValues());
            assertEquals(List.of(
                new Description("perFolder", "Folder counts", Kind.CURRENT),
                new Description("perFolder.runs", "Runs", Kind.CURRENT)),
                metrics.getDescriptions());
            assertThrows(IllegalStateException.class, () -> absent.put("runs", 1L));
            assertThrows(IllegalStateException.class, () -> absent.put("runs", 1L, "Runs"));
        }

        @Test
        public void testReportedIf()
        {
            UsageMetrics metrics = new UsageMetrics();
            assertSame(metrics, metrics.reportedIf(true));
            metrics.reportedIf(true).put("shown", () -> 1L, "Shown");
            metrics.reportedIf(false).put("hidden", () -> { throw new AssertionError("Supplier called for unreported key"); }, "Hidden", Kind.CUMULATIVE);
            UsageMetricGroup colors = metrics.group("colors", "Colors");
            colors.put("count", 0L, "Colors defined");
            colors.reportedIf(false).put("archived", () -> 2L, "Archived colors");
            UsageMetricGroup absent = metrics.reportedIf(false).group("absent", "Absent group");
            absent.group("child", "Absent child").describe("leaf", "Leaf");
            assertThrows(IllegalStateException.class, () -> absent.put("x", 1L));

            assertEquals(Map.of("shown", 1L, "colors", Map.of("count", 0L)), metrics.getValues());
            assertEquals(List.of(
                new Description("shown", "Shown", Kind.CURRENT),
                new Description("hidden", "Hidden", Kind.CUMULATIVE),
                new Description("colors", "Colors", Kind.CURRENT),
                new Description("colors.count", "Colors defined", Kind.CURRENT),
                new Description("colors.archived", "Archived colors", Kind.CURRENT),
                new Description("absent", "Absent group", Kind.CURRENT),
                new Description("absent.child", "Absent child", Kind.CURRENT),
                new Description("absent.child.leaf", "Leaf", Kind.CURRENT)),
                metrics.getDescriptions());
        }
    }
}
