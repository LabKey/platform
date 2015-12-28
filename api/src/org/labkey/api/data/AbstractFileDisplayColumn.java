/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Provides a consistent UI for both attachment (BLOB) and file link (file system) files
 * User: jeckels
 * Date: Nov 7, 2011
 */
public abstract class AbstractFileDisplayColumn extends DataColumn
{
    MimeMap _map;

    public AbstractFileDisplayColumn(ColumnInfo col)
    {
        super(col);
        _map = new MimeMap();
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    /** @return the short name of the file (not including full path) */
    protected abstract String getFileName(Object value);

    protected abstract InputStream getFileContents(RenderContext ctx, Object value) throws FileNotFoundException;
    
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link, boolean thumbnail) throws IOException
    {
       renderIconAndFilename(ctx, out, filename, null, link, thumbnail);
    }
    
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, @Nullable String fileIconUrl, boolean link, boolean thumbnail) throws IOException
    {
        if (null != filename)
        {
            String url = null;

            if (link)
            {
                url = renderURL(ctx);

                if (null != url)
                {
                    out.write("<a title=\"Download attached file\" href=\"");
                    out.write(PageFlowUtil.filter(url));
                    out.write("\">");
                }
            }

            String displayName = getFileName(filename);
            boolean isImage = filename.toLowerCase().endsWith(".png")
                    || filename.toLowerCase().endsWith(".jpeg")
                    || filename.toLowerCase().endsWith(".jpg");

            if (url != null && thumbnail && isImage)
            {
                StringBuilder popupHtml = new StringBuilder();
                popupHtml.append("<img style=\"max-width:300px; height:auto;\" src=\"");
                popupHtml.append(PageFlowUtil.filter(url));
                popupHtml.append("\" />");

                StringBuilder thumbnailHtml = new StringBuilder();
                thumbnailHtml.append("<img style=\"display:block; height:auto; width:100%; max-width: 32px; vertical-align:middle\"");
                thumbnailHtml.append(" src=\"").append(PageFlowUtil.filter(url)).append("\"");
                thumbnailHtml.append(" title=\"").append(displayName).append("\"");
                thumbnailHtml.append("\" />");

                out.write(PageFlowUtil.helpPopup(displayName, popupHtml.toString(), true, thumbnailHtml.toString(), 310, url == null ? null : "window.location = '" + url + "'"));
            }
            else
            {
                StringBuilder icon = new StringBuilder();
                icon.append("<img src=\"").append(ctx.getRequest().getContextPath());
                icon.append((null != fileIconUrl) ? fileIconUrl : Attachment.getFileIcon(filename));
                icon.append("\" alt=\"icon\"");
                icon.append("/>&nbsp;").append(PageFlowUtil.filter(displayName));

                if (url != null && thumbnail && _map.isInlineImageFor(new File(filename)) ) {
                    StringBuilder thumbnailHtml = new StringBuilder();
                    thumbnailHtml.append("<img style=\"max-width:300px; height:auto;\" src=\"");
                    thumbnailHtml.append(PageFlowUtil.filter(url));
                    thumbnailHtml.append("\" />");

                    out.write(PageFlowUtil.helpPopup(displayName, thumbnailHtml.toString(), true, icon.toString(), 310, url == null ? null : "window.location = '" + url + "'"));
                }
                else
                    out.write(icon.toString());
            }

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

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        String filename = getFileName(value);
        String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());
        String labelId = GUID.makeGUID();

        // TODO: modify outputName to return a String and use that here
        String filePicker = "<input name=\"" + PageFlowUtil.filter(formFieldName) + "\"";

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            filePicker += (" id=\"" + setFocusId + "\"");
            ctx.remove("setFocusId");
        }

        filePicker += " type=\"file\" size=\"60\" onChange=\"showPathname(this, &quot;" + labelId + "&quot;)\">&nbsp;<label id=\"" + labelId + "\"></label>\n";

        if (null == filename)
        {
            // No existing value, so render just the regular <input type=file> element
            out.write(filePicker);
        }
        else
        {
            // Existing value, so tell the user the file name, allow the file to be removed, and a new file uploaded 
            String divId = GUID.makeGUID();

            out.write("<div id=\"" + divId + "\">");
            renderIconAndFilename(ctx, out, filename, false, false);
            out.write("&nbsp;[<a href=\"javascript:{}\" onClick=\"");

            out.write("document.getElementById('" + divId + "').innerHTML = " + PageFlowUtil.filter(PageFlowUtil.jsString(filePicker + "<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><span class=\"labkey-message\">Previous file " + filename + " will be removed.</span>")) + "\"");
            out.write(">remove");
            out.write("</a>]");
            out.write("</div>\n");
        }
    }
}
