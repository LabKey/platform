/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
            out.write("<input type=checkbox name='" + getGroupFormFieldName(ctx) + "CheckBox' id='" + getGroupFormFieldName(ctx) + "CheckBox' onchange=\"");
            out.write("b = this.checked;" );
            for (int i = 1; i < getColumns().size(); i++)
            {
                DisplayColumn col = getColumns().get(i);
                ColumnInfo colInfo = col.getColumnInfo();
                if (colInfo != null)
                {
                    out.write("document.getElementsByName('" + col.getFormFieldName(ctx) + "')[0].style.display = b ? 'none' : 'block';\n");
                }
            }
            out.write(" if (b) { " + getGroupFormFieldName(ctx) + "Updated(); }\">");
        }
        out.write("</td>");
    }

    private String getGroupFormFieldName(RenderContext ctx)
    {
        return ColumnInfo.propNameFromName(getColumns().get(0).getFormFieldName(ctx));
    }
    
    public void writeCopyableJavaScript(RenderContext ctx, Writer out) throws IOException
    {
        if (!isCopyable())
        {
            return;
        }
        
        String groupName = getGroupFormFieldName(ctx);
        out.write("function " + groupName + "Updated() {\n");
        out.write("  if (document.getElementById('" + groupName + "CheckBox') != null && document.getElementById('" + groupName + "CheckBox').checked) {\n");

        if (getColumns().get(0).getColumnInfo() != null)
        {
            String valueProperty = "value";
            String inputType = getColumns().get(0).getColumnInfo().getInputType();
            if ("select".equalsIgnoreCase(inputType))
            {
                valueProperty = "selectedIndex";
            }
            else if ("checkbox".equalsIgnoreCase(inputType))
            {
                valueProperty = "checked";
            }
            out.write("    var v = document.getElementsByName('" + getColumns().get(0).getFormFieldName(ctx) + "')[0]." + valueProperty + ";\n");
            for (int i = 1; i < getColumns().size(); i++)
            {
                out.write("    document.getElementsByName('" + getColumns().get(i).getFormFieldName(ctx) + "')[0]." + valueProperty + " = v;\n");
            }
        }
        out.write("  }\n");
        out.write("}\n");

        out.write("var e = document.getElementsByName('" + getColumns().get(0).getFormFieldName(ctx) + "');\n");
        out.write("if (e.length > 0) {");
        out.write("  e[0].onchange=" + groupName + "Updated;\n");
        out.write("  e[0].onkeyup=" + groupName + "Updated;\n");
        out.write("}");
    }

    public void writeCopyableOnChangeHandler(RenderContext ctx, Writer out) throws IOException
    {
        if (isCopyable())
        {
            out.write("document.getElementById('" + getGroupFormFieldName(ctx) + "CheckBox').checked = this.checked; document.getElementById('" + getGroupFormFieldName(ctx) + "CheckBox').onchange();");
        }
    }
}
