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

import org.labkey.api.util.DOM;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static org.labkey.api.util.DOM.TD;

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

    public void writeSameCheckboxCell(RenderContext ctx, HtmlWriter out)
    {
        TD(
            isCopyable() ? (DOM.Renderable) ret -> {

                String id = getGroupFormFieldName(ctx) + "CheckBox";
                InputBuilder.checkbox().name(id).id(id).appendTo(out);
                StringBuilder onChange = new StringBuilder("b = this.checked;\n");

                // Index starts at 1 -- always leave the first column visible
                for (int i = 1; i < _columns.size(); i++)
                {
                    DisplayColumn col = getColumns().get(i);
                    ColumnInfo colInfo = col.getColumnInfo();
                    if (colInfo != null)
                    {
                        // Issue 53620: instead of hiding the input, set it "disabled" via CSS (but not actually disabled so it will still submit)
                        onChange.append("document.getElementsByName('").append(col.getFormFieldName(ctx)).append("')[0].style.opacity = b ? 0.6 : 1;\n");
                        onChange.append("document.getElementsByName('").append(col.getFormFieldName(ctx)).append("')[0].style.pointerEvents = b ? 'none' : 'all';\n");
                    }
                }

                onChange.append(" if (b) { ")
                    .append(getGroupFormFieldName(ctx))
                    .append("Updated(); }\n");

                HttpView.currentPageConfig().addHandler(id, "change", onChange.toString());

                return ret;
            } :
            null
        ).appendTo(out);
    }

    /** Use propName because DOM ids and function names can't have spaces */
    private String getGroupFormFieldName(RenderContext ctx)
    {
        return PageConfig.makeIdFromName(getColumns().getFirst().getFormFieldName(ctx));
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

        if (getColumns().getFirst().getColumnInfo() != null)
        {
            String valueProperty = "value";
            String inputType = getColumns().getFirst().getColumnInfo().getInputType();
            if ("select".equalsIgnoreCase(inputType))
            {
                valueProperty = "selectedIndex";
            }
            else if ("checkbox".equalsIgnoreCase(inputType))
            {
                valueProperty = "checked";
            }
            out.write("    var v = document.getElementsByName('" + getColumns().getFirst().getFormFieldName(ctx) + "')[0]." + valueProperty + ";\n");
            for (int i = 1; i < getColumns().size(); i++)
            {
                out.write("    document.getElementsByName('" + getColumns().get(i).getFormFieldName(ctx) + "')[0]." + valueProperty + " = v;\n");
            }
        }
        out.write("  }\n");
        out.write("}\n");

        out.write("var e = document.getElementsByName('" + getColumns().getFirst().getFormFieldName(ctx) + "');\n");
        out.write("if (e.length > 0) {");
        out.write("  e[0].onchange=" + groupName + "Updated;\n");
        out.write("  e[0].onkeyup=" + groupName + "Updated;\n");
        out.write("}");
    }

    public void appendCopyableOnChangeHandler(RenderContext ctx, StringBuilder sb)
    {
        if (isCopyable())
        {
            sb.append("document.getElementById('")
                .append(getGroupFormFieldName(ctx))
                .append("CheckBox').checked = this.checked; document.getElementById('")
                .append(getGroupFormFieldName(ctx))
                .append("CheckBox').onchange();");
        }
    }
}
