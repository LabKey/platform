package org.labkey.api.laboratory;

import org.json.JSONObject;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 1:40 PM
 */
public class SettingsItem
{
    String _schema;
    String _query;
    String _category;

    public SettingsItem(String schema, String query, String category)
    {
        _schema = schema;
        _query = query;
        _category = category;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public String getCategory()
    {
        return _category;
    }

    public JSONObject toJson()
    {
        JSONObject json = new JSONObject();

        json.put("category", _category);
        json.put("schemaName", _schema);
        json.put("queryName", _query);

        return json;
    }
}
