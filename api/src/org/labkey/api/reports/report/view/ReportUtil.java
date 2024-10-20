/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.LabKeyScriptEngine;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ThumbnailUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ReportUtil
{
    public static ActionURL getScriptReportDesignerURL(ViewContext context, ScriptReportBean bean)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlCreateScriptReport(context.getContainer());
        url.addParameters(context.getActionURL().getParameters());
        url.replaceParameter(ScriptReportDescriptor.Prop.scriptExtension.name(), bean.getScriptExtension());
        url.replaceParameter(TabStripView.TAB_PARAM, ScriptReport.TAB_SOURCE);

        // issue 18390, don't want reportId on the "create" action
        url.deleteParameter(ReportDescriptor.Prop.reportId);

        return _getChartDesignerURL(url, bean);
    }

    protected static ActionURL _getChartDesignerURL(ActionURL url, ReportDesignBean<?> bean)
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

    public static ActionURL getRunReportURL(ViewContext context, Report report, boolean useDefaultRedirect)
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
                Object returnUrlString = context.get(ReportDescriptor.Prop.returnUrl.name());

                if (returnUrlString != null)
                    url.addParameter(ReportDescriptor.Prop.redirectUrl, returnUrlString.toString());
                else if (useDefaultRedirect)
                    url.addParameter(ReportDescriptor.Prop.redirectUrl, context.getActionURL().getLocalURIString());
            }
        }
        return url;
    }

    public static URLHelper getModuleImageUrl(Container c, Report r, ImageType type)
    {
        Resource imageFile = getModuleImageFile(r, type.getFilename());
        if (null != imageFile)
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlModuleThumbnail(c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());
            url.addParameter("imageFilePrefix", type.getFilename());
            return url;
        }
        return null;
    }

    public static Resource getModuleImageFile(Report r, String fileTypePrefix)
    {
        if (!(r.getDescriptor().isModuleBased() && r.getDescriptor() instanceof ModuleReportDescriptor))
            return null;

        ModuleReportDescriptor descriptor = (ModuleReportDescriptor) r.getDescriptor();
        Resource parent = descriptor.getSourceFile().parent();
        if (null == parent)
            return null;
        Resource attachmentDir = parent.find(Path.toPathPart(ReportUtil.getSerializedName(r.getDescriptor())));
        if (null == attachmentDir)
            return null;
        String imageFileName = null;
        for (String fileName : attachmentDir.listNames())
        {
            if (fileName.equals(fileTypePrefix) || fileName.startsWith(fileTypePrefix + "."))
            {
                imageFileName = fileName;
                break;
            }
        }

        if (null == imageFileName)
            return null;

        return attachmentDir.find(Path.toPathPart(imageFileName));
    }

    // Consider: combine these two methods... need standard way to get static thumbnail and icon url
    public static URLHelper getThumbnailUrl(Container c, Report r)
    {
        URLHelper dynamicURL = getDynamicImageUrl(c, r, ImageType.Large);

        if (null != dynamicURL)
        {
            return dynamicURL;
        }
        else if (r.getDescriptor().isModuleBased())
        {
            URLHelper moduleURL = getModuleImageUrl(c, r, ImageType.Large);
            if (null != moduleURL)
                return moduleURL;
        }

        return ThumbnailUtil.getStaticThumbnailURL(r, ImageType.Large);
    }

    public static URLHelper getIconUrl(Container c, Report r)
    {
        URLHelper dynamicURL = getDynamicImageUrl(c, r, ImageType.Small);

        if (null != dynamicURL)
        {
            return dynamicURL;
        }
        else if (r.getDescriptor().isModuleBased())
        {
            URLHelper moduleURL = getModuleImageUrl(c, r, ImageType.Small);
            if (null != moduleURL)
                return moduleURL;
        }
        String path = ReportService.get().getIconPath(r);
        return new ResourceURL(path);
    }

    public static String getIconCls(Report r)
    {
        return ReportService.get().getIconCls(r);
    }

    public static @Nullable URLHelper getDynamicImageUrl(Container c, Report r, ImageType type)
    {
        String prefix = type.getPropertyNamePrefix();
        String imageType = (String) ReportPropsManager.get().getPropertyValue(r.getEntityId(), c, prefix + "Type");

        if (imageType != null && !imageType.equals(ThumbnailType.NONE.name()))
        {
            Double revision = (Double)ReportPropsManager.get().getPropertyValue(r.getEntityId(), c, prefix + "Revision");
            Integer iconRevision = (null == revision ? null : revision.intValue());

            return PageFlowUtil.urlProvider(ReportUrls.class).urlImage(c, r, type, iconRevision);
        }

        return null;
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

        // issue 19206: if we don't have either a query or a schema, just return the name of the report as the
        // report key.  This will ensure we don't duplicate reports with empty query keys when importing
        if (StringUtils.isEmpty(schemaName) && StringUtils.isEmpty(queryName))
        {
            return descriptor.getReportName();
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

    public static List<Report> getReportsIncludingInherited(Container c, User user, @Nullable String reportKey)
    {
        List<Report> reports = new ArrayList<>(ReportService.get().getReports(user, c, reportKey));

        while (!c.isRoot())
        {
            c = c.getParent();
            reports.addAll(ReportService.get().getInheritableReports(user, c, reportKey));
        }

        // look for any reports in the shared project
        if (!ContainerManager.getSharedContainer().equals(c))
            reports.addAll(ReportService.get().getReports(user, ContainerManager.getSharedContainer(), reportKey));

        return reports;
    }

    /**
     * Reports are inherited when they are viewed from a folder different from the folder it was created in.
     * The report must also be either configured as shared from a parent folder or reside in the shared folder.
     */
    public static boolean isReportInherited(Container c, Report report)
    {
        if (null != report.getDescriptor().getReportId())
        {
            if (ContainerManager.getSharedContainer().getId().equals(report.getDescriptor().getContainerId()))
            {
                return !ContainerManager.getSharedContainer().equals(c);
            }

            if (report.getDescriptor().isInheritable())
            {
                Container reportContainer = ContainerManager.getForId(report.getDescriptor().getContainerId());
                if (!c.equals(reportContainer))
                {
                    // not the current container but verify that it is from a parent container
                    Container parent = c.getParent();
                    while (parent != null)
                    {
                        if (parent.equals(reportContainer))
                        {
                            return true;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }
        return false;
    }

    /**
     * Helper to determine if the user can create a script report.
     */
    public static boolean canCreateScript(ViewContext context, String extension)
    {
        User u = context.getUser();

        if (!context.getContainer().hasPermission(u, InsertPermission.class))
            return false;

        // if you are a platform developer you can create a script report
        if (u.hasRootPermission(PlatformDeveloperPermission.class))
            return true;

        if (u.isTrustedAnalyst())
        {
            // trusted analysts can only create reports on sandboxed engine instances
            // TODO can this differ from the engine returned by the instantiated report?
            LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
            ScriptEngine engine = svc.getEngineByExtension(context.getContainer(), extension);
            return engine instanceof LabKeyScriptEngine && ((LabKeyScriptEngine)engine).isSandboxed();
        }

        return false;
    }

    public static boolean isInRole(User user, Container container, Class<? extends Role> roleCls)
    {
        Role role = RoleManager.getRole(roleCls);

        if (role != null)
        {
            return SecurityManager.getEffectiveRoles(container, user).anyMatch(r -> r.equals(role));
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

    public static URLHelper getDefaultThumbnailUrl(Container c, Report r)
    {
        return ThumbnailUtil.getStaticThumbnailURL(r, ImageType.Large);
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
        @Override
        public boolean accept(Report report, Container c, User user)
        {
            return report.hasPermission(user, c, ReadPermission.class);
        }

        @Override
        public ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
        {
            return QueryService.get().urlFor(user, c, QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                    addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());
        }

        @Override
        public ActionURL getViewEditURL(Container c, CustomViewInfo view, User user)
        {
            return null;
        }
    }

    public static ViewCategory getDefaultCategory(Container c, @Nullable String schema, @Nullable String query)
    {
        ViewCategory vc;
        if ("study".equalsIgnoreCase(schema) && !StringUtils.isEmpty(query))
        {
            StudyService svc = StudyService.get();
            if (svc != null)
            {
                Dataset ds = svc.resolveDataset(c, query);
                if (ds != null) // should this check && !StringUtils.isEmpty(ds.getCategory()))
                {
                    vc = ds.getViewCategory();
                    if (vc != null)
                        return vc;
                }
            }
        }

        vc = new ViewCategory();
        vc.setLabel("Uncategorized");
        vc.setDisplayOrder(DEFAULT_CATEGORY_DISPLAY_ORDER);

        return vc;
    }

    public static final int DEFAULT_CATEGORY_DISPLAY_ORDER = 1000;

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

    public static String getSerializedName(ReportDescriptor d)
    {
        // if we have a serialized report name, then use this one
        String reportName = d.getProperty(ReportDescriptor.Prop.serializedReportName);

        // otherwise, just fallback to the report name
        if (reportName == null)
            reportName = d.getReportName();

        return reportName;
    }

    public static String getQueryLabelByName(User user, Container container, String schema, String query)
    {
        if (schema != null && query != null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, schema);
            return getQueryLabelByName(userSchema, query);
        }
        // fall back on returning the query name as the label
        return query;
    }

    public static String getQueryLabelByName(@Nullable UserSchema userSchema, String query)
    {
        if (userSchema != null)
        {
            TableInfo tableInfo = userSchema.getTable(query);
            if (tableInfo != null)
                return tableInfo.getTitle();
        }
        // fall back on returning the query name as the label
        return query;
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
        List<ReportService.DesignerInfo> designers = new ArrayList<>();
        for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            designers.addAll(provider.getDesignerInfo(context));

        designers.sort(Comparator.comparing(ReportService.DesignerInfo::getLabel));

        JSONArray json = new JSONArray();

        for (ReportService.DesignerInfo info : designers)
        {
            JSONObject o = new JSONObject();

            o.put("text", info.getLabel());
            o.put("id", info.getId());
            o.put("disabled", info.isDisabled());
            o.put("icon", info.getIconURL().getLocalURIString());
            o.put("redirectUrl", info.getDesignerURL().getLocalURIString());

            json.put(o);
        }
        return json;
    }

    public static Report getReportById(ViewContext viewContext, String reportIdString)
    {
        ReportIdentifier reportId = ReportService.get().getReportIdentifier(reportIdString, viewContext.getUser(), viewContext.getContainer());

        //allow bare report ids for backward compatibility
        if (reportId == null && NumberUtils.isDigits(reportIdString))
            reportId = new DbReportIdentifier(Integer.parseInt(reportIdString));

        if (reportId != null)
            return reportId.getReport(viewContext);

        return null;
    }

    public static Report getReportByName(ViewContext viewContext, String reportName, String reportKey)
    {
        // try to match by report name including any explicitly shared reports in parent folders or reports in the shared container
        for (Report rpt : ReportUtil.getReportsIncludingInherited(viewContext.getContainer(), viewContext.getUser(), reportKey))
        {
            if (reportName.equals(rpt.getDescriptor().getReportName()))
                return rpt;
        }

        return null;
    }

    /**
     * Determines whether the specified report name already exists for the specified query/schema/container
     * combination
     */
    public static boolean doesReportNameExist(Container c, User user, @Nullable String schemaName, @Nullable String queryName, @NotNull String name)
    {
        String key = getReportKey(schemaName, queryName);
        String trimmedName = name.trim();

        for (Report report : ReportService.get().getReports(user, c, key))
        {
            if (StringUtils.equalsIgnoreCase(trimmedName, report.getDescriptor().getReportName().trim()))
                return true;
        }
        return false;
    }

    /**
     * Create a unique ID appropriate for use as a reportSession
     */
    public static String createReportSessionId()
    {
        return "LabKey.ReportSession" + UniqueID.getServerSessionScopedUID();
    }

    /**
     * These functions facilitate execution without a bound report (for example, executing a function that exists in
     * a shared Rserve session
     */
    public static String makeExceptionString(Exception e, String formatString)
    {
        final String error1 = "Error executing command";
        final String error2 = PageFlowUtil.filter(e.getMessage());
        return String.format(formatString, error1, error2);
    }

    /**
     * Generic form that can be used for serializing report information via json
     */
    public static class JsonReportForm extends ReturnUrlForm implements ApiJsonForm
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
        public void bindJson(JSONObject json)
        {
            _name = json.optString("name", null);
            _description = json.optString("description", null);
            _schemaName = json.optString("schemaName", null);
            _queryName = json.optString("queryName", null);
            _viewName = json.optString("viewName", null);

            if (json.has("public"))
                _public = json.optBoolean("public", true);
            else
                _public = json.optBoolean("shared");

            String reportId = json.optString("reportId", null);
            if (reportId != null)
                _reportId = ReportService.get().getReportIdentifier(reportId, null, null);
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
            json.put("public", descriptor.isShared());
            json.put("shared", descriptor.isShared());

            return json;
        }
    }

    public static void resetReportSecurityPolicy(ViewContext context, @NotNull Report report, @Nullable User owner, @NotNull User performer)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(report.getDescriptor());
        SecurityPolicyManager.savePolicy(policy, performer);
        report.getDescriptor().setOwner(owner != null ? owner.getUserId() : null); // null = "public", owner = "private"
        ReportService.get().saveReportEx(context, report.getDescriptor().getReportKey(), report);
    }

    public static void updateReportSecurityPolicy(ViewContext context, @NotNull Report report, Integer principalId, boolean toAdd)
    {
        UserPrincipal principal = principalId != null && principalId != 0 ? SecurityManager.getPrincipal(principalId) : null;
        if (null != principal)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(report.getDescriptor(), SecurityPolicyManager.getPolicy(report.getDescriptor(), false));
            // make sure the Administrators remain readers of this report
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), ReaderRole.class);

            List<Role> principalAssignedRoles = policy.getAssignedRoles(principal);
            if (toAdd && principalAssignedRoles.isEmpty())
                policy.addRoleAssignment(principal, ReaderRole.class);
            else if (!toAdd && !principalAssignedRoles.isEmpty())
                policy.addRoleAssignment(principal, NoPermissionsRole.class);
            
            SecurityPolicyManager.savePolicy(policy, context.getUser());
            report.getDescriptor().setOwner(null); // force the report to be "custom"
            ReportService.get().saveReportEx(context, report.getDescriptor().getReportKey(), report);
        }
    }

    public static void setReportSecurityPolicy(ViewContext context, @NotNull Report report, Set<Integer> groupIds, Set<Integer> userIds)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(report.getDescriptor());

        if (groupIds != null && !groupIds.isEmpty())
        {
            for (int groupId : groupIds)
            {
                Group group = groupId != 0 ? SecurityManager.getGroup(groupId) : null;
                if (null != group)
                    policy.addRoleAssignment(group, ReaderRole.class);
            }
        }
        if (userIds != null && !userIds.isEmpty())
        {
            for (int userId : userIds)
            {
                User user = userId != 0 ? UserManager.getUser(userId) : null;
                if (null != user)
                    policy.addRoleAssignment(user, ReaderRole.class);
            }
        }

        SecurityPolicyManager.savePolicy(policy, context.getUser());
        report.getDescriptor().setOwner(null);
        ReportService.get().saveReportEx(context, report.getDescriptor().getReportKey(), report);
    }
}
