package org.labkey.query.controllers;

import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

@RequiresPermission(ReadPermission.class)
@Action(ActionType.SelectMetaData.class)
public class GetQueryDetails2Action extends GetQueryDetailsAction
{
    @Override
    public ApiResponse execute(Form form, BindException errors)
    {
        return _execute(form, true);
    }
}
