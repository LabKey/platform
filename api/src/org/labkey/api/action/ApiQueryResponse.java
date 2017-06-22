/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.MVDisplayColumn;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API response class for a {@link QueryView}.
 *
 * User: Dave
 * Date: Feb 13, 2008
 */
public class ApiQueryResponse implements ApiResponse
{
    private static final String URL_COL_PREFIX = "_labkeyurl_";
    boolean _doItWithStyle = false;
    private TableInfo _tinfo = null;
    private List<DisplayColumn> _displayColumns = null;
    private Map<DisplayColumn, String> _displayColumnCaptions = new HashMap<>();
    protected long _rowCount = 0;
    protected RenderContext _ctx = null;
    private ViewContext _viewContext;
    private boolean _schemaEditable = false;
    private boolean _includeLookupInfo = true;
    private String _schemaName = null;
    protected String _queryName = null;
    protected long _offset = 0;                   //starting offset row number
    protected long _numRespRows = 0;              //number of response rows
    private List<FieldKey> _fieldKeys = null;
    protected boolean _metaDataOnly = false;
    // Include an empty "rows" array when serializing metadata only (maxRows=0)
    // CONSIDER: We could remove this option if we want to include empty "rows" array in the ReportingApiQueryResponse to be similar to the other response formats.
    protected boolean _metaDataOnlyIncludesEmptyRowset = true;
    protected Map<String, Object> _extraReturnProperties;
    protected DataRegion _dataRegion;
    private boolean _includeDetailsColumn;
    private boolean _includeUpdateColumn;
    private boolean _includeDisplayValues;
    private List<FieldKey> _columnFilter;
    private boolean _includeMetaData;

    // TODO: This is silly... switch to builder pattern, or at least a constructor that takes reasonable strategies
    public ApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo,
                            String schemaName, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly,
                            boolean includeDetailsColumn, boolean includeUpdateColumn, boolean includeDisplayValues, boolean includeMetaData)
    {
        _viewContext = view.getViewContext();
        _schemaEditable = schemaEditable;
        _includeLookupInfo = includeLookupInfo;
        _schemaName = schemaName;
        _queryName = queryName;
        _offset = offset;
        _fieldKeys = fieldKeys;
        _metaDataOnly = metaDataOnly;
        _includeDetailsColumn = includeDetailsColumn;
        _includeUpdateColumn = includeUpdateColumn;
        _includeDisplayValues = includeDisplayValues;
        _includeMetaData = includeMetaData;
        view.exportToApiResponse(this);
    }

    public ApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo,
                            String schemaName, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly,
                            boolean includeDetailsColumn, boolean includeUpdateColumn, boolean includeDisplayValues)
    {
        this(view, schemaEditable, includeLookupInfo, schemaName, queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn, includeDisplayValues, true);
    }

    public ApiQueryResponse()
    {
        _includeLookupInfo = true;
        _metaDataOnly = true;
    }

    public Map<String, ?> getProperties()
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

        //write the initial metaData section
        writeInitialMetaData(writer);

        if (_metaDataOnly)
        {
            writeMetaData(writer);
            if (_metaDataOnlyIncludesEmptyRowset)
                writeEmptyRowset(writer);
        }
        else
        {
            // First run the query, so on potential SQLException we only serialize the exception instead of outputting all the metadata before the exception
            try (Results results = getResults())
            {
                if (_includeMetaData)
                    writeMetaData(writer);

                boolean complete = writeRowset(writer, results);

                // Figure out if we need to make a separate request to get the total row count (via the aggregates)
                if (!complete && _rowCount == 0)
                {
                    // Load the aggregates
                    _dataRegion.getAggregateResults(_ctx);
                    if (_dataRegion.getTotalRows() != null)
                    {
                        _rowCount = _dataRegion.getTotalRows();
                    }
                }
                writer.writeProperty("rowCount", _rowCount > 0 ? _rowCount : _offset + _numRespRows);
            }
        }

        writer.endResponse();
    }

    /**
     * This initial set of metaData will always be serialized even if a SQLException is thrown when executing the query.
     */
    protected void writeInitialMetaData(ApiResponseWriter writer) throws IOException
    {
        writer.writeProperty("schemaName", _schemaName);
        writer.writeProperty("queryName", _queryName);
        writer.writeProperty("formatVersion", getFormatVersion());
    }

    protected void writeMetaData(ApiResponseWriter writer) throws Exception
    {
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
    }

    protected Results getResults() throws Exception
    {
        // We're going to be writing JSON back, which is tolerant of extra spaces, so allow async so we
        // can monitor if the client has stopped listening
        _dataRegion.setAllowAsync(true);
        try
        {
            return _dataRegion.getResultSet(_ctx);
        }
        catch (ConversionException e)
        {
            throw new ApiUsageException(e);
        }
    }

    public List<FieldKey> getFieldKeys()
    {
        return _fieldKeys;
    }

    public void initialize(RenderContext ctx, DataRegion dataRegion, TableInfo table, List<DisplayColumn> displayColumns)
    {
        _dataRegion = dataRegion;
        _tinfo = table;
        _displayColumns = displayColumns;
        if (null != dataRegion.getTotalRows())
            _rowCount = dataRegion.getTotalRows();

        if (isIncludeDisplayValues())
        {
            List<DisplayColumn> displayValueColumns = new ArrayList<>();
            for (DisplayColumn dc : _displayColumns)
            {
                if (dc.getColumnInfo() != null && !dc.getColumnInfo().equals(dc.getDisplayColumnInfo()))
                {
                    DisplayColumn fkDisplayColumn = dc.getDisplayColumnInfo().getRenderer();
                    displayValueColumns.add(fkDisplayColumn);

                    _displayColumnCaptions.put(fkDisplayColumn, dc.getCaption(ctx, false) + "/" + fkDisplayColumn.getCaption(ctx, false));
                }
            }

            _displayColumns.addAll(displayValueColumns);
        }

        _ctx = ctx;
        _ctx.setCache(false);
    }


    public void includeStyle(boolean withStyle)
    {
        _doItWithStyle = withStyle;
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
                Map<String, String> mvInfo = new HashMap<>();
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

    /* UNDONE: this is terrible! Shouldn't read off the URL */
    private String getSort()
    {
        if (null != _viewContext && null != _viewContext.getRequest())
            return StringUtils.trimToNull(_viewContext.getRequest().getParameter("query.sort"));
        return null;
    }


    // see Ext.data.JsonReader (response.metaData)
    protected Map<String, Object> getMetaData()
    {
        Map<String, Object> metaData = new HashMap<>();

        // see Ext.data.Field
        ArrayList<Map<String, Object>> fields = getFieldsMetaData(_displayColumns, _includeLookupInfo);

        metaData.put("root", "rows");
        metaData.put("totalProperty", "rowCount");

        String sortString = getSort();
        if (null != sortString)
        {
            JSONArray array = new JSONArray();
            String[] sortStrings = sortString.split(",");
            for (String sort : sortStrings)
            {
                if (sort.length() == 0)
                    continue;
                String dir = "ASC";
                if (sort.charAt(0) == '-' || sort.charAt(1) == '+')
                {
                    dir = sort.charAt(0) == '-' ? "DESC" : "ASC";
                    sort = sort.substring(1);
                }
                Map<String, String> sortInfo = new HashMap<>();
                sortInfo.put("field", sort);
                sortInfo.put("direction", dir);
                array.put(array.length(), sortInfo);
            }
            if (array.length() > 0)
            {
                metaData.put("sortInfo", array.get(0));
                metaData.put("sortInfoArray", array);
            }
        }

        //include an id property set to the pk column name if there is one (and only one)
        List<ColumnInfo> pkCols = _tinfo.getPkColumns();
        if (1 == pkCols.size())
            metaData.put("id", pkCols.get(0).getName());

        metaData.put("fields", fields);

        metaData.put("description", _tinfo.getDescription());
        metaData.put("title", _tinfo == null ? _queryName : _tinfo.getTitle());
        metaData.put("importMessage", _tinfo.getImportMessage());

        JSONArray templates = new JSONArray();
        List<Pair<String, String>> it = _tinfo.getImportTemplates(_ctx.getViewContext());
        if(it != null && it.size() > 0)
        {
            for (Pair<String, String> pair : it)
            {
                JSONObject o = new JSONObject();
                o.put("label", pair.getKey());
                o.put("url", pair.second);
                templates.put(o);
            }
        }
        metaData.put("importTemplates", templates);

        return metaData;
    }


    public ArrayList<Map<String, Object>> getFieldsMetaData(Collection<DisplayColumn> displayColumns, boolean includeLookupInfo)
    {
        ArrayList<Map<String, Object>> fields = new ArrayList<>();
        for (DisplayColumn dc : displayColumns)
        {
            if (includeColumnInResponse(dc))
            {
                Map<String,Object> fmdata = JsonWriter.getMetaData(dc, null, false, includeLookupInfo, false);
                //if the column type is file, include an extra column for the url
                if (dc.getColumnInfo() != null && "file".equalsIgnoreCase(dc.getColumnInfo().getInputType()))
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
        Map<String,Object> urlmdata = new HashMap<>();
        urlmdata.put("name", URL_COL_PREFIX + fileColumn.getColumnInfo().getName());
        urlmdata.put("type", "string");
        return urlmdata;
    }

    protected List<Map<String,Object>> getColumnModel() throws Exception
    {
        ArrayList<Map<String,Object>> cols = new ArrayList<>();
        for (DisplayColumn dc : _displayColumns)
        {
            if (includeColumnInResponse(dc))
            {
                cols.add(getColModel(dc));

                if (dc.getColumnInfo() != null && "file".equalsIgnoreCase(dc.getColumnInfo().getInputType()))
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
        Map<String,Object> extGridColumn = new HashMap<>();
        ColumnInfo colInfo = dc.getColumnInfo();

        // see  Ext.grid.ColumnModel Ext.grid.Column
        extGridColumn.put("dataIndex", getColumnName(dc));
        extGridColumn.put("sortable", dc.isSortable());
        extGridColumn.put("editable", isEditable(dc));
        extGridColumn.put("hidden", colInfo != null && (colInfo.isHidden() || colInfo.isAutoIncrement())); //auto-incr list key columns return false for isHidden(), so check isAutoIncrement as well
        if (dc.getTextAlign() != null)
            extGridColumn.put("align", dc.getTextAlign());
        if (dc.getDescription() != null)
            extGridColumn.put("tooltip", dc.getDescription());
        if (dc.getCaption() != null)
        {
            if (isIncludeDisplayValues() && _displayColumnCaptions.containsKey(dc))
                extGridColumn.put("header", _displayColumnCaptions.get(dc));
            else
                extGridColumn.put("header", dc.getCaption(_ctx, false));
        }
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

        /** These are not part of Ext.Grid.Column, don't know why they are here (MAB) */
        // TODO ext grids doesn't understand missing values, so treat required as !nullable
        extGridColumn.put("required", colInfo != null && (!colInfo.isNullable() || colInfo.isRequired()));
        if (colInfo != null && isEditable(dc) && null != colInfo.getDefaultValue())
            extGridColumn.put("defaultValue", colInfo.getDefaultValue());
        if (colInfo != null)
        {
            extGridColumn.put("scale", colInfo.getScale());
        }
        return extGridColumn;
    }


    protected void writeEmptyRowset(ApiResponseWriter writer) throws Exception
    {
        writer.startList("rows");
        writer.endList();
    }

    protected boolean writeRowset(ApiResponseWriter writer, Results results) throws Exception
    {
        boolean complete = true;
        writer.startList("rows");

        if (null != results)
        {
            _ctx.setResults(results);
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);
            factory.setConvertBigDecimalToDouble(false);

            while (results.next())
            {
                _ctx.setRow(factory.getRowMap(results));
                writer.writeListEntry(getRow());
                ++_numRespRows;
            }
            complete = results.isComplete();
        }
        writer.endList();
        return complete;
    }

    protected Map<String,Object> getRow() throws Exception
    {
        Map<String,Object> row = new HashMap<>();
        for (DisplayColumn dc : _displayColumns)
        {
            if (includeColumnInResponse(dc))
                putValue(row, dc);
        }
        return row;
    }

    protected boolean includeColumnInResponse(DisplayColumn dc)
    {
        if (_columnFilter != null && dc.getColumnInfo() != null && !_columnFilter.contains(dc.getColumnInfo().getFieldKey()))
        {
            return false;
        }
        return dc.isQueryColumn() || (dc instanceof DetailsColumn && _includeDetailsColumn) || (dc instanceof UpdateColumn && _includeUpdateColumn);
    }

    protected void putValue(Map<String,Object> row, DisplayColumn dc) throws Exception
    {
        Object value = getColumnValue(dc);
        String colName = getColumnName(dc);
        row.put(colName, value);

        //if the display column has a url, include that as another row property
        //with the name "<URL_COL_PREFIX><colname>"
        //Issue 23773: even for null urls include it so as not to cause ordering to change.
        if (dc.includeURL())
        {
            String url = dc.renderURL(_ctx);
            row.put(URL_COL_PREFIX + colName, url);
        }
    }


    protected Object getColumnValue(DisplayColumn dc)
    {
        Object value = dc.getJsonValue(_ctx);
        if (value instanceof String)
            return PageFlowUtil.filterControlChars(value);
        else
            return ensureJSONDate(value);
    }

    protected Object ensureJSONDate(Object value)
    {
        return value instanceof Date ? DateUtil.formatJsonDateTime((Date)value) : value;
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

    public boolean isIncludeUpdateColumn()
    {
        return _includeUpdateColumn;
    }

    public boolean isIncludeDetailsColumn()
    {
        return _includeDetailsColumn;
    }

    public boolean isIncludeDisplayValues()
    {
        return _includeDisplayValues;
    }

    protected String getColumnName(DisplayColumn dc)
    {
        String columnName = null;
        if (dc.getColumnInfo() != null)
        {
            columnName = dc.getColumnInfo().getName();
        }
        else if (dc instanceof UpdateColumn)
        {
            columnName = "~~Update~~";
        }
        else if (dc instanceof DetailsColumn)
        {
            columnName = "~~Details~~";
        }
        return columnName;
    }

    /** The client has requested an explicit column list, so remember it and filter the response to include only those columns */
    public void setColumnFilter(List<FieldKey> columnFilter)
    {
        _columnFilter = columnFilter;
    }
}
