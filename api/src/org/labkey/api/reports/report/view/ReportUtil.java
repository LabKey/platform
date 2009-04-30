/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        url.replaceParameter(TabStripView.TAB_PARAM, RunScriptReportView.TAB_SOURCE);

        return _getChartDesignerURL(url, bean);
    }

    public static ActionURL getScriptReportDesignerURL(ViewContext context, ScriptReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateScriptReport(context.getContainer());
        url.addParameters(context.getActionURL().getParameters());
        url.replaceParameter(RReportDescriptor.Prop.scriptExtension.name(), bean.getScriptExtension());
        url.replaceParameter(TabStripView.TAB_PARAM, RunScriptReportView.TAB_SOURCE);

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
            {
                // don't want to use the current url if it's an ajax request
                ActionURL ajaxAction = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViewsSummary(context.getContainer());
                if (context.getActionURL().getPageFlow().equals(ajaxAction.getPageFlow()))
                    url.addParameter("redirectUrl", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()).getLocalURIString());
                else
                    url.addParameter("redirectUrl", context.getActionURL().getLocalURIString());
            }
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

    public static interface ReportFilter
    {
        public boolean accept(Report report, ViewContext context);

        /**
         * Returns the run and edit urls for query views
         */
        public ActionURL getViewRunURL(ViewContext context, QueryDefinition def, CustomView view);
        public ActionURL getViewEditURL(ViewContext context, QueryDefinition def, CustomView view);
    }

    public static class DefaultReportFilter implements ReportFilter
    {
        public boolean accept(Report report, ViewContext context)
        {
            return true;
        }

        public ActionURL getViewRunURL(ViewContext context, QueryDefinition qd, CustomView view)
        {
            return qd.urlFor(QueryAction.executeQuery, context.getContainer()).
                    addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName, view.getName());
        }

        public ActionURL getViewEditURL(ViewContext context, QueryDefinition qd, CustomView view)
        {
            return qd.urlFor(QueryAction.chooseColumns, context.getContainer()).
                    addParameter(QueryParam.queryName, qd.getName()).
                    addParameter(QueryParam.viewName, view.getName());
        }
    }

    public static List<Map<String, String>> getViews(ViewContext context, String schemaName, String queryName, boolean includeQueries)
    {
        return getViews(context, schemaName, queryName, includeQueries, new DefaultReportFilter());
    }

    public static List<Map<String, String>> getViews(ViewContext context, String schemaName, String queryName, boolean includeQueries, ReportFilter filter)
    {
        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");
        
        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<Map<String, String>> views = new ArrayList<Map<String, String>>();

        for (Report r : ReportUtil.getReports(context, reportKey, true))
        {
            if (!filter.accept(r, context))
                continue;

            if (!StringUtils.isEmpty(r.getDescriptor().getReportName()))
            {
                ReportDescriptor descriptor = r.getDescriptor();
                Map<String, String> record = new HashMap<String, String>();

                User createdBy = UserManager.getUser(descriptor.getCreatedBy());
                User modifiedBy = UserManager.getUser(descriptor.getModifiedBy());
                boolean inherited = descriptor.isInherited(context.getContainer());
                String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);

                record.put("name", descriptor.getReportName());
                record.put("reportId", descriptor.getReportId().toString());
                record.put("query", StringUtils.defaultIfEmpty(query, "None : (stand-alone views)"));
                record.put("schema", schema);
                record.put("createdBy", createdBy != null ? createdBy.getDisplayName(context) : String.valueOf(descriptor.getCreatedBy()));
                record.put("created", DateUtil.formatDate(descriptor.getCreated()));
                record.put("modifiedBy", modifiedBy != null ? modifiedBy.getDisplayName(context) : String.valueOf(descriptor.getModifiedBy()));
                record.put("modified", DateUtil.formatDate(descriptor.getModified()));
                record.put("type", r.getTypeDescription());
                record.put("editable", String.valueOf(descriptor.canEdit(context)));
                record.put("inherited", String.valueOf(inherited));

                /**
                 * shared reports are only available if there is a query/schema available in the container that matches
                 * the view's descriptor. Normally, the check happens automatically when you get reports using a non-blank key, but when
                 * you request all reports for a container you have to do an explicit check to make sure there is a valid query
                 * available in the container. 
                 */
                if (!inherited || !StringUtils.isBlank(reportKey))
                {
                    record.put("editUrl", r.getEditReportURL(context) != null ? r.getEditReportURL(context).getLocalURIString() : null);
                    record.put("runUrl", r.getRunReportURL(context) != null ? r.getRunReportURL(context).getLocalURIString() : null);
                }
                else
                {
                    if (queryExists(context.getUser(), context.getContainer(), schema, query))
                        record.put("runUrl", r.getRunReportURL(context) != null ? r.getRunReportURL(context).getLocalURIString() : null);
                    else
                        continue;
                }
                record.put("description", descriptor.getReportDescription());
                record.put("container", descriptor.getContainerPath());

                String security;
                if (descriptor.getOwner() != null)
                    security = "private";
                else if (r.getDescriptor().getACL() != null && !r.getDescriptor().getACL().isEmpty())
                    security = "explicit";
                else
                    security = "public";

                record.put("permissions", security);
                
                String iconPath = ReportService.get().getReportIcon(context, r.getType());
                if (!StringUtils.isEmpty(iconPath))
                    record.put("icon", iconPath);

                views.add(record);
            }
        }
        if (includeQueries)
        {
            for (CustomView view : QueryService.get().getCustomViews(context.getUser(), context.getContainer(), schemaName, queryName))
            {
                if (view.isHidden())
                    continue;

                if (view.getName() == null)
                    continue;

                Map<String, String> record = new HashMap<String, String>();

                User createdBy = view.getCreatedBy();
                QueryDefinition qd = view.getQueryDefinition();

                record.put("queryView", "true");

                // create a fake report id to reference the custom views by (would be nice if this could be a rowId)
                Map<String, String> viewId = PageFlowUtil.map(QueryParam.schemaName.name(), qd.getSchemaName(),
                        QueryParam.queryName.name(), qd.getName(),
                        QueryParam.viewName.name(), view.getName());
                record.put("reportId", PageFlowUtil.encode(PageFlowUtil.toQueryString(viewId.entrySet())));
                record.put("name", view.getName());
                record.put("query", qd.getName());
                record.put("schema", qd.getSchemaName());
                record.put("type", "query view");
                record.put("editable", "false");
                record.put("createdBy", createdBy != null ? createdBy.getDisplayName(context) : null);
                record.put("permissions", view.getOwner() != null ? "private" : "public");

                boolean inherited = isInherited(view, context.getContainer());
                if (!inherited)
                {
                    record.put("editUrl", filter.getViewEditURL(context, qd, view).getLocalURIString());
                }
                record.put("runUrl", filter.getViewRunURL(context, qd, view).getLocalURIString());
                record.put("container", view.getContainer().getPath());
                record.put("inherited", String.valueOf(inherited));

                views.add(record);
            }
        }
        return views;
    }

    public static boolean queryExists(User user, Container container, String schema, String query)
    {
        if (schema == null && query == null) return true;

        QueryDefinition def = QueryService.get().getQueryDef(container, schema, query);
        if (def == null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, schema);
            if (userSchema != null)
                return userSchema.getTableNames().contains(query);
            return false;
        }
        return true;
    }

    public static boolean isInherited(CustomView view, Container container)
    {
        if (view != null && view.canInherit())
        {
            if (!container.getId().equals(view.getContainer().getId()))
                return true;
        }
        return false;
    }
}
