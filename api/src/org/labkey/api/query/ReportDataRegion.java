package org.labkey.api.query;


import org.labkey.api.data.AbstractDataRegion;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.RenderContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 23, 2011
 * Time: 7:27:25 PM
 */
public class ReportDataRegion extends AbstractDataRegion
{
    private HttpView _reportView;
    private ButtonBar _buttonBar;

    public ReportDataRegion(QuerySettings settings, ViewContext context, Report report)
    {
        setSettings(settings);

        try {
            report.getDescriptor().setProperty(ReportDescriptor.Prop.dataRegionName, settings.getDataRegionName());
            _reportView = report.getRunReportView(context);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void render(RenderContext ctx, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        try {
            StringBuilder sb = new StringBuilder();
            Writer out = response.getWriter();

            addFilterMessage(sb, ctx, true);
            renderHeaderScript(ctx, out, sb.toString());

            // for now set the width to 100%, but we want to be smarter about calculating the viewport width less scroll
            out.write("\n<table width=\"100%\" class=\"labkey-data-region");
            out.write(" labkey-show-borders\"");
            out.write(" id=\"");
            out.write(PageFlowUtil.filter("dataregion_" + getName()));
            out.write("\">\n");

            renderHeader(ctx, out, true, 0);

            out.write("<tr><td>");
            _reportView.render(ctx, request, response);
            out.write("</td></tr>");

            out.write("</table>");
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
        //_report.getRunReportView(getViewContext()).render(model, request, response);

        //super.render(ctx, request, response);
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
/*
        StringBuilder sb = new StringBuilder();

        if (_buttonBar != null)
            _button
        addFilterMessage(sb, ctx, true);
        renderHeaderScript(ctx, out, sb.toString());

        out.write("<table class=\"labkey-data-region-header\" id=\"" + PageFlowUtil.filter("dataregion_header_" + getName()) + "\">\n");
        renderMessageBox(ctx, out, 0);
        out.write("</table>");
*/
    }

    @Override
    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return true;
    }

    @Override
    protected void renderButtons(RenderContext ctx, Writer out) throws IOException
    {
        _buttonBar.render(ctx, out);
    }

    @Override
    protected void renderPagination(RenderContext ctx, Writer out, PaginationLocation location) throws IOException
    {
    }

    public ButtonBar getButtonBar()
    {
        return _buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar)
    {
        _buttonBar = buttonBar;
    }
}
