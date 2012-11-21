/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.jfree.util.StringUtils;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.List;

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
    public enum ColumnHeaderType {caption, propertyName, queryColumnName}

    private ColumnHeaderType _columnHeaderType = ColumnHeaderType.caption;
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

    protected void writeColumnHeaders(RenderContext ctx, Iterable<DisplayColumn> columns)
    {
        writeLine(getColumnHeaders(ctx, columns));
    }

    protected Iterable<String> getColumnHeaders(RenderContext ctx, Iterable<DisplayColumn> columns)
    {
        List<String> headers = new ArrayList<String>();
        for (DisplayColumn dc : columns)
        {
            if (dc.isVisible(ctx))
            {
                headers.add(getColumnHeader(dc));
            }
        }

        return headers;
    }

    protected String getColumnHeader(DisplayColumn dc)
    {
        switch(_columnHeaderType)
        {
            case queryColumnName:
            {
                ColumnInfo columnInfo = dc.getColumnInfo();
                String name;
                if (columnInfo != null)
                {
                    name = columnInfo.getName();
                    // Importers don't expect "_MVIndicator"
                    if (columnInfo.isMvIndicatorColumn() && StringUtils.endsWithIgnoreCase(name, "_" + MvColumn.MV_INDICATOR_SUFFIX))
                        name = name.substring(0, name.length()-MvColumn.MV_INDICATOR_SUFFIX.length()-1) + MvColumn.MV_INDICATOR_SUFFIX;
                    name = FieldKey.fromString(name).toDisplayString();
                }
                else
                {
                    name = dc.getName();
                }
                return name;
            }

            case propertyName:
                return dc.getName();

            default:
            case caption:
                return dc.getCaption(null, false);
        }
    }

    /** Get the unquoted column values. */
    protected Iterable<String> getValues(RenderContext ctx, Iterable<DisplayColumn> displayColumns)
    {
        List<String> values = new ArrayList<String>();
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
            return (null == rawValue ? "" : String.valueOf(rawValue));
        }
    }

}
