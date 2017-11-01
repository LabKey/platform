/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Used to render columns that persist their values into two separate database columns - the raw value, and a separate
 * String column that indicates if the value is considered out-of-range (OOR) or not. Similar to MVDisplayColumn, although
 * OOR indicators can be arbitrary strings instead of a discrete list of values.
 *
 * Rendered as the raw value prefixed by the OOR indicator (if any).
 *
 * User: jeckels
 * Date: Jul 30, 2007
 */
public class OutOfRangeDisplayColumn extends DataColumn
{
    private ColumnInfo _oorIndicatorColumn;
    private boolean _doneWithSuperclassConstructor = true;

    /**
     * Look up the OORIndicator column through QueryService
     */
    public OutOfRangeDisplayColumn(ColumnInfo numberColumn)
    {
        this(numberColumn, null);
    }

    public OutOfRangeDisplayColumn(ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn)
    {
        super(numberColumn);
        _oorIndicatorColumn = oorIndicatorColumn;
    }

    public Class getDisplayValueClass()
    {
        if (_doneWithSuperclassConstructor)
        {
            return String.class;
        }
        else
        {
            return Double.class;
        }
    }

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        StringBuilder result = new StringBuilder();
        if (_oorIndicatorColumn != null)
        {
            Object oorValue = _oorIndicatorColumn.getValue(ctx);
            if (oorValue != null)
            {
                if (getRequiresHtmlFiltering())
                    result.append(h(oorValue));
                else
                    result.append(oorValue);
            }
        }
        else
        {
            // Try to only show the error message for the first row
            int row = 1;
            if (ctx.getResults() != null)
            {
                try
                {
                    row = ctx.getResults().getRow();
                }
                catch (SQLException ignored)
                {
                }
            }
            if (row == 1)
            {
                String msg = "<missing column " + getColumnInfo().getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX + ">";
                if (getRequiresHtmlFiltering())
                    result.append(h(msg));
                else
                    result.append(msg);
            }
        }
        result.append(super.getFormattedValue(ctx));
        return result.toString();
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getFormattedValue(ctx);
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        return getFormattedValue(ctx);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String value = getFormattedValue(ctx);

        if ("".equals(value.trim()))
        {
            out.write("&nbsp;");
        }
        else
        {
            out.write(value);
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);

        if (_oorIndicatorColumn == null)
        {
            FieldKey fk = new FieldKey(getBoundColumn().getFieldKey().getParent(), getBoundColumn().getFieldKey().getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX);
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(fk));
            _oorIndicatorColumn = cols.get(fk);
        }

        if (_oorIndicatorColumn != null)
        {
            columns.add(_oorIndicatorColumn);
        }
    }
}
