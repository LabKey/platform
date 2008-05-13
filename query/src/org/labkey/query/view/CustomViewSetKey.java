/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.query.persist.CstmView;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.QueryDefinitionImpl;
import org.apache.commons.lang.ObjectUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

public class CustomViewSetKey implements Serializable
{
    static private final String KEY = CustomViewSetKey.class.getName();
    private String _containerId;
    private String _queryName;

    public CustomViewSetKey(QueryDefinition queryDef)
    {
        _containerId = queryDef.getContainer().getId();
        _queryName = queryDef.getName();
    }

    public boolean equals(Object other)
    {
        if (!(other instanceof CustomViewSetKey))
            return false;
        CustomViewSetKey that = (CustomViewSetKey) other;
        return ObjectUtils.equals(this._containerId, that._containerId) &&
                ObjectUtils.equals(this._queryName, that._queryName);
    }

    public int hashCode()
    {
        return ObjectUtils.hashCode(_containerId) ^
                ObjectUtils.hashCode(_queryName);
    }

    static private Map<CustomViewSetKey, Map<String, CstmView>> getMap(HttpServletRequest request)
    {
        return (Map<CustomViewSetKey, Map<String, CstmView>>) request.getSession().getAttribute(KEY);
    }

    static private void setMap(HttpServletRequest request, Map<CustomViewSetKey, Map<String, CstmView>> map)
    {
        request.getSession().setAttribute(KEY, map);
    }

    static public Map<String, CstmView> getCustomViewsFromSession(HttpServletRequest request, QueryDefinition queryDef)
    {
        Map<CustomViewSetKey, Map<String, CstmView>> fullMap = getMap(request);
        if (fullMap == null)
            return Collections.EMPTY_MAP;
        Map<String, CstmView> map = fullMap.get(new CustomViewSetKey(queryDef));
        if (map == null)
        {
            return Collections.EMPTY_MAP;
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
            fullMap = new HashMap();
        }

        Map<String, CstmView> map = fullMap.get(new CustomViewSetKey(queryDef));
        if (map == null)
        {
            map = new HashMap();
            fullMap.put(new CustomViewSetKey(queryDef), map);
        }
        map.put(view.getName(), view);
        setMap(request, fullMap);
        return view;
    }

}
