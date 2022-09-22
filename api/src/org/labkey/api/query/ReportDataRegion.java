/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.json.old.JSONObject;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
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
        super.render(ctx, request, response);
    }

    @Override
    protected void renderCenterContent(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount) throws IOException
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
        ButtonBar buttonBar = getButtonBar(MODE_GRID);
        if (buttonBar != null)
            buttonBar.render(ctx, out);
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

    @Override
    protected JSONObject toJSON(RenderContext ctx)
    {
        var json = super.toJSON(ctx);
        var descriptor = _report.getDescriptor();

        var reportJson = new JSONObject();
        reportJson.put("id", descriptor.getReportId().toString());
        reportJson.put("name", descriptor.getReportName());
        reportJson.put("source", StringUtils.defaultIfEmpty(descriptor.getProperty(ReportDescriptor.Prop.viewName), "default"));

        json.put("report", reportJson);

        return json;
    }
}
