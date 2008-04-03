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
