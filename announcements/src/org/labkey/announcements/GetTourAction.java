package org.labkey.announcements;

import org.json.JSONObject;
import org.labkey.announcements.model.TourManager;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

/**
 * Created by Marty on 1/19/2015.
 */
@RequiresPermissionClass(ReadPermission.class)
public class GetTourAction extends MutatingApiAction<SimpleApiJsonForm>
{

    @Override
    public Object execute(SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject json = form.getJsonObject();
        ApiSimpleResponse response = new ApiSimpleResponse();

        response.put("Mode", TourManager.getTourMode(getContainer(), json.getInt("id")));
        response.put("Json", TourManager.getTourJson(getContainer(), json.getInt("id")));
        response.put("success", true);
        return response;
    }

    public static class TourForm extends SimpleApiJsonForm
    {
    }
}
