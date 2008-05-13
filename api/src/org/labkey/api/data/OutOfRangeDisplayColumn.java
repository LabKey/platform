/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.util.Set;
import java.io.Writer;
import java.io.IOException;

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
        Object oorValue = _oorIndicatorColumn.getValue(ctx);
        if (oorValue != null)
        {
            result.append(oorValue);
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
        columns.add(_oorIndicatorColumn);
    }
}
