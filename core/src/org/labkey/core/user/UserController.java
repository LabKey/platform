/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.collections15.MultiMap;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class UserController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(UserController.class);

    private static Logger _log = Logger.getLogger(UserController.class);

    public UserController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public static ActionURL getUserURL(String action)
    {
        return new ActionURL("user", action, "");
    }


    // Note: the column list is dynamic, changing based on the current user's permissions.
    public static String getUserColumnNames(User user)
    {
        String columnNames = "Email, DisplayName, FirstName, LastName, Phone, Mobile, Pager, IM, Description";

        if (user != null && user.isAdministrator())
            columnNames = columnNames + ", UserId, LastLogin, Active";

        return columnNames;
    }

    private class SiteUserDataRegion extends DataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            int userId = ctx.getViewContext().getUser().getUserId();
            Integer rowId = (Integer) ctx.getRow().get("userId");
            return  (userId != rowId);
        }
    }

    private DataRegion getGridRegion(boolean isOwnRecord)
    {
        final User user = getUser();
        boolean isAdministrator = user.isAdministrator();
        SiteUserDataRegion rgn = new SiteUserDataRegion();

        List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(getUserColumnNames(user));
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

        SimpleDisplayColumn accountDetails = new SimpleDisplayColumn("[Details]");
        accountDetails.setURL(ActionURL.toPathString("User", "details.view", "") + "?userId=${UserId}");
        accountDetails.setDisplayModes(DataRegion.MODE_GRID);
        rgn.addDisplayColumn(0, accountDetails);

        if (isAdministrator)
        {
            SimpleDisplayColumn securityDetails = new SimpleDisplayColumn("[Permissions]");
            securityDetails.setURL(ActionURL.toPathString("User", "userAccess.view", "") + "?userId=${UserId}");
            securityDetails.setDisplayModes(DataRegion.MODE_GRID);
            rgn.addDisplayColumn(1, securityDetails);
        }

        ButtonBar gridButtonBar = new ButtonBar();

        if (isAdministrator)
        {
            rgn.setShowRecordSelectors(true);

            ActionButton delete = new ActionButton("button", "Delete");
            delete.setScript("return verifySelected(this.form, \"deleteUsers.post\", \"post\", \"users\", \"Are you sure you want to delete these users?\")");
            delete.setActionType(ActionButton.Action.GET);
            gridButtonBar.add(delete);

            ActionButton insert = new ActionButton("showAddUsers", "Add Users");
            ActionURL actionURL = new ActionURL("Security", "showAddUsers.view", "");
            insert.setURL(actionURL.getLocalURIString());
            insert.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(insert);

            ActionButton export = new ActionButton("export", "Export to Excel");
            ActionURL exportURL = getViewContext().cloneActionURL();
            exportURL.setAction("export");
            export.setURL(exportURL.getEncodedLocalURIString());
            export.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(export);

            ActionButton preferences = new ActionButton("showUserPreferences.view", "Preferences");
            preferences.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(preferences);

            if (AuditLogService.get().isViewable())
            {
                gridButtonBar.add(new ActionButton("showUserHistory.view", "History",
                        DataRegion.MODE_ALL, ActionButton.Action.LINK));
            }
        }

        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);

        ActionButton showGrid = new ActionButton("showUsers.view?.lastFilter=true", "Show Grid");
        showGrid.setActionType(ActionButton.Action.LINK);

        if (isOwnRecord || isAdministrator)
        {
            ButtonBar detailsButtonBar = new ButtonBar();
            if (isAdministrator)
                detailsButtonBar.add(showGrid);
            ActionButton edit = new ActionButton("showUpdate.view", "Edit");
            edit.setActionType(ActionButton.Action.GET);
            detailsButtonBar.add(edit);
            rgn.setButtonBar(detailsButtonBar, DataRegion.MODE_DETAILS);
        }

        ButtonBar updateButtonBar = new ButtonBar();
        ActionButton update = new ActionButton("showUpdate.post", "Submit");
        //update.setActionType(ActionButton.Action.LINK);
        updateButtonBar.add(update);

        if (isAdministrator)
            updateButtonBar.add(showGrid);
        rgn.setButtonBar(updateButtonBar, DataRegion.MODE_UPDATE);

        return rgn;
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(new ActionURL("User", "showUsers", ""));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresSiteAdmin
    public class DeleteUsersAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Set<String> userIds = DataRegionSelection.getSelected(getViewContext(), true);
            if (userIds != null)
            {
                for (String userId : userIds)
                    UserManager.deleteUser(Integer.parseInt(userId));
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL("User", "showUsers", "");
        }
    }

    private static boolean validateRequiredColumns(Map<String, Object> resultMap, TableInfo table, ActionMessage[] errors)
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
                    if (errors.length > 0)
                        errors[0] = new ActionMessage("Error", "The field: " + col.getCaption() + " is required");
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

    @RequiresSiteAdmin
    public class ShowUsersAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataRegion rgn = getGridRegion(false);
            GridView gridView = new GridView(rgn);
            gridView.setSort(new Sort("email"));
            gridView.getViewContext().setPermissions(ACL.PERM_READ);

            return gridView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Site Users");
        }
    }

    @RequiresSiteAdmin
    public class ShowUsers extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataRegion rgn = getGridRegion(false);
            GridView gridView = new GridView(rgn);
            gridView.setSort(new Sort("email"));
            gridView.getViewContext().setPermissions(ACL.PERM_READ);

            return gridView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Site Users");
        }
    }

    @RequiresSiteAdmin
    public class ShowUserHistoryAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return UserAuditViewFactory.getInstance().createUserHistoryView(getViewContext());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
            return root.addChild("Site Users History");
        }
    }

    @RequiresSiteAdmin
    public class ExportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            try
            {
                DataRegion rgn = getGridRegion(false);
                RenderContext ctx = new RenderContext(getViewContext());
                ExcelWriter ew = new ExcelWriter(rgn.getResultSet(ctx), rgn.getDisplayColumns());
                ew.setAutoSize(true);
                ew.setSheetName("Users");
                ew.setFooter("Users");
                ew.write(getViewContext().getResponse());
            }
            catch (SQLException e)
            {
                _log.error("export: " + e);
            }
            catch (IOException e)
            {
                _log.error("export: " + e);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
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
            for (String name : getUserColumnNames(null).split(","))
            {
                name = name.trim();
                if (isValidRequiredField(name))
                    columnNames.add(name);
            }
            List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(columnNames.toArray(new String[0]));
            UserPreference bean = new UserPreference(cols, UserManager.getRequiredUserFields());
            JspView<UserPreference> view = new JspView<UserPreference>("/org/labkey/core/user/userPreferences.jsp", bean);

            return view;
        }

        public boolean handlePost(UserPreferenceForm form, BindException errors) throws Exception
        {
            final StringBuffer sb = new StringBuffer();
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
            return new ActionURL("User", "showUsers", getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
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

    public static class UserPreferenceForm extends ViewForm
    {
        private String[] _requiredFields = new String[0];

        public void setRequiredFields(String[] requiredFields){_requiredFields = requiredFields;}
        public String[] getRequiredFields(){return _requiredFields;}
    }

    private void buildAccessDetailList(MultiMap<Container, Container> containerTree, Container parent, List<AccessDetailRow> rows, User requestedUser, int depth)
    {
        if (requestedUser == null)
            return;
        Collection<Container> children = containerTree.get(parent);
        if (children == null || children.isEmpty())
            return;
        for (Container child : children)
        {
            ACL acl = child.getAcl();
            int permissions = acl.getPermissions(requestedUser);
            SecurityManager.PermissionSet set = SecurityManager.PermissionSet.findPermissionSet(permissions);
            //assert set != null : "Unknown permission set: " + permissions;
            String access;
            if (set == null)
                access = SecurityManager.PermissionSet.getLabel(permissions);
            else
            {
                access = set.getLabel();
                // use set.getPermissions because this is guaranteed to be minimal: only READ or READ_OWN will be
                // set, but not both.  This is important because otherwise we might ask the acl if we have both
                // permission bits set, which may not be the case.  We only care if the greater of the two is set,
                // which the PermissionSet object guarantees.
                permissions = set.getPermissions();
            }
            List<Group> relevantGroups = new ArrayList<Group>();
            if (set == null || set != SecurityManager.PermissionSet.NO_PERMISSIONS)
            {
                Group[] groups = SecurityManager.getGroups(child.getProject(), true);
                for (Group group : groups)
                {
                    if (requestedUser.isInGroup(group.getUserId()))
                    {
                        if (set != null && acl.hasPermission(group, permissions))
                            relevantGroups.add(group);
                        // Issue #5645, if the held permission does not correspond to a standard role,
                        // it also means that a single group does not hold the entire permission set, instead
                        // we need to check whether the group has any part of the aggregate permission.
                        else if (set == null && (acl.getPermissions(group) & permissions) != 0)
                            relevantGroups.add(group);
                    }
                }
            }
            rows.add(new AccessDetailRow(child, access, relevantGroups, depth));
            buildAccessDetailList(containerTree, child, rows, requestedUser, depth + 1);
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
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
                _userId = Integer.valueOf(form.getUserId());
            }
            User requestedUser = UserManager.getUser(_userId);
            if (requestedUser == null)
                return HttpView.throwNotFoundMV();
            List<AccessDetailRow> rows = new ArrayList<AccessDetailRow>();
            buildAccessDetailList(ContainerManager.getContainerTree(), ContainerManager.getRoot(), rows, requestedUser, 0);
            AccessDetail details = new AccessDetail(rows);
            details.setActive(requestedUser.isActive());
            JspView<AccessDetail> accessView = new JspView<AccessDetail>("/org/labkey/core/user/userAccess.jsp", details);

            VBox view = new VBox(accessView);
            if (getUser().isAdministrator())
                view.addView(GroupAuditViewFactory.getInstance().createUserView(getViewContext(), Integer.parseInt(form.getUserId())));

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
                root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
                root.addChild("User Details", ActionURL.toPathString("User", "details", getViewContext().getContainer()) + "?userId=" + _userId);
                root.addChild("Permissions");
                return root.addChild("Access Details: " + UserManager.getEmailForId(_userId));
            }
            return null;
        }
    }


    public static ActionURL getDetailsURL(int userId)
    {
        ActionURL url = getUserURL("details");
        return url.addParameter("userId", userId);
    }


    public static class DetailsURLFactoryImpl implements UserManager.UserDetailsURLFactory
    {
        public ActionURL getURL(int userId)
        {
            return getDetailsURL(userId);
        }
    }


    @RequiresLogin
    public class DetailsAction extends SimpleViewAction<UserForm>
    {
        private Integer _detailsUserId;

        public ModelAndView getView(UserForm form, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            _detailsUserId = Integer.valueOf(form.getUserId());
            boolean isOwnRecord = (_detailsUserId == userId);
            DataRegion rgn = getGridRegion(isOwnRecord);
            String displayEmail = UserManager.getEmailForId(_detailsUserId);

            if (user.isAdministrator())
            {
                ButtonBar bb = rgn.getButtonBar(DataRegion.MODE_DETAILS);

                if (!SecurityManager.isLdapEmail(new ValidEmail(displayEmail)))
                {
                    ActionButton reset = new ActionButton("reset", "Reset Password");
                    ActionURL resetURL = new ActionURL("Security", "resetPassword", "");
                    resetURL.addParameter("email", displayEmail);
                    reset.setURL(resetURL.getLocalURIString());
                    reset.setActionType(ActionButton.Action.LINK);
                    bb.add(reset);
                }

                ActionButton changeEmail = new ActionButton("", "Change Email");
                changeEmail.setActionType(ActionButton.Action.LINK);
                ActionURL changeEmailURL = getViewContext().cloneActionURL().setAction("showChangeEmail");
                changeEmail.setURL(changeEmailURL.getLocalURIString());
                bb.add(changeEmail);

                ActionButton viewPermissions = new ActionButton("", "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                ActionURL viewPermissionsURL = getViewContext().cloneActionURL().setAction("userAccess");
                viewPermissions.setURL(viewPermissionsURL.getLocalURIString());
                bb.add(viewPermissions);
            }

            if (isOwnRecord)
            {
                ActionButton doneButton;

                if (null != form.getReturnUrl())
                {
                    doneButton = new ActionButton("Done", new ActionURL(form.getReturnUrl()));
                    rgn.addHiddenFormField(ReturnUrlForm.Params.returnUrl, form.getReturnUrl());
                }
                else
                {
                    Container doneContainer;
                    if (null == getContainer() || getContainer().isRoot())
                        doneContainer = ContainerManager.getHomeContainer();
                    else
                        doneContainer = getContainer().getProject();

                    doneButton = new ActionButton("", "Go to " + doneContainer.getName());
                    doneButton.setActionType(ActionButton.Action.LINK);
                    ActionURL doneURL = getViewContext().cloneActionURL();
                    doneURL.setExtraPath(doneContainer.getPath()).setPageFlow("Project").setAction("start.view");
                    doneButton.setURL(doneURL.getLocalURIString());
                }

                rgn.getButtonBar(DataRegion.MODE_DETAILS).add(doneButton);
            }
            else
            {
                if (!user.isAdministrator())
                    HttpView.throwUnauthorized();
            }

            DetailsView detailsView = new DetailsView(rgn, _detailsUserId);
            detailsView.getViewContext().setPermissions(ACL.PERM_READ);

            VBox view = new VBox(detailsView);
            if (user.isAdministrator())
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
            root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
            root.addChild("User Details");
            return root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class AjaxTestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new GroovyView("/Security/ajaxtest.gm");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static ActionURL getUpdateURL(ActionURL returnURL)
    {
        ActionURL url = getUserURL("showUpdate");
        url.addReturnURL(returnURL);
        return url;
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
            TableViewForm.copyErrorsToStruts(errors, getViewContext().getRequest());
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

                //don't let the user make themselves inactive
                if(isOwnRecord)
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
            //the Active column is on Principals, and since SQL Server can't update
            //columns from different base tables via a View, we need to strip the Active
            //form value out of the form and do a separate call under the same transaction
            Map<String,Object> values = form.getTypedValues();
            boolean isActive = values.containsKey("Active");
            values.remove("Active");
            DbScope userScope = DbSchema.get("core").getScope();
            User user = getUser();
            try
            {
                userScope.beginTransaction();

                //update the user data
                UserManager.updateUser(user, values, form.getPkVal());

                //update Principals.Active if user is admin and not editing own record
                boolean isOwnRecord = ((Integer) form.getPkVal()).intValue() == user.getUserId();
                if(user.isAdministrator() && !isOwnRecord)
                    UserManager.setUserActive(user, ((Integer)form.getPkVal()).intValue(), isActive);

                userScope.commitTransaction();
            }
            finally
            {
                if(userScope.isTransactionActive())
                    userScope.rollbackTransaction();
            }
            return true;
        }

        public ActionURL getSuccessURL(UpdateForm form)
        {
            ActionURL details = new ActionURL("User", "details", getContainer());
            details.addParameter("userId", ObjectUtils.toString(form.getPkVal()));
            String returnUrl = form.getStrings().get(ReturnUrlForm.Params.returnUrl.toString());
            if (null != returnUrl)
                details.addParameter(ReturnUrlForm.Params.returnUrl, returnUrl);

            return details;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
            root.addChild("User Details");
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
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(getUserColumnNames(null));
            rgn.setColumns(columns);

            TableInfo info = rgn.getTable();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("userId", user.getUserId(), CompareType.EQUAL);

            Table.TableResultSet trs = Table.select(info, columns, filter, null);
            try {
                // this should really only return one row
                if (trs.next())
                {
                    Map<String, Object> rowMap = null;
                    rowMap = ResultSetUtil.mapRow(trs, rowMap);

                    return !validateRequiredColumns(rowMap, info, new ActionMessage[0]);
                }
            }
            finally
            {
                trs.close();
            }
        }
        return false;
    }

    @RequiresSiteAdmin
    public class ShowChangeEmail extends FormViewAction<UserForm>
    {
        private Integer _userId;

        public void validateCommand(UserForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            _userId = Integer.valueOf(form.getUserId());

            return new JspView<ChangeEmailBean>("/org/labkey/core/user/changeEmail.jsp", new ChangeEmailBean(_userId, form.getMessage()), errors);
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            try
            {
                User user = UserManager.getUser(Integer.parseInt(form.getUserId()));

                String message = UserManager.changeEmail(user.getUserId(), new ValidEmail(user.getEmail()), new ValidEmail(form.getNewEmail()), getUser());

                if (message.length() > 0)
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
            ActionURL redirectURL = getViewContext().cloneActionURL();
            redirectURL.setAction("details");
            return redirectURL.addParameter("userId", form.getUserId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new ActionURL("User", "showUsers", getViewContext().getContainer()));
            root.addChild("User Details", ActionURL.toPathString("User", "details", getViewContext().getContainer()) + "?userId=" + _userId);
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
                errors.reject(SpringActionController.ERROR_MSG, "User Id cannot be null");
            String displayName = (String) this.getTypedValue("DisplayName");
            if (displayName != null)
            {
                //ensure that display name is unique
                User user = UserManager.getUserByDisplayName(displayName);
                //if there's a user with this display name and it's not the user currently being edited
                if (user != null && user.getUserId() != userId)
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


    public static class UserForm extends ViewForm
    {
        private String userId;
        private String newEmail;
        private String _message = null;
        private boolean _renderInHomeTemplate = true;
        private String _returnUrl;

        public String getNewEmail()
        {
            return newEmail;
        }

        public void setNewEmail(String newEmail)
        {
            this.newEmail = newEmail;
        }

        public String getUserId()
        {
            if (null == userId)
                return String.valueOf(getUser().getUserId());
            else
                return userId;
        }

        public void setUserId(String userId)
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

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
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
}
