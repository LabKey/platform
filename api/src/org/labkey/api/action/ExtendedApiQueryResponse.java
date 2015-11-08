/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MVDisplayColumn;
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
    boolean _doItWithStyle = false;

    public enum ColMapEntry
    {
        value,
        displayValue,
        mvValue,
        mvIndicator,
        mvRawValue,
        url,
        style
    }

    // Used for jackson serialization/deserialization
    public static class ColMap extends HashMap<ColMapEntry, Object>
    {
    }

    public ExtendedApiQueryResponse(QueryView view, boolean schemaEditable,
                                    boolean includeLookupInfo, String schemaName, String queryName,
                                    long offset, List<FieldKey> fieldKeys, boolean metaDataOnly, boolean includeDetailsColumn, boolean includeUpdateColumn)
    {
        super(view, schemaEditable, includeLookupInfo, schemaName, queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn, false);
    }

    public void includeStyle(boolean withStyle)
    {
        _doItWithStyle = withStyle;
    }

    @Override
    protected double getFormatVersion()
    {
        return 9.1;
    }

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc) throws Exception
    {
        Map<ColMapEntry, Object> colMap = createColMap(dc);

        //put the column map into the row map using the column name as the key
        String columnName = getColumnName(dc);

        if (columnName != null)
        {
            row.put(columnName, colMap);
        }
    }

    protected Map<ColMapEntry, Object> createColMap(DisplayColumn dc)
    {
        //in the extended response format, each column will have a map of its own
        //that will contain entries for value, mvValue, mvIndicator, etc.
        Map<ColMapEntry, Object> colMap = new ColMap();

        //column value
        Object value = getColumnValue(dc);
        colMap.put(ColMapEntry.value, value);

        //display value (if different from value)
        Object displayValue = ensureJSONDate(dc.getDisplayValue(getRenderContext()));
        if(null != displayValue && !displayValue.equals(value))
            colMap.put(ColMapEntry.displayValue, displayValue);

        //url
        if (null != value)
        {
            String url = dc.renderURL(getRenderContext());
            if(null != url)
                colMap.put(ColMapEntry.url, url);
        }

        //missing values
        if (dc instanceof MVDisplayColumn)
        {
            MVDisplayColumn mvColumn = (MVDisplayColumn)dc;
            RenderContext ctx = getRenderContext();
            colMap.put(ColMapEntry.mvValue, mvColumn.getMvIndicator(ctx));
            colMap.put(ColMapEntry.mvRawValue, mvColumn.getRawValue(ctx));
        }

        if (_doItWithStyle)
        {
            RenderContext ctx = getRenderContext();
            String style = dc.getCssStyle(ctx);
            if (!StringUtils.isEmpty(style))
                colMap.put(ColMapEntry.style, style);
        }

        return colMap;
    }

    @Override
    protected Map<String, Object> getFileUrlMeta(DisplayColumn fileColumn)
    {
        //file urls now returned in the 'url' column map property
        return null;
    }
}
