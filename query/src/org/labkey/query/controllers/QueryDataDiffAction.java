/*
 * Copyright (c) 2025 LabKey Corporation
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
package org.labkey.query.controllers;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares live query data against a previously captured TSV baseline.
 * Produces a row-level diff report with added, deleted, and modified rows.
 *
 * URL: /query-queryDataDiff.api (POST)
 * Body:
 *   {
 *     "baselineFileName": "query-baseline-core-users-2026-03-22-143000.meta.json",  // required
 *     "queryTimeout": 120,    // optional
 *     "maxDiffs": 500         // optional: max differences to report
 *   }
 */
@RequiresPermission(AdminPermission.class)
public class QueryDataDiffAction extends MutatingApiAction<SimpleApiJsonForm>
{
    private static final Logger LOG = LogHelper.getLogger(QueryDataDiffAction.class, "Query data diff action");

    @Override
    public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
    {
        JSONObject json = form.getJsonObject();
        if (json == null)
            throw new ApiUsageException("Request body is required");

        String baselineFileName = json.optString("baselineFileName", null);
        if (baselineFileName == null || baselineFileName.trim().isEmpty())
            throw new ApiUsageException("'baselineFileName' is required");

        baselineFileName = baselineFileName.trim();

        int queryTimeout = json.optInt("queryTimeout", 120);
        int maxDiffs = json.optInt("maxDiffs", 500);

        List<String> overridePkColumns = null;
        JSONArray pkArray = json.optJSONArray("primaryKeyColumns");
        if (pkArray != null && !pkArray.isEmpty())
        {
            overridePkColumns = new ArrayList<>();
            for (int i = 0; i < pkArray.length(); i++)
                overridePkColumns.add(pkArray.getString(i).trim());
        }

        LOG.info("Starting query data diff against baseline '{}' in container '{}' (user={}, queryTimeout={}, maxDiffs={}, overridePK={})",
            baselineFileName, getContainer().getPath(), getUser().getEmail(), queryTimeout, maxDiffs, overridePkColumns);

        long startTime = System.currentTimeMillis();

        QueryDataService service = new QueryDataService();
        JSONObject report = service.diffAgainstBaseline(
            getUser(), getContainer(), baselineFileName, queryTimeout, maxDiffs, overridePkColumns);

        long elapsed = System.currentTimeMillis() - startTime;
        report.put("elapsedSeconds", elapsed / 1000.0);

        JSONObject summary = report.optJSONObject("summary");
        if (summary != null)
        {
            LOG.info("Query data diff complete in {}s: {} baseline rows, {} live rows, {} matched, {} added, {} deleted, {} modified",
                elapsed / 1000.0, summary.optLong("baselineRowCount"), summary.optLong("liveRowCount"),
                summary.optLong("matchedRows"), summary.optInt("addedRows"),
                summary.optInt("deletedRows"), summary.optInt("modifiedRows"));
        }

        return new ApiSimpleResponse(report);
    }
}
