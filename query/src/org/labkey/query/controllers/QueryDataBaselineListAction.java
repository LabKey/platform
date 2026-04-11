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
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.springframework.validation.BindException;

/**
 * Lists saved query data baseline metadata files.
 * Returns schema, query, database, timestamp, and row count for each.
 *
 * URL: /query-queryDataBaselineList.api (GET)
 */
@RequiresPermission(AdminPermission.class)
public class QueryDataBaselineListAction extends ReadOnlyApiAction<Object>
{
    @Override
    public ApiResponse execute(Object form, BindException errors)
    {
        QueryDataService service = new QueryDataService();
        JSONObject result = service.listBaselines(getContainer());
        return new ApiSimpleResponse(result);
    }
}
