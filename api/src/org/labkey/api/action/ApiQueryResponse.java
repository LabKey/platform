/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.data.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * API response class for a query view
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 13, 2008
 * Time: 3:21:08 PM
 */
public class ApiQueryResponse implements ApiResponse
{
    private Map<String, Object> _props = new HashMap<String, Object>();
    private TableInfo _tinfo = null;
    private ResultSet _rs = null;
    private List<DisplayColumn> _displayColumns = null;
    private RenderContext _ctx = null;
    private ViewContext _viewContext;
    private SimpleDateFormat _dateFormat = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
    private boolean _schemaEditable = false;
    private boolean _includeLookupInfo = true;
    private String _schemaName = null;
    private String _queryName = null;
    private long _offset = 0;                   //starting offset row number
    private long _numRespRows = 0;              //number of response rows

    public ApiQueryResponse(QueryView view, ViewContext viewContext, boolean schemaEditable, boolean includeLookupInfo,
                            String schemaName, String queryName, long offset) throws Exception
    {
        _viewContext = viewContext;
        _schemaEditable = schemaEditable;
        _includeLookupInfo = includeLookupInfo;
        _schemaName = schemaName;
        _queryName = queryName;
        _offset = offset;
        view.exportToApiResponse(this);
    }

    public Map<String, Object> getProperties()
    {
        return _props;
    }

    public void populate(ResultSet rs, TableInfo table, List<DisplayColumn> displayColumns, Long rowCount) throws Exception
    {
        _rs = rs;
        _tinfo = table;
        _displayColumns = displayColumns;

        _ctx = new RenderContext(_viewContext);
        _ctx.setResultSet(_rs);

        _props.put("metaData", getMetaData());
        _props.put("columnModel", getColumnModel());
        _props.put("rows", getRowset());
        _props.put("schemaName", _schemaName);
        _props.put("queryName", _queryName);

        //rowCount--use the value supplied by query if possible
        //otherwise send back the _offset + _numRespRows.
        if(null != rowCount)
            _props.put("rowCount", rowCount);
        else
            _props.put("rowCount", _offset + _numRespRows);

    }

    protected Map<String,Object> getMetaData() throws Exception
    {
        Map<String,Object> mdata = new HashMap<String,Object>();
        ArrayList<Map<String,Object>> fields = new ArrayList<Map<String,Object>>();
        for(DisplayColumn dc : _displayColumns)
        {
            if(dc.isQueryColumn())
                fields.add(getMetaData(dc));
        }

        mdata.put("root", "rows");
        mdata.put("totalProperty", "rowCount");

        String sort =_viewContext.getRequest().getParameter("query.sort");
        if (sort != null && sort.length() > 1)
        {
            String dir =  "ASC";
            if (sort.charAt(0) == '-')
            {
                dir = "DESC";
                sort = sort.substring(1);
            }
            Map<String, String> sortInfo = new HashMap<String, String>();
            sortInfo.put("field", sort);
            sortInfo.put("direction", dir);
            mdata.put("sortInfo", sortInfo);
        }
        
        //include an id property set to the pk column name if there is one (and only one)
        List<ColumnInfo> pkCols = _tinfo.getPkColumns();
        if (null != pkCols && 1 == pkCols.size())
            mdata.put("id", pkCols.get(0).getName());
        
        mdata.put("fields", fields);

        return mdata;
    }

    protected DisplayColumn getDisplayCol(ColumnInfo col)
    {
        DisplayColumn ret = null;
        for(DisplayColumn dc : _displayColumns)
        {
            if(null != dc.getColumnInfo() && dc.getColumnInfo().equals(col))
                ret = dc;
        }
        return ret;
    }

    protected Map<String, Object> getMetaData(DisplayColumn dc) throws Exception
    {
        HashMap<String,Object> fmdata = new HashMap<String,Object>();
        fmdata.put("name", dc.getColumnInfo().getName());
        fmdata.put("type", dc.getJsonTypeName());

        if(isLookup(dc))
        {
            TableInfo lookupTable = dc.getColumnInfo().getFkTableInfo();

            if (null != lookupTable && lookupTable.getPkColumns().size() == 1)
            {
                Map<String,Object> lookupInfo = new HashMap<String,Object>();
                
                lookupInfo.put("table", lookupTable.getPublicName());
                lookupInfo.put("schema", lookupTable.getPublicSchemaName());
                lookupInfo.put("displayColumn", lookupTable.getTitleColumn());
                lookupInfo.put("keyColumn", lookupTable.getPkColumns().get(0).getName());

                fmdata.put("lookup", lookupInfo);
            }
        }

        return fmdata;
    }

    protected List<Map<String,Object>> getColumnModel() throws Exception
    {
        ArrayList<Map<String,Object>> cols = new ArrayList<Map<String,Object>>();
        for(DisplayColumn dc : _displayColumns)
        {
            if(dc.isQueryColumn())
                cols.add(getColModel(dc));
        }
        return cols;
    }

    protected Map<String,Object> getColModel(DisplayColumn dc) throws Exception
    {
        Map<String,Object> colModel = new HashMap<String,Object>();
        ColumnInfo colInfo = dc.getColumnInfo();

        colModel.put("dataIndex", colInfo.getName());
        colModel.put("sortable", dc.isSortable());
        colModel.put("editable", !"file".equals(colInfo.getInputType()) && isEditable(dc));
        colModel.put("required", !colInfo.isNullable());
        colModel.put("hidden", colInfo.isHidden() || colInfo.isAutoIncrement()); //auto-incr list key columns return false for isHidden(), so check isAutoIncrement as well
        if (colInfo.getCssClass() != null)
            colModel.put("css", colInfo.getCssClass());
        if(dc.getTextAlign() != null)
            colModel.put("align", dc.getTextAlign());
        if (dc.getCaption() != null)
            colModel.put("header", dc.getCaption());
        if (dc.getDescription() != null)
            colModel.put("tooltip", dc.getDescription());
        if (dc.getWidth() != null)
            colModel.put("width", dc.getWidth());
        if(isEditable(dc) && null != colInfo.getDefaultValue())
            colModel.put("defaultValue", colInfo.getDefaultValue());
        return colModel;
    }

    protected List<Object> getRowset() throws Exception
    {
        List<Object> rowset = new ArrayList<Object>();
        Map<String, Object> rowMap = null;

        while(_rs.next())
        {
            _ctx.setRow(ResultSetUtil.mapRow(_rs, rowMap));
            rowset.add(getRow());
            ++_numRespRows;
        }
        return rowset;
    }

    protected Map<String,Object> getRow() throws Exception
    {
        Map<String,Object> row = new HashMap<String,Object>();
        for(DisplayColumn dc : _displayColumns)
        {
            if(dc.isQueryColumn())
                putValue(row, dc);
        }
        return row;
    }

    protected void putValue(Map<String,Object> row, DisplayColumn dc) throws Exception
    {
        Object value = dc.getValue(_ctx); //FIX: 5682 -- always use getVale() so that we return raw FK value instead of display value

        if(value instanceof Date)
            row.put(dc.getColumnInfo().getName(), _dateFormat.format(value));
        else
            row.put(dc.getColumnInfo().getName(), value);
    }

    protected boolean isEditable(DisplayColumn dc)
    {
        return _schemaEditable && dc.isEditable();
    }

    protected boolean isLookup(DisplayColumn dc)
    {
        //to be treated as a lookup, the column must have an FK, and an FK TableInfo that is public
        return (_includeLookupInfo
                && null != dc.getColumnInfo().getFk()
                && null != dc.getColumnInfo().getFkTableInfo()
                && dc.getColumnInfo().getFkTableInfo().isPublic());

    }

}
