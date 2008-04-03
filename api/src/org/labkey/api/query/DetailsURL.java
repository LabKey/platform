package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class DetailsURL
{
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
        ActionURL baseURL = new ActionURL(strPageFlow, strAction.substring(0, strAction.length() - 5), container.getPath());
        Map <String, String> params = new HashMap();
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
                baseURL.addParameter(key, decode(value));
            }
        }
        return new DetailsURL(baseURL, params);
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
    Map<String, String> _columnParams;

    /**
     *
     * @param baseURL
     * @param columnParams map from URL parameter to column name
     */
    public DetailsURL(ActionURL baseURL, Map<String, String> columnParams)
    {
        _baseURL = baseURL;
        _columnParams = columnParams;
    }

    public StringExpressionFactory.StringExpression getURL(Map<String, ColumnInfo> columns)
    {
        Map<String, ColumnInfo> params = new LinkedHashMap();
        for (Map.Entry<String, String> entry : _columnParams.entrySet())
        {
            ColumnInfo column = columns.get(entry.getValue());
            if (column == null)
                return null;
            params.put(entry.getKey(), column);
        }
        return new LookupURLExpression(_baseURL, params);
    }

    public ActionURL getBaseURL()
    {
        return _baseURL;
    }

    public Map<String, String> getColumnParams()
    {
        return _columnParams;
    }
}
