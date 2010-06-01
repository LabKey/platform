/**
 * Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Feb 11, 2008 12:57:07 PM
 */

package org.labkey.api.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RowIdForeignKey;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.*;

public class JsonWriter
{
    private TableInfo _tinfo = null;
    private ResultSet _rs = null;
    private List<DisplayColumn> _displayColumns = null;
    private PrintWriter _out = null;
    private RenderContext _ctx = null;
    private Long _rowCount = null;

    private static final int INDENT_FACTOR = 4;

    public JsonWriter(ResultSet rs, TableInfo tinfo, List<DisplayColumn> displayColumns, Long rowCount)
    {
        _tinfo = tinfo;
        _rs = rs;
        _displayColumns = displayColumns;
        _rowCount = rowCount;
    }

    public void write(HttpServletResponse response) throws Exception
    {
        response.setContentType("application/json");
        _out = response.getWriter();

        _ctx = new RenderContext();
        _ctx.setResultSet(_rs);

        //start the result object
        //we don't build a JSON object for this because the number of rows
        //could be more than we should buffer in memory
        _out.write("{\n\"metadata\": ");
        _out.write(getMetaData().toString(INDENT_FACTOR));

        _out.write(",\n\"columnModel\": ");
        _out.write(getColumnModel().toString(INDENT_FACTOR));

        writeRows();

        //end the result object
        _out.write("\n}");
    }

    protected JSONObject getMetaData() throws Exception
    {
        JSONObject mdata = new JSONObject();
        JSONArray fields = new JSONArray();
        for(DisplayColumn dc : _displayColumns)
            fields.put(getMetaData(dc, null, false, true));

        mdata.put("root", "rowset");
        mdata.put("totalProperty", "rowCount");

        //include an id property set to the pk column name if there is one (and only one)
        List<String> pkColNames = _tinfo.getPkColumnNames();
        if(null != pkColNames && 1 == pkColNames.size())
            mdata.put("id", pkColNames.get(0));
        
        mdata.put("fields", fields);

        return mdata;
    }

    public static JSONObject getMetaData(DisplayColumn dc, FieldKey fieldKeyPrefix, boolean useFriendlyAsType, boolean includeLookup)
    {
        ColumnInfo cinfo = dc.getColumnInfo();
        JSONObject props = new JSONObject();
        JSONObject ext = new JSONObject();
        props.put("ext",ext);

        props.put("name", (null != fieldKeyPrefix ? FieldKey.fromString(fieldKeyPrefix, cinfo.getName()) : cinfo.getName()));
        if (null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        props.put("friendlyType", cinfo.getFriendlyTypeName());
        props.put("type", useFriendlyAsType ? cinfo.getFriendlyTypeName() : dc.getJsonTypeName());
        props.put("jsonType", dc.getJsonTypeName());

        if (null != cinfo.getFieldKey())
            props.put("fieldKey", cinfo.getFieldKey().toString());

        // Duplicate booleans with alternate property name for backwards compatibility
        props.put("isAutoIncrement", cinfo.isAutoIncrement());
        props.put("autoIncrement", cinfo.isAutoIncrement());
        props.put("isHidden", cinfo.isHidden());
        props.put("hidden", cinfo.isHidden());
        props.put("isKeyField", cinfo.isKeyField());
        props.put("keyField", cinfo.isKeyField());
        props.put("isMvEnabled", cinfo.isMvEnabled());
        props.put("mvEnabled", cinfo.isMvEnabled());
        props.put("isNullable", cinfo.isNullable());
        props.put("nullable", cinfo.isNullable());
        props.put("isReadOnly", cinfo.isReadOnly());
        props.put("readOnly", cinfo.isReadOnly());
        props.put("isUserEditable", cinfo.isUserEditable());
        props.put("userEditable", cinfo.isUserEditable());
        props.put("isVersionField", cinfo.isVersionColumn());
        props.put("versionField", cinfo.isVersionColumn());
        props.put("isSelectable", !cinfo.isUnselectable()); //avoid double-negative boolean name
        props.put("selectable", !cinfo.isUnselectable()); //avoid double-negative boolean name

        // These fields are new and don't need to have the "is" prefix for backwards compatibility
        props.put("shownInInsertView", cinfo.isShownInInsertView());
        props.put("shownInUpdateView", cinfo.isShownInUpdateView());
        props.put("shownInDetailsView", cinfo.isShownInDetailsView());

        if (!cinfo.getImportAliasesSet().isEmpty())
        {
            props.put("importAliases", new ArrayList<String>(cinfo.getImportAliasesSet()));
        }

        if (cinfo.getTsvFormatString() != null)
        {
            props.put("tsvFormat", cinfo.getTsvFormatString());
        }
        if (cinfo.getFormat() != null)
        {
            props.put("format", cinfo.getTsvFormatString());
        }
        if (cinfo.getExcelFormatString() != null)
        {
            props.put("excelFormat", cinfo.getExcelFormatString());
        }

        props.put("inputType", cinfo.getInputType());
        // UNDONE ext info for other field typesxtype: checkbox, combo, datefield, field, hidden, htmleditor, numberfield, radio, textarea, textfield, timefield
        //fmdata.put("xtype","");
        if ("textarea".equals(cinfo.getInputType()))
        {
            if (dc instanceof DataColumn)
            {
                int cols = ((DataColumn)dc).getInputLength();
                if (cols > 0)
                    props.put("cols", Math.min(1000,cols));
                int rows = ((DataColumn)dc).getInputRows();
                if (rows > 0)
                    props.put("rows", Math.min(1000,rows));
            }
            ext.put("xtype","textarea");
        }

        props.put("caption", dc.getCaption());

        if (includeLookup)
        {
            Map<String, Object> lookupJSON = getLookupInfo(cinfo);
            if (lookupJSON != null)
            {
                props.put("lookup", lookupJSON);
            }
        }

        return props;
    }
    
    private static JSONObject getLookupInfo(ColumnInfo columnInfo)
    {
        ForeignKey fk = columnInfo.getFk();

        //lookup info
        if (null != fk
                && null != columnInfo.getFkTableInfo()
                && (!(fk instanceof RowIdForeignKey) || !(((RowIdForeignKey)fk).getOriginalColumn().equals(columnInfo))))
        {
            TableInfo lookupTable = columnInfo.getFkTableInfo();
            if(lookupTable != null && lookupTable.getPkColumns().size() == 1)
            {
                JSONObject lookupInfo = new JSONObject();
                if (null != fk.getLookupContainerId())
                {
                    Container fkContainer = ContainerManager.getForId(fk.getLookupContainerId());
                    if (null != fkContainer)
                        lookupInfo.put("containerPath", fkContainer.getPath());
                }

                boolean isPublic = lookupTable.isPublic() && null != lookupTable.getPublicName() && null != lookupTable.getPublicSchemaName();
                // Duplicate with alternate property name for backwards compatibility
                lookupInfo.put("isPublic", isPublic);
                lookupInfo.put("public", isPublic);
                String queryName;
                String schemaName;
                if (isPublic)
                {
                    queryName = lookupTable.getPublicName();
                    schemaName = lookupTable.getPublicSchemaName();
                }
                else
                {
                    queryName = lookupTable.getName();
                    schemaName = lookupTable.getSchema().getName();
                }
                // Duplicate info with different property names for backwards compatibility
                lookupInfo.put("queryName", queryName);
                lookupInfo.put("table", queryName);
                lookupInfo.put("schemaName", schemaName);
                lookupInfo.put("schema", schemaName);

                lookupInfo.put("displayColumn", lookupTable.getTitleColumn());
                if (lookupTable.getPkColumns().size() > 0)
                    lookupInfo.put("keyColumn", lookupTable.getPkColumns().get(0).getName());

                return lookupInfo;
            }
        }

        return null;
    }



    protected JSONArray getColumnModel() throws Exception
    {
        JSONArray cols = new JSONArray();
        for(DisplayColumn dc : _displayColumns)
            cols.put(getColModel(dc));
        return cols;
    }

    protected JSONObject getColModel(DisplayColumn dc) throws Exception
    {
        JSONObject colModel = new JSONObject();
        ColumnInfo colInfo = dc.getColumnInfo();

        colModel.put("dataIndex", dc.getName());
        colModel.put("hidden", colInfo.isHidden());
        colModel.put("sortable", "true");
        if(colInfo.getTextAlign() != null)
            colModel.put("align", colInfo.getTextAlign());
        if (colInfo.getCssClass() != null)
            colModel.put("css", colInfo.getCssClass());
        if (colInfo.getLabel() != null)
            colModel.put("header", colInfo.getLabel());
        if (colInfo.getDescription() != null)
            colModel.put("tooltip", colInfo.getDescription());
        if (colInfo.getWidth() != null)
            colModel.put("width", colInfo.getWidth());

        return colModel;
    }

    protected void writeRows() throws Exception
    {
        _out.write(",\n\"rowset\":[\n");

        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(_rs);
        String rowSep = "";

        while(_rs.next())
        {
            _out.write(rowSep);
            _ctx.setRow(factory.getRowMap(_rs));
            _out.write(getRow().toString(INDENT_FACTOR));
            rowSep = ",\n";
        }
        _out.write("]");

        if(null != _rowCount)
        {
            _out.write(",\n\"rowCount\": ");
            _out.write(String.valueOf(_rowCount));
        }
    }

    protected JSONObject getRow() throws Exception
    {
        JSONObject row = new JSONObject();
        for(DisplayColumn dc : _displayColumns)
        {
            putValue(row, dc);
        }
        return row;
    }

    protected void putValue(JSONObject row, DisplayColumn dc) throws Exception
    {
        Object value = dc.getDisplayValue(_ctx);
        if(value instanceof Date)
            row.put(dc.getName(), "new Date(" + String.valueOf(((Date) value).getTime()) + ")");
        else
            row.put(dc.getName(), value);
    }
}
