/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Pair;
import org.labkey.query.ValidateQueriesVisitor;
import org.springframework.validation.BindException;

/**
 * User: kevink
 * Date: 10/12/12
 * 
 * Validates all queries.
 */
@RequiresPermission(ReadPermission.class)
@Action(ActionType.SelectMetaData.class)
public class ValidateQueriesAction extends ApiAction
{
    @Override
    public ApiResponse execute(Object o, BindException errors) throws Exception
    {
        ValidateQueriesVisitor validator = new ValidateQueriesVisitor();
        validator.visitTop(DefaultSchema.get(getUser(), getContainer()), null);
        if (validator.getInvalidCount() > 0)
        {
            JSONArray warnings = new JSONArray();
            for (Pair<String, ? extends Throwable> warning : validator.getWarnings())
            {
                JSONObject json = new JSONObject();
                json.put("message", warning.first);
                json.put("exception", warning.second.toString());
                warnings.put(json);
            }

            JSONObject ret = new JSONObject();
            ret.put("valid", false);
            ret.put("warnings", warnings);
            return new ApiSimpleResponse(ret);
        }
        else
        {
            return new ApiSimpleResponse("valid", true);
        }
    }
}
