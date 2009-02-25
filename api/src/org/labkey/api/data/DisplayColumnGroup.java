/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import java.util.List;
import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
* Date: Mar 5, 2008
*/
public class DisplayColumnGroup
{
    private final List<DisplayColumn> _columns;
    private final String _name;
    private final boolean _copyable;

    public DisplayColumnGroup(List<DisplayColumn> columns, String name, boolean copyable)
    {
        _columns = columns;
        _name = name;
        _copyable = copyable;
    }

    public List<DisplayColumn> getColumns()
    {
        return _columns;
    }

    public String getName()
    {
        return _name;
    }

    public boolean isCopyable()
    {
        return _copyable;
    }

    public void writeSameCheckboxCell(RenderContext ctx, Writer out)
            throws IOException
    {
        out.write("<td>");
        if (isCopyable())
        {
            out.write("<input type=checkbox name='" + ctx.getForm().getFormFieldName(getColumns().get(0).getColumnInfo()) + "CheckBox' id='" + ctx.getForm().getFormFieldName(getColumns().get(0).getColumnInfo()) + "CheckBox' onchange=\"");
            out.write("b = this.checked;" );
            for (int i = 1; i < getColumns().size(); i++)
            {
                DisplayColumn col = getColumns().get(i);
                ColumnInfo colInfo = col.getColumnInfo();
                if (colInfo != null)
                {
                    out.write("document.getElementsByName('" + ctx.getForm().getFormFieldName(colInfo) + "')[0].style.display = b ? 'none' : 'block';\n");
                }
            }
            out.write(" if (b) { " + ctx.getForm().getFormFieldName(getColumns().get(0).getColumnInfo()) + "Updated(); }\">");
        }
        out.write("</td>");
    }
    
}
