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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures normalized row data for a single schema+query to TSV + metadata JSON.
 * Used for cross-database row-level comparison.
 *
 * URL: /query-queryDataCapture.api (POST)
 * Body:
 *   {
 *     "schemaName": "core",       // required
 *     "queryName": "users",       // required
 *     "rowLimit": 0,              // optional: 0 = all rows
 *     "queryTimeout": 120,        // optional: seconds
 *     "namedParameters": {        // optional: query named parameters
 *       "MyParam": "value"
 *     }
 *   }
 */
@RequiresPermission(AdminPermission.class)
public class QueryDataCaptureAction extends MutatingApiAction<SimpleApiJsonForm>
{
    private static final Logger LOG = LogHelper.getLogger(QueryDataCaptureAction.class, "Query data capture action");

    @Override
    public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
    {
        JSONObject json = form.getJsonObject();
        if (json == null)
            throw new ApiUsageException("Request body is required");

        String schemaName = json.optString("schemaName", null);
        String queryName = json.optString("queryName", null);

        if (schemaName == null || schemaName.trim().isEmpty())
            throw new ApiUsageException("'schemaName' is required");
        if (queryName == null || queryName.trim().isEmpty())
            throw new ApiUsageException("'queryName' is required");

        schemaName = schemaName.trim();
        queryName = queryName.trim();

        int rowLimit = json.optInt("rowLimit", 0);
        int queryTimeout = json.optInt("queryTimeout", 120);

        Map<String, Object> namedParameters = null;
        JSONObject namedParametersJson = json.optJSONObject("namedParameters");
        if (namedParametersJson != null && namedParametersJson.length() > 0)
        {
            namedParameters = new LinkedHashMap<>();
            for (String key : namedParametersJson.keySet())
            {
                Object rawValue = namedParametersJson.opt(key);
                if (rawValue == null || rawValue == JSONObject.NULL)
                    continue;

                String value = rawValue.toString().trim();
                if (!value.isEmpty())
                    namedParameters.put(key, value);
            }
        }

        namedParameters = addLegacyDateParameter(namedParameters, "StartDate", json.optString("startDate", null));
        namedParameters = addLegacyDateParameter(namedParameters, "EndDate", json.optString("endDate", null));

        if (namedParameters != null && namedParameters.isEmpty())
            namedParameters = null;

        LOG.info("Capturing query data for {}.{} in container '{}' (user={}, rowLimit={}, queryTimeout={}, params={})",
            schemaName, queryName, getContainer().getPath(), getUser().getEmail(), rowLimit, queryTimeout, namedParameters);

        long startTime = System.currentTimeMillis();

        QueryDataService service = new QueryDataService();
        QueryDataService.CaptureResult result = service.captureQueryData(
            getUser(), getContainer(), schemaName, queryName, rowLimit, queryTimeout, namedParameters);

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("Query data capture complete in {}s", elapsed / 1000.0);

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("metadata", result.metadata());
        response.put("metaFileName", result.metaFile().getFileName().toString());
        response.put("tsvFileName", result.tsvFile().getFileName().toString());
        response.put("elapsedSeconds", elapsed / 1000.0);

        return new ApiSimpleResponse(response);
    }

    private Map<String, Object> addLegacyDateParameter(Map<String, Object> namedParameters, String paramName, String value)
    {
        if (value == null || value.trim().isEmpty())
            return namedParameters;

        if (namedParameters == null)
            namedParameters = new LinkedHashMap<>();

        namedParameters.putIfAbsent(paramName, value.trim());
        return namedParameters;
    }
}
