/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
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

    @Deprecated // use getScriptReportDesignerURL instead
    public static ActionURL getRReportDesignerURL(ViewContext context, ScriptReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateScriptReport(context.getContainer());
        url.addParameters(context.getActionURL().getParameters());
        url.replaceParameter(TabStripView.TAB_PARAM, ScriptReport.TAB_SOURCE);

        return _getChartDesignerURL(url, bean);
    }

    public static ActionURL getScriptReportDesignerURL(ViewContext context, ScriptReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateScriptReport(context.getContainer());
        url.addParameters(context.getActionURL().getParameters());
        url.replaceParameter(ScriptReportDescriptor.Prop.scriptExtension.name(), bean.getScriptExtension());
        url.replaceParameter(TabStripView.TAB_PARAM, ScriptReport.TAB_SOURCE);

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
            url.replaceParameter(ReportDescriptor.Prop.redirectUrl.name(), bean.getRedirectUrl());
        if (bean.getDataRegionName() != null)
            url.replaceParameter(QueryParam.dataRegionName.toString(), bean.getDataRegionName());
        if (bean.getReportId() != null)
            url.replaceParameter(ReportDescriptor.Prop.reportId, bean.getReportId().toString());

        return url;
    }

    public static ActionURL getPlotChartURL(ViewContext context, Report report)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlPlotChart(context.getContainer());

        if (report instanceof Report.ImageReport)
        {
            ActionURL filterUrl = RenderContext.getSortFilterURLHelper(context);
            ReportIdentifier id = report.getDescriptor().getReportId();
            if (id != null)
                url.addParameter(ReportDescriptor.Prop.reportId, report.getDescriptor().getReportId().toString());
            for (Pair<String, String> param : filterUrl.getParameters())
            {
                if (!param.getKey().equals(ReportDescriptor.Prop.reportId.name()))
                    url.addParameter(param.getKey(), param.getValue());
            }
        }
        else
        {
            throw new IllegalArgumentException("Report must implement Report.ImageReport to use the plot chart action");
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
            if (context.getActionURL().getParameter(ReportDescriptor.Prop.redirectUrl) != null)
                url.addParameter(ReportDescriptor.Prop.redirectUrl, context.getActionURL().getParameter(ReportDescriptor.Prop.redirectUrl));
            else
            {
                // don't want to use the current url if it's an ajax request
                ActionURL ajaxAction = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViewsSummary(context.getContainer());
                if (context.getActionURL().getPageFlow().equals(ajaxAction.getPageFlow()))
                    url.addParameter(ReportDescriptor.Prop.redirectUrl, PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()).getLocalURIString());
                else
                    url.addParameter(ReportDescriptor.Prop.redirectUrl, context.getActionURL().getLocalURIString());
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
        if (key == null)
            return new String[0];

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
        if (errorMessage == null)
        {
            errorMessage = "ERROR";
        }
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.black);
        g.drawString(errorMessage, (width - g.getFontMetrics().stringWidth(errorMessage)) / 2, (height - g.getFontMetrics().getHeight()) / 2);
        ImageIO.write(bi, "png", outputStream);
        outputStream.flush();
    }

    public static List<Report> getReports(Container c, User user, String reportKey, boolean inherited)
    {
        try
        {
            List<Report> reports = new ArrayList<Report>();
            reports.addAll(Arrays.asList(ReportService.get().getReports(user, c, reportKey)));

            if (inherited)
            {
                while (!c.isRoot())
                {
                    c = c.getParent();
                    reports.addAll(Arrays.asList(ReportService.get().getReports(user, c, reportKey, ReportDescriptor.FLAG_INHERITABLE, 1)));
                }

                // look for any reports in the shared project
                if (!ContainerManager.getSharedContainer().equals(c))
                    reports.addAll(Arrays.asList(ReportService.get().getReports(user, ContainerManager.getSharedContainer(), reportKey)));
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

    public static boolean canCreateScript(ViewContext context)
    {
        return context.getUser().isDeveloper();
    }

    public static interface ReportFilter
    {
        public boolean accept(Report report, Container c, User user);

        /**
         * Returns the run and edit urls for query views
         */
        public ActionURL getViewRunURL(User user, Container c, CustomViewInfo view);
        public ActionURL getViewEditURL(Container c, CustomViewInfo view, User user);
    }

    public static class DefaultReportFilter implements ReportFilter
    {
        public boolean accept(Report report, Container c, User user)
        {
            return report.getDescriptor().canEdit(user, c);
        }

        public ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
        {
            return QueryService.get().urlFor(user, c, QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                    addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());
        }

        public ActionURL getViewEditURL(Container c, CustomViewInfo view, User user)
        {
            return null;
        }
    }

    public static List<ViewInfo> getViews(ViewContext context, String schemaName, String queryName, boolean includeQueries)
    {
        return getViews(context, schemaName, queryName, includeQueries, new DefaultReportFilter());
    }

    public static List<ViewInfo> getViews(ViewContext context, String schemaName, String queryName, boolean includeQueries, ReportFilter filter)
    {
        Container c = context.getContainer();
        User user = context.getUser();

        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");
        
        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<ViewInfo> views = new ArrayList<ViewInfo>();

        for (Report r : ReportUtil.getReports(c, user, reportKey, true))
        {
            if (!filter.accept(r, c, user))
                continue;

            if (!StringUtils.isEmpty(r.getDescriptor().getReportName()))
            {
                ReportDescriptor descriptor = r.getDescriptor();

                User createdBy = UserManager.getUser(descriptor.getCreatedBy());
                User modifiedBy = UserManager.getUser(descriptor.getModifiedBy());
                boolean inherited = descriptor.isInherited(c);
                String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);

                ViewInfo info = new ViewInfo(descriptor.getReportName(), r.getTypeDescription());

                info.setReportId(descriptor.getReportId());
                info.setQuery(StringUtils.defaultIfEmpty(query, "Stand-alone views"));

                if (descriptor.getCategory() != null)
                {
                    info.setCategory(descriptor.getCategory().getLabel());
                    info.setCategoryDisplayOrder(descriptor.getCategory().getDisplayOrder());
                }
                //info.setCategory(StringUtils.defaultIfEmpty(query, "Stand-alone views"));
                info.setSchema(schema);
                info.setCreatedBy(createdBy);
                info.setCreated(descriptor.getCreated());
                info.setModifiedBy(modifiedBy);
                info.setModified(descriptor.getModified());
                info.setEditable(descriptor.canEdit(user, c));
                info.setInherited(inherited);
                info.setVersion(descriptor.getVersionString());
                info.setHidden(descriptor.isHidden());
                info.setDisplayOrder(descriptor.getDisplayOrder());

                /**
                 * shared reports are only available if there is a query/schema available in the container that matches
                 * the view's descriptor. Normally, the check happens automatically when you get reports using a non-blank key, but when
                 * you request all reports for a container you have to do an explicit check to make sure there is a valid query
                 * available in the container. 
                 */
                if (!inherited || !StringUtils.isBlank(reportKey))
                {
                    ActionURL editUrl = r.getEditReportURL(context);
                    ActionURL runUrl = r.getRunReportURL(context);

                    info.setEditUrl(editUrl);
                    info.setRunUrl(runUrl);
                }
                else
                {
                    ActionURL runUrl = r.getRunReportURL(context);

                    if (queryExists(user, c, schema, query))
                        info.setRunUrl(runUrl);
                    else
                        continue;
                }
                info.setDescription(descriptor.getReportDescription());
                info.setContainer(descriptor.lookupContainer());

                String security;
                if (descriptor.getOwner() != null)
                    security = "private";
                // FIXME: see 10473: ModuleRReportDescriptor extends securable resource, but doesn't properly implement it.  File-based resources don't have a Container or Owner.
                else if (!(descriptor instanceof ModuleRReportDescriptor) && !SecurityManager.getPolicy(descriptor, false).isEmpty())
                    security = "explicit";
                else
                    security = "public";

                info.setPermissions(security);

                String iconPath = ReportService.get().getReportIcon(context, r.getType());  
                if (!StringUtils.isEmpty(iconPath))
                    info.setIcon(iconPath);

                info.setThumbnailUrl(PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(c, r).toString());

                views.add(info);
            }
        }

        if (includeQueries)
        {
            for (CustomViewInfo view : QueryService.get().getCustomViewInfos(user, c, schemaName, queryName))
            {
                if (view.isHidden())
                    continue;

                if (view.getName() == null)
                    continue;

                ViewInfo info = new ViewInfo(view.getName(), "query view");

                User createdBy = view.getCreatedBy();

                // create a fake report id to reference the custom views by (would be nice if this could be a rowId)
                Map<String, String> viewId = PageFlowUtil.map(QueryParam.schemaName.name(), view.getSchemaName(),
                        QueryParam.queryName.name(), view.getQueryName(),
                        QueryParam.viewName.name(), view.getName());

                info.setQueryView(true);
                info.setCategory(view.getQueryName());
                info.setReportId(new QueryViewReportId(viewId));
                info.setQuery(view.getQueryName());
                info.setSchema(view.getSchemaName());
                info.setEditable(view.isEditable());
                info.setCreatedBy(createdBy);
                info.setPermissions(view.isShared() ? "public" : "private");

                boolean inherited = isInherited(view, c);

                if (!inherited)
                {
                    ActionURL url = filter.getViewEditURL(c, view, user);
                    if (url != null)
                    {
                        url.addParameter(QueryParam.srcURL, PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c).getLocalURIString());
                        info.setEditUrl(url);
                    }
                }

                info.setRunUrl(filter.getViewRunURL(user, c, view));
                // FIXME: see 10473: ModuleCustomViewInfo has no Container or Owner.
                info.setContainer(view.getContainer());
                info.setInherited(inherited);

                if (!StringUtils.isEmpty(view.getCustomIconUrl()))
                    info.setIcon(view.getCustomIconUrl());

                views.add(info);
            }
        }

        return views;
    }

    private static class QueryViewReportId implements ReportIdentifier
    {
        private Map<String, String> _params;

        public QueryViewReportId(Map<String, String> params)
        {
            _params = params;
        }

        @Override
        public Report getReport() throws Exception
        {
            throw new UnsupportedOperationException("No report bound to this id");
        }

        @Override
        public String toString()
        {
            return PageFlowUtil.encode(PageFlowUtil.toQueryString(_params.entrySet()));
        }
    }

    public static boolean queryExists(User user, Container container, String schema, String query)
    {
        if (schema == null && query == null) return true;

        QueryDefinition def = QueryService.get().getQueryDef(user, container, schema, query);
        if (def == null)
        {                                                                                                                            
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, schema);
            if (userSchema != null)
                return userSchema.getTableNames().contains(query);
            return false;
        }
        return true;
    }

    public static boolean isInherited(CustomViewInfo view, Container container)
    {
        if (view != null && view.canInherit())
        {
            if (!container.getId().equals(view.getContainer().getId()))
                return true;
        }
        return false;
    }
}
