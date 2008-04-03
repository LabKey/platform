package org.labkey.api.study.assay;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.view.JspView;
import org.labkey.api.view.HttpView;
import org.labkey.api.study.actions.AssayRunUploadForm;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Aug 3, 2007
 */
public class AssayDataCollectorDisplayColumn extends SimpleDisplayColumn
{
    private final AssayRunUploadForm _form;
    private ColumnInfo _col;

    public AssayDataCollectorDisplayColumn(AssayRunUploadForm form)
    {
        _form = form;
        setCaption("Run Data");
        _col = new ColumnInfo("Run Data");
        _col.setInputType("file");
    }

    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='ms-searchform'>");
        renderTitle(ctx, out);
        out.write(" *");
        out.write("</td>");
    }

    public boolean isEditable()
    {
        return true;
    }
    
    public ColumnInfo getColumnInfo()
    {
        return _col;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        HttpView descriptionView = _form.getProvider().getDataDescriptionView(_form);
        JspView view = new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/dataUpload.jsp", _form);
        try
        {
            if (descriptionView != null)
            {
                descriptionView.render(ctx.getRequest(), ctx.getViewContext().getResponse());
            }
            view.render(ctx.getRequest(), ctx.getViewContext().getResponse());
        }
        catch (Exception e)
        {
            throw (IOException)new IOException().initCause(e);
        }
    }
}
