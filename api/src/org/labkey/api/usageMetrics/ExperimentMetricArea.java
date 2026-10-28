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

import org.labkey.api.exp.api.ExperimentService;

public enum ExperimentMetricArea implements SimpleMetricArea
{
    MOVE_ENTITIES("moveEntities", "Requests that moved entities to another folder, by entity type (samples, sources for data class rows, assayRuns, list, jobs, notebooks)"),
    CONVERT_TABLE("convertTable", "Client-supplied grid data converted to a download, by format (asExcel, asDelimited); only counted when the request includes query info for auditing"),
    SAMPLE_IMPORT("sampleImport", "Non-background sample imports spanning multiple sample types, including ones that failed validation (crossTypeImport), plus the subset using the update (crossTypeUpdate) or merge (crossTypeMerge) option");

    private final Definition _definition;

    ExperimentMetricArea(String areaName, String description)
    {
        _definition = new Definition(ExperimentService.MODULE_NAME, areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }
}
