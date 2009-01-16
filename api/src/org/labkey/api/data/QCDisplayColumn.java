/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import java.util.Set;

/**
 * User: jgarms
 * Date: Jan 08, 2009
 */
public class QCDisplayColumn extends DataColumn
{
    private final ColumnInfo qcValueColumn;

    public QCDisplayColumn(ColumnInfo dataColumn, ColumnInfo qcValueColumn)
    {
        super(dataColumn);
        this.qcValueColumn = qcValueColumn;
    }


    public Class getDisplayValueClass()
    {
        return String.class;
    }

    public String getFormattedValue(RenderContext ctx)
    {
        Object qcValue = qcValueColumn.getValue(ctx);
        if (qcValue != null)
        {
            return qcValue.toString();
        }
        return super.getFormattedValue(ctx);
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
        columns.add(qcValueColumn);
    }
}