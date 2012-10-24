/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 9:33 AM
 */
abstract public class AbstractNavItem implements NavItem
{
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = new JSONObject();
        ret.put("name", getName());
        ret.put("label", getLabel());
        ret.put("category", getCategory());
        ret.put("importIntoWorkbooks", isImportIntoWorkbooks());
        ret.put("rendererName", getRendererName());
        ret.put("visible", isVisible(c, u));
        ret.put("key", getPropertyManagerKey());

        ret.put("importUrl", getUrlObject(getImportUrl(c, u)));
        ret.put("searchUrl", getUrlObject(getSearchUrl(c, u)));
        ret.put("browseUrl", getUrlObject(getBrowseUrl(c, u)));

        return ret;
    }

    protected JSONObject getUrlObject(ActionURL url)
    {
        JSONObject json = new JSONObject();
        if (url != null)
        {
            json.put("url", url);
            json.put("controller", url.getController());
            json.put("action", url.getAction());
            json.put("params", url.getParameterMap());
        }
        return json;
    }

    public boolean isVisible(Container c, User u)
    {
        if (getDataProvider() != null && getDataProvider().getOwningModule() != null)
        {
            if (!c.getActiveModules().contains(getDataProvider().getOwningModule()))
                return false;
        }

        Map<String, String> map = PropertyManager.getProperties(c, NavItem.PROPERTY_CATEGORY);
        if (map.containsKey(getPropertyManagerKey()))
            return Boolean.parseBoolean(map.get(getPropertyManagerKey()));

        return getDefaultVisibility(c, u);
    }

    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getCategory() + "||" + getName();
    }

    public static String inferDataProviderNameFromKey(String key)
    {
        String[] tokens = key.split("\\|\\|");
        return tokens[0] + "||" + tokens[1] + "||" + tokens[2];
    }
}
