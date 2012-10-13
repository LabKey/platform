package org.labkey.query.controllers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.security.RequiresPermissionClass;
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
@RequiresPermissionClass(ReadPermission.class)
public class ValidateQueriesAction extends ApiAction
{
    @Override
    public ApiResponse execute(Object o, BindException errors) throws Exception
    {
        ValidateQueriesVisitor validator = new ValidateQueriesVisitor(true);
        validator.visitTop(DefaultSchema.get(getViewContext().getUser(), getViewContext().getContainer()), null);
        if (validator.getInvalidCount() > 0)
        {
            JSONArray warnings = new JSONArray();
            for (Pair<String, Throwable> warning : validator.getWarnings())
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
