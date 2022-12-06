/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracted DisplayColumn handling out of TSVGridWriter so rendering DisplayColumns
 * may be used without a ResultSet.  You will still need to set up a RenderContext
 * for DisplayColumn to render values.
 *
 * User: kevink
 * Date: 9/9/11
 */
public abstract class TSVColumnWriter extends TSVWriter
{
    private ColumnHeaderType _columnHeaderType = ColumnHeaderType.Caption;
    private boolean _applyFormats = true;

    public ColumnHeaderType getColumnHeaderType()
    {
        return _columnHeaderType;
    }

    public void setColumnHeaderType(ColumnHeaderType columnHeaderType)
    {
        _columnHeaderType = columnHeaderType;
    }

    public boolean isApplyFormats()
    {
        return _applyFormats;
    }

    public void setApplyFormats(boolean applyFormats)
    {
        _applyFormats = applyFormats;
    }

    protected void writeColumnHeaders(RenderContext ctx, Iterable<DisplayColumn> columns, @NotNull Map<String, String> renameColumn)
    {
        if (_columnHeaderType != null && _columnHeaderType != ColumnHeaderType.None)
            writeLine(getColumnHeaders(ctx, columns, renameColumn));
    }

    protected Iterable<String> getColumnHeaders(RenderContext ctx, Iterable<DisplayColumn> columns, @NotNull Map<String, String> renameColumn)
    {
        List<String> headers = new ArrayList<>();
        for (DisplayColumn dc : columns)
        {
            if (dc.isVisible(ctx))
            {
                String colName = dc.getName();
                String header = _columnHeaderType.getText(dc);
                if (renameColumn.containsKey(colName))
                    header = renameColumn.get(colName);
                else if (dc instanceof DataColumn)
                {
                    if (((DataColumn) dc).getBoundColumn() != null)
                    {
                        String fieldKey = ((DataColumn) dc).getBoundColumn().getFieldKey().toString();
                        if (renameColumn.containsKey(fieldKey))
                            header = renameColumn.get(fieldKey);

                    }
                }
                headers.add(header);
            }
        }

        return headers;
    }


    /** Get the unquoted column values. */
    protected Iterable<String> getValues(RenderContext ctx, Iterable<DisplayColumn> displayColumns)
    {
        List<String> values = new ArrayList<>();
        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isVisible(ctx))
            {
                values.add(getValue(ctx, dc));
            }
        }
        return values;
    }

    protected String getValue(RenderContext ctx, DisplayColumn dc)
    {
        // Export formatted values some of the time; see #10771
        if (isApplyFormats())
        {
            return dc.getTsvFormattedValue(ctx);
        }
        else
        {
            Object rawValue = dc.getValue(ctx);
            return (null == rawValue ? null : String.valueOf(rawValue));
        }
    }
}
