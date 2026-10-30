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

public enum AssayMetricArea implements SimpleMetricArea
{
    ASSAY_DESIGNER("AssayDesigner", "Assay imports started from files selected in the file browser: InferDesignFromFileAndImport creates a design inferred from one file, ImportFromFile imports into an existing design"),
    TRANSFORM_SCRIPT("transformScript", "Assay transform script executions, by operation (executionCountForInsert, executionCountForUpdate)"),
    PLATE("plate", "Plate export requests, by format (exportCSV, exportTSV, exportMAP)"),
    PLATE_SET("plateSet", "Plate set worklist exports (exportWorklist)");

    private final Definition _definition;

    AssayMetricArea(String areaName, String description)
    {
        _definition = new Definition("Assay", areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }
}
