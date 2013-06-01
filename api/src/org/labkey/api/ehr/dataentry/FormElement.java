package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;

/**
 * User: bimber
 * Date: 5/17/13
 * Time: 12:35 PM
 */
public class FormElement
{
    private String _xtype;
    private ColumnInfo _boundCol;

    private FormElement()
    {

    }

    public static FormElement createForColumn(ColumnInfo col)
    {
        FormElement fd = new FormElement();
        fd._boundCol = col;

        return fd;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        DisplayColumn dc = _boundCol.getDisplayColumnFactory().createRenderer(_boundCol);
        json.putAll(JsonWriter.getMetaData(dc, null, true, true, true));

        json.put("schemaName", _boundCol.getParentTable().getPublicSchemaName());
        json.put("queryName", _boundCol.getParentTable().getPublicName());

        return json;
    }
}
