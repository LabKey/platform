/*
 * Copyright (c) 2003-2018 Fred Hutchinson Cancer Research Center
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
package org.labkey.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Test;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.LabKeyErrorWithHtml;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.audit.provider.GroupAuditProvider.GroupAuditEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.external.tools.ExternalToolsViewProvider;
import org.labkey.api.external.tools.ExternalToolsViewService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.GroupMembershipCache;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.SessionApiKeyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.NotebookReadPermission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.SeeGroupDetailsPermission;
import org.labkey.api.security.permissions.SeeUserDetailsPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.UpdateUserPermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ContainerUser;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.user.SecurityAccessView;
import org.labkey.core.user.UserController;
import org.labkey.core.user.UserController.AccessDetailRow;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.labkey.api.util.PageFlowUtil.filter;

public class SecurityController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
        SecurityController.class,
        SecurityApiActions.AddAssignmentAction.class,
        SecurityApiActions.AddGroupMemberAction.class,
        SecurityApiActions.AdminRotatePasswordAction.class,
        SecurityApiActions.BulkUpdateGroupAction.class,
        SecurityApiActions.ClearAssignedRolesAction.class,
        SecurityApiActions.CreateGroupAction.class,
        SecurityApiActions.CreateNewUsersAction.class,
        SecurityApiActions.DeleteGroupAction.class,
        SecurityApiActions.DeletePolicyAction.class,
        SecurityApiActions.DeleteUserAction.class,
        SecurityApiActions.EnsureLoginAction.class,
        SecurityApiActions.GetGroupPermsAction.class,
        SecurityApiActions.GetGroupsForCurrentUserAction.class,
        SecurityApiActions.GetPolicyAction.class,
        SecurityApiActions.GetRolesAction.class,
        SecurityApiActions.GetSecurableResourcesAction.class,
        SecurityApiActions.GetUserPermsAction.class,
        SecurityApiActions.ListProjectGroupsAction.class,
        SecurityApiActions.RemoveAssignmentAction.class,
        SecurityApiActions.RemoveGroupMemberAction.class,
        SecurityApiActions.RenameGroupAction.class,
        SecurityApiActions.SavePolicyAction.class
    );

    public SecurityController()
    {
        setActionResolver(_actionResolver);
    }

    public static class SecurityUrlsImpl implements SecurityUrls
    {
        @Override
        public ActionURL getManageGroupURL(Container container, String groupName, @Nullable URLHelper returnUrl)
        {
            ActionURL url = new ActionURL(GroupAction.class, container);
            if (returnUrl != null)
                url = url.addReturnURL(returnUrl);
            return url.addParameter("group", groupName);
        }

        @Override
        public ActionURL getManageGroupURL(Container container, String groupName)
        {
            return getManageGroupURL(container, groupName, null);
        }

        @Override
        public ActionURL getGroupPermissionURL(Container container, int id, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(GroupPermissionAction.class, container);
            url.addParameter("id", id);
            if (returnURL != null)
                url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getGroupPermissionURL(Container container, int id)
        {
            return getGroupPermissionURL(container, id, null);
        }

        @Override
        public ActionURL getPermissionsURL(Container container)
        {
            return getPermissionsURL(container, null);
        }

        @Override
        public ActionURL getPermissionsURL(Container container, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(PermissionsAction.class, container);
            if (returnURL != null)
               url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getSiteGroupsURL(Container container, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(PermissionsAction.class, container);
            url.addParameter("t", "sitegroups");
            if (returnURL != null)
                url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getContainerURL(Container container)
        {
            return new ActionURL(PermissionsAction.class, container);
        }

        @Override
        public ActionURL getCompleteUserURL(Container container)
        {
            return new ActionURL(CompleteUserAction.class, container).addParameter("prefix", "");
        }

        @Override
        public ActionURL getCompleteUserReadURL(Container container)
        {
            return new ActionURL(CompleteUserReadAction.class, container).addParameter("prefix", "");
        }

        @Override
        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        @Override
        public ActionURL getShowRegistrationEmailURL(Container container, ValidEmail email, String mailPrefix)
        {
            ActionURL url = new ActionURL(ShowRegistrationEmailAction.class, container);
            url.addParameter("email", email.getEmailAddress());
            url.addParameter("mailPrefix", mailPrefix);

            return url;
        }

        @Override
        public ActionURL getAddUsersURL(Container container)
        {
            return new ActionURL(AddUsersAction.class, container);
        }

        @Override
        public ActionURL getFolderAccessURL(Container container)
        {
            return new ActionURL(FolderAccessAction.class, container);
        }

        @Override
        @Nullable
        public ActionURL getExternalToolsViewURL(User user, Container c, @NotNull ActionURL returnURL)
        {
            long viewCount = ExternalToolsViewService.get().getExternalAccessViewProviders().stream()
                .filter(externalToolsViewProvider -> !externalToolsViewProvider.getViews(user).isEmpty())
                .count();
            if (viewCount > 0)
            {
                ActionURL url = new ActionURL(ExternalToolsViewAction.class, c);
                url.addReturnURL(returnURL);
                return url;
            }
            return null;
        }

        @Override
        public ActionURL getClonePermissionsURL(User targetUser, @NotNull ActionURL returnUrl)
        {
            return new ActionURL(ClonePermissionsAction.class, ContainerManager.getRoot())
                .addParameter("targetUser", targetUser.getUserId())
                .addReturnURL(returnUrl);
        }
    }

    private static void ensureGroupUserAccess(Group group, User user)
    {
        if (!user.hasSiteAdminPermission() && group.isSystemGroup())
            throw new UnauthorizedException();
    }

    private static void ensureGroupUserAccess(String group, User user)
    {
        if (!user.hasSiteAdminPermission() && group != null && (
            group.equalsIgnoreCase("Administrators") || group.equalsIgnoreCase("Users") ||
            group.equalsIgnoreCase("Guests") || group.equalsIgnoreCase("Developers")
        ))
            throw new UnauthorizedException();
    }

    private static void ensureGroupInContainer(Group group, Container c)
    {
        if (group.getContainer() == null)
        {
            if (!c.isRoot())
            {
                throw new UnauthorizedException();
            }
        }
        else
        {
            if (!c.getId().equals(group.getContainer()))
            {
                throw new UnauthorizedException();
            }
        }
    }


    private static void ensureGroupInContainer(String group, Container c)
    {
        if (group.startsWith("/"))
            group = group.substring(1);
        if (!group.contains("/"))
        {
            if (!c.isRoot())
            {
                throw new UnauthorizedException();
            }
        }
        else
        {
            String groupContainer = group.substring(0, group.lastIndexOf("/"));
            if (c.isRoot())
            {
                throw new UnauthorizedException();
            }
            String projectContainer = c.getPath();
            if (projectContainer.startsWith("/"))
                projectContainer = projectContainer.substring(1);
            if (!projectContainer.equals(groupContainer))
            {
                throw new UnauthorizedException();
            }
        }
    }

    @RequiresNoPermission
    public class BeginAction extends SimpleRedirectAction<Object>
    {
        @Override
        public URLHelper getRedirectURL(Object o)
        {
            if (null == getContainer() || getContainer().isRoot())
            {
                return new ActionURL(AddUsersAction.class, ContainerManager.getRoot());
            }
            return new ActionURL(PermissionsAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetMaxPhiLevelAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            // ComplianceService.getMaxAllowedPhi() only checks assigned permssions.
            // For current usages of this API, we want to return the "effective" permissions for resources/tables defined
            // in this container, so check isPhiRolesRequired().
            PHI maxPhi = PHI.Restricted;
            if (ComplianceService.get().isComplianceSupported() && ComplianceService.get().getFolderSettings(getContainer(), User.getAdminServiceUser()).isPhiRolesRequired())
                maxPhi = ComplianceService.get().getMaxAllowedPhi(getContainer(), getUser());

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("maxPhiLevel", maxPhi.name());
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ActionNames("permissions,project")
    public class PermissionsAction extends SimpleViewAction<PermissionsForm>
    {
        @Override
        public ModelAndView getView(PermissionsForm form, BindException errors)
        {
            String resource = getContainer().getId();
            ActionURL doneURL = form.isWizard() ? getContainer().getFolderType().getStartURL(getContainer(), getUser()) : form.getReturnActionURL();

            FolderPermissionsView permsView = new FolderPermissionsView(resource, doneURL);

            setHelpTopic("configuringPerms");
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setTitle("Permissions for " + getContainer().getPath());

            return permsView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    public static class FolderPermissionsView extends JspView<FolderPermissionsView>
    {
        public final String resource;
        public final ActionURL doneURL;
        
        FolderPermissionsView(String resource, ActionURL doneURL)
        {
            super("/org/labkey/core/security/permissions.jsp", null);
            this.setModelBean(this);
            this.setFrame(FrameType.NONE);
            this.resource = resource;
            this.doneURL = doneURL;
        }
    }


    public static class GroupForm
    {
        private String group = null;
        private int id = Integer.MIN_VALUE;
        private boolean exportActive;

        public void setId(int id)
        {
            this.id = id;
        }

        public int getId()
        {
            return id;
        }

        public void setGroup(String name)
        {
            group = name;
        }

        public String getGroup()
        {
            return group;
        }

        // validates that group is visible from container c
        public Group getGroupFor(Container c)
        {
            if (id == Integer.MIN_VALUE)
            {
                Integer gid = SecurityManager.getGroupId(group);
                if (gid == null)
                    return null;
                id = gid;
            }
            Group group = SecurityManager.getGroup(id);
            Container p = c == null ? null : c.getProject();
            if (null != group && null != p)
            {
                if (group.getContainer() != null && !p.getId().equals(group.getContainer()))
                {
                    throw new UnauthorizedException();
                }
            }
            return group;
        }

        public boolean isExportActive()
        {
            return exportActive;
        }

        @SuppressWarnings("unused")
        public void setExportActive(boolean exportActive)
        {
            this.exportActive = exportActive;
        }
    }

    public static class GroupAccessForm extends GroupForm
    {
        private boolean showAll = false;

        public boolean getShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean showAll)
        {
            this.showAll = showAll;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class StandardDeleteGroupAction extends FormHandlerAction<GroupForm>
    {
        @Override
        public void validateCommand(GroupForm form, Errors errors) {}

        @Override
        public boolean handlePost(GroupForm form, BindException errors)
        {
            try
            {
                Group group = form.getGroupFor(getContainer());
                ensureGroupInContainer(group,getContainer());
                ensureGroupUserAccess(group, getUser());
                if (group != null)
                {
                    SecurityManager.deleteGroup(group);
                    addGroupAuditEvent(getViewContext(), group, "The group: " + group.getPath() + " was deleted.");
                }
            }
            catch(NotFoundException e)
            {
                // Issue 13837: if someone else already deleted the group, no need to throw exception
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(GroupForm form)
        {
            return new ActionURL(PermissionsAction.class, getContainer());
        }
    }

    public static class PermissionsForm extends ReturnUrlForm
    {
        private boolean _wizard;
        private String _inherit;
        private String _objectId;

        public boolean isWizard()
        {
            return _wizard;
        }

        public void setWizard(boolean wizard)
        {
            _wizard = wizard;
        }

        public String getInherit()
        {
            return _inherit;
        }

        public void setInherit(String inherit)
        {
            _inherit = inherit;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(String objectId)
        {
            _objectId = objectId;
        }
    }


    private void addGroupAuditEvent(ContainerUser context, Group group, String message)
    {
        GroupAuditEvent event = new GroupAuditEvent(group.getContainer(), message);

        event.setGroup(group.getUserId());
        Container c = null==group.getContainer() ? ContainerManager.getRoot() : ContainerManager.getForId(group.getContainer());
        if (c != null && c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        else
            event.setProjectId(ContainerManager.getRoot().getId());

        AuditLogService.get().addEvent(context.getUser(), event);
    }


    public static class UpdateMembersForm extends GroupForm
    {
        private String names;
        private String[] delete;
        private boolean sendEmail;
        private boolean confirmed;
        private String mailPrefix;

        public boolean isConfirmed()
        {
            return confirmed;
        }

        @SuppressWarnings("unused")
        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public String getNames()
        {
            return this.names;
        }

        @SuppressWarnings("unused")
        public void setNames(String names)
        {
            this.names = names;
        }

        public String[] getDelete()
        {
            return delete;
        }

        @SuppressWarnings("unused")
        public void setDelete(String[] delete)
        {
            this.delete = delete;
        }

        public boolean getSendEmail()
        {
            return sendEmail;
        }

        @SuppressWarnings("unused")
        public void setSendEmail(boolean sendEmail)
        {
            this.sendEmail = sendEmail;
        }

        public String getMailPrefix()
        {
            return mailPrefix;
        }

        @SuppressWarnings("unused")
        public void setMailPrefix(String messagePrefix)
        {
            this.mailPrefix = messagePrefix;
        }
    }


    private void addGroupNavTrail(NavTree root, Group group)
    {
        root.addChild("Permissions", new ActionURL(PermissionsAction.class, getContainer()));
        root.addChild("Manage Group");
        root.addChild(group.getName() + " Group");
    }

    private ModelAndView renderGroup(Group group, BindException errors, List<HtmlString> messages)
    {
        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        ensureGroupUserAccess(group, getUser());
        Set<UserPrincipal> members = SecurityManager.getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS);
        Map<UserPrincipal, List<UserPrincipal>> redundantMembers = SecurityManager.getRedundantGroupMembers(group);

        // Warn if Site Admin group isn't assigned SiteAdminRole or Developer group isn't assigned PlatformDeveloperRole
        if (group.isAdministrators())
            verifySystemGroupIsAssignedRole(group, RoleManager.getRole(SiteAdminRole.class), errors);
        else if (group.isDevelopers())
            verifySystemGroupIsAssignedRole(group, RoleManager.getRole(PlatformDeveloperRole.class), errors);

        VBox view = new VBox(new GroupView(group, members, redundantMembers, messages, group.isSystemGroup(), errors));

        if (getUser().hasRootPermission(UserManagementPermission.class))
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_GROUP), group.getUserId());
                List<FieldKey> columns = new ArrayList<>();

                columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED));
                columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED_BY));
                columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CONTAINER));
                columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_COMMENT));

                settings.setBaseFilter(filter);
                settings.setQueryName(GroupManager.GROUP_AUDIT_EVENT);
                settings.setFieldKeys(columns);
                QueryView auditView = schema.createView(getViewContext(), settings, errors);
                auditView.setFrame(WebPartView.FrameType.TITLE);
                auditView.setTitle("Group Membership History");

                view.addView(auditView);
            }
        }

        return view;
    }

    private void verifySystemGroupIsAssignedRole(Group group, Role role, BindException errors)
    {
        Set<Role> roles = ContainerManager.getRoot().getPolicy().getRoles(new PrincipalArray(List.of(group.getUserId())));
        if (!roles.contains(role))
            errors.reject(ERROR_MSG, "Warning: This group is not assigned its standard role, "
                + role.getDisplayName() + "! Consider assigning it on the Site Permissions page.");
    }

    @RequiresPermission(AdminPermission.class)
    public class GroupAction extends FormViewAction<UpdateMembersForm>
    {
        private Group _group;
        private ActionURL _successURL;
        private List<HtmlString> _messages = new ArrayList<>();

        @Override
        public ModelAndView getView(UpdateMembersForm form, boolean reshow, BindException errors) throws Exception
        {
            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(PermissionsAction.class, getContainer()));
            return renderGroup(_group, errors, _messages);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("globalGroups");
            addGroupNavTrail(root, _group);
        }

        @Override
        public void validateCommand(UpdateMembersForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(UpdateMembersForm form, BindException errors) throws Exception
        {
            // 0 - only site admins can modify members of system groups
            // 1 - only site admins can modify groups that are assigned a privileged role
            // 2 - warn if you are deleting yourself from global or project admins
            // 3 - if user confirms delete, post to action again, with list of users to delete and confirmation flag.

            Container container = getContainer();

            if (!container.isRoot() && !container.isProject())
                container = container.getProject();

            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(PermissionsAction.class, container));

            verifyUserCanModifyGroup(_group, getUser());

            _messages = new ArrayList<>();

            //check for new users to add.
            String[] addNames = form.getNames() == null ? new String[0] : form.getNames().split("\n");

            // split the list of names to add into groups and users (emails)
            List<Group> addGroups = new ArrayList<>();
            List<String> emails = new ArrayList<>();
            for (String name : addNames)
            {
                // check for the groupId in the global group list or in the project
                Integer gid = SecurityManager.getGroupId(null, StringUtils.trim(name), false);
                Integer pid = SecurityManager.getGroupId(container, StringUtils.trim(name), false);

                if (null != gid || null != pid)
                {
                    Group g = (gid != null ? SecurityManager.getGroup(gid) : SecurityManager.getGroup(pid));
                    addGroups.add(g);
                }
                else
                {
                    emails.add(name);
                }
            }

            List<String> invalidEmails = new ArrayList<>();
            List<ValidEmail> addEmails = SecurityManager.normalizeEmails(emails, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not add user " + filter(e) + ": Invalid email address");
            }

            String[] removeNames = form.getDelete();
            invalidEmails.clear();

            // delete group members by ID (can be both groups and users)
            List<UserPrincipal> removeIds = new ArrayList<>();
            if (removeNames != null)
            {
                for (String removeName : removeNames)
                {
                    // first check if the member name is a site group, otherwise get principal based on this container
                    Integer id = SecurityManager.getGroupId(null, removeName, false);
                    if (null != id)
                    {
                        removeIds.add(SecurityManager.getGroup(id));
                    }
                    else
                    {
                        UserPrincipal principal = SecurityManager.getPrincipal(removeName, container);

                        // Race condition... principal could have been deleted, #18560
                        if (null != principal)
                            removeIds.add(principal);
                    }
                }
            }

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not remove user " + filter(e) + ": Invalid email address");
            }

            if (_group != null)
            {
                //check for users to delete
                if (removeNames != null)
                {
                    // Note: deleteMembers() will throw if removing this member will result in no root admins
                    SecurityManager.deleteMembers(_group, removeIds);
                }

                // issue 43366 : users without the AddUserPermission are still allowed to create new users through the
                // manage group UI.
                if (!container.hasPermission(getUser(), AddUserPermission.class))
                {
                    for (ValidEmail email : addEmails)
                    {
                        if (!UserManager.userExists(email))
                        {
                            errors.reject(ERROR_MSG, "You do not have permissions to create new users.");
                            break;
                        }
                    }
                }

                if (!errors.hasErrors() && (!addGroups.isEmpty() || !addEmails.isEmpty()))
                {
                    // add new users
                    List<User> addUsers = new ArrayList<>(addEmails.size());
                    for (ValidEmail email : addEmails)
                    {
                        HtmlString addMessage = SecurityManager.addUser(getViewContext(), email, form.getSendEmail(), form.getMailPrefix());
                        if (addMessage != null)
                            _messages.add(addMessage);

                        // get the user and ensure that the user is still active
                        User user = UserManager.getUser(email);

                        // Null check since user creation may have failed, #8066
                        if (null != user)
                        {
                            if (!user.isActive())
                                errors.reject(ERROR_MSG, "You may not add the user '" + PageFlowUtil.filter(email)
                                        + "' to this group because that user account is currently deactivated." +
                                        " To reactivate this account, contact your system administrator.");
                            else
                                addUsers.add(user);
                        }
                    }

                    List<String> addErrors = SecurityManager.addMembers(_group, addGroups);
                    addErrors.addAll(SecurityManager.addMembers(_group, addUsers));

                    for (String error : addErrors)
                        errors.reject(ERROR_MSG, error);
                }
            }

            _successURL = new ActionURL(GroupAction.class, getContainer()).addParameter("id", _group.getUserId());

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(UpdateMembersForm updateMembersForm)
        {
            return _messages.isEmpty() ? _successURL : null;
        }
    }

    /**
     * Throws UnauthorizedException if user is not allowed to modify this group
     * @param group the Group being modified
     * @param user the current admin user
     */
    public static void verifyUserCanModifyGroup(Group group, User user)
    {
        if (!user.hasSiteAdminPermission())
        {
            if (group.isSystemGroup())
                throw new UnauthorizedException("Can not update members of system group: " + group.getName());

            if (group.hasPrivilegedRole())
                throw new UnauthorizedException("Can not update members of a group assigned a privileged role: " + group.getName());
        }
    }

    public static class CompleteMemberForm
    {
        private String _prefix;
        private Integer _groupId;
        private Group _group;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public Group getGroup()
        {
            if (_group == null && getGroupId() != null)
            {
                _group = SecurityManager.getGroup(getGroupId());
                if (_group == null)
                {
                    throw new NotFoundException("Could not find group for id " + getGroupId());
                }
            }

            return _group;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CompleteMemberAction extends ReadOnlyApiAction<CompleteMemberForm>
    {
        @Override
        public ApiResponse execute(CompleteMemberForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Group> allGroups = SecurityManager.getGroups(getContainer().getProject(), true);
            Collection<Group> validGroups = SecurityManager.getValidPrincipals(form.getGroup(), allGroups);

            Collection<User> validUsers = SecurityManager.getValidPrincipals(form.getGroup(), UserManager.getActiveUsers());

            List<JSONObject> completions = new ArrayList<>();

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(validGroups, validUsers, getUser(), getContainer()))
                completions.add(completion.toJSON());

            response.put("completions", completions);

            return response;
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;
        private boolean _includeInactive = false;
        private boolean _excludeSiteAdmins = false;
        private Set<Integer> _excludeUsers = Collections.emptySet();

        public String getPrefix()
        {
            return _prefix;
        }

        @SuppressWarnings("unused")
        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public boolean isIncludeInactive()
        {
            return _includeInactive;
        }

        @SuppressWarnings("unused")
        public void setIncludeInactive(boolean includeInactive)
        {
            _includeInactive = includeInactive;
        }

        public boolean isExcludeSiteAdmins()
        {
            return _excludeSiteAdmins;
        }

        @SuppressWarnings("unused")
        public void setExcludeSiteAdmins(boolean excludeSiteAdmins)
        {
            _excludeSiteAdmins = excludeSiteAdmins;
        }

        public Set<Integer> getExcludeUsers()
        {
            return _excludeUsers;
        }

        @SuppressWarnings("unused")
        public void setExcludeUsers(Set<Integer> excludeUsers)
        {
            _excludeUsers = excludeUsers;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CompleteUserAction extends ReadOnlyApiAction<CompleteUserForm>
    {
        @Override
        public ApiResponse execute(CompleteUserForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> completions = new ArrayList<>();
            Collection<User> users = UserManager.getUsers(form.isIncludeInactive());
            Set<Integer> excludedUsers = form.getExcludeUsers();
            boolean excludeSiteAdmins = form.isExcludeSiteAdmins();

            if (!excludedUsers.isEmpty() || excludeSiteAdmins)
            {
                // New list with excluded users removed
                users = users.stream()
                    .filter(u -> !excludedUsers.contains(u.getUserId()))
                    .filter(u -> !excludeSiteAdmins || !u.hasSiteAdminPermission())
                    .toList();
            }

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(users, getUser(), getContainer()))
                completions.add(completion.toJSON());

            response.put("completions", completions);

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CompleteUserReadAction extends ReadOnlyApiAction<CompleteUserForm>
    {
        @Override
        public ApiResponse execute(CompleteUserForm completeUserForm, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> completions = new ArrayList<>();

            List<User> possibleUsers = SecurityManager.getUsersWithPermissions(getContainer(), Collections.singleton(ReadPermission.class));
            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, getUser(), getContainer()))
                completions.add(completion.toJSON());

            response.put("completions", completions);

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GroupExportAction extends ExportAction<GroupForm>
    {
        @Override
        public void export(GroupForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String group = trimToNull(form.getGroup());
            if (null == group)
                throw new NotFoundException("group not specified");
            if (group.startsWith("/"))
                group = group.substring(1);
            // validate that group is in the current project!
            Container c = getContainer();
            ensureGroupInContainer(group, c);
            ensureGroupUserAccess(group, getUser());
            List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group, true);

            DataRegion rgn = new DataRegion();
            UserSchema schema = QueryService.get().getUserSchema(getUser(), c, CoreQuerySchema.NAME);
            TableInfo tinfo = schema.getTable(CoreQuerySchema.USERS_TABLE_NAME);
            List<ColumnInfo> columns = new ArrayList<>();
            for (FieldKey fk : tinfo.getDefaultVisibleColumns())
                columns.add(tinfo.getColumn(fk));

            rgn.setColumns(columns);
            RenderContext ctx = new RenderContext(getViewContext());
            List<Integer> userIds = new ArrayList<>();
            final List<Pair<Integer, String>> memberGroups = new ArrayList<>();
            for (Pair<Integer, String> member : members)
            {
                Group g = SecurityManager.getGroup(member.getKey());
                if (null == g)
                {
                    userIds.add(member.getKey());
                }
                else
                {
                    memberGroups.add(member);
                }
            }
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(FieldKey.fromParts("UserId"), userIds);
            if (form.isExportActive())
                filter.addCondition(FieldKey.fromParts("Active"), true);
            ctx.setBaseFilter(filter);
            rgn.prepareDisplayColumns(c);
            ExcelWriter ew = new ExcelWriter(()->rgn.getResults(ctx), rgn.getDisplayColumns())
            {
                @Override
                public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException, SQLException, IOException
                {
                    for (Pair<Integer, String> memberGroup : memberGroups)
                    {
                        Map<String, Object> row = new CaseInsensitiveHashMap<>();
                        row.put("displayName", memberGroup.getValue());
                        row.put("userId", memberGroup.getKey());
                        ctx.setRow(row);
                        renderGridRow(sheet, ctx, visibleColumns);
                    }
                    super.renderGrid(ctx, sheet, visibleColumns);
                }
            };
            ew.setAutoSize(true);
            ew.setSheetName(group + " Members");
            ew.setFooter(group + " Members");
            ew.renderWorkbook(response);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GroupPermissionAction extends SimpleViewAction<GroupAccessForm>
    {
        private Group _requestedGroup;

        @Override
        public ModelAndView getView(GroupAccessForm form, BindException errors)
        {
            _requestedGroup = form.getGroupFor(getContainer());

            if (_requestedGroup == null)
                throw new NotFoundException("Group not found");

            if (getContainer().isRoot() && _requestedGroup.isProjectGroup())
                throw new UnauthorizedException("Can not view a project group's permissions from the root container");

            return new SecurityAccessView(getContainer(), getUser(), _requestedGroup, form.getShowAll());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(PermissionsAction.class, getContainer()));
            root.addChild("Group Permissions");
            root.addChild(_requestedGroup == null || _requestedGroup.isUsers() ? "Access Details: Site Users" : "Access Details: " + _requestedGroup.getName());
        }
    }

    protected enum AuditChangeType
    {
        explicit,
        fromInherited,
        toInherited,
    }
   
    @RequiresPermission(AdminPermission.class)
    public class UpdatePermissionsAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object target, Errors errors) {}

        private void addAuditEvent(User user, String comment, int groupId)
        {
            if (user != null)
                SecurityManager.addAuditEvent(getContainer(), user, comment, groupId);
        }

        // UNDONE move to SecurityManager
        private void addAuditEvent(Group group, SecurityPolicy newPolicy, SecurityPolicy oldPolicy, AuditChangeType changeType)
        {
            Role oldRole = RoleManager.getRole(NoPermissionsRole.class);
            if(null != oldPolicy)
            {
                List<Role> oldRoles = oldPolicy.getAssignedRoles(group);
                if(oldRoles.size() > 0)
                    oldRole = oldRoles.get(0);
            }

            Role newRole = RoleManager.getRole(NoPermissionsRole.class);
            if(null != newPolicy)
            {
                List<Role> newRoles = newPolicy.getAssignedRoles(group);
                if(newRoles.size() > 0)
                    newRole = newRoles.get(0);
            }

            switch (changeType)
            {
                case explicit:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
                case fromInherited:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s (inherited) to %s",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
                case toInherited:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s (inherited)",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
            }
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            ViewContext ctx = getViewContext();
            Container c = getContainer();
            AuditChangeType changeType = AuditChangeType.explicit;

            // UNDONE: remove objectId from the form
            assert c.getId().equals(ctx.get("objectId"));

            boolean inherit = "on".equals(ctx.get("inheritPermissions"));

            if (c.isProject())
            {
                boolean newSubfoldersInherit = "on".equals(ctx.get("newSubfoldersInheritPermissions"));
                if (newSubfoldersInherit != SecurityManager.shouldNewSubfoldersInheritPermissions(c))
                {
                    SecurityManager.setNewSubfoldersInheritPermissions(c, getUser(), newSubfoldersInherit);
                }
            }

            if (inherit)
            {
                addAuditEvent(getUser(), String.format("Container %s was updated to inherit security permissions", c.getName()), 0);

                //get existing policy specifically for this container
                SecurityPolicy oldPolicy = SecurityPolicyManager.getPolicy(c, false);

                //delete
                SecurityPolicyManager.deletePolicy(c);

                //now get the nearest policy for this container so we can write to the
                //audit log how the permissions have changed
                SecurityPolicy newPolicy = SecurityPolicyManager.getPolicy(c);

                changeType = AuditChangeType.toInherited;

                for (Group g : SecurityManager.getGroups(c.getProject(), true))
                {
                    addAuditEvent(g, newPolicy, oldPolicy, changeType);
                }
            }
            else
            {
                MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(c);

                //get the current nearest policy for this container
                SecurityPolicy oldPolicy = SecurityPolicyManager.getPolicy(c);

                //if resource id is not the same as the current container
                //set change type to indicate we're moving from inherited
                if(!oldPolicy.getResourceId().equals(c.getResourceId()))
                    changeType = AuditChangeType.fromInherited;

                final AuditChangeType ct = changeType;
                HttpServletRequest request = getViewContext().getRequest();

                IteratorUtils.asIterator(request.getParameterNames()).forEachRemaining(key -> {
                    try
                    {
                        if (!key.startsWith("group."))
                            return;
                        int groupid = (int) Long.parseLong(key.substring(6), 16);
                        Group group = SecurityManager.getGroup(groupid);
                        if (null == group)
                            return; //invalid group id

                        String roleName = request.getParameter(key);
                        Role role = RoleManager.getRole(roleName);
                        if (null == role)
                            return; //invalid role name

                        newPolicy.addRoleAssignment(group, role);
                        addAuditEvent(group, newPolicy, oldPolicy, ct);
                    }
                    catch (NumberFormatException x)
                    {
                        // continue;
                    }
                });

                SecurityPolicyManager.savePolicy(newPolicy, getUser());
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL("Security", getViewContext().getRequest().getParameter("view"), getContainer());
        }

    }

    public static class AddUsersForm extends ReturnUrlForm
    {
        private boolean _sendMail;
        private String _newUsers;
        private String _cloneUser;
        private boolean _skipProfile;
        private String _provider = null;

        private final HtmlStringBuilder _message = HtmlStringBuilder.of();

        @SuppressWarnings("unused")
        public void setProvider(String provider)
        {
            _provider = provider;
        }

        public String getProvider()
        {
            return _provider;
        }

        @SuppressWarnings("unused")
        public void setNewUsers(String newUsers)
        {
            _newUsers = newUsers;
        }

        public String getNewUsers()
        {
            return _newUsers;
        }

        @SuppressWarnings("unused")
        public void setSendMail(boolean sendMail)
        {
            _sendMail = sendMail;
        }

        public boolean getSendMail()
        {
            return _sendMail;
        }

        @SuppressWarnings("unused")
        public void setCloneUser(String cloneUser)
        {
            _cloneUser = cloneUser;
        }

        public String getCloneUser(){return _cloneUser;}

        @SuppressWarnings("unused")
        public void setSkipProfile(boolean skipProfile)
        {
            _skipProfile = skipProfile;
        }

        public boolean isSkipProfile()
        {
            return _skipProfile;
        }

        public void addMessage(HtmlString message)
        {
            if (!_message.isEmpty())
                _message.unsafeAppend("<br/>");
            _message.append(message);
        }

        public HtmlString getMessage()
        {
            return _message.getHtmlString();
        }
    }


    @RequiresPermission(AddUserPermission.class)
    public class AddUsersAction extends FormViewAction<AddUsersForm>
    {
        @Override
        public ModelAndView getView(AddUsersForm form, boolean reshow, BindException errors)
        {
            return new JspView<Object>("/org/labkey/core/security/addUsers.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("addUsers");
            root.addChild("Site Users", urlProvider(UserUrls.class).getSiteUsersURL());
            root.addChild("Add Users");
        }

        @Override
        public void validateCommand(AddUsersForm form, Errors errors) {}

        @Override
        public boolean handlePost(AddUsersForm form, BindException errors) throws Exception
        {
            String[] rawEmails = form.getNewUsers() == null ? null : form.getNewUsers().split("\n");
            List<String> invalidEmails = new ArrayList<>();
            List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            User userToClone = null;

            final String sourceUser = form.getCloneUser();
            if (sourceUser != null && sourceUser.length() > 0)
            {
                try
                {
                    final ValidEmail emailToClone = new ValidEmail(sourceUser);
                    userToClone = UserManager.getUser(emailToClone);
                    if (userToClone == null)
                        errors.addError(new LabKeyError("Failed to clone user permissions " + emailToClone + ": User email does not exist in the system"));
                }
                catch (InvalidEmailException e)
                {
                    errors.addError(new LabKeyError("Failed to clone user permissions " + sourceUser.trim() + ": Invalid email address"));
                }
            }

            // don't attempt to create the users if the user to clone is invalid
            if (errors.getErrorCount() > 0)
            {
                return false;
            }

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                if (!"".equals(rawEmail.trim()))
                    errors.addError(new LabKeyError("Failed to create user " + rawEmail.trim() + ": Invalid email address"));
            }

            List<Pair<String, String>> extraParams = new ArrayList<>();
            if (form.isSkipProfile())
                extraParams.add(new Pair<>("skipProfile", "1"));

            URLHelper returnURL = null;
            if (null != form.getReturnUrl())
            {
                extraParams.add(new Pair<>(ActionURL.Param.returnUrl.name(), form.getReturnUrl()));
                returnURL = form.getReturnURLHelper();
            }

            for (ValidEmail email : emails)
            {
                HtmlString result = SecurityManager.addUser(getViewContext(), email, form.getSendMail(), null, extraParams, form.getProvider(), true);
                User newUser = UserManager.getUser(email);

                if (newUser == null)
                {
                    errors.addError(new LabKeyErrorWithHtml("", result));
                }
                else
                {
                    if (HtmlString.isBlank(result))
                    {
                        ActionURL url = urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), newUser.getUserId(), returnURL);
                        result = HtmlString.unsafe(PageFlowUtil.filter(email) + " was already a registered system user. Click <a href=\"" + url.getEncodedLocalURIString() + "\">here</a> to see this user's profile and history.");
                    }
                    else if (userToClone != null)
                    {
                        if (userToClone.hasPrivilegedRole() && !getUser().hasSiteAdminPermission())
                        {
                            errors.addError(new LabKeyError(userToClone.getEmail() + " cannot be cloned. Only site administrators can clone users assigned a privileged role."));
                        }
                        else
                        {
                            try (Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
                            {
                                audit(newUser, "New user " + newUser.getEmail() + " had group memberships and role assignments cloned from user " + userToClone.getEmail());
                                clonePermissions(userToClone, UserManager.getUser(email), getUser().hasSiteAdminPermission());
                                result = HtmlStringBuilder.of(result).append(" Group memberships and role assignments were cloned from " + userToClone.getEmail() + ".").getHtmlString();
                                transaction.commit();
                            }
                        }
                    }
                    form.addMessage(HtmlString.unsafe(String.format("%s<meta userId='%d' email='%s'/>", result, newUser.getUserId(), PageFlowUtil.filter(newUser.getEmail()))));
                }
            }

            return false;
        }

        @Override
        public ActionURL getSuccessURL(AddUsersForm addUsersForm)
        {
            throw new UnsupportedOperationException();
        }
    }

    private void audit(User targetUser, String message)
    {
        GroupAuditEvent event = new GroupAuditEvent(ContainerManager.getRoot().getId(), message);
        event.setUser(targetUser.getUserId());
        event.setProjectId(ContainerManager.getRoot().getId());
        AuditLogService.get().addEvent(getUser(), event);
    }

    private void clonePermissions(User source, User target, boolean currentUserIsSiteAdmin)
    {
        if (source != null && target != null)
        {
            // Clone group memberships
            handleGroups(source, group -> {
                if (!target.isInGroup(group.getUserId()))
                {
                    if (currentUserIsSiteAdmin || !group.hasPrivilegedRole())
                    {
                        try
                        {
                            SecurityManager.addMember(group, target);
                        }
                        catch (InvalidGroupMembershipException e)
                        {
                            // Best effort... fail quietly
                        }
                    }
                }
            });

            target.refreshGroups();

            // Clone direct role assignments
            handleDirectRoleAssignments(source, (policy, roles) -> {
                for (Role role : roles)
                {
                    if (currentUserIsSiteAdmin || !role.isPrivileged())
                        policy.addRoleAssignment(target, role, false);
                }

                SecurityPolicyManager.savePolicy(policy, getUser());
            });
        }
    }

    // Delete all container permissions. Note: savePolicy() and deleteMember() throw on some unauthorized actions
    // (e.g., App Admin attempting to delete Site Admin perms, deleting the last root admin)
    private void deletePermissions(User user)
    {
        if (user != null)
        {
            // Delete group memberships
            handleGroups(user, group -> {
                if (user.isInGroup(group.getUserId()))
                {
                    SecurityManager.deleteMember(group, user);
                }
            });

            user.refreshGroups(); // We just deleted them all; refresh so subsequent operations see that

            // Delete direct role assignments
            handleDirectRoleAssignments(user, (policy, roles) -> {
                policy.clearAssignedRoles(user);
                SecurityPolicyManager.savePolicy(policy, getUser());
            });
        }
    }

    private void handleGroups(User user, Consumer<Group> consumer)
    {
        GroupMembershipCache.getGroupMemberships(user.getUserId()).stream()
            .map(SecurityManager::getGroup)
            .filter(Objects::nonNull)
            .forEach(consumer);
    }

    private void handleDirectRoleAssignments(User user, BiConsumer<MutableSecurityPolicy, Collection<Role>> consumer)
    {
        Set<Container> containers = ContainerManager.getAllChildren(ContainerManager.getRoot());

        for (Container container: containers)
        {
            if (container.isInheritedAcl())
                continue;

            MutableSecurityPolicy policy = new MutableSecurityPolicy(container, container.getPolicy());
            Collection<Role> roles = policy.getAssignedRoles(user);

            if (!roles.isEmpty())
            {
                consumer.accept(policy, roles);
            }
        }
    }

    public static class ClonePermissionsForm extends ReturnUrlForm
    {
        private String _cloneUser;
        private int _targetUser;

        public String getCloneUser()
        {
            return _cloneUser;
        }

        @SuppressWarnings("unused")
        public void setCloneUser(String cloneUser)
        {
            _cloneUser = cloneUser;
        }

        public int getTargetUser()
        {
            return _targetUser;
        }

        @SuppressWarnings("unused")
        public void setTargetUser(int targetUser)
        {
            _targetUser = targetUser;
        }

        public @Nullable User getTargetUserObject()
        {
            int userId = getTargetUser();
            return 0 == userId ? null : UserManager.getUser(userId);
        }
    }

    @RequiresPermission(UserManagementPermission.class)
    public class ClonePermissionsAction extends FormViewAction<ClonePermissionsForm>
    {
        private ClonePermissionsForm _form;
        private User _source;
        private User _target;

        @Override
        public void validateCommand(ClonePermissionsForm form, Errors errors)
        {
            String sourceEmail = form.getCloneUser();

            if (null == sourceEmail)
            {
                errors.reject(ERROR_MSG, "Clone user is required");
                return;
            }

            try
            {
                _source = UserManager.getUser(new ValidEmail(sourceEmail));
            }
            catch (InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid clone user email address");
                return;
            }

            if (null == _source)
            {
                errors.reject(ERROR_MSG, "Unknown clone user");
                return;
            }

            _target = form.getTargetUserObject();

            if (null == _target)
                errors.reject(ERROR_MSG, "Unknown target user");

            if (!getUser().hasSiteAdminPermission())
            {
                if (_source.hasPrivilegedRole())
                    errors.reject(ERROR_MSG, "Only site administrators can clone from users assigned a privileged role");
                else if (_target.hasSiteAdminPermission())
                    errors.reject(ERROR_MSG, "Only site administrators can clone to users assigned a privileged role");
            }
        }

        @Override
        public ModelAndView getView(ClonePermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;

            // We already have a spring error if targetUser parameter is blank
            if (!errors.hasErrors() && null == form.getTargetUserObject())
                errors.reject(ERROR_MSG, "Unknown target user");

            return new JspView<>("/org/labkey/core/security/clonePermissions.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ClonePermissionsForm form, BindException errors) throws Exception
        {
            try (Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                audit(_target, "The user " + _target.getEmail() + " had their group memberships and role assignments deleted and replaced with those of user " + _source.getEmail());

                // Determine and stash this before delete because we could be deleting the current user's permissions
                boolean currentUserIsSiteAdmin = getUser().hasSiteAdminPermission();
                deletePermissions(_target);
                clonePermissions(_source, _target, currentUserIsSiteAdmin);
                transaction.commit();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ClonePermissionsForm form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            UserController.addUserDetailsNavTrail(getContainer(), getUser(), root, _form.getReturnActionURL());
            root.addChild("Clone Permissions");
        }
    }

    public static class EmailForm extends ReturnUrlForm
    {
        private String _email;
        private String _mailPrefix;

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getMailPrefix()
        {
            return _mailPrefix;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMailPrefix(String mailPrefix)
        {
            _mailPrefix = mailPrefix;
        }
    }

    private abstract class AbstractEmailAction extends SimpleViewAction<EmailForm>
    {
        protected abstract SecurityMessage createMessage(EmailForm form);

        @Override
        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            Writer out = getViewContext().getResponse().getWriter();
            String rawEmail = form.getEmail();

            try
            {
                ValidEmail email = new ValidEmail(rawEmail);

                SecurityMessage message = createMessage(form);

                // Issue 33254: only allow Site Admins to see the verification token
                message.setMaskToken(true);

                if (SecurityManager.isVerified(email))
                {
                    out.write("Can't display " + message.getType().toLowerCase() + "; " + PageFlowUtil.filter(email) + " has already chosen a password.");
                }
                else
                {
                    String verification = SecurityManager.getVerification(email);
                    ActionURL verificationURL = SecurityManager.createVerificationURL(getContainer(), email, verification, null);
                    SecurityManager.renderEmail(getContainer(), getUser(), message, email.getEmailAddress(), verificationURL, out);
                }
            }
            catch (InvalidEmailException e)
            {
                out.write("Invalid email address: " + PageFlowUtil.filter(rawEmail));
            }

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowRegistrationEmailAction extends AbstractEmailAction
    {
        @Override
        protected SecurityMessage createMessage(EmailForm form)
        {
            // Site admins can see the email for everyone, but project admins can only see it for users they added
            if (!getUser().hasRootPermission(AddUserPermission.class))
            {
                try
                {
                    ValidEmail email = new ValidEmail(form.getEmail());
                    User user = UserManager.getUser(email);
                    if (user == null)
                    {
                        throw new NotFoundException();
                    }
                    if (user.getCreatedBy() == null || getUser().getUserId() != user.getCreatedBy().intValue())
                    {
                        throw new UnauthorizedException();
                    }
                }
                catch (InvalidEmailException e)
                {
                    throw new NotFoundException("Invalid email address: " + form.getEmail());
                }
            }
            return SecurityManager.getRegistrationMessage(form.getMailPrefix(), false);
        }
    }


    @RequiresPermission(UpdateUserPermission.class)
    public class ShowResetEmailAction extends AbstractEmailAction
    {
        @Override
        protected SecurityMessage createMessage(EmailForm form)
        {
            return SecurityManager.getResetMessage(false);
        }
    }


    /**
     * Base class for admin password actions
     */
    private abstract static class AdminPasswordAction extends ConfirmAction<EmailForm>
    {
        abstract String getTitle();
        abstract String getVerb();
        abstract HtmlString getConfirmationMessage(boolean loginExists, String emailAddress);

        @Override
        public ModelAndView getConfirmView(EmailForm emailForm, BindException errors)
        {
            setTitle(getTitle());

            boolean loginExists = false;

            try
            {
                loginExists = SecurityManager.loginExists(new ValidEmail(emailForm.getEmail()));
            }
            catch (InvalidEmailException e)
            {
                // Allow display and edit of users with invalid email addresses so they can be fixed, #12276.
            }

            return new HtmlView(getConfirmationMessage(loginExists, emailForm.getEmail()));
        }

        @Override
        public void validateCommand(EmailForm form, Errors errors)
        {
            String rawEmail = form.getEmail();

            try
            {
                ValidEmail email = new ValidEmail(rawEmail);

                // don't let non-site admin delete/reset password of site admin
                User formUser = UserManager.getUser(email);
                if (formUser != null && !getUser().hasSiteAdminPermission() && formUser.hasSiteAdminPermission())
                    errors.reject(ERROR_MSG, "Permission denied: not authorized to " + getVerb() + " password for a Site Admin user.");
            }
            catch (InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, getVerb() + " failed: invalid email address.");
            }
        }

        @Override
        public @NotNull URLHelper getSuccessURL(EmailForm emailForm)
        {
            return emailForm.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL());
        }
    }

    /**
     * Invalidate existing password and send new password link
     */
    @RequiresPermission(UpdateUserPermission.class)
    public class AdminResetPasswordAction extends AdminPasswordAction
    {
        @Override
        String getTitle()
        {
            return "Confirm Password Reset";
        }

        @Override
        String getVerb()
        {
            return "reset";
        }

        @Override
        HtmlString getConfirmationMessage(boolean loginExists, String emailAddress)
        {
            return HtmlString.of(loginExists ?
                "You are about to clear the user's current password, send the user a reset password email, and force the user to pick a new password to access the site." :
                "You are about to send the user a reset password email, letting the user pick a password to access the site.");
        }

        private boolean _loginExists;

        @Override
        public boolean handlePost(EmailForm form, BindException errors) throws Exception
        {
            try
            {
                ValidEmail email = new ValidEmail(form.getEmail());
                _loginExists = SecurityManager.loginExists(email);
                SecurityManager.adminRotatePassword(email, errors, getContainer(), getUser(), getMailHelpText(form.getEmail()));
            }
            catch (InvalidEmailException e)
            {
                //Should be caught in validation
                errors.addError(new LabKeyError(new Exception("Invalid email address." + e.getMessage(), e)));
            }

            return !errors.hasErrors();
        }

        @Override
        public ModelAndView getSuccessView(EmailForm form)
        {
            ActionURL actionURL = new ActionURL(ShowResetEmailAction.class, getContainer()).addParameter("email", form.getEmail());

            String page = String.format(
                "<p>%1$s: Password %2$s.</p><p>Email sent. Click <a href=\"%3$s\" target=\"_blank\">here</a> to see the email.</p>%4$s",
                PageFlowUtil.filter(form.getEmail()),
                _loginExists ? "reset" : "created",
                PageFlowUtil.filter(actionURL.getLocalURIString()),
                PageFlowUtil.button("Done").href(form.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL()))
            );

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            setTitle("Password Reset Success");

            return HtmlView.unsafe(page);
        }

        @Override
        public ModelAndView getFailView(EmailForm form, BindException errors)
        {
            HtmlStringBuilder builder = HtmlStringBuilder.of()
                .unsafeAppend("<p>")
                .append(form.getEmail() + ": Password " + (_loginExists ? "reset" : "created") + ".")
                .unsafeAppend("</p><p>")
                .append(getErrorMessage(errors))
                .unsafeAppend("</p>")
                .append(PageFlowUtil.button("Done").href(form.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL())));

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            setTitle("Password Reset Failed");
            return new HtmlView(builder);
        }

        private HtmlString getErrorMessage(BindException errors)
        {
            HtmlStringBuilder builder = HtmlStringBuilder.of();

            for(ObjectError e : errors.getAllErrors())
            {
                if (e instanceof LabKeyError le)
                    builder.append(le.renderToHTML(getViewContext()));
                else
                    builder.append(e.getDefaultMessage()).append('\n');
            }

            return builder.getHtmlString();
        }

        private HtmlString getMailHelpText(String emailAddress)
        {
            ActionURL mailHref = new ActionURL(ShowResetEmailAction.class, getContainer()).addParameter("email", emailAddress);

            HtmlStringBuilder builder = HtmlStringBuilder.of()
                .unsafeAppend("<p>")
                .append("You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");
            if (mailHref != null)
            {
                builder.append(" Alternatively, you can copy the ")
                    .append(new Link.LinkBuilder("contents of the message").href(mailHref).target("_blank").clearClasses())
                    .append(" into an email client and send it to the user manually.");
            }
            builder.unsafeAppend("</p>\n<p>")
                .append("For help on fixing your mail server settings, please consult the SMTP section of the ")
                .append(new HelpTopic("labkeyxml").getSimpleLinkHtml("LabKey Server documentation on modifying your configuration file"))
                .unsafeAppend(".</p>");

            return builder.getHtmlString();
        }
    }

    /**
     * Delete existing password
     */
    @RequiresPermission(UpdateUserPermission.class)
    public class AdminDeletePasswordAction extends AdminPasswordAction
    {
        @Override
        String getTitle()
        {
            return "Delete Password";
        }

        @Override
        String getVerb()
        {
            return "delete";
        }

        @Override
        HtmlString getConfirmationMessage(boolean loginExists, String emailAddress)
        {
            if (!loginExists)
                throw new NotFoundException(emailAddress + " does not seem to have a password");

            List<String> authMethods = new LinkedList<>();

            Collection<LoginFormAuthenticationConfiguration> formConfigs = AuthenticationManager.getActiveConfigurations(LoginFormAuthenticationConfiguration.class);
            String ldapDetails = formConfigs.stream()
                .filter(ac->null != ac.getDomain())
                .filter(ac->!AuthenticationManager.ALL_DOMAINS.equals(ac.getDomain()))
                .filter(ac->StringUtils.endsWithIgnoreCase(emailAddress, "@" + ac.getDomain()))
                .map(AuthenticationConfiguration::getDescription)
                .collect(Collectors.joining(", "));
            if (!ldapDetails.isBlank())
                authMethods.add("LDAP (" + ldapDetails + ")");

            Collection<SSOAuthenticationConfiguration> ssoConfigs = AuthenticationManager.getActiveConfigurations(SSOAuthenticationConfiguration.class);
            if (!ssoConfigs.isEmpty())
            {
                authMethods.add("SSO (" +
                    ssoConfigs.stream()
                        .map(AuthenticationConfiguration::getDescription)
                        .collect(Collectors.joining(", ")) +
                    ")"
                );
            }

            String guidance;

            if (authMethods.isEmpty())
                guidance = "have no way to login!";
            else
                guidance = "be able to login via " + String.join(" or ", authMethods) + " only.";

            return HtmlString.of("Are you sure you want to delete the current password for " + emailAddress + "? Once deleted, this user will " + guidance);
        }

        @Override
        public boolean handlePost(EmailForm form, BindException errors) throws Exception
        {
            try
            {
                ValidEmail email = new ValidEmail(form.getEmail());
                SecurityManager.adminDeletePassword(email, getUser());
            }
            catch (InvalidEmailException e)
            {
                //Should be caught in validation
                errors.addError(new LabKeyError(new Exception("Invalid email address." + e.getMessage(), e)));
            }

            return !errors.hasErrors();
        }
    }

    public static class GroupDiagramViewFactory implements SecurityManager.ViewFactory
    {
        @Override
        public HttpView createView(ViewContext context)
        {
            JspView view = new JspView("/org/labkey/core/security/groupDiagram.jsp");
            view.setTitle("Group Diagram");

            return view;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GroupDiagramAction extends ReadOnlyApiAction<GroupDiagramForm>
    {
        @Override
        public ApiResponse execute(GroupDiagramForm form, BindException errors) throws Exception
        {
            List<Group> groups = SecurityManager.getGroups(getContainer().getProject(), false);
            String html;

            if (groups.isEmpty())
            {
                html = "This project has no security groups defined";
            }
            else
            {
                String graph = GroupManager.getGroupGraphDot(groups, getUser(), form.getHideUnconnected());
                File dir = FileUtil.getTempDirectory();
                File svgFile = null;

                try
                {
                    svgFile = FileUtil.createTempFile("groups", ".svg", dir);
                    svgFile.deleteOnExit();
                    DotRunner runner = new DotRunner(dir, graph);
                    runner.addSvgOutput(svgFile);
                    runner.execute();
                    String svg = PageFlowUtil.getFileContentsAsString(svgFile);

                    int idx = svg.indexOf("<svg");
                    html = -1 != idx ? svg.substring(idx) : "Graphviz failed to generate this group diagram";
                }
                catch (IOException ioe)
                {
                    if (ioe.getMessage().startsWith("Cannot run program \"dot\""))
                    {
                        html = "This feature requires graphviz to be installed; ";

                        if (getUser().hasRootPermission(AdminOperationsPermission.class))
                            html += "see " + new HelpTopic("thirdPartyCode").getSimpleLinkHtml("the LabKey installation instructions") + " for more information.";
                        else
                            html += "contact a server administrator about this problem.";
                    }
                    else
                    {
                        throw ioe;
                    }
                }
                finally
                {
                    if (null != svgFile)
                        svgFile.delete();
                }
            }

            return new ApiSimpleResponse("html", html);
        }
    }

    private static class GroupDiagramForm
    {
        private boolean _hideUnconnected = false;

        @SuppressWarnings("unused")
        public void setHideUnconnected(boolean hideUnconnected)
        {
            _hideUnconnected = hideUnconnected;
        }

        public boolean getHideUnconnected()
        {
            return _hideUnconnected;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FolderAccessAction extends SimpleViewAction<FolderAccessForm>
    {
        @Override
        public ModelAndView getView(FolderAccessForm form, BindException errors)
        {
            VBox view = new VBox();
            form.setShowCaption("show all users");
            form.setHideCaption("hide unassigned users");
            view.addView(new JspView<>("/org/labkey/core/user/toggleShowAll.jsp", form));

            List<AccessDetailRow> rows = new ArrayList<>();
            Collection<User> activeUsers = UserManager.getActiveUsers();
            buildAccessDetailList(activeUsers, rows, form.showAll());
            Collections.sort(rows); // the sort is done using the user display name
            UserController.AccessDetail bean = new UserController.AccessDetail(rows, true, true);
            JspView<UserController.AccessDetail> accessView = new JspView<>("/org/labkey/core/user/securityAccess.jsp", bean, errors);
            accessView.setTitle("Folder Role Assignments");
            accessView.setFrame(WebPartView.FrameType.PORTAL);
            view.addView(accessView);

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                settings.setQueryName(GroupManager.GROUP_AUDIT_EVENT);
                QueryView auditView = schema.createView(getViewContext(), settings, errors);
                auditView.setTitle("Access Modification History For This Folder");

                view.addView(auditView);
            }
            return view;
        }

        private void buildAccessDetailList(Collection<User> activeUsers, List<AccessDetailRow> rows, boolean showAll)
        {
            if (activeUsers.isEmpty())
                return;

            // add an AccessDetailRow for each user that has perm within the project
            Container project = getContainer().getProject();
            List<Group> groups = SecurityManager.getGroups(project, true);
            for (User user : activeUsers)
            {
                user = UserManager.getUser(user.getUserId()); // the cache from UserManager.getUsers might not have the updated groups list
                Map<String, List<Group>> userAccessGroups = new TreeMap<>();
                Set<Role> effectiveRoles = SecurityManager.getEffectiveRoles(getContainer(), user)
                    .filter(role -> !(role instanceof NoPermissionsRole))
                    .collect(Collectors.toSet());

                for (Role role : effectiveRoles)
                {
                    userAccessGroups.put(role.getName(), new ArrayList<>());
                }

                if (!effectiveRoles.isEmpty())
                {
                    fillUserAccessGroups(user, groups, getContainer().getPolicy(), effectiveRoles, userAccessGroups);
                }

                if (showAll || !userAccessGroups.isEmpty())
                    rows.add(new AccessDetailRow(getUser(), getContainer(), user, userAccessGroups, 0));
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(PermissionsAction.class, getContainer()));
            root.addChild("Folder Permissions");
            root.addChild("Folder Access Details");
        }
    }

    public static void fillUserAccessGroups(UserPrincipal user, List<Group> groups, SecurityPolicy policy, Collection<Role> effectiveRoles, Map<String, List<Group>> userAccessGroups)
    {
        for (Group group : groups)
        {
            if (user.isInGroup(group.getUserId()))
            {
                Collection<Role> groupRoles = policy.getAssignedRoles(group);
                for (Role role : effectiveRoles)
                {
                    if (groupRoles.contains(role))
                        userAccessGroups.get(role.getName()).add(group);
                }
            }
        }
    }

    public static class FolderAccessForm
    {
        private boolean _showAll;
        private String _showCaption = null;
        private String _hideCaption = null;

        public boolean showAll()
        {
            return _showAll;
        }

        public void setShowAll(boolean showAll)
        {
            _showAll = showAll;
        }

        public String getShowCaption()
        {
            return _showCaption;
        }

        public void setShowCaption(String showCaption)
        {
            _showCaption = showCaption;
        }

        public String getHideCaption()
        {
            return _hideCaption;
        }

        public void setHideCaption(String hideCaption)
        {
            _hideCaption = hideCaption;
        }
    }

    @RequiresLogin
    public static class ExternalToolsViewAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            VBox view = new VBox();
            int viewCount = 0;
            for (ExternalToolsViewProvider externalAccessViewProvider : ExternalToolsViewService.get().getExternalAccessViewProviders())
            {
                for (ModelAndView providerView : externalAccessViewProvider.getViews(getUser()))
                {
                    view.addView(providerView);
                    ++viewCount;
                }
            }

            //using view.isEmpty() || !view.hasView() wasn't reliable, so resorting to this count approach
            if (viewCount == 0)
            {
                view.addView(new JspView<>("/org/labkey/core/security/nothingEnabled.jsp", form));
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            view.addView(new JspView<>("/org/labkey/core/security/externalToolsBase.jsp", form));

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("externalTools");
            root.addChild("External Tool Access");
        }
    }

    public static class CreateApiKeyForm
    {
        private String _type;
        private String _description;

        public String getType()
        {
            return _type;
        }

        @SuppressWarnings("unused")
        public void setType(String type)
        {
            _type = type;
        }

        public String getDescription()
        {
            return _description;
        }

        @SuppressWarnings("unused")
        public void setDescription(String description)
        {
            _description = description;
        }
    }

    @RequiresLogin
    public static class CreateApiKeyAction extends MutatingApiAction<CreateApiKeyForm>
    {
        @Override
        public Object execute(CreateApiKeyForm form, BindException errors)
        {
            final String apiKey;

            switch (form.getType())
            {
                case "apikey":
                    if (!AppProps.getInstance().isAllowApiKeys())
                        throw new NotFoundException("Creation of API keys is disabled");

                    apiKey = ApiKeyManager.get().createKey(getUser(), form.getDescription());
                    break;
                case "session":
                    if (!AppProps.getInstance().isAllowSessionKeys())
                        throw new NotFoundException("Creation of session keys is disabled");

                    ViewContext ctx = getViewContext();
                    apiKey = SessionApiKeyManager.get().createKey(ctx.getRequest(), ctx.getSession());
                    break;
                default:
                    throw new NotFoundException("Invalid type specified");
            }

            ApiSimpleResponse response = new ApiSimpleResponse();

            if (null != apiKey)
                response.put("apikey", apiKey);

            return response;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            SecurityController controller = new SecurityController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                controller.new CompleteUserReadAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new PermissionsAction(),
                controller.new StandardDeleteGroupAction(),
                controller.new GroupAction(),
                controller.new CompleteMemberAction(),
                controller.new CompleteUserAction(),
                controller.new GroupExportAction(),
                controller.new GroupPermissionAction(),
                controller.new UpdatePermissionsAction(),
                controller.new ShowRegistrationEmailAction(),
                controller.new GroupDiagramAction(),
                controller.new FolderAccessAction()
            );

            // @RequiresPermission(UserManagementPermission.class)
            assertForUserPermissions(user,
                controller.new AddUsersAction(),
                controller.new ShowResetEmailAction(),
                controller.new AdminResetPasswordAction()
            );
        }

        @Test
        public void validateAdministratorRolePermissionAssignment()
        {
            Set<Role> siteAdminRoleSet = RoleManager.roleSet(SiteAdminRole.class);
            Set<Role> appAdminRoleSet = RoleManager.roleSet(ApplicationAdminRole.class);
            Set<Role> otherAdminRoleSet = RoleManager.roleSet(ProjectAdminRole.class, FolderAdminRole.class);

            RoleManager.testPermissionsInAdminRoles(true, siteAdminRoleSet, AdminOperationsPermission.class);
            RoleManager.testPermissionsInAdminRoles(false, appAdminRoleSet, AdminOperationsPermission.class);
            RoleManager.testPermissionsInAdminRoles(false, otherAdminRoleSet, AdminOperationsPermission.class);

            RoleManager.testPermissionsInAdminRoles(true, siteAdminRoleSet, UserManagementPermission.class);
            RoleManager.testPermissionsInAdminRoles(true, appAdminRoleSet, UserManagementPermission.class);
            RoleManager.testPermissionsInAdminRoles(false, otherAdminRoleSet, UserManagementPermission.class);

            RoleManager.testPermissionsInAdminRoles(true,
                ReadPermission.class,
                ReadSomePermission.class,
                AssayReadPermission.class,
                DataClassReadPermission.class,
                MediaReadPermission.class,
                NotebookReadPermission.class,
                InsertPermission.class,
                UpdatePermission.class,
                DeletePermission.class,
                AdminPermission.class,
                EditSharedViewPermission.class,
                SeeUserDetailsPermission.class,
                SeeGroupDetailsPermission.class,
                CanSeeAuditLogPermission.class,
                FolderExportPermission.class,
                QCAnalystPermission.class
            );
        }
    }
}
