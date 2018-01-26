/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.query;


import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
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
import java.sql.SQLException;
import java.util.List;

/**
 * User: klum
 * Date: Jun 23, 2011
 * Time: 7:27:25 PM
 */
public class ReportDataRegion extends DataRegion
{
    private HttpView _reportView;
    private Report _report;
    private ButtonBar _buttonBar;

    private HttpServletRequest _request;
    private HttpServletResponse _response;

    public ReportDataRegion(QuerySettings settings, ViewContext context, Report report)
    {
        setSettings(settings);

        try
        {
            // subtle, but we always want to use the data region specified from the enclosing query
            report.getDescriptor().setProperty(ReportDescriptor.Prop.dataRegionName, settings.getDataRegionName());

            // Update in case the resolved schemaName and queryName differ from the one that was stored (which
            // will be the case if the report is associated with a legacy schema/query name)
            report.getDescriptor().setProperty(ReportDescriptor.Prop.schemaName, settings.getSchemaName());
            report.getDescriptor().setProperty(ReportDescriptor.Prop.queryName, settings.getQueryName());
            _report = report;
            _reportView = report.getRunReportView(context);
            setAllowHeaderLock(false);
            setShowPagination(false);
            setShowPaginationCount(false);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void render(RenderContext ctx, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        _request = request;
        _response = response;

        String name = _report.getDescriptor().getReportName();
        String source = StringUtils.defaultIfEmpty(_report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName), "default");

        String msg = "<span class=\"labkey-strong\">Name:</span>&nbsp;" + PageFlowUtil.filter(name);
        msg += "&nbsp;";
        msg += "<span class=\"labkey-strong\" style=\"padding-left: 30px;\">Source:</span>&nbsp;" + PageFlowUtil.filter(source);

        addMessage(new Message(msg, MessageType.INFO, "report"));
        super.render(ctx, request, response);
    }

    @Override
    protected void renderCenterContent(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount) throws IOException, SQLException
    {
        try
        {
            _reportView.render(ctx, _request, _response);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    @Override
    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return true;
    }

    @Override
    protected void renderButtons(RenderContext ctx, Writer out) throws IOException
    {
        if (_buttonBar != null)
            _buttonBar.render(ctx, out);
    }

    public ButtonBar getButtonBar()
    {
        return _buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar)
    {
        _buttonBar = buttonBar;
    }

    @Override
    public boolean getAllowHeaderLock()
    {
        return false;
    }

    @Override
    protected boolean usesResultSet()
    {
        return false;
    }

    @Override
    protected boolean useTableWrap()
    {
        return false;
    }
}
