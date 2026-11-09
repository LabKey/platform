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

import org.labkey.api.query.QueryUpdateService.InsertOption;

public enum QueryMetricArea implements SimpleMetricArea
{
    FILE_BACKGROUND_IMPORTS("fileBackgroundImports", "Query imports queued as background pipeline jobs, by target schema name"),
    FILE_INSERT_COUNTS("fileInsertCounts", Descriptions.ROW_COUNTS),
    FILE_UPSERT_COUNTS("fileUpsertCounts", Descriptions.ROW_COUNTS),
    FILE_IMPORT_COUNTS("fileImportCounts", Descriptions.ROW_COUNTS),
    FILE_MERGE_COUNTS("fileMergeCounts", Descriptions.ROW_COUNTS),
    FILE_REPLACE_COUNTS("fileReplaceCounts", Descriptions.ROW_COUNTS),
    FILE_IMPORT_IDENTITY_COUNTS("fileImport_identityCounts", Descriptions.ROW_COUNTS),
    FILE_UPDATE_COUNTS("fileUpdateCounts", Descriptions.ROW_COUNTS),
    INSERT_FAIL_WITH_VALIDATION_ERROR("insertFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    UPSERT_FAIL_WITH_VALIDATION_ERROR("upsertFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    IMPORT_FAIL_WITH_VALIDATION_ERROR("importFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    MERGE_FAIL_WITH_VALIDATION_ERROR("mergeFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    REPLACE_FAIL_WITH_VALIDATION_ERROR("replaceFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    IMPORT_IDENTITY_FAIL_WITH_VALIDATION_ERROR("import_identityFailWithValidationError", Descriptions.VALIDATION_ERRORS),
    UPDATE_FAIL_WITH_VALIDATION_ERROR("updateFailWithValidationError", Descriptions.VALIDATION_ERRORS);

    // Enum constants can't refer to the enum's own static fields
    private static class Descriptions
    {
        static final String ROW_COUNTS = "Completed query imports (file, pasted text, background job, or other server code) using this insert option, by lowercase target schema name plus row-count bucket (1to1000, 1001to5000, 5001to10000, GT10000)";
        static final String VALIDATION_ERRORS = "Query imports using this insert option that failed with validation errors, by target schema name, or query name when the table is unresolved (materials as samples)";
    }

    private final Definition _definition;

    QueryMetricArea(String areaName, String description)
    {
        _definition = new Definition("query", areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }

    public static QueryMetricArea rowCounts(InsertOption option)
    {
        return valueOf("FILE_" + option.name() + "_COUNTS");
    }

    public static QueryMetricArea validationErrors(InsertOption option)
    {
        return valueOf(option.name() + "_FAIL_WITH_VALIDATION_ERROR");
    }
}
