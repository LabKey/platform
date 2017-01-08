/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.data.IMultiValuedDisplayColumn;
import org.labkey.api.data.MVDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;

import java.util.ArrayList;
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
    boolean _arrayMultiValueColumns = false;
    boolean _useTsvFormatAsDisplayValue = false;

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

    // When true, serialize multi-value columns as an array of objects containing 'value', 'url', 'displayValue'
    public void arrayMultiValueColumns(boolean arrayMultiValueColumns)
    {
        _arrayMultiValueColumns = arrayMultiValueColumns;
    }

    // When true, use the tsv formatted value as the displayValue.  Currently,
    // the DisplayColumn.getDisplayValue() will return the display object without any formatting
    // and DisplayColumn.getFormattedValue() is formatted html for display within the DataRegion.
    // Prior to this setting, we didn't include the value with just basic formatting applied and without html encoding.
    public void useTsvFormatAsDisplayValue(boolean useTsvFormatAsDisplayValue)
    {
        _useTsvFormatAsDisplayValue = useTsvFormatAsDisplayValue;
    }

    @Override
    protected double getFormatVersion()
    {
        return 9.1;
    }

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc) throws Exception
    {
        Object data = createColMap(dc);

        //put the column map into the row map using the column name as the key
        String columnName = getColumnName(dc);

        if (columnName != null)
        {
            row.put(columnName, data);
        }
    }

    protected Object createColMap(DisplayColumn dc)
    {
        if (_arrayMultiValueColumns && dc instanceof IMultiValuedDisplayColumn)
        {
            // render MultiValue columns as an array of 'value', 'displayValue', and 'url' objects
            IMultiValuedDisplayColumn mdc = (IMultiValuedDisplayColumn)dc;

            List<Object> values = mdc.getJsonValues(getRenderContext());
            if (values == null)
                return null;

            List<String> urls = mdc.renderURLs(getRenderContext());
            List<?> display;
            if (_useTsvFormatAsDisplayValue)
                display = mdc.getTsvFormattedValues(getRenderContext()); // formats values, but does not html encode values -- .getFormattedValue() returns html
            else
                display = mdc.getDisplayValues(getRenderContext()); // does not format values

            assert values.size() == urls.size() && values.size() == display.size();
            List<Map<ColMapEntry, Object>> list = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++)
            {
                Object value = values.get(i);
                Object displayValue = display.get(i);
                String url = urls.get(i);
                ColMap nested = makeColMap(value, displayValue, url);

                // TODO: missing value indicators ?

                list.add(nested);
            }
            return list;
        }
        else
        {
            //column value
            Object value = dc.getJsonValue(_ctx);
            Object displayValue;
            if (_useTsvFormatAsDisplayValue)
                displayValue = dc.getTsvFormattedValue(getRenderContext()); // formats values, but does not html encode values -- .getFormattedValue() returns html
            else
                displayValue = dc.getDisplayValue(getRenderContext()); // does not format

            String url = null;
            if (null != value)
                url = dc.renderURL(getRenderContext());

            //in the extended response format, each column will have a map of its own
            //that will contain entries for value, mvValue, mvIndicator, etc.
            Map<ColMapEntry, Object> colMap = makeColMap(value, displayValue, url);

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
    }

    protected ColMap makeColMap(Object value, Object displayValue, String url)
    {
        ColMap colMap = new ColMap();

        value = ensureJSONDate(value);
        colMap.put(ColMapEntry.value, value);

        displayValue = ensureJSONDate(displayValue);
        if (null != displayValue && !displayValue.equals(value))
            colMap.put(ColMapEntry.displayValue, displayValue);

        if (value != null && url != null)
            colMap.put(ColMapEntry.url, url);

        return colMap;
    }

    @Override
    protected Map<String, Object> getFileUrlMeta(DisplayColumn fileColumn)
    {
        //file urls now returned in the 'url' column map property
        return null;
    }
}
