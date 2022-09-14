/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.IMultiValuedDisplayColumn;
import org.labkey.api.data.MVDisplayColumn;
import org.labkey.api.data.NestedPropertyDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.Pair;

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
    boolean _arrayMultiValueColumns = false;
    boolean _includeFormattedValue = false;


    public enum ColMapEntry
    {
        // fieldKey is only used when rendering nested properties as an ordered list
        fieldKey,
        value,
        displayValue,
        formattedValue,
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

    public ExtendedApiQueryResponse(QueryView view, boolean schemaEditable, boolean includeLookupInfo, String schemaName, String queryName, long offset,
                                    List<FieldKey> fieldKeys, boolean metaDataOnly, boolean includeDetailsColumn, boolean includeUpdateColumn, boolean includeMetaData)
    {
        super(view, schemaEditable, includeLookupInfo, schemaName, queryName, offset, fieldKeys, metaDataOnly, includeDetailsColumn, includeUpdateColumn, false, includeMetaData);
    }

    // When true, serialize multi-value columns as an array of objects containing 'value', 'url', 'displayValue'
    public void arrayMultiValueColumns(boolean arrayMultiValueColumns)
    {
        _arrayMultiValueColumns = arrayMultiValueColumns;
    }

    // When true, add the formattedValue property to the response.
    // Currently, the DisplayColumn.getDisplayValue() will return the display object without any formatting
    // and DisplayColumn.getFormattedValue() is formatted html for display within the DataRegion.
    // Prior to this setting, we didn't include the value with just basic formatting applied and without html encoding.
    public void includeFormattedValue(boolean includeFormattedValue)
    {
        _includeFormattedValue = includeFormattedValue;
    }

    @Override
    protected double getFormatVersion()
    {
        return 9.1;
    }

    @Override
    protected void putValue(Map<String, Object> row, DisplayColumn dc)
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
        return createColMap(getRenderContext(), dc, _arrayMultiValueColumns, _includeFormattedValue, _doItWithStyle);
    }

    /**
     * Return an object representing the DisplayColumn's value in the RenderContext.
     *
     * For basic column, the return value is a {@link ColMap}.
     * For multi-value column, the return value is a {@link List<ColMap>}
     * For a nested properties column, the return value is a {@link List<ColMap>}.
     */
    public static Object createColMap(
            RenderContext ctx,
            DisplayColumn dc,
            boolean arrayMultiValueColumns,
            boolean includeFormattedValue,
            boolean doItWithStyle)
    {
        if (dc instanceof NestedPropertyDisplayColumn npc)
        {
            return getNestedPropertiesArray(ctx, npc, arrayMultiValueColumns, includeFormattedValue, doItWithStyle);
        }
        else if (arrayMultiValueColumns && dc instanceof IMultiValuedDisplayColumn mdc)
        {
            // render MultiValue columns as an array of 'value', 'displayValue', and 'url' objects
            return getMultiValuedColumnArray(ctx, includeFormattedValue, mdc);
        }
        else
        {
            return getColMap(ctx, dc, includeFormattedValue, doItWithStyle);
        }
    }

    @NotNull
    private static ColMap getColMap(RenderContext ctx, DisplayColumn dc, boolean includeFormattedValue, boolean doItWithStyle)
    {
        //column value
        Object value = dc.getJsonValue(ctx);
        Object displayValue = dc.getDisplayValue(ctx);
        String formattedValue = null;
        if (includeFormattedValue)
            formattedValue = dc.getFormattedText(ctx);

        String url = null;
        if (null != value)
            url = dc.renderURL(ctx);

        //in the extended response format, each column will have a map of its own
        //that will contain entries for value, mvValue, mvIndicator, etc.
        ColMap colMap = makeColMap(value, displayValue, formattedValue, url, includeFormattedValue);

        //missing values
        if (dc instanceof MVDisplayColumn)
        {
            MVDisplayColumn mvColumn = (MVDisplayColumn) dc;
            colMap.put(ColMapEntry.mvValue, mvColumn.getMvIndicator(ctx));
            colMap.put(ColMapEntry.mvRawValue, mvColumn.getRawValue(ctx));
        }

        if (doItWithStyle)
        {
            String style = dc.getCssStyle(ctx);
            if (!StringUtils.isEmpty(style))
                colMap.put(ColMapEntry.style, style);
        }

        return colMap;
    }

    @Nullable
    private static List<ColMap> getMultiValuedColumnArray(RenderContext ctx, boolean includeFormattedValue, IMultiValuedDisplayColumn mdc)
    {
        List<Object> values = mdc.getJsonValues(ctx);
        if (values == null)
            return null;

        List<String> urls = mdc.renderURLs(ctx);
        List<Object> display = mdc.getDisplayValues(ctx);
        List<String> formatted = null;
        if (includeFormattedValue)
            formatted = mdc.getFormattedTexts(ctx);

        assert values.size() == urls.size() && values.size() == display.size();
        List<ColMap> list = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++)
        {
            Object value = values.get(i);
            Object displayValue = display.get(i);
            String formattedValue = null;
            if (includeFormattedValue)
                formattedValue = formatted.get(i);
            String url = urls.get(i);
            ColMap nested = makeColMap(value, displayValue, formattedValue, url, includeFormattedValue);

            // TODO: missing value indicators ?

            list.add(nested);
        }
        return list;
    }

    @NotNull
    public static List<ColMap> getNestedPropertiesArray(RenderContext ctx, NestedPropertyDisplayColumn npc, boolean arrayMultiValueColumns, boolean includeFormattedValue, boolean doItWithStyle)
    {
        List<ColMap> nestedProperties = new ArrayList<>();
        for (Pair<RenderContext, DisplayColumn> pair : npc.getNestedDisplayColumns(ctx))
        {
            RenderContext nestedCtx = pair.first;
            DisplayColumn nestedCol = pair.second;

            String key = npc.getNestedColumnKey(nestedCol);

            // NOTE: For now, we are assuming all of the nested property columns are basic display columns.
            ColMap colMap = getColMap(nestedCtx, nestedCol, includeFormattedValue, doItWithStyle);
            colMap.put(ColMapEntry.fieldKey, key);
            nestedProperties.add(colMap);
        }
        return nestedProperties;
    }

    protected static ColMap makeColMap(
            @Nullable Object value, @Nullable Object displayValue, @Nullable String formattedValue, @Nullable String url,
            boolean includeFormattedValue)
    {
        ColMap colMap = new ColMap();

        value = ensureJSONDate(value);
        colMap.put(ColMapEntry.value, value);

        displayValue = ensureJSONDate(displayValue);
        if (null != displayValue && !displayValue.equals(value))
            colMap.put(ColMapEntry.displayValue, displayValue);

        if (includeFormattedValue && formattedValue != null && !formattedValue.equals(displayValue))
            colMap.put(ColMapEntry.formattedValue, formattedValue);

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
