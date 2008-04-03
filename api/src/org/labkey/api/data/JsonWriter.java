/**
 * Copyright (c) 2007 LabKey Software Foundation
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
import org.labkey.api.util.ResultSetUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
            fields.put(getMetaData(dc));

        mdata.put("root", "rowset");
        mdata.put("totalProperty", "rowCount");

        //include an id property set to the pk column name if there is one (and only one)
        String[] pkCols = _tinfo.getPkColumnNames();
        if(null != pkCols && 1 == pkCols.length)
            mdata.put("id", pkCols[0]);
        
        mdata.put("fields", fields);

        return mdata;
    }

    protected JSONObject getMetaData(DisplayColumn dc) throws Exception
    {
        JSONObject fmdata = new JSONObject();
        fmdata.put("name", dc.getName());
        fmdata.put("type", dc.getJsonTypeName());
        return fmdata;
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
        if (colInfo.getCaption() != null)
            colModel.put("header", colInfo.getCaption());
        if (colInfo.getDescription() != null)
            colModel.put("tooltip", colInfo.getDescription());
        if (colInfo.getWidth() != null)
            colModel.put("width", colInfo.getWidth());

        return colModel;
    }

    protected void writeRows() throws Exception
    {
        Map<String, Object> rowMap = null;
        _out.write(",\n\"rowset\":[\n");

        String rowSep = "";
        while(_rs.next())
        {
            _out.write(rowSep);
            _ctx.setRow(ResultSetUtil.mapRow(_rs, rowMap));
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
