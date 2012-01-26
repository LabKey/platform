/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * API response class for a query view
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 13, 2008
 * Time: 3:21:08 PM
 */
public class ApiQueryResponse implements ApiResponse, ApiStreamResponse
{
    private static final String URL_COL_PREFIX = "_labkeyurl_";
    private TableInfo _tinfo = null;
    private ResultSet _rs = null;
    private List<DisplayColumn> _displayColumns = null;
    private long _rowCount = 0;
    private RenderContext _ctx = null;
    private ViewContext _viewContext;
    private SimpleDateFormat _dateFormat = new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT);
    private boolean _schemaEditable = false;
    private boolean _includeLookupInfo = true;
    private String _schemaName = null;
    private String _queryName = null;
    private long _offset = 0;                   //starting offset row number
    private long _numRespRows = 0;              //number of response rows
    private List<FieldKey> _fieldKeys = null;
    private boolean _metaDataOnly = false;
    private Map<String, Object> _extraReturnProperties;

    public ApiQueryResponse(QueryView view, ViewContext viewContext, boolean schemaEditable, boolean includeLookupInfo,
                            String schemaName, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly) throws Exception
    {
        _viewContext = viewContext;
        _schemaEditable = schemaEditable;
        _includeLookupInfo = includeLookupInfo;
        _schemaName = schemaName;
        _queryName = queryName;
        _offset = offset;
        _fieldKeys = fieldKeys;
        _metaDataOnly = metaDataOnly;
        view.exportToApiResponse(this);
    }

    public ApiQueryResponse()
    {
        _includeLookupInfo = true;
        _metaDataOnly = true;
    }

    public Map<String, Object> getProperties()
    {
        //this will stream the response instead
        return null;
    }

    public void setExtraReturnProperties(Map<String, Object> extraReturnProperties)
    {
        _extraReturnProperties = extraReturnProperties;
    }

    public void render(ApiResponseWriter writer) throws Exception
    {
        writer.startResponse();

        //write the metaData section
        writer.writeProperty("schemaName", _schemaName);
        writer.writeProperty("queryName", _queryName);
        writer.writeProperty("formatVersion", getFormatVersion());
        // see Ext.data.JsonReader
        writer.writeProperty("metaData", getMetaData());
        // see Ext.data.ColumnModel
        writer.writeProperty("columnModel", getColumnModel());
        Map<String,String> mvInfo = getMvInfo();

        if (mvInfo != null)
        {
            writer.writeProperty("qcInfo", mvInfo);    // Leave for backward compatibility
            writer.writeProperty("mvInfo", mvInfo);    // New name
        }

        if (_extraReturnProperties != null)
        {
            for (Map.Entry<String, Object> entry : _extraReturnProperties.entrySet())
                writer.writeProperty(entry.getKey(), entry.getValue());
        }

        writeRowset(writer);

        if (!_metaDataOnly)
        {
            writer.writeProperty("rowCount", _rowCount > 0 ? _rowCount : _offset + _numRespRows);
        }

        writer.endResponse();
    }

    public List<FieldKey> getFieldKeys()
    {
        return _fieldKeys;
    }

    public void initialize(Results rs, TableInfo table, List<DisplayColumn> displayColumns, Long rowCount)
    {
        _rs = rs;
        _tinfo = table;
        _displayColumns = displayColumns;
        if (null != rowCount)
            _rowCount = rowCount;

        _ctx = new RenderContext(_viewContext);
        _ctx.setResults(rs);

    }


    protected double getFormatVersion()
    {
        return 8.3;
    }

    /**
     * Returns a map of MV indicators to their labels.
     * E.g.: ".Q" -> "This value has been flagged as failing QC"
     *
     * If no columns contained in this result allow missing values,
     * the result of this method will be null
     * @return Map of MV indicators to their labels.
     */
    protected Map<String, String> getMvInfo()
    {
        for (DisplayColumn dc : _displayColumns)
        {
            if (dc instanceof MVDisplayColumn)
            {
                Map<String, String> mvInfo = new HashMap<String, String>();
                Set<String> mvIndicators = MvUtil.getMvIndicators(_viewContext.getContainer());
                for (String mvIndicator : mvIndicators)
                {
                    mvInfo.put(mvIndicator, MvUtil.getMvLabel(mvIndicator, _viewContext.getContainer()));
                }
                return mvInfo;
            }
        }
        return null;
    }


    // see Ext.data.JsonReader (response.metaData)
    protected Map<String, Object> getMetaData() throws Exception
    {
        Map<String, Object> metaData = new HashMap<String,Object>();

        // see Ext.data.Field
        ArrayList<Map<String, Object>> fields = getFieldsMetaData(_displayColumns, _includeLookupInfo);

        metaData.put("root", "rows");
        metaData.put("totalProperty", "rowCount");

        if (null != _viewContext && null != _viewContext.getRequest())
        {
            String sort =_viewContext.getRequest().getParameter("query.sort");
            if (sort != null && sort.length() > 1)
            {
                String dir = "ASC";
                if (sort.charAt(0) == '-')
                {
                    dir = "DESC";
                    sort = sort.substring(1);
                }
                Map<String, String> sortInfo = new HashMap<String, String>();
                sortInfo.put("field", sort);
                sortInfo.put("direction", dir);
                metaData.put("sortInfo", sortInfo);
            }
        }

        //include an id property set to the pk column name if there is one (and only one)
        List<ColumnInfo> pkCols = _tinfo.getPkColumns();
        if (null != pkCols && 1 == pkCols.size())
            metaData.put("id", pkCols.get(0).getName());

        metaData.put("fields", fields);

        return metaData;
    }


    public ArrayList<Map<String, Object>> getFieldsMetaData(Collection<DisplayColumn> displayColumns, boolean includeLookupInfo)
    {
        ArrayList<Map<String, Object>> fields = new ArrayList<Map<String,Object>>();
        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isQueryColumn())
            {
                Map<String,Object> fmdata = JsonWriter.getMetaData(dc, null, false, includeLookupInfo);
                //if the column type is file, include an extra column for the url
                if ("file".equalsIgnoreCase(dc.getColumnInfo().getInputType()))
                {
                    fmdata.put("file", true);
                    Map<String,Object> urlmdata = getFileUrlMeta(dc);
                    if (null != urlmdata)
                        fields.add(urlmdata);
                }
                fields.add(fmdata);
            }
        }
        return fields;
    }


    protected Map<String,Object> getFileUrlMeta(DisplayColumn fileColumn)
    {
        Map<String,Object> urlmdata = new HashMap<String,Object>();
        urlmdata.put("name", URL_COL_PREFIX + fileColumn.getColumnInfo().getName());
        urlmdata.put("type", "string");
        return urlmdata;
    }

    protected DisplayColumn getDisplayCol(ColumnInfo col)
    {
        DisplayColumn ret = null;
        for (DisplayColumn dc : _displayColumns)
        {
            if (null != dc.getColumnInfo() && dc.getColumnInfo().equals(col))
                ret = dc;
        }
        return ret;
    }

    protected List<Map<String,Object>> getColumnModel() throws Exception
    {
        ArrayList<Map<String,Object>> cols = new ArrayList<Map<String,Object>>();
        for (DisplayColumn dc : _displayColumns)
        {
            if (dc.isQueryColumn())
            {
                cols.add(getColModel(dc));

                if ("file".equalsIgnoreCase(dc.getColumnInfo().getInputType()))
                {
                    Map<String,Object> urlmdata = getFileUrlMeta(dc);
                    if (null != urlmdata)
                        cols.add(urlmdata);
                }
            }
        }
        return cols;
    }


    protected Map<String,Object> getColModel(DisplayColumn dc) throws Exception
    {
        Map<String,Object> extGridColumn = new HashMap<String,Object>();
        ColumnInfo colInfo = dc.getColumnInfo();

        // see  Ext.grid.ColumnModel Ext.grid.Column
        extGridColumn.put("dataIndex", colInfo.getName());
        extGridColumn.put("sortable", dc.isSortable());
        extGridColumn.put("editable", isEditable(dc));
        extGridColumn.put("hidden", colInfo.isHidden() || colInfo.isAutoIncrement()); //auto-incr list key columns return false for isHidden(), so check isAutoIncrement as well
        if (dc.getTextAlign() != null)
            extGridColumn.put("align", dc.getTextAlign());
        if (dc.getCaption() != null)
            extGridColumn.put("header", dc.getCaption(_ctx, false));
        if (dc.getDescription() != null)
            extGridColumn.put("tooltip", dc.getDescription());
        if (dc.getWidth() != null)
        {
            try
            {
                //try to parse as integer (which is what Ext wants)
                extGridColumn.put("width", Integer.parseInt(dc.getWidth()));
            }
            catch(NumberFormatException e)
            {
                //include it as a string
                extGridColumn.put("width", dc.getWidth());
            }
        }

        /** These are not part of Ext.Grid.Column, don't know why they are hear (MAB) */
        extGridColumn.put("required", !colInfo.isNullable());
        if (isEditable(dc) && null != colInfo.getDefaultValue())
            extGridColumn.put("defaultValue", colInfo.getDefaultValue());
        extGridColumn.put("scale", dc.getColumnInfo().getScale());
        return extGridColumn;
    }


    protected void writeRowset(ApiResponseWriter writer) throws Exception
    {
        writer.startList("rows");
        if (!_metaDataOnly)
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(_rs);

            while(_rs.next())
            {
                _ctx.setRow(factory.getRowMap(_rs));
                writer.writeListEntry(getRow());
                ++_numRespRows;
            }
        }
        writer.endList();
    }

    protected Map<String,Object> getRow() throws Exception
    {
        Map<String,Object> row = new HashMap<String,Object>();
        for (DisplayColumn dc : _displayColumns)
        {
            if (dc.isQueryColumn())
                putValue(row, dc);
        }
        return row;
    }

    protected void putValue(Map<String,Object> row, DisplayColumn dc) throws Exception
    {
        Object value = getColumnValue(dc);
        row.put(dc.getColumnInfo().getName(), value);

        //if the display column has a url, include that as another row property
        //with the name "<URL_COL_PREFIX><colname>"
        if (null != value)
        {
            String url = dc.renderURL(_ctx);
            if (null != url)
                row.put(URL_COL_PREFIX + dc.getColumnInfo().getName(), url);
        }
    }


    protected Object getColumnValue(DisplayColumn dc)
    {
        Object value = dc.getJsonValue(_ctx);
        return ensureJSONDate(value);
    }

    protected Object ensureJSONDate(Object value)
    {
        return value instanceof Date ? _dateFormat.format(value) : value;
    }


    protected boolean isEditable(DisplayColumn dc)
    {
        if (!_schemaEditable || !dc.isEditable())
            return false;
        // UNDONE: make the schema set isEditable() correctly and remove this hack
        ColumnInfo col = dc.getColumnInfo();
        return (!(col instanceof LookupColumn) || col instanceof PropertyColumn);
    }


    protected boolean isLookup(DisplayColumn dc)
    {
        //to be treated as a lookup, the column must have an FK, and an FK TableInfo that is public
        return (null != dc.getColumnInfo().getFk()
                && null != dc.getColumnInfo().getFkTableInfo()
                && dc.getColumnInfo().getFkTableInfo().isPublic());

    }


    protected RenderContext getRenderContext()
    {
        return _ctx;
    }

    public boolean isMetaDataOnly()
    {
        return _metaDataOnly;
    }
}
