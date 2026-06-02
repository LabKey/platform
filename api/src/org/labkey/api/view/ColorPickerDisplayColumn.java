/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.writer.HtmlWriter;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.id;

/**
 * {@link org.labkey.api.data.DisplayColumn} that shows an ExtJS-based color picker component for insert/update forms
 * and a small square of the color in grid views.
 */
public class ColorPickerDisplayColumn extends DataColumn
{
    public ColorPickerDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        Object value = getValue(ctx);
        if (value != null)
        {
            DIV(
                at(style, "height: 20px; width: 20px; background: #" + value)
            ).appendTo(out);
        }
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        String name = getFormFieldName(ctx);
        renderHiddenFormInput(out, name, value);

        String renderId = "color-picker-div-" + UniqueID.getRequestScopedUID(ctx.getRequest());

        SCRIPT(
            JavaScriptFragment.unsafe(
                "   LABKEY.requiresExt4Sandbox(function(){\n" +
                "      Ext4.onReady(function(){\n" +
                "        Ext4.create('Ext.picker.Color', {\n" +
                "            renderTo     : " + PageFlowUtil.jsString(renderId) + ",\n" +
                "            value        : " + PageFlowUtil.jsString(value == null ? null : value.toString()) + ",\n" +
                "            listeners: { \n" +
                "                select: function(picker, selColor) {\n" +
                "                    document.getElementsByName(" + PageFlowUtil.jsString(name) + ")[0].value = selColor;\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "      });\n" +
                "   });\n")
        ).appendTo(out);

        DIV(id(renderId)).appendTo(out);
    }
}
