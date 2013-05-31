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

import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ReportingWriter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
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

        // see Ext.data.JsonReader
        writer.writeProperty("metaData", getMetaData());
        // TODO: can use super's getMetaData(), but sort info not in mockup. Also not comment around super's getSort(); should we override?

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

        boolean complete = writeRowset(writer);

        if (!_metaDataOnly)
        {
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

        writer.endResponse();
    }

    @Override
    public ArrayList<Map<String, Object>> getFieldsMetaData(Collection<DisplayColumn> displayColumns, boolean includeLookupInfo)
    {
        ArrayList<Map<String, Object>> fields = new ArrayList<>();
        for (DisplayColumn dc : displayColumns)
        {
            if (includeColumnInResponse(dc))
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
    protected boolean writeRowset(ApiResponseWriter writer) throws Exception
    {
        return super.writeRowset(writer);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected Object getColumnValue(DisplayColumn dc)
    {
        return super.getColumnValue(dc);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc) throws Exception
    {
        super.putValue(row, dc);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
