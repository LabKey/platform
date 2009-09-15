/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class DetailsURL
{
    /**
     * Create a new DetailsURL from a string of the form "pageflow/action.view?paramKey=${paramVal}".
     * A Controller will be required when creating the url using {@link DetailsURL#getURL(Map, Container)}.
     */
    static public DetailsURL fromString(String str) throws MetadataException
    {
        return fromString(null, str);
    }

    /**
     * Create a new DetailsURL from a string of the form "pageflow/action.view?paramKey=${paramVal}".
     */
    static public DetailsURL fromString(Container container, String str) throws MetadataException
    {
        int ichQuery = str.indexOf("?");
        if (ichQuery < 0)
            throw new MetadataException("Expected '?' in path string.");
        String strPath = str.substring(0, ichQuery);
        String strQuery = str.substring(ichQuery + 1);
        String[] pathParts = StringUtils.split(strPath, "/");
        String[] queryParams = StringUtils.split(strQuery, "&");
        if (pathParts.length != 2)
            throw new MetadataException("Expected 'pageflow/action.view'");
        String strPageFlow = pathParts[0];
        String strAction = pathParts[1];
        if (!strAction.endsWith(".view"))
            throw new MetadataException("Action name should end with '.view'");
        strAction = strAction.substring(0, strAction.length() - 5);
        Map<String, String> fixedParams = new HashMap<String, String>();
        Map<String, String> params = new HashMap<String, String>();
        for (String param : queryParams)
        {
            String[] pair = StringUtils.split(param, "=");
            if (pair.length != 2)
            {
                throw new MetadataException("Unable to parse '" + param + "'");
            }
            String key = decode(pair[0]);
            String value = pair[1];
            if (value.startsWith("${") && value.endsWith("}"))
            {
                params.put(key, value.substring(2, value.length() - 1));
            }
            else
            {
                fixedParams.put(key, decode(value));
            }
        }
        return new DetailsURL(strPageFlow, strAction, container, fixedParams, params);
    }
    static private String decode(String str) throws MetadataException
    {
        try
        {
            return PageFlowUtil.decode(str);
        }
        catch (Exception e)
        {
            throw new MetadataException("Error decoding '" + str + "'", e);
        }
    }

    ActionURL _baseURL;
    String _controller;
    String _action;
    Container _container;
    Map<String, String> _fixedParams;
    Map<String, String> _columnParams;

    /**
     *
     * @param columnParams map from URL parameter to column name
     */

    public DetailsURL(ActionURL baseURL, Map<String, String> columnParams)
    {
        _controller = baseURL.getPageFlow();
        _action = baseURL.getAction();
        _container = ContainerManager.getForPath(baseURL.getExtraPath());
        _fixedParams = new HashMap<String, String>();
        for (Pair<String, String> param : baseURL.getParameters())
            _fixedParams.put(param.getKey(), param.getValue());
        _columnParams = columnParams;
        _baseURL = baseURL;
    }

    public DetailsURL(String controller, String action, Container container, Map<String, String> fixedParams, Map<String, String> columnParams)
    {
        _controller = controller;
        _action = action;
        _container = container;
        _fixedParams = fixedParams;
        _columnParams = columnParams;
        if (_container != null)
            _baseURL = buildURL(_container);
    }

    public StringExpressionFactory.StringExpression getURL(Map<String, ColumnInfo> columns)
    {
        return getURL(columns, null);
    }

    public StringExpressionFactory.StringExpression getURL(Map<String, ColumnInfo> columns, Container c)
    {
        Map<String, ColumnInfo> params = new LinkedHashMap();
        for (Map.Entry<String, String> entry : _columnParams.entrySet())
        {
            ColumnInfo column = columns.get(entry.getValue());
            if (column == null)
                return null;
            params.put(entry.getKey(), column);
        }
        return new LookupURLExpression(getBaseURL(c), params);
    }

    public ActionURL getBaseURL(Container c)
    {
        if (c == null && _container == null)
            throw new IllegalArgumentException("container required");
        return c == null ? _baseURL : buildURL(c);
    }

    private ActionURL buildURL(Container c)
    {
        ActionURL url = new ActionURL(_controller, _action, c);
        for (Map.Entry<String, String> entry : _fixedParams.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());
        return url;
    }

    public Map<String, String> getColumnParams()
    {
        return _columnParams;
    }
}
