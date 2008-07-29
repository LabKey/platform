/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.common.util.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 20, 2007
 */
public class ChartUtil
{
    private static final Logger _log = Logger.getLogger(ChartUtil.class);

    public static final String REPORT_ID = "reportId";
    public static final String FORWARD_URL = "forwardUrl";

    public static ActionURL getChartDesignerURL(ViewContext context, ChartDesignerBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlDesignChart(context.getContainer());

        for (Pair<String, String> param : bean.getParameters())
            url.addParameter(param.getKey(), param.getValue());

        return url;
    }

    public static ActionURL getRReportDesignerURL(ViewContext context, RReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateRReport(context.getContainer());
        return _getChartDesignerURL(url, bean);
    }

    protected static ActionURL _getChartDesignerURL(ActionURL url, ReportDesignBean bean)
    {
        url.addParameter("reportType", bean.getReportType());
        if (bean.getSchemaName() != null)
            url.addParameter("schemaName", bean.getSchemaName());
        if (bean.getQueryName() != null)
            url.addParameter("queryName", bean.getQueryName());
        if (bean.getViewName() != null)
            url.addParameter("viewName", bean.getViewName());
        if (bean.getFilterParam() != null)
            url.addParameter(ReportDescriptor.Prop.filterParam.toString(), bean.getFilterParam());
        if (bean.getRedirectUrl() != null)
            url.addParameter("redirectUrl", bean.getRedirectUrl());
        if (bean.getDataRegionName() != null)
            url.addParameter(QueryParam.dataRegionName.toString(), bean.getDataRegionName());
        if (bean.getReportId() != -1)
            url.addParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));

        int i=0;
        for (ReportDesignBean.ExParam param : bean.getExParam())
        {
            url.addParameter("exParam[" + i + "].key", param.getKey());
            url.addParameter("exParam[" + i + "].value", param.getValue());
            i++;
        }
        return url;
    }

    public static ActionURL getPlotChartURL(ViewContext context, Report report)
    {
        ActionURL url = new ActionURL("reports", "plotChart", context.getContainer());

        if (report != null)
        {
            ActionURL filterUrl = RenderContext.getSortFilterURLHelper(context);
            url.addParameter(REPORT_ID, String.valueOf(report.getDescriptor().getReportId()));
            for (Pair<String, String> param : filterUrl.getParameters())
                url.addParameter(param.getKey(), param.getValue());
        }
        return url;
    }

    public static String getShowReportTag(ViewContext context, Report report)
    {
        StringBuilder sb = new StringBuilder();
        if (RReport.TYPE.equals(report.getDescriptor().getReportType()))
        {
            sb.append("<a href='");

/*
            if (BooleanUtils.toBoolean(report.getDescriptor().getProperty("runInBackground")))
                sb.append(getRunBackgroundRReportURL(context, report).toString());
            else
*/
                sb.append(getRunReportURL(context, report).toString());
            sb.append("'>");
            sb.append(PageFlowUtil.filter(report.getDescriptor().getProperty("reportName")));
            sb.append("</a>");
        }
        else
        {
            sb.append("<img src='");
            sb.append(getPlotChartURL(context, report).toString());
            sb.append("'>");
        }
        return sb.toString();
    }

    public static ActionURL getRunReportURL(ViewContext context, Report report)
    {
        ActionURL url = new ActionURL("reports", "runReport", context.getContainer());

        if (report != null)
        {
            //ActionURL filterUrl = RenderContext.getSortFilterURLHelper(context);
            if (report.getDescriptor().getReportId() != -1)
                url.addParameter(REPORT_ID, String.valueOf(report.getDescriptor().getReportId()));
            if (context.getActionURL().getParameter("redirectUrl") != null)
                url.addParameter("redirectUrl", context.getActionURL().getParameter("redirectUrl"));
            else
                url.addParameter("redirectUrl", context.getActionURL().getLocalURIString());
            //for (Pair<String, String> param : filterUrl.getParameters())
            //    url.addParameter(param.getKey(), param.getValue());
        }
        return url;
    }

/*
    public static ActionURL getRunBackgroundRReportURL(ViewContext context, Report report)
    {
        ActionURL url = new ActionURL("reports", "runBackgroundRReport", context.getContainer());

        if (report != null)
        {
            ActionURL filterUrl = RenderContext.getSortFilterURLHelper(context);
            url.addParameter(REPORT_ID, String.valueOf(report.getDescriptor().getReportId()));
            for (Pair<String, String> param : filterUrl.getParameters())
                url.addParameter(param.getKey(), param.getValue());
        }
        return url;
    }
*/

    public static ActionURL getDeleteReportURL(ViewContext context, Report report, ActionURL forwardURL)
    {
        if (forwardURL == null)
            throw new UnsupportedOperationException("A forward URL must be specified");

        ActionURL url = new ActionURL("reports", "deleteReport", context.getContainer());

        if (report != null)
        {
            url.addParameter(REPORT_ID, String.valueOf(report.getDescriptor().getReportId()));
            url.addParameter(FORWARD_URL, forwardURL.toString());
        }
        return url;
    }

    public static String getReportQueryKey(ReportDescriptor descriptor)
    {
        if (descriptor == null)
            throw new UnsupportedOperationException("ReportDescriptor is null");

        String schemaName = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
        String queryName = descriptor.getProperty(ReportDescriptor.Prop.queryName);

        final String dataRegion = descriptor.getProperty(ReportDescriptor.Prop.dataRegionName);
        if (StringUtils.isEmpty(queryName) && !StringUtils.isEmpty(dataRegion))
        {
            queryName = descriptor.getProperty(dataRegion + '.' + QueryParam.queryName.toString());
        }
        return getReportKey(schemaName, queryName);
    }

    private static final Pattern toPattern = Pattern.compile("/");
    private static final Pattern fromPattern = Pattern.compile("%2F");

    public static String getReportKey(String schema, String query)
    {
        if (StringUtils.isEmpty(schema))
            return " ";
        
        StringBuilder sb = new StringBuilder(toPattern.matcher(schema).replaceAll("%2F"));
        if (!StringUtils.isEmpty(query))
            sb.append('/').append(toPattern.matcher(query).replaceAll("%2F"));

        return sb.toString();
    }

    public static String[] splitReportKey(String key)
    {
        String[] parts = key.split("/");

        for (int i=0; i < parts.length; i++)
            parts[i] = fromPattern.matcher(parts[i]).replaceAll("/");
        return parts;
    }


    public static void renderErrorImage(OutputStream outputStream, Report r, String errorMessage) throws IOException
    {
        int width = 640;
        int height = 480;

        ReportDescriptor descriptor = r.getDescriptor();
        if (descriptor instanceof ChartReportDescriptor)
        {
            width = ((ChartReportDescriptor)descriptor).getWidth();
            height = ((ChartReportDescriptor)descriptor).getHeight();
        }
        renderErrorImage(outputStream, width, height, errorMessage);
    }

    public static void renderErrorImage(OutputStream outputStream, int width, int height, String errorMessage) throws IOException
    {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.black);
        g.drawString(errorMessage, (width - g.getFontMetrics().stringWidth(errorMessage)) / 2, (height - g.getFontMetrics().getHeight()) / 2);
        ImageIO.write(bi, "png", outputStream);
        outputStream.flush();
    }

    public static List<Report> getAvailableSharedRScripts(ViewContext context, RReportBean bean) throws Exception
    {
        List<Report> scripts = new ArrayList<Report>();

        String reportKey = ChartUtil.getReportKey(bean.getSchemaName(), bean.getQueryName());
        String reportName = bean.getReportName();
        for (Report r : getReports(context, reportKey, true))
        {
            if (!RReportDescriptor.class.isAssignableFrom(r.getDescriptor().getClass()))
                continue;
            if (reportName == null || !reportName.equals(r.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)))
                scripts.add(r);
        }
        return scripts;
    }

    public static List<Report> getReports(ViewContext context, String reportKey, boolean inherited)
    {
        try {
            List<Report> reports = new ArrayList<Report>();
            Container container = context.getContainer();
            for (Report report : ReportService.get().getReports(context.getUser(), container, reportKey))
            {
                reports.add(report);
            }

            if (inherited)
            {
                while (!container.isRoot())
                {
                    container = container.getParent();
                    for (Report report : ReportService.get().getReports(context.getUser(), container, reportKey, ReportDescriptor.FLAG_INHERITABLE, 1))
                    {
                        reports.add(report);
                    }
                }

                // look for any reports in the shared project
                for (Report report : ReportService.get().getReports(context.getUser(), ContainerManager.getSharedContainer(), reportKey))
                {
                    reports.add(report);
                }
            }
            return reports;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean canReadReport(Report report, User user)
    {
        if (report != null)
        {
            if (report.getDescriptor().getOwner() == null)
                return true;

            return (report.getDescriptor().getOwner().equals(user.getUserId()));
        }
        return false;
    }

    public static boolean isReportInherited(Container c, Report report)
    {
        if (report.getDescriptor().getReportId() != -1)
        {
            if ((report.getDescriptor().getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0)
            {
                return !c.getId().equals(report.getDescriptor().getContainerId());
            }
        }
        return false;
    }
}
