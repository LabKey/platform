/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MVDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ReportingWriter;
import org.labkey.api.data.Results;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 5/22/13
 */
public class ReportingApiQueryResponse extends ExtendedApiQueryResponse
{
    private SchemaKey _schemaName = null;

    public ReportingApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly, boolean includeDetailsColumn, boolean includeUpdateColumn)
    {
        // Mostly piggybacking on ApiQueryResponse constructor
        super(view, schemaEditable, includeLookupInfo, "", queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn);
        _schemaName = view.getTable().getUserSchema().getSchemaPath();
    }

    @Override
    public void render(ApiResponseWriter writer) throws Exception
    {
        writer.setSerializeViaJacksonAnnotations(true);

        writer.startResponse();

        //write the metaData section
        writer.writeProperty("schemaName", _schemaName);
        writer.writeProperty("queryName", _queryName);

        if (_metaDataOnly)
        {
            writeMetaData(writer);
        }
        else
        {
            // First run the query, so on potential SQLException we only serialize the exception instead of outputting all the metadata before the exception
            try (Results results = getResults())
            {
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
                        _rowCount = _dataRegion.getTotalRows();
                    }
                }
                writer.writeProperty("rowCount", _rowCount > 0 ? _rowCount : _offset + _numRespRows);
            }
        }
        writer.endResponse();
    }

    private void writeMetaData(ApiResponseWriter writer) throws Exception
    {
        // see Ext.data.JsonReader
        writer.writeProperty("metaData", getMetaData());
        // TODO: can use super's getMetaData(), but sort info not in mockup. Also note comment around super's getSort(); should we override?

        Map<String,String> mvInfo = getMvInfo();

        // TODO: mvInfo not in mockup
        if (mvInfo != null)
        {
            writer.writeProperty("mvInfo", mvInfo);    // New name
        }

        // TODO: extraReturnProperties not in mockup
        if (_extraReturnProperties != null)
        {
            for (Map.Entry<String, Object> entry : _extraReturnProperties.entrySet())
                writer.writeProperty(entry.getKey(), entry.getValue());
        }
    }

    private Results getResults() throws Exception
    {
        return _dataRegion.getResultSet(_ctx);
    }

    @Override
    public ArrayList<Map<String, Object>> getFieldsMetaData(Collection<DisplayColumn> displayColumns, boolean includeLookupInfo)
    {
        ArrayList<Map<String, Object>> fields = new ArrayList<>();
        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isQueryColumn())  // Don't put details or update columns into the field map
            {
                Map<String,Object> fmdata = ReportingWriter.getMetaData(dc, false, includeLookupInfo, false);
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

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc) throws Exception
    {
        // Splitting the individual row result into two objects- "data" and "links". "links" has the detail & update links.

        //column value
        Object value = getColumnValue(dc);

        if (null != value && dc instanceof UrlColumn)
        {
            putLinksMap(row, dc, value.toString());
        }
        else
        {
            putDataMap(row, dc, value);
        }
    }

    private void putLinksMap(Map<String, Object> row, DisplayColumn dc, String displayText)
    {
        if (!row.containsKey("links"))
        {
            row.put("links", new HashMap<>());
        }
        String url = dc.renderURL(getRenderContext());
        if(null != url)
        {
            Map<String,Object> urlMap = new HashMap<>();
            urlMap.put("href", url);
            urlMap.put("title", displayText);
            ((HashMap)row.get("links")).put(displayText, urlMap);
        }
    }

    private void putDataMap(Map<String, Object> row, DisplayColumn dc, Object value)
    {

        String columnName = getColumnName(dc);
        if (columnName != null)
        {
            if (!row.containsKey("data"))
            {
                row.put("data", new HashMap<>());
            }

            Map<String,Object> colMap = new HashMap<>();
            colMap.put(ColMapEntry.value.name(), value);

            //display value (if different from value)
            Object displayValue = ensureJSONDate(dc.getDisplayValue(getRenderContext()));
            if(null != displayValue && !displayValue.equals(value))
                colMap.put(ColMapEntry.displayValue.name(), displayValue);

            //missing values
            if (dc instanceof MVDisplayColumn)
            {
                MVDisplayColumn mvColumn = (MVDisplayColumn)dc;
                RenderContext ctx = getRenderContext();
                colMap.put(ColMapEntry.mvValue.name(), mvColumn.getMvIndicator(ctx));
                colMap.put(ColMapEntry.mvRawValue.name(), mvColumn.getRawValue(ctx));
            }

            if (_doItWithStyle)
            {
                RenderContext ctx = getRenderContext();
                String style = dc.getCssStyle(ctx);
                if (!StringUtils.isEmpty(style))
                    colMap.put("style", style);
            }

            //put the column map into the data map using the column name as the key
            ((HashMap)row.get("data")).put(columnName, colMap);
        }
    }

    private boolean writeRowset(ApiResponseWriter writer, Results results) throws Exception
    {
        boolean complete = true;
        writer.startList("rows");
        // We're going to be writing JSON back, which is tolerant of extra spaces, so allow async so we
        // can monitor if the client has stopped listening
        _dataRegion.setAllowAsync(true);

        _ctx.setResults(results);
        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);
        factory.setConvertBigDecimalToDouble(false);

        while(results.next())
        {
            _ctx.setRow(factory.getRowMap(results));
            writer.writeListEntry(getRow());
            ++_numRespRows;
        }
        complete = results.isComplete();
        writer.endList();
        return complete;
    }
}
