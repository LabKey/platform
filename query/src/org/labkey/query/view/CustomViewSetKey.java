/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.query.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.SessionHelper;
import org.labkey.query.persist.CstmView;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CustomViewSetKey implements Serializable
{
    static private final String KEY = CustomViewSetKey.class.getName();
    private String _containerId;
    private String _queryName;

    public CustomViewSetKey(QueryDefinition queryDef)
    {
        this(queryDef.getContainer(), queryDef.getName());
    }

    public CustomViewSetKey(Container c, String queryName)
    {
        _containerId = c.getId();
        _queryName = queryName;
    }

    public boolean equals(Object other)
    {
        if (!(other instanceof CustomViewSetKey))
            return false;
        CustomViewSetKey that = (CustomViewSetKey) other;
        return Objects.equals(this._containerId, that._containerId) &&
                Objects.equals(this._queryName, that._queryName);
    }

    public int hashCode()
    {
        return Objects.hashCode(_containerId) ^
                Objects.hashCode(_queryName);
    }

    static private Map<CustomViewSetKey, Map<String, CstmView>> getMap(@NotNull HttpServletRequest request)
    {
        return (Map<CustomViewSetKey, Map<String, CstmView>>)SessionHelper.getAttribute(request, KEY, null);
    }

    static private void setMap(HttpServletRequest request, Map<CustomViewSetKey, Map<String, CstmView>> map)
    {
        SessionHelper.setAttribute(request, KEY, map, true);
    }

    static public Map<String, CstmView> getCustomViewsFromSession(@NotNull HttpServletRequest request, QueryDefinition queryDef)
    {
        return getCustomViewsFromSession(request, queryDef.getContainer(), queryDef.getName());
    }

    static public Map<String, CstmView> getCustomViewsFromSession(@NotNull HttpServletRequest request, Container c, String queryName)
    {
        Map<CustomViewSetKey, Map<String, CstmView>> fullMap = getMap(request);
        if (fullMap == null)
            return Collections.emptyMap();
        Map<String, CstmView> map = fullMap.get(new CustomViewSetKey(c, queryName));
        if (map == null)
        {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    static public void deleteCustomViewFromSession(HttpServletRequest request, QueryDefinition queryDef, String name)
    {
        Map<CustomViewSetKey, Map<String, CstmView>> fullMap = getMap(request);
        if (fullMap == null)
            return;

        Map<String, CstmView> map = fullMap.get(new CustomViewSetKey(queryDef));
        if (map == null)
            return;
        map.remove(name);
        setMap(request, fullMap);
    }

    static public CstmView saveCustomViewInSession(HttpServletRequest request, QueryDefinition queryDef, CstmView view)
    {
        Map<CustomViewSetKey, Map<String, CstmView>> fullMap = getMap(request);
        if (fullMap == null)
        {
            fullMap = new HashMap<>();
        }

        Map<String, CstmView> map = fullMap.get(new CustomViewSetKey(queryDef));
        if (map == null)
        {
            map = new HashMap<>();
            fullMap.put(new CustomViewSetKey(queryDef), map);
        }
        map.put(view.getName(), view);
        setMap(request, fullMap);
        
        // Stop tracking session views
        MemTracker.getInstance().remove(view);
        return view;
    }

}
