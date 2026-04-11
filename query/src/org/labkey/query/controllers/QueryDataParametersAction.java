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

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.springframework.validation.BindException;

/**
 * Returns named parameter declarations for a specific schema/query pair.
 *
 * URL: /query-queryDataParameters.api (GET)
 * Parameters:
 *   schemaName (required)
 *   queryName  (required)
 */
@RequiresPermission(AdminPermission.class)
public class QueryDataParametersAction extends ReadOnlyApiAction<QueryDataParametersAction.Form>
{
    @Override
    public ApiResponse execute(Form form, BindException errors)
    {
        String schemaName = form.getSchemaName();
        String queryName = form.getQueryName();

        if (schemaName == null || schemaName.trim().isEmpty())
            throw new ApiUsageException("'schemaName' is required");
        if (queryName == null || queryName.trim().isEmpty())
            throw new ApiUsageException("'queryName' is required");

        QueryDataService service = new QueryDataService();
        JSONObject response = new JSONObject();
        response.put("parameters", service.getQueryParameters(getUser(), getContainer(), schemaName.trim(), queryName.trim()));
        return new ApiSimpleResponse(response);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Form
    {
        private String _schemaName;
        private String _queryName;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
    }
}
