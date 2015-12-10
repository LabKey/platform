package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.view.ViewContext;

public interface ActivityService
{
    @Nullable Activity getCurrentActivity(ViewContext context);

    @Nullable JSONObject getCurrentActivityAsJson(ViewContext context);
}
