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
package org.labkey.list.view;

import org.labkey.api.data.*;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.Collections;

/**
 * User: adam
 * Date: Feb 12, 2008
 * Time: 1:52:50 PM
 */
public class AttachmentDisplayColumn extends DataColumn
{
    private ColumnInfo _colEntityId;

    public AttachmentDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        if (null == _colEntityId)
            return null;

        String filename = (String)getValue(ctx);
        String entityId = (String)_colEntityId.getValue(ctx);
        return ListController.getDownloadURL(ctx.getContainer(), entityId, filename).getLocalURIString();
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true);
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        String filename = (String)value;
        String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());
        String labelName = getBoundColumn().getAlias();

        // TODO: modify outputName to return a String and use that here
        String filePicker = "<input name=\"" + formFieldName + "\"";

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            filePicker += (" id=\"" + setFocusId + "\"");
            ctx.remove("setFocusId");
        }

        filePicker += " type=\"file\" size=\"60\" onChange=\"showPathname(this, &quot;" + labelName + "&quot;)\">&nbsp;<label id=\"" + labelName + "\"></label>\n";

        if (null == filename)
        {
            out.write(filePicker);
        }
        else
        {
            String divId = "div_" + getBoundColumn().getAlias();

            out.write("<div id=\"" + divId + "\">");
            renderIconAndFilename(ctx, out, filename, false);
            out.write("&nbsp;[<a href=\"javascript:{}\" onClick=\"");

            out.write("document.getElementById('" + divId + "').innerHTML = " + PageFlowUtil.filter(PageFlowUtil.jsString(filePicker + "&nbsp;<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><br><span class=\"labkey-message\">Previous file, \"" + filename + "\", has been deleted; click \"Cancel\" to restore it.</span>")) + "\" class=\"labkey-message\"");
            out.write(">delete");
            out.write("</a>]\n");
            out.write("</div>");
        }
    }

    private void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link) throws IOException
    {
        if (null != filename)
        {
            String url = null;

            if (link)
            {
                url = renderURL(ctx);

                if (null != url)
                {
                    out.write("<a href=\"");
                    out.write(PageFlowUtil.filter(renderURL(ctx)));
                    out.write("\">");
                }
            }

            out.write("<img src=\"" + ctx.getRequest().getContextPath() + Attachment.getFileIcon(filename) + "\" alt=\"icon\"/>&nbsp;" + filename);

            if (link && null != url)
            {
                out.write("</a>");
            }
        }
        else
        {
            out.write("&nbsp;");
        }
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        TableInfo table = getBoundColumn().getParentTable();
        FieldKey currentKey = FieldKey.fromString(getBoundColumn().getName());
        FieldKey parentKey = currentKey.getParent();

        FieldKey entityKey = new FieldKey(parentKey, "EntityId");

        _colEntityId = QueryService.get().getColumns(table, Collections.singleton(entityKey)).get(entityKey);

        if (null != _colEntityId)
            columns.add(_colEntityId);
    }
}
