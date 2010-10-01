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
package org.labkey.core.security;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.*;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ContainerUser;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.user.UserController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;

import static org.labkey.api.util.PageFlowUtil.filter;

public class SecurityController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class,
            SecurityApiActions.GetGroupPermsAction.class,
            SecurityApiActions.GetUserPermsAction.class,
            SecurityApiActions.GetGroupsForCurrentUserAction.class,
            SecurityApiActions.EnsureLoginAction.class,
            SecurityApiActions.GetRolesAction.class,
            SecurityApiActions.GetSecurableResourcesAction.class,
            SecurityApiActions.GetPolicyAction.class,
            SecurityApiActions.SavePolicyAction.class,
            SecurityApiActions.DeletePolicyAction.class,
            SecurityApiActions.CreateGroupAction.class,
            SecurityApiActions.DeleteGroupAction.class,
            SecurityApiActions.AddGroupMemberAction.class,
            SecurityApiActions.RemoveGroupMemberAction.class,
            SecurityApiActions.CreateNewUserAction.class,
            SecurityApiActions.RenameGroupAction.class);

    public SecurityController()
    {
        setActionResolver(_actionResolver);
    }

    public static class SecurityUrlsImpl implements SecurityUrls
    {
        public ActionURL getManageGroupURL(Container container, String groupName)
        {
            ActionURL url = new ActionURL(GroupAction.class, container);
            return url.addParameter("group", groupName);
        }

        public ActionURL getGroupPermissionURL(Container container, int id)
        {
            ActionURL url = new ActionURL(GroupPermissionAction.class, container);
            return url.addParameter("id", id);
        }

        public ActionURL getProjectURL(Container container)
        {
            return new ActionURL(ProjectAction.class, container);
        }

        public ActionURL getContainerURL(Container container)
        {
            return new ActionURL(ProjectAction.class, container);
        }

        public String getCompleteUserURLPrefix(Container container)
        {
            ActionURL url = new ActionURL(CompleteUserAction.class, container);
            url.addParameter("prefix", "");
            return url.getLocalURIString();
        }

        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL getShowRegistrationEmailURL(Container container, ValidEmail email, String mailPrefix)
        {
            ActionURL url = new ActionURL(ShowRegistrationEmailAction.class, container);
            url.addParameter("email", email.getEmailAddress());
            url.addParameter("mailPrefix", mailPrefix);

            return url;
        }

        public ActionURL getUpdateMembersURL(Container container, String groupPath, String deleteEmail, boolean quickUI)
        {
            ActionURL url = new ActionURL(UpdateMembersAction.class, container);

            if (quickUI)
                url.addParameter("quickUI", "1");

            url.addParameter("group", groupPath);
            url.addParameter("delete", deleteEmail);

            return url;
        }
    }

    private static void ensureGroupInContainer(Group group, Container c)
            throws ServletException
    {
        if (group.getContainer() == null)
        {
            if (!c.isRoot())
                HttpView.throwUnauthorized();
        }
        else
        {
            if (!c.getId().equals(group.getContainer()))
                HttpView.throwUnauthorized();
        }
    }
    

    private static void ensureGroupInContainer(String group, Container c)
        throws ServletException
    {
        if (group.startsWith("/"))
            group = group.substring(1);
        if (-1 == group.indexOf("/"))
        {
            if (!c.isRoot())
                HttpView.throwUnauthorized();
        }
        else
        {
            String groupContainer = group.substring(0, group.lastIndexOf("/"));
            if (c.isRoot())
                HttpView.throwUnauthorized();
            String projectContainer = c.getPath();
            if (projectContainer.startsWith("/"))
                projectContainer = projectContainer.substring(1);
            if (!projectContainer.equals(groupContainer))
                HttpView.throwUnauthorized();
        }
    }

    @RequiresNoPermission
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (null == getContainer() || getContainer().isRoot())
            {
                HttpView.throwRedirect(new ActionURL(AddUsersAction.class, ContainerManager.getRoot()));
            }
            else
            {
                HttpView.throwRedirect(new ActionURL(ProjectAction.class, getContainer()));
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    private abstract class ProjectActionExtStyle extends SimpleViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, BindException errors) throws Exception
        {
            String root = getContainer().getId();
            String resource = root;
            ActionURL doneURL = form.isWizard() ? getContainer().getFolderType().getStartURL(getContainer(), getUser()) : null;

            Container container = getViewContext().getContainer();

            FolderPermissions permsView = new FolderPermissions(root, resource, doneURL);

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setTitle("Permissions for " + container.getPath());

            return permsView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Container c = getContainer();
            String title;
            if (c.isRoot())
                title = "Site Permissions";
            else if (c.isProject())
                title = "Project Permissions";
            else
                title = "Folder Permissions";
            root.addChild(title + " for " + c.getPath());
            return root;
        }
    }


    public class FolderPermissions extends JspView<FolderPermissions>
    {
        public final String resource;
        public final ActionURL doneURL;
        
        FolderPermissions(String root, String resource, ActionURL doneURL)
        {
            super(SecurityController.class, "FolderPermissions.jsp", null);
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
        public Group getGroupFor(Container c) throws ServletException
        {
            if (id == Integer.MIN_VALUE)
            {
                Integer gid = SecurityManager.getGroupId(group);
                if (gid == null)
                    return null;
                id = gid.intValue();
            }
            Group group = SecurityManager.getGroup(id);
            Container p = c == null ? null : c.getProject();
            if (null != p)
            {
                if (group.getContainer() != null && !p.getId().equals(group.getContainer()))
                    HttpView.throwUnauthorized();
            }
            return group;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class StandardDeleteGroupAction extends FormHandlerAction<GroupForm>
    {
        public void validateCommand(GroupForm form, Errors errors) {}

        public boolean handlePost(GroupForm form, BindException errors) throws Exception
        {
            Group group = form.getGroupFor(getContainer());
            ensureGroupInContainer(group,getContainer());
            if (group != null)
            {
                SecurityManager.deleteGroup(group);
                addGroupAuditEvent(getViewContext(), group, "The group: " + group.getPath() + " was deleted.");
            }
            return true;
        }

        public ActionURL getSuccessURL(GroupForm form)
        {
            return new ActionURL(ProjectAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return getGroupsView(getContainer(), null, errors, Collections.<String>emptyList());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Groups");
        }
    }

    public static class GroupsBean
    {
        ViewContext _context;
        Container _container;
        Group[] _groups;
        String _expandedGroupPath;
        List<String> _messages;

        public GroupsBean(ViewContext context, Group expandedGroupPath, List<String> messages)
        {
            Container c = context.getContainer();
            if (null == c || c.isRoot())
            {
                _groups = org.labkey.api.security.SecurityManager.getGroups(null, false);
                _container = ContainerManager.getRoot();
            }
            else
            {
                _groups = SecurityManager.getGroups(c.getProject(), false);
                _container = c;
            }
            _expandedGroupPath = expandedGroupPath == null ? null : expandedGroupPath.getPath();
            _messages = messages != null ? messages : Collections.<String>emptyList();
        }

        public Container getContainer()
        {
            return _container.isRoot() ? _container : _container.getProject();
        }

        public Group[] getGroups()
        {
            return _groups;
        }

        public boolean isExpandedGroup(String groupPath)
        {
            return _expandedGroupPath != null && _expandedGroupPath.equals(groupPath);
        }

        public List<String> getCompletionNames() throws SQLException
        {
            User[] users = UserManager.getActiveUsers();
            List<String> names = new ArrayList<String>();
            for (User user : users)
            {
                String shortName = user.getEmail();
                if (user.getFirstName() != null && user.getFirstName().length() > 0)
                {
                    names.add(user.getFirstName() + " " + user.getLastName() + " (" + user.getEmail() + ")");
                    names.add(user.getLastName() + ", " + user.getFirstName() + " (" + user.getEmail() + ")");
                    shortName += " (" + user.getFirstName() + " " + user.getLastName() + ")";
                }
                if (user.getDisplayNameOld(_context).compareToIgnoreCase(user.getEmail()) != 0)
                    names.add(user.getDisplayNameOld(_context) + " (" + user.getEmail() + ")");

                names.add(shortName);
            }
            return names;
        }

        public List<String> getMessages()
        {
            return _messages;
        }
    }

    private HttpView getGroupsView(Container container, Group expandedGroup, BindException errors, List<String> messages)
    {
        JspView<GroupsBean> groupsView = new JspView<GroupsBean>("/org/labkey/core/security/groups.jsp", new GroupsBean(getViewContext(), expandedGroup, messages), errors);
        if (null == container || container.isRoot())
            groupsView.setTitle("Site Groups");
        else
            groupsView.setTitle("Groups for project " + container.getProject().getName());
        return groupsView;
    }
    

    private ModelAndView renderContainerPermissions(Group expandedGroup, BindException errors, List<String> messages, boolean wizard) throws Exception
    {
        Container c = getContainer();
        Container project = c.getProject();

        // If we are working with the root container, project will be null.
        // This causes ContainersView() to fail, so handle specially.
        if (project == null)
            project = ContainerManager.getRoot();

        HBox body = new HBox();
        VBox projectViews = new VBox();
        //don't display folder tree if we are in root
        if (!project.isRoot())
        {
            projectViews.addView(new ContainersView(project));
        }

        // Display groups only if user has permissions in this project (or the root)
        if (project.hasPermission(getUser(), AdminPermission.class))
        {
            projectViews.addView(getGroupsView(c, expandedGroup, errors, messages));

            UserController.ImpersonateView impersonateView = new UserController.ImpersonateView(project, false);

            if (impersonateView.hasUsers())
                projectViews.addView(impersonateView);
        }

        ActionURL startURL = c.getFolderType().getStartURL(c, getUser());
        projectViews.addView(new HtmlView(PageFlowUtil.generateButton("Done", startURL)));
        if(c.isRoot())
            body.addView(projectViews);
        else
        {
            body.addView(projectViews, "60%");
            body.addView(new PermissionsDetailsView(c, "container"), "40%");
        }
        if (wizard)
        {
            VBox outer = new VBox();
            String message = "Use this page to ensure that only appropriate users have permission to view and edit the new " + (c.isProject() ? "Project" : "folder");
            outer.addView(new HtmlView(message));
            outer.addView(body);
            return outer;
        }

        return body;
    }


//    @RequiresPermissionClass(AdminPermission.class)
//    private abstract class ProjectActionOldSChool extends SimpleViewAction<PermissionsForm>
//    {
//        public ModelAndView getView(PermissionsForm form, BindException errors) throws Exception
//        {
//            return renderContainerPermissions(null, null, null, form.isWizard());
//        }
//
//        public NavTree appendNavTrail(NavTree root)
//        {
//            root.addChild("Permissions");
//            return root;
//        }
//    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ProjectAction extends ProjectActionExtStyle
    {
    }


    public static class PermissionsForm
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


    public static class GroupResponse extends ApiSimpleResponse
    {
        GroupResponse(Group group)
        {
            Map<String, Object> map = ObjectFactory.Registry.getFactory(Group.class).toMap(group, new HashMap<String, Object>());
            List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group.getUserId());
            map.put("members",members);
            put("success", true);
            put("group",map);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class NewGroupAction extends FormViewAction<NewGroupForm>
    {
        public ModelAndView getView(NewGroupForm form, boolean reshow, BindException errors) throws Exception
        {
            return renderContainerPermissions(null, errors, null, false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Permissions");
        }
        
        public boolean handlePost(NewGroupForm form, BindException errors) throws Exception
        {
            // UNDONE: use form validation
            String name = form.getName();
            String error;
            if (name == null || name.length() == 0)
            {
                error = "Group name cannot be empty.";
            }
            else
            {
                error  = UserManager.validGroupName(name, Group.typeProject);
            }

            if (null == error)
            {
                try
                {
                    Group group = SecurityManager.createGroup(getContainer().getProject(), name);
                    addGroupAuditEvent(getViewContext(), group, "The group: " + name + " was created.");
                }
                catch (IllegalArgumentException e)
                {
                    error = e.getMessage();
                }
            }
            if (error != null)
            {
                errors.addError(new LabkeyError(error));
                return false;
            }
            return true;
        }

        public void validateCommand(NewGroupForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(NewGroupForm newGroupForm)
        {
            return new ActionURL(ProjectAction.class, getContainer());
        }
    }

    private void addGroupAuditEvent(ContainerUser context, Group group, String message)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
        event.setCreatedBy(context.getUser());
        event.setIntKey2(group.getUserId());
        Container c = ContainerManager.getForId(group.getContainer());
        if (c != null && c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        else
            event.setProjectId(ContainerManager.getRoot().getId());
        event.setComment(message);
        event.setContainerId(context.getContainer().getId());

        AuditLogService.get().addEvent(event);
    }

    public static class NewGroupForm
    {
        private String _name;

        public void setName(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateMembersAction extends SimpleViewAction<UpdateMembersForm>
    {
        private Group _group;
        private boolean _showGroup;

        public ModelAndView getView(UpdateMembersForm form, BindException errors) throws Exception
        {
            // 1 - Global admins group cannot be empty
            // 2 - warn if you are deleting yourself from global or project admins
            // 3 - if user confirms delete, post to action again, with list of users to delete and confirmation flag.

            Container container = getContainer();

            if (!container.isRoot() && !container.isProject())
                container = container.getProject();

            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(ProjectAction.class, container));

            List<String> messages = new ArrayList<String>();

            //check for new users to add.
            String[] allNames = form.getNames() == null ? new String[0] : form.getNames().split("\n");

            List<String> emails = new ArrayList<String>(Arrays.asList(allNames));

            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> addEmails = SecurityManager.normalizeEmails(emails, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not add user " + filter(e) + ": Invalid email address");
            }

            String[] removeNames = form.getDelete();
            invalidEmails.clear();
            List<ValidEmail> removeEmails = SecurityManager.normalizeEmails(removeNames, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not remove user " + filter(e) + ": Invalid email address");
            }

            if (_group != null)
            {
                //check for users to delete
                if (removeNames != null)
                {
                    //get list of group members. need this to determine how many there are.
                    String[] groupMemberNames = SecurityManager.getGroupMemberNames(_group.getUserId());

                    //if this is the site admins group and user is attempting to remove all site admins, display error.
                    if (_group.getUserId() == Group.groupAdministrators && removeNames.length == groupMemberNames.length)
                    {
                        errors.addError(new LabkeyError("The Site Administrators group must always contain at least one member. You cannot remove all members of this group."));
                    }
                    //if this is site or project admins group and user is removing themselves, display warning.
                    else if (_group.getName().compareToIgnoreCase("Administrators") == 0
                            && Arrays.asList(removeNames).contains(getUser().getEmail())
                            && !form.isConfirmed())
                    {
                        //display warning form, including users to delete and add
                        HttpView<UpdateMembersBean> v = new JspView<UpdateMembersBean>("/org/labkey/core/security/deleteUser.jsp", new UpdateMembersBean());

                        UpdateMembersBean bean = v.getModelBean();
                        bean.addnames = addEmails;
                        bean.removenames = removeNames;
                        bean.groupName = _group.getName();
                        bean.mailPrefix = form.getMailPrefix();

                        getPageConfig().setTemplate(PageConfig.Template.Dialog);
                        return v;
                    }
                    else
                    {
                        SecurityManager.deleteMembers(_group, removeEmails);
                    }
                }

                if (addEmails.size() > 0)
                {
                    List<User> users = new ArrayList<User>(addEmails.size());

                    // add new users
                    for (ValidEmail email : addEmails)
                    {
                        String addMessage = SecurityManager.addUser(getViewContext(), email, form.getSendEmail(), form.getMailPrefix(), null);
                        if (addMessage != null)
                            messages.add(addMessage);

                        // get the user and ensure that the user is still active
                        User user = UserManager.getUser(email);

                        // Null check since user creation may have failed, #8066
                        if (null != user)
                        {
                            if (!user.isActive())
                                errors.reject(ERROR_MSG, "You may not add the user '" + PageFlowUtil.filter(email)
                                    + "' to this group because that user account is currently deactivated." +
                                    " To re-activate this account, contact your system administrator.");
                            else
                                users.add(user);
                        }
                    }

                    try
                    {
                        SecurityManager.addMembers(_group, users);
                    }
                    catch (SQLException e)
                    {
                        errors.addError(new LabkeyError("A failure occurred adding users to the group: " + e.getMessage()));
                    }
                }
            }

            if (form.isQuickUI())
                return renderContainerPermissions(_group, errors, messages, false);
            else
            {
                _showGroup = true;
                return renderGroup(_group, errors, messages);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showGroup)
            {
                return addGroupNavTrail(root, _group);
            }
            else
            {
                root.addChild("Permissions");
                return root;
            }
        }
    }

    public static class UpdateMembersBean
    {
        public List<ValidEmail> addnames;
        public String[] removenames;
        public String groupName;
        public String mailPrefix;
    }

    public static class UpdateMembersForm extends GroupForm
    {
        private String names;
        private String[] delete;
        private boolean sendEmail;
        private boolean confirmed;
        private String mailPrefix;
        // flag to indicate whether this modification was made via the 'quick ui'
        // if so, we'll redirect to a different page when we're done.
        private boolean quickUI;

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public void setNames(String names)
        {
            this.names = names;
        }

        public String getNames()
        {
            return this.names;
        }

        public String[] getDelete()
        {
            return delete;
        }

        public void setDelete(String[] delete)
        {
            this.delete = delete;
        }

        public boolean getSendEmail()
        {
            return sendEmail;
        }

        public void setSendEmail(boolean sendEmail)
        {
            this.sendEmail = sendEmail;
        }

        public String getMailPrefix()
        {
            return mailPrefix;
        }

        public void setMailPrefix(String messagePrefix)
        {
            this.mailPrefix = messagePrefix;
        }

        public boolean isQuickUI()
        {
            return quickUI;
        }

        public void setQuickUI(boolean quickUI)
        {
            this.quickUI = quickUI;
        }
    }


    private NavTree addGroupNavTrail(NavTree root, Group group)
    {
        root.addChild("Permissions", new ActionURL(ProjectAction.class, getContainer()));
        root.addChild("Manage Group");
        root.addChild(group.getName() + " Group");
        return root;
    }

    private ModelAndView renderGroup(Group group, BindException errors, List<String> messages) throws Exception
    {
        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group.getUserId());

        if (null == members)
            HttpView.throwNotFound();

        VBox view = new VBox(new GroupView(group, members, messages, group.isSystemGroup(), errors));
        if (getUser().isAdministrator())
        {
            AuditLogQueryView log = GroupAuditViewFactory.getInstance().createGroupView(getViewContext(), group.getUserId());
            log.setFrame(WebPartView.FrameType.TITLE);
            view.addView(log);
        }
        return view;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupAction extends SimpleViewAction<GroupForm>
    {
        private Group _group;

        public ModelAndView getView(GroupForm form, BindException errors) throws Exception
        {
            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(ProjectAction.class, getContainer()));
            return renderGroup(_group, errors, Collections.<String>emptyList());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return addGroupNavTrail(root, _group);
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CompleteUserAction extends SimpleViewAction<CompleteUserForm>
    {
        public ModelAndView getView(CompleteUserForm form, BindException errors) throws Exception
        {
            List<AjaxCompletion> completions = UserManager.getAjaxCompletions(form.getPrefix(), HttpView.currentContext());
            PageFlowUtil.sendAjaxCompletions(getViewContext().getResponse(), completions);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupExportAction extends ExportAction<GroupForm>
    {
        public void export(GroupForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String group = form.getGroup();
            if (group.startsWith("/"))
                group = group.substring(1);
            // validate that group is in the current project!
            Container c = getContainer();
            ensureGroupInContainer(group, c);
            List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group);

            DataRegion rgn = new DataRegion();
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(UserController.getUserColumnNames(getUser(), c));
            rgn.setColumns(columns);
            RenderContext ctx = new RenderContext(getViewContext());
            List<Integer> userIds = new ArrayList<Integer>();
            for (Pair<Integer, String> member : members)
                userIds.add(member.getKey());
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause("UserId", userIds);
            ctx.setBaseFilter(filter);
            ExcelWriter ew = new ExcelWriter(rgn.getResultSet(ctx), rgn.getDisplayColumns());
            ew.setAutoSize(true);
            ew.setSheetName(group + " Members");
            ew.setFooter(group + " Members");
            ew.write(response);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class GroupPermissionAction extends SimpleViewAction<GroupForm>
    {
        private Group _requestedGroup;

        public ModelAndView getView(GroupForm form, BindException errors) throws Exception
        {
            List<UserController.AccessDetailRow> rows = new ArrayList<UserController.AccessDetailRow>();
            _requestedGroup = form.getGroupFor(getContainer());
            if (_requestedGroup != null)
            {
                buildAccessDetailList(Collections.singletonList(getContainer().getProject()), rows, _requestedGroup, 0);
            }
            else
                return HttpView.throwNotFound("Group not found");

            UserController.AccessDetail bean = new UserController.AccessDetail(rows, false);
            return new JspView<UserController.AccessDetail>("/org/labkey/core/user/userAccess.jsp", bean, errors);
        }

        private void buildAccessDetailList(List<Container> children, List<UserController.AccessDetailRow> rows, Group requestedGroup, int depth)
        {
            if (children == null || children.isEmpty())
                return;
            for (Container child : children)
            {
                if (child != null)
                {
                    SecurityPolicy policy = child.getPolicy();
                    String sep = "";
                    StringBuilder access = new StringBuilder();
                    Collection<Role> roles = policy.getEffectiveRoles(requestedGroup);
                    for(Role role : roles)
                    {
                        access.append(sep);
                        access.append(role.getName());
                        sep = ", ";
                    }

                    rows.add(new UserController.AccessDetailRow(child, access.toString(), null, depth));
                    buildAccessDetailList(child.getChildren(), rows, requestedGroup, depth + 1);
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(ProjectAction.class, getContainer()));
            root.addChild("Group Permissions");
            root.addChild(_requestedGroup == null || _requestedGroup.isUsers() ? "Access Details: Site Users" : "Access Details: " + _requestedGroup.getName());
            return root;
        }
    }

    protected enum AuditChangeType
    {
        explicit,
        fromInherited,
        toInherited,
    }
   
    @RequiresPermissionClass(AdminPermission.class)
    public class UpdatePermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors) {}

        private void addAuditEvent(User user, String comment, int groupId)
        {
            if (user != null)
                SecurityManager.addAuditEvent(getViewContext().getContainer(), user, comment, groupId);
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

        public boolean handlePost(Object o, BindException errors) throws Exception
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

                //get any existing policy specifically for this container (may return null)
                SecurityPolicy oldPolicy = SecurityManager.getPolicy(c, false);

                //delete if we found one
                if(null != oldPolicy)
                    SecurityManager.deletePolicy(c);

                //now get the nearest policy for this container so we can write to the
                //audit log how the permissions have changed
                SecurityPolicy newPolicy = SecurityManager.getPolicy(c);

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
                SecurityPolicy oldPolicy = SecurityManager.getPolicy(c);

                //if resource id is not the same as the current container
                //set chagne type to indicate we're moving from inhereted
                if(!oldPolicy.getResourceId().equals(c.getResourceId()))
                    changeType = AuditChangeType.fromInherited;

                HttpServletRequest request = getViewContext().getRequest();
                Enumeration e = request.getParameterNames();
                while (e.hasMoreElements())
                {
                    try
                    {
                        String key = (String) e.nextElement();
                        if (!key.startsWith("group."))
                            continue;
                        int groupid = (int) Long.parseLong(key.substring(6), 16);
                        Group group = SecurityManager.getGroup(groupid);
                        if(null == group)
                            continue; //invalid group id

                        String roleName = request.getParameter(key);
                        Role role = RoleManager.getRole(roleName);
                        if(null == role)
                            continue; //invalid role name

                        newPolicy.addRoleAssignment(group, role);
                        addAuditEvent(group, newPolicy, oldPolicy, changeType);
                    }
                    catch (NumberFormatException x)
                    {
                        // continue;
                    }
                }

                SecurityManager.savePolicy(newPolicy);
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL("Security", getViewContext().getRequest().getParameter("view"), getContainer());
        }
    }

    public static class AddUsersForm extends ReturnUrlForm
    {
        private boolean sendMail;
        private String newUsers;
        private String _cloneUser;
        private boolean _skipProfile;

        public void setNewUsers(String newUsers)
        {
            this.newUsers = newUsers;
        }

        public String getNewUsers()
        {
            return this.newUsers;
        }

        public void setSendMail(boolean sendMail)
        {
            this.sendMail = sendMail;
        }

        public boolean getSendMail()
        {
            return this.sendMail;
        }

        public void setCloneUser(String cloneUser){_cloneUser = cloneUser;}
        public String getCloneUser(){return _cloneUser;}

        public boolean isSkipProfile()
        {
            return _skipProfile;
        }

        public void setSkipProfile(boolean skipProfile)
        {
            _skipProfile = skipProfile;
        }
    }


    @ActionNames("showAddUsers, addUsers") @RequiresSiteAdmin
    public class AddUsersAction extends FormViewAction<AddUsersForm>
    {
        public ModelAndView getView(AddUsersForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<Object>("/org/labkey/core/security/addUsers.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Add Users");
        }

        public void validateCommand(AddUsersForm form, Errors errors) {}

        public boolean handlePost(AddUsersForm form, BindException errors) throws Exception
        {
            String[] rawEmails = form.getNewUsers() == null ? null : form.getNewUsers().split("\n");
            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            User userToClone = null;

            final String cloneUser = form.getCloneUser();
            if (cloneUser != null && cloneUser.length() > 0)
            {
                try {
                    final ValidEmail emailToClone = new ValidEmail(cloneUser);
                    userToClone = UserManager.getUser(emailToClone);
                    if (userToClone == null)
                        errors.addError(new FormattedError("Failed to clone user permissions " + emailToClone + ": User email does not exist in the system"));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.addError(new FormattedError("Failed to clone user permissions " + cloneUser.trim() + ": Invalid email address"));
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
                    errors.addError(new FormattedError("Failed to create user " + PageFlowUtil.filter(rawEmail.trim()) + ": Invalid email address"));
            }

            List<Pair<String, String>> extraParams = new ArrayList<Pair<String, String>>(2);
            if (form.isSkipProfile())
                extraParams.add(new Pair<String, String>("skipProfile", "1"));

            URLHelper returnURL = null;

            if (null != form.getReturnUrl())
            {
                extraParams.add(new Pair<String, String>(ReturnUrlForm.Params.returnUrl.toString(), form.getReturnUrl().getSource()));
                returnURL = form.getReturnURLHelper();
            }

            for (ValidEmail email : emails)
            {
                String result = SecurityManager.addUser(getViewContext(), email, form.getSendMail(), null, extraParams.<Pair<String, String>>toArray(new Pair[extraParams.size()]));

                if (result == null)
                {
                    User user = UserManager.getUser(email);
                    ActionURL url = PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), user.getUserId(), returnURL);
                    result = email + " was already a registered system user.  Click <a href=\"" + url.getEncodedLocalURIString() + "\">here</a> to see this user's profile and history.";
                }
                else if (userToClone != null)
                {
                    clonePermissions(userToClone, email);
                }
                errors.addError(new FormattedError(result));
            }

            return false;
        }

        private void clonePermissions(User clone, ValidEmail userEmail) throws ServletException
        {
            // clone this user's permissions
            final User user = UserManager.getUser(userEmail);
            if (clone != null && user != null)
            {
                for (int groupId : clone.getGroups())
                {
                    if (!user.isInGroup(groupId))
                    {
                        final Group group = SecurityManager.getGroup(groupId);
                        if (group != null)
                            SecurityManager.addMember(group, user);
                    }
                }
            }
        }

        public ActionURL getSuccessURL(AddUsersForm addUsersForm)
        {
            throw new UnsupportedOperationException();
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

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getMailPrefix()
        {
            return _mailPrefix;
        }

        public void setMailPrefix(String mailPrefix)
        {
            _mailPrefix = mailPrefix;
        }
    }


    private abstract class AbstractEmailAction extends SimpleViewAction<EmailForm>
    {
        protected abstract SecurityMessage createMessage(EmailForm form) throws Exception;

        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            Writer out = getViewContext().getResponse().getWriter();
            String rawEmail = form.getEmail();
            ValidEmail email = new ValidEmail(rawEmail);

            SecurityMessage message = createMessage(form);
            if (SecurityManager.isVerified(email))
                out.write("Can't display " + message.getType().toLowerCase() + "; " + email + " has already chosen a password.");
            else
            {
                String verification = SecurityManager.getVerification(email);
                ActionURL verificationURL = SecurityManager.createVerificationURL(getContainer(), email, verification, null);
                SecurityManager.renderEmail(getContainer(), getUser(), message, email.getEmailAddress(), verificationURL, out);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresSiteAdmin
    public class ShowRegistrationEmailAction extends AbstractEmailAction
    {
        // TODO: Allow project admins to view verification emails for users they've added?
        protected SecurityMessage createMessage(EmailForm form) throws Exception
        {
            return SecurityManager.getRegistrationMessage(form.getMailPrefix(), false);
        }
    }


    @RequiresSiteAdmin
    public class ShowResetEmailAction extends AbstractEmailAction
    {
        protected SecurityMessage createMessage(EmailForm form) throws Exception
        {
            return SecurityManager.getResetMessage(false);
        }
    }


    @RequiresSiteAdmin
    public class AdminResetPasswordAction extends SimpleViewAction<EmailForm>
    {
        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            User user = getUser();
            StringBuilder sbReset = new StringBuilder();

            String rawEmail = form.getEmail();

            sbReset.append("<p>").append(rawEmail);

            ValidEmail email = new ValidEmail(rawEmail);

            if (SecurityManager.isLdapEmail(email))
            {
                sbReset.append(" failed: can't reset the password for an LDAP user.");
            }
            else
            {
                // We allow admins to create passwords (i.e., entries in the logins table) if they don't already exist.
                // This addresses an SSO scenario.  See #10374.
                boolean loginExists = SecurityManager.loginExists(email);
                String pastVerb = loginExists ? "reset" : "created";
                String infinitiveVerb = loginExists ? "reset" : "create";

                try
                {
                    String verification;

                    if (loginExists)
                    {
                        // Create a placeholder password that's impossible to guess and a separate email
                        // verification key that gets emailed.
                        verification = SecurityManager.createTempPassword();
                        SecurityManager.setPassword(email, SecurityManager.createTempPassword());
                        SecurityManager.setVerification(email, verification);
                    }
                    else
                    {
                        verification = SecurityManager.createLogin(email);
                    }

                    sbReset.append(": password ").append(pastVerb).append(".</p><p>");

                    ActionURL actionURL = new ActionURL(ShowResetEmailAction.class, getContainer()).addParameter("email", email.toString());
                    String url = actionURL.getLocalURIString();
                    String href = "<a href=" + url + " target=\"_blank\">here</a>";

                    try
                    {
                        Container c = getContainer();
                        ActionURL verificationURL = SecurityManager.createVerificationURL(c, email, verification, null);
                        SecurityManager.sendEmail(c, user, SecurityManager.getResetMessage(false), email.getEmailAddress(), verificationURL);

                        if (!user.getEmail().equals(email.getEmailAddress()))
                        {
                            SecurityMessage msg = SecurityManager.getResetMessage(true);
                            msg.setTo(email.getEmailAddress());
                            SecurityManager.sendEmail(c, user, msg, user.getEmail(), verificationURL);
                        }

                        sbReset.append("Email sent. ");
                        sbReset.append("Click ").append(href).append(" to see the email.");
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password.");
                    }
                    catch (MessagingException e)
                    {
                        sbReset.append("Failed to send email due to: <pre>").append(e.getMessage()).append("</pre>");
                        appendMailHelpText(sbReset, url);
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password, but sending the email failed.");
                    }
                }
                catch (SecurityManager.UserManagementException e)
                {
                    sbReset.append(": failed to reset password due to: ").append(e.getMessage());
                    UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " attempted to " + infinitiveVerb + " the password, but the " + infinitiveVerb + " failed: " + e.getMessage());
                }
            }

            sbReset.append("</p>");
            sbReset.append(PageFlowUtil.generateButton("Done", form.getReturnURLHelper()));
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new HtmlView(sbReset.toString());
        }

        private void appendMailHelpText(StringBuilder sb, String mailHref)
        {
            sb.append("<p>You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");
            if (mailHref != null)
            {
                sb.append(" Alternatively, you can copy the <a href=\"");
                sb.append(mailHref);
                sb.append("\" target=\"_blank\">contents of the message</a> into an email client and send it to the user manually.");
            }
            sb.append("</p>");
            sb.append("<p>For help on fixing your mail server settings, please consult the SMTP section of the <a href=\"");
            sb.append((new HelpTopic("cpasxml")).getHelpTopicLink());
            sb.append("\" target=\"_blank\">LabKey Server documentation on modifying your configuration file</a>.</p>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestCase extends Assert
    {
        private Container c;

        @Test
        public void testAnnotations() throws Exception
        {
            //clean up users in case this failed part way through
            String[] cleanupUsers = {"guest@scjutc.com", "user@scjutc.com", "admin@scjutc.com"};
            for (String cleanupUser : cleanupUsers)
            {
                User oldUser = UserManager.getUser(new ValidEmail(cleanupUser));
                if (null != oldUser)
                    UserManager.deleteUser(oldUser.getUserId());
            }

            Container junit = ContainerManager.ensureContainer("/Shared/_junit");
            c = ContainerManager.createContainer(junit, "SecurityController-" + GUID.makeGUID());

            User site = TestContext.get().getUser();
            assertTrue(site.isAdministrator());

            User guest = SecurityManager.addUser(new ValidEmail("guest@scjutc.com")).getUser();
            Group guestsGroup = SecurityManager.getGroup(Group.groupGuests);
            SecurityManager.addMember(guestsGroup, guest);

            User user = SecurityManager.addUser(new ValidEmail("user@scjutc.com")).getUser();
            Group usersGroup = SecurityManager.getGroup(Group.groupUsers);
            SecurityManager.addMember(usersGroup, user);

            User admin = SecurityManager.addUser(new ValidEmail("admin@scjutc.com")).getUser();
            SecurityManager.addMember(usersGroup, admin);
            SecurityManager.addMember(guestsGroup, admin);

            MutableSecurityPolicy policy = new MutableSecurityPolicy(c, c.getPolicy());
            policy.addRoleAssignment(admin, RoleManager.getRole(SiteAdminRole.class));
            policy.addRoleAssignment(guest, RoleManager.getRole(ReaderRole.class));
            policy.addRoleAssignment(user, RoleManager.getRole(EditorRole.class));
            SecurityManager.savePolicy(policy);

            // @RequiresNoPermission
            assertPermission(guest, BeginAction.class);
            assertPermission(user, BeginAction.class);
            assertPermission(admin, BeginAction.class);
            assertPermission(site, BeginAction.class);

            // @RequiresPermissionClass(AdminPermission.class)
            assertNoPermission(guest, GroupsAction.class);
            assertNoPermission(user, GroupsAction.class);
            assertPermission(admin, GroupsAction.class);
            assertPermission(site, GroupsAction.class);

            // @RequiresSiteAdmin
            assertNoPermission(guest, AddUsersAction.class);
            assertNoPermission(user, AddUsersAction.class);
            assertNoPermission(admin, AddUsersAction.class);
            assertPermission(site, AddUsersAction.class);

            assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
            UserManager.deleteUser(admin.getUserId());
            UserManager.deleteUser(user.getUserId());
            UserManager.deleteUser(guest.getUserId());
        }


        private void assertPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, makeContext(u), null);
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission");
            }
        }

        private void assertNoPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, makeContext(u), null);
                fail("Should not have permission");
            }
            catch (UnauthorizedException x)
            {
                // expected
            }
        }

        private ViewContext makeContext(User u)
        {
            HttpServletRequest w = new HttpServletRequestWrapper(TestContext.get().getRequest()){
                @Override
                public String getParameter(String name)
                {
                    if (CSRFUtil.csrfName.equals(name))
                        return CSRFUtil.getExpectedToken(TestContext.get().getRequest());
                    return super.getParameter(name);
                }
            };
            ViewContext context = new ViewContext();
            context.setContainer(c);
            context.setUser(u);
            context.setRequest(w);
            return context;
        }


        private static class TestUser extends User
        {
            TestUser(String name, int id, Integer... groups)
            {
                super(name,id);
                _groups = new int[groups.length+1];
                for (int i=0 ; i<groups.length; i++)
                    _groups[i] = groups[i];
                _groups[groups.length] = id;
                Arrays.sort(_groups);
            }
        }
    }
}
