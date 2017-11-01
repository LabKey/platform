/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.query.reports.chart;

import com.google.gwt.user.client.rpc.SerializableException;
import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.model.GWTChartRenderer;
import org.labkey.api.gwt.client.model.GWTChartColumn;
import org.labkey.api.gwt.client.ui.ChartService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.Pair;
import org.labkey.query.reports.ReportsController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Dec 3, 2007
 */
public class ChartServiceImpl extends BaseRemoteService implements ChartService
{
    public ChartServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTChart getChart(int id)
    {
        return null;
    }

    public String saveChart(GWTChart chart) throws SerializableException
    {
        try
        {
            if (getUser().isGuest())
                throw new SerializableException("Unable to save report, you must be logged in");

            ChartDesignerBean bean = new ChartDesignerBean();
            PropertyUtils.copyProperties(bean, chart);
            if (!chart.isShared())
                bean.setOwner(_context.getUser().getUserId());

            Report report = bean.getReport(getViewContext());
            if (report != null)
            {
                final String key = ReportUtil.getReportQueryKey(report.getDescriptor());
                if (!ReportService.get().reportNameExists(_context, chart.getReportName(), key))
                {
                    ReportService.get().saveReport(_context, key, report);
                }
                else
                    throw new SerializableException("There is already a report with the name of: '" + report.getDescriptor().getReportName() +
                            "'. Please specify a different name.");
            }
            else
                throw new RuntimeException("Unable to save report, the report could not be instantiated");
            return null;
        }
        catch (SerializableException se)
        {
            throw se;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public List<GWTChartRenderer> getChartRenderers(GWTChart chart)
    {
        List<GWTChartRenderer> gwtRenderers = new ArrayList<>();

        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), chart.getSchemaName());
        if (schema != null)
        {
            QuerySettings qs = schema.getSettings(_context, null, chart.getQueryName());
            qs.setViewName(chart.getViewName());

            QueryView view = new QueryView(schema, qs, null);

            for (ChartRenderer renderer : ChartRendererFactory.get().getChartRenderers())
            {
                GWTChartRenderer cr = new GWTChartRenderer();

                cr.setName(renderer.getName());
                cr.setType(renderer.getType());
                cr.setColumnX(createColumnList(renderer.getDisplayColumns(view, true)));
                cr.setColumnY(createColumnList(renderer.getDisplayColumns(view, false)));

                gwtRenderers.add(cr);
            }
        }

        return gwtRenderers;
    }

    private List<GWTChartColumn> createColumnList(Map<String, String> columnMap)
    {
        List<GWTChartColumn> columns = new ArrayList<>();

        for (Map.Entry<String, String> entry : columnMap.entrySet())
            columns.add(new GWTChartColumn(entry.getKey(), entry.getValue()));
        return columns;
    }

    public String getDisplayURL(GWTChart chart)
    {
        ChartDesignerBean bean = new ChartDesignerBean();
        try
        {
            PropertyUtils.copyProperties(bean, chart);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        ActionURL url = new ActionURL(ReportsController.PlotChartAction.class, _context.getContainer());

        for (Pair<String, String> param : bean.getParameters())
        {
            url.addParameter(param.getKey(), param.getValue());
        }

        return url.toString();
    }
}
