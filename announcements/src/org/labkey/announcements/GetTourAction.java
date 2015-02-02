/*
 * Copyright (c) 2015 LabKey Corporation
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
