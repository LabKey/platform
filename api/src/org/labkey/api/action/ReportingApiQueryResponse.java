/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ReportingWriter;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;

import java.io.IOException;
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
    private final SchemaKey _schemaPath;

    public ReportingApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly, boolean includeDetailsColumn, boolean includeUpdateColumn)
    {
        this(view, schemaEditable, includeLookupInfo, queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn, true);
    }

    public ReportingApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo, String queryName, long offset, List<FieldKey> fieldKeys, boolean metaDataOnly, boolean includeDetailsColumn, boolean includeUpdateColumn, boolean includeMetaData)
    {
        // Mostly piggybacking on ApiQueryResponse constructor
        super(view, schemaEditable, includeLookupInfo, "", queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn, includeMetaData);
        _schemaPath = view.getTable().getUserSchema().getSchemaPath();
        _metaDataOnlyIncludesEmptyRowset = false;
    }

    @Override
    protected double getFormatVersion()
    {
        return 13.2;
    }

    @Override
    public void render(ApiResponseWriter writer) throws Exception
    {
        writer.setSerializeViaJacksonAnnotations(true);
        super.render(writer);
    }

    protected void writeInitialMetaData(ApiResponseWriter writer) throws IOException
    {
        writer.writeProperty("schemaName", _schemaPath);
        writer.writeProperty("queryName", _queryName);
    }

    protected void writeMetaData(ApiResponseWriter writer) throws Exception
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

    @Override
    public ArrayList<Map<String, Object>> getFieldsMetaData(Collection<DisplayColumn> displayColumns, boolean includeLookupInfo)
    {
        ArrayList<Map<String, Object>> fields = new ArrayList<>();

        displayColumns
                .stream()
                .filter(DisplayColumn::isQueryColumn) // Don't put details or update columns into the field map
                .forEach(dc ->
                {
                    Map<String, Object> fieldMeta = ReportingWriter.getMetaData(dc, false, includeLookupInfo, false);
                    //if the column type is file, include an extra column for the url
                    if (dc.getColumnInfo() != null && "file".equalsIgnoreCase(dc.getColumnInfo().getInputType()))
                    {
                        fieldMeta.put("file", true);
                        Map<String, Object> urlMeta = getFileUrlMeta(dc);
                        if (null != urlMeta)
                            fields.add(urlMeta);
                    }
                    fields.add(fieldMeta);
                });

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
        if (null != url)
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

            Object data = createColMap(dc);

            //put the column map into the data map using the column name as the key
            ((HashMap)row.get("data")).put(columnName, data);
        }
    }

}
