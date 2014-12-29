/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: 12/23/2014
 */
public class ColorPickerDisplayColumn extends DataColumn
{
    public ColorPickerDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        if (value != null)
        {
            out.write("<div style=\"height: 20px; width: 20px; background: #" + PageFlowUtil.filter(value.toString()) + "\"></div>");
        }
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        String name = getFormFieldName(ctx);
        renderHiddenFormInput(ctx, out, name, value);

        String renderId = "color-picker-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());

        out.write("<script type=\"text/javascript\">LABKEY.requiresExt4Sandbox()</script>\n");
        out.write("<script type=\"text/javascript\">");
        out.write("Ext4.onReady(function(){\n" +
                "        Ext4.create('Ext.picker.Color', {\n" +
                "            renderTo        : " + PageFlowUtil.jsString(renderId) + ",\n" +
                "            value        : " + PageFlowUtil.jsString(value == null ? null : value.toString()) + ",\n" +
                "            listeners: { \n" +
                "                select: function(picker, selColor) {\n" +
                "                    document.getElementsByName(" + PageFlowUtil.jsString(name) + ")[0].value = selColor;\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "      });\n");
        out.write("</script>\n");
        out.write("<div id='");
        out.write(PageFlowUtil.filter(renderId));
        out.write("'></div>");
    }
}
