/*
 * Copyright (c) 2003-2010 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.user;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.TemplateHeaderView;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.security.SecurityController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.*;

public class UserController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(UserController.class);

    public UserController()
    {
        setActionResolver(_actionResolver);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig ret = super.defaultPageConfig();
        ret.setFrameOption(PageConfig.FrameOption.DENY);
        return ret;
    }

    public static class UserUrlsImpl implements UserUrls
    {
        public ActionURL getSiteUsersURL()
        {
            return new ActionURL(ShowUsersAction.class, ContainerManager.getRoot());
            // TODO: Always add lastFilter?
        }

        public ActionURL getProjectMembersURL(Container container)
        {
            return new ActionURL(ShowUsersAction.class, container);
        }

        public ActionURL getUserAccessURL(Container container, int userId)
        {
            ActionURL url = getUserAccessURL(container);
            url.addParameter("userId", userId);
            return url;
        }

        public ActionURL getUserAccessURL(Container container)
        {
            return new ActionURL(UserAccessAction.class, container);
        }

        public ActionURL getUserDetailsURL(Container c, int userId, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);
            url.addParameter("userId", userId);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getUserDetailsURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getUserUpdateURL(URLHelper returnURL, int userId)
        {
            ActionURL url = new ActionURL(ShowUpdateAction.class, ContainerManager.getRoot());
            url.addReturnURL(returnURL);
            url.addParameter("userId", userId);
            return url;
        }

        public ActionURL getImpersonateURL(Container c)
        {
            return new ActionURL(ImpersonateAction.class, c);
        }
    }


    // Note: the column list is dynamic, changing based on the current user's permissions.
    public static String getUserColumnNames(User user, Container c)
    {
        String columnNames = "Email, DisplayName, FirstName, LastName, Phone, Mobile, Pager, IM, Description";

        if (user != null && (user.isAdministrator() || c.hasPermission(user, AdminPermission.class)))
            columnNames = columnNames + ", UserId, Created, LastLogin, Active";

        return columnNames;
    }

    public static String getDefaultUserColumnNames()
    {
        return getUserColumnNames(null, null);
    }

    private class SiteUserDataRegion extends DataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            int userId = ctx.getViewContext().getUser().getUserId();
            Integer rowId = (Integer) ctx.getRow().get("userId");
            return  (userId != rowId.intValue());
        }
    }

    private DataRegion getGridRegion(boolean isOwnRecord)
    {
        final User user = getUser();
        Container c = getContainer();
        ActionURL currentURL = getViewContext().getActionURL();
        boolean isSiteAdmin = user.isAdministrator();
        boolean isAnyAdmin = isSiteAdmin || c.hasPermission(user, AdminPermission.class);

        assert isOwnRecord || isAnyAdmin;

        SiteUserDataRegion rgn = new SiteUserDataRegion();

        List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(getUserColumnNames(user, c));
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>();
        final String requiredFields = UserManager.getRequiredUserFields();
        for (ColumnInfo col : cols)
        {
            if (isColumnRequired(col.getName(), requiredFields))
            {
                final RequiredColumn required = new RequiredColumn(col);
                displayColumns.add(required.getRenderer());
            }
            else
                displayColumns.add(col.getRenderer());
        }
        rgn.setDisplayColumns(displayColumns);

        SimpleDisplayColumn accountDetails = new UrlColumn(new UserUrlsImpl().getUserDetailsURL(c, currentURL) + "userId=${UserId}", "details");
        accountDetails.setDisplayModes(DataRegion.MODE_GRID);
        rgn.addDisplayColumn(0, accountDetails);

        if (isAnyAdmin)
        {
            SimpleDisplayColumn securityDetails = new UrlColumn(new UserUrlsImpl().getUserAccessURL(c) + "userId=${UserId}", "permissions");
            securityDetails.setDisplayModes(DataRegion.MODE_GRID);
            rgn.addDisplayColumn(1, securityDetails);
        }

        ButtonBar gridButtonBar = new ButtonBar();

        if (isSiteAdmin)
        {
            rgn.setShowRecordSelectors(true);
        }
        populateUserGridButtonBar(gridButtonBar, isSiteAdmin, isAnyAdmin);

        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);

        ActionButton showGrid = new ActionButton("showUsers.view?.lastFilter=true", c.isRoot() ? "Show All Users" : "Show Project Members");
        showGrid.setActionType(ActionButton.Action.LINK);

        ButtonBar detailsButtonBar = new ButtonBar();
        if (isAnyAdmin)
            detailsButtonBar.add(showGrid);
        if (isOwnRecord || isSiteAdmin)
        {
            ActionButton edit = new ActionButton("showUpdate.view", "Edit");
            edit.setActionType(ActionButton.Action.GET);
            edit.addContextualRole(OwnerRole.class);
            detailsButtonBar.add(edit);
        }
        rgn.setButtonBar(detailsButtonBar, DataRegion.MODE_DETAILS);

        ButtonBar updateButtonBar = new ButtonBar();
        updateButtonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton update = new ActionButton("showUpdate.post", "Submit");
        if (isOwnRecord)
        {
            updateButtonBar.addContextualRole(OwnerRole.class);
            update.addContextualRole(OwnerRole.class);
        }
        //update.setActionType(ActionButton.Action.LINK);
        updateButtonBar.add(update);
        if (isSiteAdmin)
            updateButtonBar.add(showGrid);
        rgn.setButtonBar(updateButtonBar, DataRegion.MODE_UPDATE);

        return rgn;
    }

    private void populateUserGridButtonBar(ButtonBar gridButtonBar, boolean siteAdmin, boolean anyAdmin)
    {
        if (siteAdmin)
        {
            ActionButton deactivate = new ActionButton("deactivateUsers.post", "Deactivate");
            deactivate.setRequiresSelection(true);
            deactivate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(deactivate);

            ActionButton activate = new ActionButton("activateUsers.post", "Re-Activate");
            activate.setRequiresSelection(true);
            activate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(activate);

            ActionButton delete = new ActionButton("deleteUsers.post", "Delete");
            delete.setRequiresSelection(true);
            delete.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(delete);

            // Could allow project admins to do this... but they can already add users when adding to a group
            ActionButton insert = new ActionButton("showAddUsers", "Add Users");
            ActionURL actionURL = new ActionURL(SecurityController.AddUsersAction.class, getContainer());
            insert.setURL(actionURL.getLocalURIString());
            insert.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(insert);

            if (getContainer().isRoot())
            {
                ActionButton preferences = new ActionButton("showUserPreferences.view", "Preferences");
                preferences.setActionType(ActionButton.Action.LINK);
                gridButtonBar.add(preferences);
            }
        }

        if (anyAdmin)
        {
            if (AuditLogService.get().isViewable())
            {
                gridButtonBar.add(new ActionButton("showUserHistory.view", "History",
                        DataRegion.MODE_ALL, ActionButton.Action.LINK));
            }
        }
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(new UserUrlsImpl().getSiteUsersURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class UserIdForm
    {
        private Integer[] _userId;
        private String _redirUrl;

        public Integer[] getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer[] userId)
        {
            _userId = userId;
        }

        public String getRedirUrl()
        {
            return _redirUrl;
        }

        public void setRedirUrl(String redirUrl)
        {
            _redirUrl = redirUrl;
        }
    }
    public abstract class BaseActivateUsersAction extends FormViewAction<UserIdForm>
    {
        private boolean _active = true;

        protected BaseActivateUsersAction(boolean active)
        {
            _active = active;
        }

        public void validateCommand(UserIdForm form, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getViewContext().getUser();
            DeactivateUsersBean bean = new DeactivateUsersBean(_active, null == form.getRedirUrl() ? null : new ActionURL(form.getRedirUrl()));
            if(null != form.getUserId())
            {
                for(Integer userId : form.getUserId())
                {
                    if (null != userId && userId.intValue() != user.getUserId())
                        bean.addUser(UserManager.getUser(userId.intValue()));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<String> userIds = DataRegionSelection.getSelected(getViewContext(), true);
                if(null == userIds || userIds.size() == 0)
                    throw new RedirectException(new UserUrlsImpl().getSiteUsersURL().getLocalURIString());

                for(String userId : userIds)
                {
                    int id = Integer.parseInt(userId);
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if(bean.getUsers().size() == 0)
                throw new RedirectException(bean.getRedirUrl().getLocalURIString());

            return new JspView<DeactivateUsersBean>("/org/labkey/core/user/deactivateUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if(null == form.getUserId())
                return false;

            User curUser = getViewContext().getUser();
            for(Integer userId : form.getUserId())
            {
                if (null != userId && userId.intValue() != curUser.getUserId())
                    UserManager.setUserActive(curUser, userId.intValue(), _active);
            }
            return true;
        }

        public ActionURL getSuccessURL(UserIdForm form)
        {
            return null != form.getRedirUrl() ? new ActionURL(form.getRedirUrl())
                    : new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String title = _active ? "Re-activate Users" : "Deactivate Users";
            return root.addChild(title);
        }
    }

    @RequiresSiteAdmin
    public class DeactivateUsersAction extends BaseActivateUsersAction
    {
        public DeactivateUsersAction()
        {
            super(false);
        }
    }

    @RequiresSiteAdmin
    public class ActivateUsersAction extends BaseActivateUsersAction
    {
        public ActivateUsersAction()
        {
            super(true);
        }
    }

    @RequiresSiteAdmin
    public class DeleteUsersAction extends FormViewAction<UserIdForm>
    {
        public void validateCommand(UserIdForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            String siteUsersUrl = new UserUrlsImpl().getSiteUsersURL().getLocalURIString();
            DeleteUsersBean bean = new DeleteUsersBean();
            User user = getViewContext().getUser();

            if(null != form.getUserId())
            {
                for(Integer userId : form.getUserId())
                {
                    if (null != userId && userId.intValue() != user.getUserId())
                        bean.addUser(UserManager.getUser(userId.intValue()));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<String> userIds = DataRegionSelection.getSelected(getViewContext(), true);
                if(null == userIds || userIds.size() == 0)
                    throw new RedirectException(siteUsersUrl);

                for(String userId : userIds)
                {
                    int id = Integer.parseInt(userId);
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(Integer.parseInt(userId)));
                }
            }

            if(bean.getUsers().size() == 0)
                throw new RedirectException(siteUsersUrl);

            return new JspView<DeleteUsersBean>("/org/labkey/core/user/deleteUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if(null == form.getUserId())
                return false;

            User curUser = getViewContext().getUser();

            for(Integer userId : form.getUserId())
            {
                if (null != userId && userId.intValue() != curUser.getUserId())
                    UserManager.deleteUser(userId.intValue());
            }
            return true;
        }

        public ActionURL getSuccessURL(UserIdForm userIdForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Delete Users");
        }
    }

    private static boolean validateRequiredColumns(Map<String, Object> resultMap, TableInfo table)
    {
        final String requiredFields = UserManager.getRequiredUserFields();
        if (requiredFields == null || requiredFields.length() == 0)
            return true;

        for (String key : resultMap.keySet())
        {
            final ColumnInfo col = table.getColumn(key);
            if (col != null && isColumnRequired(col.getName(), requiredFields))
            {
                final Object val = resultMap.get(key);
                if (val == null || val.toString().trim().length() == 0)
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isColumnRequired(String column, String requiredColumns)
    {
        if (requiredColumns != null)
        {
            return requiredColumns.toLowerCase().indexOf(column.toLowerCase()) != -1;
        }
        return false;
    }

    public static class ShowUsersForm extends QueryViewAction.QueryExportForm
    {
        private boolean _inactive;

        public boolean isInactive()
        {
            return _inactive;
        }

        public void setInactive(boolean inactive)
        {
            _inactive = inactive;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)   // Root requires site admin; any other container requires PERM_ADMIN (see below)
    public class ShowUsersAction extends QueryViewAction<ShowUsersForm, QueryView>
    {
        private static final String DATA_REGION_NAME = "Users";

        public ShowUsersAction()
        {
            super(ShowUsersForm.class);
        }

        protected QueryView createQueryView(final ShowUsersForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, getContainer().isRoot() ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME);
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            final boolean forExport2 = forExport;
            QueryView queryView = new QueryView(new CoreQuerySchema(getUser(), getContainer()), settings, errors)
            {
                @Override
                protected void setupDataView(DataView ret)
                {
                    super.setupDataView(ret);

                    if (!forExport2)
                    {
                        ActionURL permissions = new UserUrlsImpl().getUserAccessURL(getContainer());
                        permissions.addParameter("userId", "${UserId}");
                        SimpleDisplayColumn securityDetails = new UrlColumn(StringExpressionFactory.createURL(permissions), "permissions");
                        ret.getDataRegion().addDisplayColumn(1, securityDetails);
                    }

                    ret.getRenderContext().setBaseSort(new Sort("email"));
                    SimpleFilter filter = authorizeAndGetProjectMemberFilter();
                    if (!form.isInactive())
                        filter.addCondition("Active", true); //filter out active users by default
                    ret.getRenderContext().setBaseFilter(filter);
                }

                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);
                    boolean isSiteAdmin = getUser().isAdministrator();
                    boolean isAnyAdmin = isSiteAdmin || getContainer().hasPermission(getUser(), AdminPermission.class);
                    populateUserGridButtonBar(bar, isSiteAdmin, isAnyAdmin);
                }
            };
            queryView.setUseQueryViewActionExportURLs(true);
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(true);
            queryView.setShowRecordSelectors(getUser().isAdministrator());
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.setAllowableContainerFilterTypes();
            return queryView;
        }

        @Override
        protected ModelAndView getHtmlView(ShowUsersForm form, BindException errors) throws Exception
        {
            VBox vbox = new VBox();
            ImpersonateView impersonateView = new ImpersonateView(getContainer(), false);

            JspView<ShowUsersForm> toggleInactiveView = new JspView<ShowUsersForm>("/org/labkey/core/user/toggleInactive.jsp", form);
            toggleInactiveView.setTitle("Users");
            toggleInactiveView.setFrame(WebPartView.FrameType.PORTAL);
            
            if (impersonateView.hasUsers())
            {
                vbox.addView(impersonateView);
            }
            vbox.addView(toggleInactiveView);
            vbox.addView(createQueryView(form, errors, false, "Users"));
            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                setHelpTopic(new HelpTopic("manageUsers"));
                return root.addChild("Site Users");
            }
            else
            {
                setHelpTopic(new HelpTopic("manageProjectMembers"));
                return root.addChild("Project Users");
            }
        }
    }

    // Site admins can act on any user
    // Project admins can only act on users who are project members
    private void authorizeUserAction(Integer userId, String action) throws UnauthorizedException
    {
        User user = getUser();

        // Site admin can do anything
        if (user.isAdministrator())
            return;

        Container c = getContainer();

        if (c.isRoot())
        {
            // Only site admin can view at the root (all users)
            HttpView.throwUnauthorized();
        }
        else
        {
            // Must be project admin to view outside the root...
            if (!c.hasPermission(user, AdminPermission.class))
                HttpView.throwUnauthorized();

            // ...and user must be a project member
            if (!SecurityManager.getProjectMembersIds(c).contains(userId))
                HttpView.throwUnauthorized("Project administrators can only " + action + " project members");
        }
    }


    private SimpleFilter authorizeAndGetProjectMemberFilter() throws UnauthorizedException
    {
        return authorizeAndGetProjectMemberFilter("UserId");
    }


    private SimpleFilter authorizeAndGetProjectMemberFilter(String userIdColumnName) throws UnauthorizedException
    {
        Container c = getContainer();
        SimpleFilter filter = new SimpleFilter();

        if (c.isRoot())
        {
            if (!getUser().isAdministrator())
                throw new UnauthorizedException();
        }
        else
        {
            SQLFragment sql = SecurityManager.getProjectMembersSQL(c.getProject());
            sql.insert(0, userIdColumnName + " IN (SELECT members.UserId ");
            sql.append(")");

            filter.addWhereClause(sql.getSQL(), sql.getParamsArray());
        }

        return filter;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ShowUserHistoryAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SimpleFilter projectMemberFilter = authorizeAndGetProjectMemberFilter("IntKey1");
            return UserAuditViewFactory.getInstance().createUserHistoryView(getViewContext(), projectMemberFilter);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
                return root.addChild("Site Users History");
            }
            else
            {
                root.addChild("Project Members", new UserUrlsImpl().getProjectMembersURL(getContainer()));
                return root.addChild("Project Members History");
            }
        }
    }


    private boolean isValidRequiredField(final String name)
    {
        return (!"Email".equals(name) && !"UserId".equals(name) &&
                !"LastLogin".equals(name) && !"DisplayName".equals(name));
    }


    @RequiresSiteAdmin
    public class ShowUserPreferencesAction extends FormViewAction<UserPreferenceForm>
    {
        public void validateCommand(UserPreferenceForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserPreferenceForm userPreferenceForm, boolean reshow, BindException errors) throws Exception
        {
            List<String> columnNames = new ArrayList<String>();
            for (String name : getDefaultUserColumnNames().split(","))
            {
                name = name.trim();
                if (isValidRequiredField(name))
                    columnNames.add(name);
            }
            List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(columnNames.toArray(new String[columnNames.size()]));
            UserPreference bean = new UserPreference(cols, UserManager.getRequiredUserFields());
            return new JspView<UserPreference>("/org/labkey/core/user/userPreferences.jsp", bean);
        }

        public boolean handlePost(UserPreferenceForm form, BindException errors) throws Exception
        {
            final StringBuilder sb = new StringBuilder();
            if (form.getRequiredFields().length > 0)
            {
                String sep = "";
                for (String field : form.getRequiredFields())
                {
                    sb.append(sep);
                    sb.append(field);
                    sep = ";";
                }
            }
            UserManager.setRequiredUserFields(sb.toString());
            return true;
        }

        public ActionURL getSuccessURL(UserPreferenceForm userPreferenceForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            return root.addChild("User Preferences");
        }
    }

    public static class AccessDetail
    {
        private List<AccessDetailRow> _rows;
        private boolean _showGroups;
        private boolean _active = true;

        public AccessDetail(List<AccessDetailRow> rows)
        {
            this(rows, true);
        }
        public AccessDetail(List<AccessDetailRow> rows, boolean showGroups)
        {
            _rows = rows;
            _showGroups = showGroups;
        }

        public List<AccessDetailRow> getRows()
        {
            return _rows;
        }
        public boolean showGroups()
        {
            return _showGroups;
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }
    }

    public static class AccessDetailRow
    {
        private Container _container;
        private String _access;
        private List<Group> _groups;
        private int _depth;

        public AccessDetailRow(Container container, String access, List<Group> groups, int depth)
        {
            _container = container;
            _access = access;
            _groups = groups;
            _depth = depth;
        }

        public String getAccess()
        {
            return _access;
        }

        public Container getContainer()
        {
            return _container;
        }

        public int getDepth()
        {
            return _depth;
        }

        public List<Group> getGroups()
        {
            return _groups;
        }

        public boolean isInheritedAcl()
        {
            return _container.isInheritedAcl();
        }
    }

    public static class UserPreference
    {
        private List<ColumnInfo> _columns;
        private String _requiredFields;

        public UserPreference(List<ColumnInfo> columns, String requiredFields)
        {
            _columns = columns;
            _requiredFields = requiredFields;
        }

        public List<ColumnInfo> getColumns(){return _columns;}
        public String getRequiredFields(){return _requiredFields;}
    }

    public static class UserPreferenceForm
    {
        private String[] _requiredFields = new String[0];

        public void setRequiredFields(String[] requiredFields){_requiredFields = requiredFields;}
        public String[] getRequiredFields(){return _requiredFields;}
    }

    private void buildAccessDetailList(MultiMap<Container, Container> containerTree, Container parent,
                                       List<AccessDetailRow> rows, User requestedUser, int depth, Map<Container, Group[]> projectGroupCache)
    {
        if (requestedUser == null)
            return;
        Collection<Container> children = containerTree.get(parent);
        if (children == null || children.isEmpty())
            return;

        for (Container child : children)
        {
            String sep = "";
            StringBuilder access = new StringBuilder();
            SecurityPolicy policy = SecurityManager.getPolicy(child);
            Set<Role> effectiveRoles = policy.getEffectiveRoles(requestedUser);
            effectiveRoles.remove(RoleManager.getRole(NoPermissionsRole.class)); //ignore no perms
            for(Role role : effectiveRoles)
            {
                access.append(sep);
                access.append(role.getName());
                sep = ", ";
            }

            List<Group> relevantGroups = new ArrayList<Group>();
            if (effectiveRoles.size() > 0)
            {
                Container project = child.getProject();
                Group[] groups = projectGroupCache.get(project);
                if (groups == null)
                {
                    groups = SecurityManager.getGroups(project, true);
                    projectGroupCache.put(project, groups);
                }
                for (Group group : groups)
                {
                    if (requestedUser.isInGroup(group.getUserId()))
                    {
                        Collection<Role> groupRoles = policy.getAssignedRoles(group);
                        for(Role role : effectiveRoles)
                        {
                            if (groupRoles.contains(role))
                                relevantGroups.add(group);
                        }
                    }
                }
            }
            rows.add(new AccessDetailRow(child, access.toString(), relevantGroups, depth));
            buildAccessDetailList(containerTree, child, rows, requestedUser, depth + 1, projectGroupCache);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UserAccessAction extends SimpleViewAction<UserForm>
    {
        private boolean _showNavTrail;
        private Integer _userId;

        public ModelAndView getView(UserForm form, BindException errors) throws Exception
        {
            String email = form.getNewEmail();
            if (email != null)
            {
                try
                {
                    User user = UserManager.getUser(new ValidEmail(email));
                    if (user != null)
                    {
                        _userId = user.getUserId();
                    }
                    else
                    {
                        HttpView.throwNotFound();
                    }
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    HttpView.throwNotFound();
                }
            }
            if (_userId == null)
            {
                _userId = form.getUserId();
            }
            User requestedUser = UserManager.getUser(_userId.intValue());
            if (requestedUser == null)
                return HttpView.throwNotFound("User not found");
            List<AccessDetailRow> rows = new ArrayList<AccessDetailRow>();

            Container c = getContainer();
            MultiMap<Container, Container> containerTree =  c.isRoot() ? ContainerManager.getContainerTree() : ContainerManager.getContainerTree(c.getProject());
            Map<Container, Group[]> projectGroupCache = new HashMap<Container, Group[]>();
            buildAccessDetailList(containerTree, c.isRoot() ? ContainerManager.getRoot() : null, rows, requestedUser, 0, projectGroupCache);
            AccessDetail details = new AccessDetail(rows);
            details.setActive(requestedUser.isActive());
            JspView<AccessDetail> accessView = new JspView<AccessDetail>("/org/labkey/core/user/userAccess.jsp", details);

            VBox view = new VBox(accessView);

            if (c.isRoot())
                view.addView(GroupAuditViewFactory.getInstance().createSiteUserView(getViewContext(), form.getUserId()));
            else
                view.addView(GroupAuditViewFactory.getInstance().createProjectMemberView(getViewContext(), form.getUserId()));

            if (form.getRenderInHomeTemplate())
            {
                _showNavTrail = true;
                return view;
            }
            else
            {
                return new PrintTemplate(view);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showNavTrail)
            {
                addUserDetailsNavTrail(root, _userId);
                root.addChild("Permissions");
                return root.addChild("Access Details: " + UserManager.getEmailForId(_userId));
            }
            return null;
        }
    }


    private void addUserDetailsNavTrail(NavTree root, Integer userId)
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            if (getUser().isAdministrator())
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
        }
        else
        {
            if (c.hasPermission(getUser(), AdminPermission.class))
                root.addChild("Project Members", new UserUrlsImpl().getProjectMembersURL(c));
        }

        if (null == userId)
            root.addChild("User Details");
        else
            root.addChild("User Details", new UserUrlsImpl().getUserDetailsURL(c, getViewContext().getActionURL()).addParameter("userId", userId.intValue()));
    }


    @RequiresLogin
    public class DetailsAction extends SimpleViewAction<UserForm>
    {
        private int _detailsUserId;

        public ModelAndView getView(UserForm form, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            _detailsUserId = form.getUserId();
            User detailsUser = UserManager.getUser(_detailsUserId);

            boolean isOwnRecord = (_detailsUserId == userId);

            // Anyone can view their own record; otherwise, make sure current user can view the details of this user
            if (!isOwnRecord)
                authorizeUserAction(_detailsUserId, "view details of");

            if (null == detailsUser || detailsUser.isGuest())
                throw new NotFoundException("User does not exist");

            Container c = getContainer();
            boolean isSiteAdmin = user.isAdministrator();
            boolean hasAdminPerm = c.hasPermission(user, AdminPermission.class);
            boolean isAnyAdmin = isSiteAdmin || hasAdminPerm;

            DataRegion rgn = getGridRegion(isOwnRecord);
            String displayEmail = UserManager.getEmailForId(_detailsUserId);
            ButtonBar bb = rgn.getButtonBar(DataRegion.MODE_DETAILS);
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (isSiteAdmin)
            {
                if (!SecurityManager.isLdapEmail(new ValidEmail(displayEmail)))
                {
                    ActionButton reset = new ActionButton("reset", "Reset Password");
                    ActionURL resetURL = new ActionURL(SecurityController.ResetPasswordAction.class, c);
                    resetURL.addParameter("email", displayEmail);
                    resetURL.addReturnURL(getViewContext().getActionURL());
                    reset.setURL(resetURL.getLocalURIString());
                    reset.setActionType(ActionButton.Action.LINK);
                    bb.add(reset);
                }

                ActionButton changeEmail = new ActionButton("", "Change Email");
                changeEmail.setActionType(ActionButton.Action.LINK);
                ActionURL changeEmailURL = getViewContext().cloneActionURL().setAction(ShowChangeEmail.class);
                changeEmail.setURL(changeEmailURL.getLocalURIString());
                bb.add(changeEmail);

                if (user.getUserId() != detailsUser.getUserId())
                {
                    ActionURL deactivateUrl = new ActionURL(detailsUser.isActive() ? DeactivateUsersAction.class : ActivateUsersAction.class, c);
                    deactivateUrl.addParameter("userId", _detailsUserId);
                    deactivateUrl.addParameter("redirUrl", getViewContext().getActionURL().getEncodedLocalURIString());
                    bb.add(new ActionButton(detailsUser.isActive() ? "Deactivate" : "Re-Activate", deactivateUrl));

                    ActionURL deleteUrl = new ActionURL(DeleteUsersAction.class, c);
                    deleteUrl.addParameter("userId", _detailsUserId);
                    bb.add(new ActionButton("Delete", deleteUrl));
                }
            }

            if (isAnyAdmin)
            {
                ActionButton viewPermissions = new ActionButton("", "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                ActionURL viewPermissionsURL = getViewContext().cloneActionURL().setAction(UserAccessAction.class);
                viewPermissions.setURL(viewPermissionsURL.getLocalURIString());
                bb.add(viewPermissions);
            }

            if (isOwnRecord)
            {
                if (!SecurityManager.isLdapEmail(new ValidEmail(user.getEmail())))
                {
                    ActionButton changePasswordButton = new ActionButton(PageFlowUtil.urlProvider(LoginUrls.class).getChangePasswordURL(c, user.getEmail(), getViewContext().getActionURL(), null), "Change Password");
                    changePasswordButton.setActionType(ActionButton.Action.LINK);
                    changePasswordButton.addContextualRole(OwnerRole.class);
                    bb.add(changePasswordButton);
                }

                ActionButton doneButton;

                if (null != form.getReturnUrl())
                {
                    doneButton = new ActionButton("Done", form.getReturnURLHelper());
                    rgn.addHiddenFormField(ReturnUrlForm.Params.returnUrl, form.getReturnUrl());
                }
                else
                {
                    Container doneContainer;

                    if (c.isRoot())
                        doneContainer = ContainerManager.getHomeContainer();
                    else
                        doneContainer = c.getProject();

                    doneButton = new ActionButton("", "Go to " + doneContainer.getName());
                    doneButton.setActionType(ActionButton.Action.LINK);
                    ActionURL doneURL = doneContainer.getStartURL(user);
                    doneButton.setURL(doneURL.getLocalURIString());
                }

                doneButton.addContextualRole(OwnerRole.class);
                bb.add(doneButton);
                bb.addContextualRole(OwnerRole.class);
            }

            DetailsView detailsView = new DetailsView(rgn, _detailsUserId);
            detailsView.getViewContext().setPermissions(ACL.PERM_READ);

            VBox view = new VBox(detailsView);

            if (isAnyAdmin)
            {
                SimpleFilter filter = new SimpleFilter("IntKey1", _detailsUserId);
                filter.addCondition("EventType", UserManager.USER_AUDIT_EVENT);

                AuditLogQueryView queryView = AuditLogService.get().createQueryView(getViewContext(), filter, UserManager.USER_AUDIT_EVENT);
                queryView.setVisibleColumns(new String[]{"CreatedBy", "Date", "Comment"});
                queryView.setTitle("History:");
                queryView.setSort(new Sort("-Date"));

                view.addView(queryView);
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, null);
            return root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    @RequiresLogin
    public class ShowUpdateAction extends FormViewAction<UpdateForm>
    {
        Integer _userId;

        public void validateCommand(UpdateForm form, Errors errors)
        {
        }

        public ModelAndView getView(UpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            if (null == form.getPkVal())
                form.setPkVal(new Integer(userId));

            boolean isOwnRecord = ((Integer) form.getPkVal()).intValue() == userId;
            HttpView view = null;

            if (user.isAdministrator() || isOwnRecord)
            {
                form.setContainer(null);
                DataRegion rgn = getGridRegion(isOwnRecord);

                rgn.removeColumns("Active");

                String returnUrl = form.getStrings().get(ReturnUrlForm.Params.returnUrl.toString());

                if (null != returnUrl)
                    rgn.addHiddenFormField(ReturnUrlForm.Params.returnUrl, returnUrl);

                view = new UpdateView(rgn, form, errors);
                view.getViewContext().setPermissions(ACL.PERM_UPDATE + ACL.PERM_READ);
            }
            else
                HttpView.throwUnauthorized();

            if (isOwnRecord)
                view =  new VBox(new HtmlView("Please enter your contact information."), view);

            _userId = (Integer)form.getPkVal();
            return view;
        }

        public boolean handlePost(UpdateForm form, BindException errors) throws Exception
        {
            User user = getUser();
            Map<String,Object> values = form.getTypedValues();
            UserManager.updateUser(user, values, form.getPkVal());
            return true;
        }

        public ActionURL getSuccessURL(UpdateForm form)
        {
            ActionURL returnURL = null;

            String returnURLString = form.getStrings().get(ReturnUrlForm.Params.returnUrl.toString());
            if (null != returnURLString)
                returnURL = new ActionURL(returnURLString);

            return new UserUrlsImpl().getUserDetailsURL(getContainer(), ((Integer)form.getPkVal()).intValue(), returnURL);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _userId);
            root.addChild("Update");
            return root.addChild(UserManager.getEmailForId(_userId));
        }
    }

    /**
     * Checks to see if the specified user has required fields in their
     * info form that have not been filled.
     * @param user
     * @return
     */
    public static boolean requiresUpdate(User user) throws SQLException
    {
        final String required = UserManager.getRequiredUserFields();
        if (user != null && required != null && required.length() > 0)
        {
            DataRegion rgn = new DataRegion();
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(getDefaultUserColumnNames());
            rgn.setColumns(columns);

            TableInfo info = rgn.getTable();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("userId", user.getUserId(), CompareType.EQUAL);

			List<ColumnInfo> select = new ArrayList<ColumnInfo>(columns);
			select.add(CoreSchema.getInstance().getTableInfoUsers().getColumn("userId"));
            Table.TableResultSet trs = Table.select(info, select, filter, null);
            try
			{
                // this should really only return one row
                if (trs.next())
                    return !validateRequiredColumns(trs.getRowMap(), info);
            }
            finally
            {
                trs.close();
            }
        }
        return false;
    }

    @RequiresSiteAdmin @CSRF
    public class ShowChangeEmail extends FormViewAction<UserForm>
    {
        private int _userId;

        public void validateCommand(UserForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            _userId = form.getUserId();

            return new JspView<ChangeEmailBean>("/org/labkey/core/user/changeEmail.jsp", new ChangeEmailBean(_userId, form.getMessage()), errors);
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            try
            {
                User user = UserManager.getUser(form.getUserId());

                String message = UserManager.changeEmail(user.getUserId(), new ValidEmail(user.getEmail()), new ValidEmail(form.getNewEmail()), getUser());

                if (null != message && message.length() > 0)
                    errors.reject(ERROR_MSG, message);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid email address");
            }
            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            return new UserUrlsImpl().getUserDetailsURL(getContainer(), form.getUserId(), form.getReturnURLHelper());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _userId);
            return root.addChild("Change Email Address: " + UserManager.getEmailForId(_userId));
        }
    }


    public static class ChangeEmailBean
    {
        public Integer userId;
        public String currentEmail;
        public String message;

        private ChangeEmailBean(Integer userId, String message)
        {
            this.userId = userId;
            this.currentEmail  = UserManager.getEmailForId(userId);
            this.message = message;
        }
    }


    public static class UpdateForm extends TableViewForm
    {
        public UpdateForm()
        {
            super(CoreSchema.getInstance().getTableInfoUsers());
        }

        // CONSIDER: implements HasValidator
        public void validateBind(BindException errors)
        {
            super.validateBind(errors);
            Integer userId = (Integer) getPkVal();
            if (userId == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "User Id cannot be null");
                return;
            }
            String displayName = (String) this.getTypedValue("DisplayName");
            if (displayName != null)
            {
                //ensure that display name is unique
                User user = UserManager.getUserByDisplayName(displayName);
                //if there's a user with this display name and it's not the user currently being edited
                if (user != null && user.getUserId() != userId.intValue())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' field conflicts with another value in the database. Please enter a different value");
                }
            }
            String phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("phone"));
            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Phone number greater than 64 characters: " + phoneNum);
            }
            phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("mobile"));
            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Mobile number greater than 64 characters: " + phoneNum);
            }
            phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("pager"));
            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Pager number greater than 64 characters: " + phoneNum);
            }
        }

        public ColumnInfo getColumnByFormFieldName(String name)
        {
            ColumnInfo info = super.getColumnByFormFieldName(name);
            final String requiredFields = UserManager.getRequiredUserFields();
            if (isColumnRequired(name, requiredFields))
            {
                return new RequiredColumn(info);
            }
            return info;
        }
    }


    public static class UserForm extends ReturnUrlForm
    {
        private int userId;
        private String newEmail;
        private String _message = null;
        private boolean _renderInHomeTemplate = true;

        public String getNewEmail()
        {
            return newEmail;
        }

        public void setNewEmail(String newEmail)
        {
            this.newEmail = newEmail;
        }

        public int getUserId()
        {
            return userId;
        }

        public void setUserId(int userId)
        {
            this.userId = userId;
        }

        public void setRenderInHomeTemplate(boolean renderInHomeTemplate){_renderInHomeTemplate = renderInHomeTemplate;}
        public boolean getRenderInHomeTemplate(){return _renderInHomeTemplate;}


        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }
    }

    /**
     * Wrapper to create required columninfos
     */
    private static class RequiredColumn extends ColumnInfo
    {
        public RequiredColumn(ColumnInfo col)
        {
            super(col, col.getParentTable());
        }

        public boolean isNullable()
        {
            return false;
        }
    }

    public static class GetUsersForm
    {
        private String _group;
        private Integer _groupId;
        private String _name;

        public String getGroup()
        {
            return _group;
        }

        public void setGroup(String group)
        {
            _group = group;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresLogin
    @RequiresPermissionClass(ReadPermission.class)
    public class GetUsersAction extends ApiAction<GetUsersForm>
    {
        protected static final String PROP_USER_ID = "userId";
        protected static final String PROP_USER_NAME = "displayName";

        public ApiResponse execute(GetUsersForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if(container.isRoot() && !getViewContext().getUser().isAdministrator())
                throw new UnauthorizedException("Only system administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            List<User> users;
            List<Map<String,Object>> userResponseList = new ArrayList<Map<String,Object>>();

            //if requesting users in a specific group...
            if(null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {

                Container project = container.getProject();

                //get users in given group/role name
                Integer groupId = form.getGroupId();
                if(null == groupId)
                    groupId = SecurityManager.getGroupId(container.getProject(), form.getGroup(), false);
                if(null == groupId)
                    throw new IllegalArgumentException("The group '" + form.getGroup() + "' does not exist in the project '"
                            + project.getPath() + "'");

                Group group = SecurityManager.getGroup(groupId.intValue());
                if(null == group)
                    throw new RuntimeException("Could not get group for group id " + groupId);

                response.put("groupId", group.getUserId());
                response.put("groupName", group.getName());
                response.put("groupCaption", SecurityManager.getDisambiguatedGroupName(group));

                users = SecurityManager.getGroupMembers(group);
            }
            else
            {
                //special-case: if container is root, return all active users
                //else, return all users in the current project
                //we've already checked above that the current user is a system admin
                if(container.isRoot())
                    users = Arrays.asList(UserManager.getActiveUsers());
                else
                    users = SecurityManager.getProjectMembers(container, true);
            }

            if(null != users)
            {
                //trim name filter to empty so we are guaranteed a non-null string
                //and conver to lower-case for the compare below
                String nameFilter = StringUtils.trimToEmpty(form.getName()).toLowerCase();
                if(nameFilter.length() > 0)
                    response.put("name", nameFilter);
                
                for(User user : users)
                {
                    //according to the docs, startsWith will return true even if nameFilter is empty string
                    if(user.getEmail().toLowerCase().startsWith(nameFilter) || user.getDisplayName(null).toLowerCase().startsWith(nameFilter))
                    {
                        Map<String,Object> userInfo = new HashMap<String,Object>();
                        userInfo.put(PROP_USER_ID, user.getUserId());

                        //force sanitize of the display name, even for logged-in users
                        userInfo.put(PROP_USER_NAME, user.getDisplayName(getViewContext()));

                        //include email address (we now require login so no guests can see the response)
                        userInfo.put("email", user.getEmail());

                        userResponseList.add(userInfo);
                    }
                }
            }

            response.put("users", userResponseList);
            return response;
        }
    }


    public static class ImpersonateBean
    {
        public List<String> emails;
        public String message;

        public ImpersonateBean(Container c, boolean isAdminConsole)
        {
            if (c.isRoot())
            {
                emails = UserManager.getUserEmailList();
                message = isAdminConsole ? "<b>Impersonate User</b>" : "";
            }
            else
            {
                // Filter to project users
                List<User> projectMembers = SecurityManager.getProjectMembers(c);
                emails = new ArrayList<String>(projectMembers.size());

                for (User member : projectMembers)
                    emails.add(member.getEmail());

                Collections.sort(emails);
                message = PageFlowUtil.filter("Impersonate user within the " + c.getName() + " project");
            }
        }
    }


    public static class ImpersonateView extends JspView<ImpersonateBean>
    {
        public ImpersonateView(Container c, boolean isAdminConsole)
        {
            super("/org/labkey/core/user/impersonate.jsp", new ImpersonateBean(c, isAdminConsole));

            if (!isAdminConsole)
                setTitle("Impersonation");
        }

        public boolean hasUsers()
        {
            return !getModelBean().emails.isEmpty();
        }
    }


    public static class ImpersonateForm extends ReturnUrlForm
    {
        private String _email;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }
    }


    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class ImpersonateAction extends SimpleRedirectAction<ImpersonateForm>
    {
        public ActionURL getRedirectURL(ImpersonateForm form) throws Exception
        {
            if (getUser().isImpersonated())
                throw new UnauthorizedException("Can't impersonate; you're already impersonating");

            String rawEmail = form.getEmail();
            ValidEmail email = new ValidEmail(rawEmail);

            if (!UserManager.userExists(email))
                throw new NotFoundException("User doesn't exist");

            final User impersonatedUser = UserManager.getUser(email);

            authorizeUserAction(impersonatedUser.getUserId(), "impersonate");
            Container c = getContainer();

            if (c.isRoot())
            {
                SecurityManager.impersonate(getViewContext(), impersonatedUser, null, form.getReturnURLHelper());
                return AppProps.getInstance().getHomePageActionURL();
            }
            else
            {
                SecurityManager.impersonate(getViewContext(), impersonatedUser, c.getProject(), form.getReturnURLHelper());
                return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
            }
        }
    }

    public static class ShowWarningMessagesForm
    {
        private boolean _showMessages = true;

        public boolean isShowMessages()
        {
            return _showMessages;
        }

        public void setShowMessages(boolean showMessages)
        {
            _showMessages = showMessages;
        }
    }

    @RequiresNoPermission
    public class SetShowWarningMessagesAction extends ApiAction<ShowWarningMessagesForm>
    {
        public ApiResponse execute(ShowWarningMessagesForm form, BindException errors) throws Exception
        {
            getViewContext().getSession().setAttribute(TemplateHeaderView.SHOW_WARNING_MESSAGES_SESSION_PROP,
                    form.isShowMessages());
            return new ApiSimpleResponse("success", true);
        }
    }
}
