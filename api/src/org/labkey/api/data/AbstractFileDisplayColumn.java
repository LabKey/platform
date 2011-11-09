package org.labkey.api.data;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * Provides a consistent UI for both attachment (BLOB) and file link (file system) files
 * User: jeckels
 * Date: Nov 7, 2011
 */
public abstract class AbstractFileDisplayColumn extends DataColumn
{
    public AbstractFileDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, getFileName(getValue(ctx)), true);
    }

    /** @return the short name of the file (not including full path) */
    protected abstract String getFileName(Object value);
    
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link) throws IOException
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
            renderIconAndFilename(ctx, out, filename, false);
            out.write("&nbsp;[<a href=\"javascript:{}\" onClick=\"");

            out.write("document.getElementById('" + divId + "').innerHTML = " + PageFlowUtil.filter(PageFlowUtil.jsString(filePicker + "<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><span class=\"labkey-message\">Previous file " + filename + " will be removed.</span>")) + "\"");
            out.write(">remove");
            out.write("</a>]");
            out.write("</div>\n");
        }
    }
}
