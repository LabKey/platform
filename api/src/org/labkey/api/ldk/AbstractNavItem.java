/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.ldk;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 9:33 AM
 */
abstract public class AbstractNavItem implements NavItem
{
    protected static final Logger _log = Logger.getLogger(AbstractNavItem.class);

    protected String _ownerKey = null;
    protected Container _targetContainer = null;

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = new JSONObject();
        ret.put("name", getName());
        ret.put("label", getLabel());
        ret.put("category", getCategory());
        ret.put("renderer", getRendererName());
        ret.put("visible", isVisible(c, u));
        ret.put("providerName", getDataProvider() == null ? null : getDataProvider().getName());
        ret.put("key", getPropertyManagerKey());
        ret.put("ownerKey", getOwnerKey());
        ret.put("targetContainer", (_targetContainer == null ? null : _targetContainer.getPath()));

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

    @Override
    public boolean isVisible(Container c, User u)
    {
        Container targetContainer = c.isWorkbook() ? c.getParent() : c;
        if (getDataProvider() != null && getDataProvider().getOwningModule() != null)
        {
            if (!targetContainer.getActiveModules().contains(getDataProvider().getOwningModule()))
                return false;
        }

        Map<String, String> map = new CaseInsensitiveHashMap(PropertyManager.getProperties(targetContainer, NavItem.PROPERTY_CATEGORY));
        if (map.containsKey(getPropertyManagerKey()))
            return Boolean.parseBoolean(map.get(getPropertyManagerKey()));

        return getDefaultVisibility(targetContainer, u);
    }

    @NotNull
    public Container getTargetContainer(Container c)
    {
        return _targetContainer == null ? c : _targetContainer;
    }

    public void setTargetContainer(Container targetContainer)
    {
        _targetContainer = targetContainer;
    }

    public ActionURL appendDefaultView(Container c, ActionURL url, String dataRegionName)
    {
        String view = getDefaultViewName(c, getPropertyManagerKey());
        if (view != null)
        {
            url.addParameter(dataRegionName + ".viewName", view);
        }

        return url;
    }

    public static String getDefaultViewName(Container c, String key)
    {
        Map<String, String> map = PropertyManager.getProperties(c, NavItem.VIEW_PROPERTY_CATEGORY);
        if (map != null && map.containsKey(key))
            return map.get(key);

        return null;
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getCategory() + "||" + getName() + "||" + getLabel();
    }

    public static String inferDataProviderNameFromKey(String key)
    {
        String[] tokens = key.split("\\|\\|");
        return tokens[0] + "||" + tokens[1] + "||" + tokens[2];
    }

    public String getOwnerKey()
    {
        return _ownerKey;
    }

    public void setOwnerKey(String ownerKey)
    {
        _ownerKey = ownerKey;
    }
}
