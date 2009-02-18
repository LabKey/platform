/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.data.QCDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Feb 16, 2009
* Time: 11:06:17 AM
*/

/**
 * Provided an extended response that includes additional meta-data
 * for each row/column
 */
public class ExtendedApiQueryResponse extends ApiQueryResponse
{
    public enum ColMapEntry
    {
        value,
        qcValue,
        qcIndicator,
        qcRawValue,
        url
    }

    public ExtendedApiQueryResponse(QueryView view, ViewContext viewContext, boolean schemaEditable,
                                    boolean includeLookupInfo, String schemaName, String queryName,
                                    long offset, List<FieldKey> fieldKeys)
            throws Exception
    {
        super(view, viewContext, schemaEditable, includeLookupInfo, schemaName, queryName, offset, fieldKeys);
    }

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc) throws Exception
    {
        //in the extended response format, each column will have a map of its own
        //that will contain entries for value, qcValue, qcIndicator, etc.
        Map<String,Object> colMap = new HashMap<String,Object>();

        //column value
        Object value = getColumnValue(dc);
        colMap.put(ColMapEntry.value.name(), value);

        //url
        String url = dc.getURL(getRenderContext());
        if(null != value && null != url)
            colMap.put(ColMapEntry.url.name(), url);

        //qc values
        if (dc instanceof QCDisplayColumn)
        {
            QCDisplayColumn qcColumn = (QCDisplayColumn)dc;
            RenderContext ctx = getRenderContext();
            colMap.put(ColMapEntry.qcValue.name(), qcColumn.getQcValue(ctx));
            colMap.put(ColMapEntry.qcRawValue.name(), qcColumn.getRawValue(ctx));
        }

        //put the column map into the row map using the column name as the key
        row.put(dc.getColumnInfo().getName(), colMap);
    }
}