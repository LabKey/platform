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
package org.labkey.api.reports.report;

import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.usageMetrics.SimpleMetricsService;

import java.util.Map;
import java.util.Set;

/**
 * Tracks which packages/modules are loaded by scripts run on this server (R reports, assay transform scripts, Python
 * scripts, and anything else that runs through {@link ExternalScriptEngine}). Populated by a language-specific epilog
 * appended to each script that captures the loaded packages and writes them to a sidecar file, which the engine reads
 * back after the script runs. Usage is tracked per language (e.g. "r", "python").
 *
 * Each load is recorded via {@link SimpleMetricsService}, which persists a cumulative per-package load count across
 * restarts and reports it to mothership under "simpleMetricCounts". The package name is the metric name and the feature
 * area is "&lt;language&gt;PackageUsage".
 */
public class ScriptPackageUsageTracker
{
    private static final String MODULE_NAME = "API";
    private static final String FEATURE_AREA_SUFFIX = "PackageUsage";
    private static final int MAX_METRIC_NAME_LENGTH = 255;

    /**
     * Packages that ship with a given language's runtime and are always present, so aren't interesting as "library
     * usage". R's base packages are filtered here; Python's standard library is filtered in the capture epilog itself
     * (via sys.stdlib_module_names), so no Python entry is needed.
     */
    private static final Map<String, Set<String>> BASE_PACKAGES = Map.of(
        "r", Set.of("base", "compiler", "datasets", "graphics", "grDevices", "methods", "stats", "tools", "utils")
    );

    private ScriptPackageUsageTracker()
    {
    }

    private static boolean isBasePackage(String language, String packageName)
    {
        return BASE_PACKAGES.getOrDefault(language, Set.of()).contains(packageName);
    }

    /**
     * Record that the given package was loaded by a script run in the given language (e.g. "r", "python"). Safe to call
     * repeatedly; base packages and blank names are ignored.
     */
    public static void record(String language, String packageName)
    {
        if (packageName == null || packageName.isBlank() || isBasePackage(language, packageName))
            return;

        SimpleMetricsService.get().increment(MODULE_NAME, language + FEATURE_AREA_SUFFIX, truncateMetricName(packageName));
    }

    /**
     * The package name is used as the metric name, and the names come from whatever the script actually loaded rather
     * than from a fixed list, so cap the length at what the DB column holds.
     */
    private static String truncateMetricName(String packageName)
    {
        return packageName.length() <= MAX_METRIC_NAME_LENGTH ? packageName : packageName.substring(0, MAX_METRIC_NAME_LENGTH);
    }
}
