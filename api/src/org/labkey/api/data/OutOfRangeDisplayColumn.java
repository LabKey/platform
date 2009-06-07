/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jul 30, 2007
 */
public class OutOfRangeDisplayColumn extends DataColumn
{
    private final ColumnInfo _oorIndicatorColumn;
    private boolean _doneWithSuperclassConstructor = true;

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

    public String getFormattedValue(RenderContext ctx)
    {
        StringBuilder result = new StringBuilder();
        if (_oorIndicatorColumn != null)
        {
            Object oorValue = _oorIndicatorColumn.getValue(ctx);
            if (oorValue != null)
            {
                result.append(oorValue);
            }
        }
        else
        {
            // Try to only show the error message for the first row
            int row = 1;
            if (ctx.getResultSet() != null)
            {
                try
                {
                    row = ctx.getResultSet().getRow();
                }
                catch (SQLException e) {}
            }
            if (row == 1)
            {
                result.append("<missing column ");
                result.append(getColumnInfo().getName());
                result.append(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
                result.append("> ");
            }
        }
        result.append(super.getFormattedValue(ctx));
        return result.toString();
    }
    
    public Object getDisplayValue(RenderContext ctx)
    {
        return getFormattedValue(ctx);
    }

    public String getTsvFormattedValue(RenderContext ctx)
    {
        return getFormattedValue(ctx);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String value = getFormattedValue(ctx);

        if ("".equals(value.trim()))
        {
            out.write("&nbsp;");
        }
        else
        {
            out.write(h(value));
        }
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_oorIndicatorColumn != null)
        {
            columns.add(_oorIndicatorColumn);
        }
    }
}
