/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.springframework.validation.Errors;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Apr 20, 2007
 */
public class ReportUtil
{
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
                Object returnUrlString = context.get(ReportDescriptor.Prop.returnUrl.name());

                if (returnUrlString != null)
                    url.addParameter(ReportDescriptor.Prop.redirectUrl, returnUrlString.toString());
                else if (context.getActionURL().getController().equals(ajaxAction.getController()))
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
        return context.getUser().isDeveloper() &&
               context.getContainer().hasPermission(context.getUser(), InsertPermission.class);
    }

    public static boolean isInRole(User user, Container container, Class<? extends Role> roleCls)
    {
        Role role = RoleManager.getRole(roleCls);

        if (role != null)
        {
            SecurityPolicy policy = container.getPolicy();
            Set<Role> roles = policy.getEffectiveRoles(user);

            return roles.contains(role);
        }
        return false;
    }

    /**
     * Transfer list of validation errors to the spring error object
     */
    public static void addErrors(List<ValidationError> reportErrors, Errors errors)
    {
        for (ValidationError error : reportErrors)
            errors.reject(SpringActionController.ERROR_MSG, error.getMessage());
    }

    public static String getErrors(List<ValidationError> reportErrors)
    {
        StringBuffer sb = new StringBuffer();
        for (ValidationError error : reportErrors)
            sb.append(error.getMessage()).append("\n");

        return sb.toString();
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
            return report.canEdit(user, c);
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
        return getViews(context, schemaName, queryName, true, includeQueries, new DefaultReportFilter());
    }

    public static List<ViewInfo> getViews(ViewContext context, String schemaName, String queryName, boolean includeReports, boolean includeQueries)
    {
        return getViews(context, schemaName, queryName, includeReports, includeQueries, new DefaultReportFilter());
    }

    public static ViewCategory getDefaultCategory(Container c, String schema, String query)
    {
        ViewCategory vc = null;
        String category = query;
        if ("study".equalsIgnoreCase(schema) && !StringUtils.isEmpty(query))
        {
            int datasetId = StudyService.get().getDatasetId(c, query);
            if (datasetId >= 0)
            {
                DataSet ds = StudyService.get().getDataSet(c, datasetId);
                if (ds != null) // should this check && !StringUtils.isEmpty(ds.getCategory()))
                {
                    vc = ds.getViewCategory();
                    if (vc != null)
                        return vc;
                }
            }
        }
        category = StringUtils.defaultIfEmpty(category, "Uncategorized");

        vc = new ViewCategory();

        vc.setLabel(category);
        vc.setDisplayOrder(DEFAULT_CATEGORY_DISPLAY_ORDER);

        return vc;
    }

    public static final int DEFAULT_CATEGORY_DISPLAY_ORDER = 1000;

    public static List<ViewInfo> getViews(ViewContext context, String schemaName, String queryName, boolean includeReports, boolean includeQueries, ReportFilter filter)
    {
        Container c = context.getContainer();
        User user = context.getUser();

        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");
        
        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<ViewInfo> views = new ArrayList<ViewInfo>();

        try
        {
            if (includeReports)
            {
                ReportPropsManager.get().ensureProperty(c, user, "status", "Status", PropertyType.STRING);
                ReportPropsManager.get().ensureProperty(c, user, "author", "Author", PropertyType.INTEGER);
                ReportPropsManager.get().ensureProperty(c, user, "refreshDate", "RefreshDate", PropertyType.DATE_TIME);
                ReportPropsManager.get().ensureProperty(c, user, "thumbnailType", "ThumbnailType", PropertyType.STRING);

                for (Report r : ReportUtil.getReports(c, user, reportKey, true))
                {
                    if (!filter.accept(r, c, user))
                        continue;

                    if (!StringUtils.isEmpty(r.getDescriptor().getReportName()))
                    {
                        ReportDescriptor descriptor = r.getDescriptor();

                        User createdBy = UserManager.getUser(descriptor.getCreatedBy());
                        User modifiedBy = UserManager.getUser(descriptor.getModifiedBy());
                        Object authorId = ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "author");

                        User author = authorId != null ? UserManager.getUser(((Double)authorId).intValue()) : null;
                        boolean inherited = descriptor.isInherited(c);
                        String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                        String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
                        String status = (String)ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "status");

                        ViewInfo info = new ViewInfo(descriptor.getReportName(), r.getTypeDescription());

                        info.setReportId(descriptor.getReportId());
                        info.setEntityId(descriptor.getEntityId());
                        info.setDataType(ViewInfo.DataType.reports);
                        info.setQuery(StringUtils.defaultIfEmpty(query, "Stand-alone views"));

                        if (status != null)
                            info.setStatus(ViewInfo.Status.valueOf(status));

                        if (descriptor.getCategory() != null)
                        {
                            info.setCategory(descriptor.getCategory().getLabel());
                            info.setCategoryDisplayOrder(descriptor.getCategory().getDisplayOrder());
                        }
                        else
                        {
                            ViewCategory vc = getDefaultCategory(c, schema, query);

                            info.setCategory(vc.getLabel());
                            info.setCategoryDisplayOrder(vc.getDisplayOrder());
                        }
                        info.setSchema(schema);
                        info.setCreatedBy(createdBy);
                        info.setCreated(descriptor.getCreated());
                        info.setRefreshDate((Date)ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "refreshDate"));
                        info.setModifiedBy(modifiedBy);
                        info.setAuthor(author);
                        info.setModified(descriptor.getModified());
                        info.setEditable(r.canEdit(user, c));
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
                            ActionURL editUrl = null;

                            if (info.getEditable())
                                editUrl = r.getEditReportURL(context);
                            ActionURL runUrl = r.getRunReportURL(context);
                            ActionURL detailsUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlReportDetails(c, r);

                            info.setEditUrl(editUrl);
                            info.setRunUrl(runUrl);
                            info.setDetailsUrl(detailsUrl);
                        }
                        else
                        {
                            ActionURL runUrl = r.getRunReportURL(context);

                            if (queryExists(user, c, schema, query))
                                info.setRunUrl(runUrl);
                            else
                                continue;
                        }

                        info.setRunTarget(r.getRunReportTarget());

                        if (c.hasPermission(user, AdminPermission.class))
                            info.setInfoUrl(PageFlowUtil.urlProvider(ReportUrls.class).urlReportInfo(c).addParameter("reportId", descriptor.getReportId().toString()));
                        info.setDescription(descriptor.getReportDescription());
                        info.setContainer(descriptor.lookupContainer());

                        String security;
                        if (descriptor.getOwner() != null)
                            security = "private";
                        else if (!SecurityPolicyManager.getPolicy(descriptor, false).isEmpty())
                            security = "custom"; // 13571: Explicit is a bad name for custom permissions
                        else
                            security = "public";

                        info.setShared(descriptor.getOwner() == null);
                        info.setPermissions(security);

                        // This icon is the small icon -- not the same as thumbnail
                        String iconPath = ReportService.get().getIconPath(r);

                        if (!StringUtils.isEmpty(iconPath))
                            info.setIcon(iconPath);

                        info.setThumbnailUrl(PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(c, r));

                        views.add(info);
                    }
                }
            }

            if (includeQueries)
            {
                for (CustomView view : QueryService.get().getCustomViews(user, c, schemaName, queryName, false))
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

                    ViewCategory vc = getDefaultCategory(c, view.getSchemaName(), view.getQueryName());
                    info.setCategory(vc.getLabel());
                    info.setCategoryDisplayOrder(vc.getDisplayOrder());

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

                    // run url and details url are the same for now
                    ActionURL runUrl = filter.getViewRunURL(user, c, view);
                    info.setRunUrl(runUrl);
                    info.setDetailsUrl(runUrl);
                    // FIXME: see 10473: ModuleCustomViewInfo has no Container or Owner.
                    info.setContainer(view.getContainer());
                    info.setInherited(inherited);

                    if (!StringUtils.isEmpty(view.getCustomIconUrl()))
                    {
                        URLHelper url = new URLHelper(view.getCustomIconUrl());
                        url.setContextPath(AppProps.getInstance().getParsedContextPath());
                        info.setIcon(url.toString());
                    }
                    info.setThumbnailUrl(PageFlowUtil.urlProvider(QueryUrls.class).urlThumbnail(c));

                    views.add(info);
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
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
        public Report getReport(ContainerUser cu)
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

    public static JSONArray getCreateReportButtons(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();
        for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            designers.addAll(provider.getDesignerInfo(context));

        Collections.sort(designers, new Comparator<ReportService.DesignerInfo>(){
            @Override
            public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
            {
                return o1.getLabel().compareTo(o2.getLabel());
            }
        });

        JSONArray json = new JSONArray();

        for (ReportService.DesignerInfo info : designers)
        {
            JSONObject o = new JSONObject();

            o.put("text", info.getLabel());
            o.put("id", info.getId());
            o.put("disabled", info.isDisabled());
            o.put("icon", info.getIconPath());
            o.put("redirectUrl", info.getDesignerURL().getLocalURIString());

            json.put(o);
        }
        return json;
    }

    /**
     * Generic form that can be used for serializing report information via json
     */
    public static class JsonReportForm implements CustomApiForm
    {
        private String _name;
        private String _description;
        private String _queryName;
        private String _schemaName;
        private String _viewName;
        private ReportIdentifier _reportId;
        private String _componentId;
        private boolean _public;

        public String getComponentId()
        {
            return _componentId;
        }

        public void setComponentId(String componentId)
        {
            _componentId = componentId;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }

        public String getName()
        {
            return _name;
        }

        public String getDescription()
        {
            return _description;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public boolean isPublic()
        {
            return _public;
        }

        public void setPublic(boolean isPublic)
        {
            _public = isPublic;
        }

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _name = (String)props.get("name");
            _description = (String)props.get("description");
            _schemaName = (String)props.get("schemaName");
            _queryName = (String)props.get("queryName");
            _viewName = (String)props.get("viewName");
            _public = BooleanUtils.toBooleanDefaultIfNull((Boolean)props.get("public"), true);

            Object reportId = props.get("reportId");
            if (reportId != null)
                _reportId = ReportService.get().getReportIdentifier((String)reportId);
        }

        public static JSONObject toJSON(User user, Container container, Report report)
        {
            JSONObject json = new JSONObject();
            ReportDescriptor descriptor = report.getDescriptor();

            json.put("name", descriptor.getReportName());
            json.put("description", descriptor.getReportDescription());
            json.put("schemaName", descriptor.getProperty(ReportDescriptor.Prop.schemaName));
            json.put("queryName", descriptor.getProperty(ReportDescriptor.Prop.queryName));
            json.put("viewName", descriptor.getProperty(ReportDescriptor.Prop.viewName));

            json.put("editable", report.canEdit(user, container));
            json.put("public", descriptor.getOwner() == null);

            return json;
        }
    }
}
