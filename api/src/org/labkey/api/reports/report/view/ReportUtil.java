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
import org.labkey.api.view.TabStripView;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.common.util.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 20, 2007
 */
public class ReportUtil
{
    private static final Logger _log = Logger.getLogger(ReportUtil.class);

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
        url.addParameters(context.getActionURL().getParameters());

        return _getChartDesignerURL(url, bean);
    }

    public static ActionURL getScriptReportDesignerURL(ViewContext context, ScriptReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateScriptReport(context.getContainer());
        url.addParameters(context.getActionURL().getParameters());
        url.replaceParameter(RReportDescriptor.Prop.scriptExtension.name(), bean.getScriptExtension());

        return _getChartDesignerURL(url, bean);
    }

    protected static ActionURL _getChartDesignerURL(ActionURL url, ReportDesignBean bean)
    {
        url.replaceParameter("reportType", bean.getReportType());
        if (bean.getSchemaName() != null)
            url.replaceParameter("schemaName", bean.getSchemaName());
        if (bean.getQueryName() != null)
            url.replaceParameter("queryName", bean.getQueryName());
        if (bean.getViewName() != null)
            url.replaceParameter("viewName", bean.getViewName());
        if (bean.getFilterParam() != null)
            url.replaceParameter(ReportDescriptor.Prop.filterParam.toString(), bean.getFilterParam());
        if (bean.getRedirectUrl() != null)
            url.replaceParameter("redirectUrl", bean.getRedirectUrl());
        if (bean.getDataRegionName() != null)
            url.replaceParameter(QueryParam.dataRegionName.toString(), bean.getDataRegionName());
        if (bean.getReportId() != null)
            url.replaceParameter(ReportDescriptor.Prop.reportId, bean.getReportId().toString());

        return url;
    }

    public static ActionURL getPlotChartURL(ViewContext context, Report report)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlPlotChart(context.getContainer());

        if (report != null)
        {
            ActionURL filterUrl = RenderContext.getSortFilterURLHelper(context);
            url.addParameter(ReportDescriptor.Prop.reportId, report.getDescriptor().getReportId().toString());
            for (Pair<String, String> param : filterUrl.getParameters())
                url.addParameter(param.getKey(), param.getValue());
        }
        return url;
    }

    public static ActionURL getRunReportURL(ViewContext context, Report report)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlRunReport(context.getContainer());

        if (report != null)
        {
            if (null != report.getDescriptor().getReportId())
                url.addParameter(ReportDescriptor.Prop.reportId, report.getDescriptor().getReportId().toString());
            if (context.getActionURL().getParameter("redirectUrl") != null)
                url.addParameter("redirectUrl", context.getActionURL().getParameter("redirectUrl"));
            else
                url.addParameter("redirectUrl", context.getActionURL().getLocalURIString());
        }
        return url;
    }

    public static ActionURL getDeleteReportURL(ViewContext context, Report report, ActionURL forwardURL)
    {
        if (forwardURL == null)
            throw new UnsupportedOperationException("A forward URL must be specified");

        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlDeleteReport(context.getContainer());

        if (report != null)
        {
            url.addParameter(ReportDescriptor.Prop.reportId, report.getDescriptor().getReportId().toString());
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

        String reportKey = ReportUtil.getReportKey(bean.getSchemaName(), bean.getQueryName());
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
                if (!ContainerManager.getSharedContainer().equals(context.getContainer()))
                {
                    for (Report report : ReportService.get().getReports(context.getUser(), ContainerManager.getSharedContainer(), reportKey))
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
        if (null != report.getDescriptor().getReportId())
        {
            if ((report.getDescriptor().getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0)
            {
                return !c.getId().equals(report.getDescriptor().getContainerId());
            }
        }
        return false;
    }

    public static List<Map<String, String>> getViewsJson(ViewContext context)
    {
        List<Map<String, String>> views = new ArrayList<Map<String, String>>();

        for (Report r : ReportUtil.getReports(context, null, true))
        {
            if (!StringUtils.isEmpty(r.getDescriptor().getReportName()))
            {
                ReportDescriptor descriptor = r.getDescriptor();
                Map<String, String> record = new HashMap<String, String>();

                User createdBy = UserManager.getUser(descriptor.getCreatedBy());
                boolean shared = descriptor.getOwner() == null;
                ActionURL editUrl = r.getEditReportURL(context);

                record.put("name", descriptor.getReportName());
                record.put("displayName", "<a href=\"" + r.getRunReportURL(context) + "\">" + descriptor.getReportName() + "</a>");
                record.put("reportId", descriptor.getReportId().toString());
                record.put("query", StringUtils.defaultIfEmpty(descriptor.getProperty(ReportDescriptor.Prop.queryName), "Unknown"));
                record.put("schema", descriptor.getProperty(ReportDescriptor.Prop.schemaName));
                record.put("owner", createdBy != null ? createdBy.getDisplayName(context) : String.valueOf(descriptor.getCreatedBy()));
                record.put("public", String.valueOf(shared));
                record.put("type", r.getTypeDescription());
                record.put("editable", String.valueOf(descriptor.canEdit(context)));
                record.put("editUrl", editUrl != null ? editUrl.toString() : null);
                record.put("description", descriptor.getReportDescription());

                String iconPath = ReportService.get().getReportIcon(context, r.getType());
                if (!StringUtils.isEmpty(iconPath))
                    record.put("type", "<img src=\"" + iconPath + "\">&nbsp;" + r.getTypeDescription());
                else
                record.put("type", r.getTypeDescription());

                views.add(record);
            }
        }
        return views;
    }
}
