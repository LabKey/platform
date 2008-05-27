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
package org.labkey.core.security;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.common.util.Pair;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.user.UserController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;

public class SecurityController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class);

    public SecurityController()
    {
        setActionResolver(_actionResolver);
    }

    private void ensureGroupInContainer(String group, Container c)
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

    @RequiresPermission(ACL.PERM_NONE)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (null == getContainer() || getContainer().isRoot())
            {
                HttpView.throwRedirect(new ActionURL("Security", "showAddUsers", "/"));
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

    public static class GroupForm
    {
        private String group;

        public void setGroup(String name)
        {
            group = name;
        }

        public String getGroup()
        {
            return group;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteGroupAction extends FormHandlerAction<GroupForm>
    {
        public void validateCommand(GroupForm form, Errors errors) {}

        public boolean handlePost(GroupForm form, BindException errors) throws Exception
        {
            String groupPath = form.getGroup();
            ensureGroupInContainer(groupPath, getContainer());
            Integer gid = SecurityManager.getGroupId(groupPath);
            if (gid != null)
            {
                Group group = SecurityManager.getGroup(gid.intValue());
                addGroupAuditEvent(group, "The group: " + form.getGroup() + " was deleted.");
            }
            SecurityManager.deleteGroup(groupPath);
            return true;
        }

        public ActionURL getSuccessURL(GroupForm form)
        {
            if (getContainer().isRoot())
            {
                return new ActionURL(GroupsAction.class, getContainer());
            }
            else
            {
                return new ActionURL(ProjectAction.class, getContainer());
            }
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
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

    public class GroupsBean
    {
        Container _container;
        Group[] _groups;
        String _expandedGroupPath;
        List<String> _messages;

        public GroupsBean(Container c, String expandedGroupPath, List<String> messages)
        {
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
            _expandedGroupPath = expandedGroupPath;
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
            User[] users = UserManager.getAllUsers();
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
                if (user.getDisplayName(getViewContext()).compareToIgnoreCase(user.getEmail()) != 0)
                    names.add(user.getDisplayName(getViewContext()) + " (" + user.getEmail() + ")");

                names.add(shortName);
            }
            return names;
        }

        public List<String> getMessages()
        {
            return _messages;
        }
    }

    private HttpView getGroupsView(Container container, String expandedGroup, BindException errors, List<String> messages)
    {
        JspView<GroupsBean> groupsView = new JspView<GroupsBean>("/org/labkey/core/security/groups.jsp", new GroupsBean(container, expandedGroup, messages), errors);
        if (null == container || container.isRoot())
            groupsView.setTitle("Global Groups");
        else
            groupsView.setTitle("Groups for project " + container.getProject().getName());
        groupsView.setBodyClass("normal");
        return groupsView;
    }

    private ModelAndView renderContainerPermissions(String expandedGroup, BindException errors, List<String> messages, boolean wizard) throws Exception
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
        if (project.hasPermission(getUser(), ACL.PERM_ADMIN))
            projectViews.addView(getGroupsView(c, expandedGroup, errors, messages));

        ActionURL startURL = c.getFolderType().getStartURL(c, getUser());
        projectViews.addView(new HtmlView(PageFlowUtil.buttonLink("Done", startURL)));
        body.addView(projectViews, "60%");
        body.addView(new PermissionsDetailsView(c, "container"), "40%");

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


    @RequiresPermission(ACL.PERM_ADMIN)
    public class ProjectAction extends SimpleViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, BindException errors) throws Exception
        {
            return renderContainerPermissions(null, null, null, form.isWizard());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions");
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ContainerAction extends SimpleViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, BindException errors) throws Exception
        {
            return renderContainerPermissions(null, errors, Collections.<String>emptyList(), form.isWizard());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions");
            return root;
        }
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class NewGroupAction extends SimpleViewAction<NewGroupForm>
    {
        public ModelAndView getView(NewGroupForm form, BindException errors) throws Exception
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
                    addGroupAuditEvent(group, "The group: " + name + " was created.");
                }
                catch (IllegalArgumentException e)
                {
                    error = e.getMessage();
                }
            }
            if (error != null)
            {
                errors.addError(new LabkeyError(error));
            }
            return renderContainerPermissions(null, errors, null, false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Permissions");
        }
    }

    private void addGroupAuditEvent(Group group, String message)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
        event.setCreatedBy(getUser().getUserId());
        event.setIntKey2(group.getUserId());
        Container c = ContainerManager.getForId(group.getContainer());
        if (c != null && c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setComment(message);
        event.setContainerId(group.getContainer());

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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateMembersAction extends SimpleViewAction<UpdateMembersForm>
    {
        private String _groupName;
        private boolean _showGroup;

        public ModelAndView getView(UpdateMembersForm form, BindException errors) throws Exception
        {
            // 1 - Global admins group cannot be empty
            // 2 - warn if you are deleting yourself from global or project admins
            // 3 - if user confirms delete, post to action again, with list of users to delete and confirmation flag.

            _groupName = StringUtils.trimToNull(form.getGroup());
            if (null == _groupName)
                HttpView.throwNotFound();
            Container container = getContainer();
            Container project = container.isRoot() ? null : container.getProject();

            if (!container.isRoot() && !container.isProject())
                container = container.getProject();
            ensureGroupInContainer(_groupName, container);

            List<String> messages = new ArrayList<String>();

            // UNDONE: need SecurityManager.getGroup()
            Integer groupId = SecurityManager.getGroupId(_groupName);
            Group group = null == groupId ? null : SecurityManager.getGroup(groupId.intValue());

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
                    messages.add("Could not add user " + e + ": Invalid email address");
            }

            String[] removeNames = form.getDelete();
            invalidEmails.clear();
            List<ValidEmail> removeEmails = SecurityManager.normalizeEmails(removeNames, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                    messages.add("Could not remove user " + e + ": Invalid email address");
            }

            if (group != null)
            {
                //check for users to delete
                if (removeNames != null)
                {
                    //get list of group members. need this to determine how many there are.
                    String[] groupMemberNames = SecurityManager.getGroupMemberNames(groupId);

                    //if this is the site admins group and user is attempting to remove all site admins, display error.
                    if (groupId == Group.groupAdministrators && removeNames.length == groupMemberNames.length)
                    {
                        errors.addError(new LabkeyError("The Site Administrators group must always contain at least one member. You cannot remove all members of this group."));
                    }
                    //if this is site or project admins group and user is removing themselves, display warning.
                    else if (_groupName.compareToIgnoreCase("Administrators") == 0
                            && Arrays.asList(removeNames).contains(getUser().getEmail())
                            && !form.isConfirmed())
                    {
                        //display warning form, including users to delete and add
                        HttpView<UpdateMembersBean> v = new JspView<UpdateMembersBean>("/org/labkey/core/security/deleteUser.jsp", new UpdateMembersBean());

                        UpdateMembersBean bean = v.getModelBean();
                        bean.addnames = addEmails;
                        bean.removenames = removeNames;
                        bean.groupName = _groupName;
                        bean.mailPrefix = form.getMailPrefix();

                        getPageConfig().setTemplate(PageConfig.Template.Dialog);
                        return v;
                    }
                    else
                    {
                        SecurityManager.deleteMembers(group, removeEmails);
                    }
                }

                if (addEmails.size() > 0)
                {
                    // add new users
                    for (ValidEmail email : addEmails)
                    {
                        String addMessage = SecurityManager.addUser(getViewContext(), email, form.getSendEmail(), form.getMailPrefix(), null);
                        if (addMessage != null)
                            messages.add(addMessage);
                    }

                    try
                    {
                        SecurityManager.addMembers(group, addEmails);
                    }
                    catch (SQLException e)
                    {
                        errors.addError(new LabkeyError("A failure occurred adding users to the group: " + e.getMessage()));
                    }
                }
            }

            if (form.isQuickUI())
                return renderContainerPermissions(_groupName, errors, messages, false);
            else
            {
                _showGroup = true;
                return renderGroup(_groupName, errors, messages);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showGroup)
            {
                return addGroupNavTrail(root, _groupName);
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

    public static class UpdateMembersForm
    {
        private String names;
        private String group;
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

        public String getGroup()
        {
            return group;
        }

        public void setGroup(String group)
        {
            this.group = group;
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


    private NavTree addGroupNavTrail(NavTree root, String group)
    {
        root.addChild("Permissions", new ActionURL(ContainerAction.class, getContainer()));
        root.addChild("Manage Group");

        String title;
        if (-1 == group.indexOf('/'))
            title = group;
        else
            title = group.substring(group.lastIndexOf('/') + 1);

        root.addChild(title + " Group");
        return root;
    }

    private ModelAndView renderGroup(String group, BindException errors, List<String> messages) throws Exception
    {
        if (group.startsWith("/"))
            group = group.substring(1);

        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group);

        if (null == members)
            HttpView.throwNotFound();

        VBox view = new VBox(new GroupView(group, members, messages, c.isRoot(), errors));
        if (getUser().isAdministrator())
        {
            Integer id = SecurityManager.getGroupId(group);
            if (id != null)
            {
                view.addView(GroupAuditViewFactory.getInstance().createGroupView(getViewContext(), id));
            }
        }
        return view;
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class GroupAction extends SimpleViewAction<GroupForm>
    {
        private String _group;

        public ModelAndView getView(GroupForm form, BindException errors) throws Exception
        {
            _group = form.getGroup();
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

    @RequiresPermission(ACL.PERM_ADMIN)
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

    @RequiresPermission(ACL.PERM_ADMIN)
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
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(UserController.getUserColumnNames(getUser()));
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

    public static class GroupIdForm
    {
        private int _group;

        public int getGroup()
        {
            return _group;
        }

        public void setGroup(int group)
        {
            _group = group;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class GroupPermissionAction extends SimpleViewAction<GroupIdForm>
    {
        private Group _requestedGroup;

        public ModelAndView getView(GroupIdForm form, BindException errors) throws Exception
        {
            final int groupId = form.getGroup();
            List<UserController.AccessDetailRow> rows = new ArrayList<UserController.AccessDetailRow>();
            _requestedGroup = SecurityManager.getGroup(groupId);
            if (_requestedGroup != null)
            {
                buildAccessDetailList(Collections.singletonList(getContainer().getProject()), rows, _requestedGroup, 0);
            }

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
                    ACL acl = child.getAcl();
                    int permissions = acl.getPermissions(requestedGroup);
                    SecurityManager.PermissionSet set = SecurityManager.PermissionSet.findPermissionSet(permissions);
                    assert set != null : "Unknown permission set: " + permissions;
                    String access;
                    if (set == null)
                        access = "Unknown: " + permissions;
                    else
                        access = set.getLabel();

                    rows.add(new UserController.AccessDetailRow(child, access, null, depth));
                    buildAccessDetailList(child.getChildren(), rows, requestedGroup, depth + 1);
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(ContainerAction.class, getContainer()));
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdatePermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors) {}

        private void addAuditEvent(User user, String comment, int groupId)
        {
            if (user != null)
            {
                AuditLogEvent event = new AuditLogEvent();

                event.setCreatedBy(user.getUserId());
                event.setComment(comment);

                Container c = getViewContext().getContainer();
                event.setContainerId(c.getId());
                if (c.getProject() != null)
                    event.setProjectId(c.getProject().getId());

                event.setIntKey2(groupId);
                event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
                AuditLogService.get().addEvent(event);
            }
        }

        private void addAuditEvent(int groupId, int perm, ACL acl, AuditChangeType changeType)
        {
            int prevPerm = acl.getPermissions(groupId);
            if (prevPerm != perm)
            {
                Group g = SecurityManager.getGroup(groupId);
                if (g != null)
                {
                    SecurityManager.PermissionSet newSet = SecurityManager.PermissionSet.findPermissionSet(perm);
                    SecurityManager.PermissionSet oldSet = SecurityManager.PermissionSet.findPermissionSet(prevPerm);

                    switch (changeType)
                    {
                        case explicit:
                            addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s",
                                    g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                            break;
                        case fromInherited:
                            addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s (inherited) to %s",
                                    g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                            break;
                        case toInherited:
                            addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s (inherited)",
                                    g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                            break;
                    }
                }
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

            if (inherit)
            {
                addAuditEvent(getUser(), String.format("Container %s was updated to inherit security permissions", c.getName()), 0);

                Container container = c.getParent();
                ACL old = SecurityManager.getACL(c);
                ACL cur = SecurityManager.getACL(container);
                changeType = AuditChangeType.toInherited;

                while (cur.isEmpty() && container.getParent() != null)
                {
                    // find the acl that applies
                    container = container.getParent();
                    cur = SecurityManager.getACL(container);
                }

                for (Group g : SecurityManager.getGroups(c.getProject(), true))
                {
                    addAuditEvent(g.getUserId(), cur.getPermissions(g), old, changeType);
                }
                SecurityManager.removeACL(c, c.getId());
            }
            else
            {
                ACL acl = new ACL(false); // not empty
                Container container = c;
                ACL old = SecurityManager.getACL(container);

                while (old.isEmpty() && container.getParent() != null)
                {
                    container = container.getParent();
                    old = SecurityManager.getACL(container);
                    changeType = AuditChangeType.fromInherited;
                }

                HttpServletRequest request = getViewContext().getRequest();
                Enumeration e = request.getParameterNames();
                while (e.hasMoreElements())
                {
                    try
                    {
                        String key = (String) e.nextElement();
                        if (!key.startsWith("group."))
                            continue;
                        String value = request.getParameter(key);
                        int groupid = (int) Long.parseLong(key.substring(6), 16);
                        int perm = NumberUtils.toInt(value, 0);

                        addAuditEvent(groupid, perm, old, changeType);
                        if (perm != 0)
                            acl.setPermission(groupid, perm);
                    }
                    catch (NumberFormatException x)
                    {
                        // continue;
                    }
                }

                SecurityManager.updateACL(c, acl);
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL("Security", getViewContext().getRequest().getParameter("view"), getContainer());
        }
    }

    public static class AddUsersForm
    {
        private boolean sendMail;
        private String newUsers;
        private String _cloneUser;
        private boolean _skipProfile;
        private String _returnUrl;

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

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
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
                        errors.addError(new LabkeyError("Failed to clone user permissions " + emailToClone + ": User email does not exist in the system<br>"));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.addError(new LabkeyError("Failed to clone user permissions " + cloneUser.trim() + ": Invalid email address<br>"));
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
                    errors.addError(new LabkeyError("Failed to create user " + rawEmail.trim() + ": Invalid email address<br>"));
            }

            List<Pair<String, String>> extraParams = new ArrayList<Pair<String, String>>(2);
            if (form.isSkipProfile())
                extraParams.add(new Pair<String, String>("skipProfile", "1"));
            if (null != form.getReturnUrl())
                extraParams.add(new Pair<String, String>(ReturnUrlForm.Params.returnUrl.toString(), form.getReturnUrl()));

            for (ValidEmail email : emails)
            {
                String result = SecurityManager.addUser(getViewContext(), email, form.getSendMail(), null, extraParams.<Pair<String, String>>toArray(new Pair[extraParams.size()]));

                if (result == null)
                {
                    User user = UserManager.getUser(email);
                    ActionURL url = new ActionURL(UserController.DetailsAction.class, ContainerManager.getRoot());
                    url.addParameter("userId", String.valueOf(user.getUserId()));
                    result = email + " was already a registered system user.  Click <a href=\"" + url.getEncodedLocalURIString() + "\">here</a> to see this user's profile and history.";
                }
                else if (userToClone != null)
                {
                    clonePermissions(userToClone, email);
                }
                errors.addError(new LabkeyError(result));
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

    public static class EmailForm
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
                String verificationUrl = SecurityManager.createVerificationUrl(getContainer(), email.getEmailAddress(),
                        verification, null).getURIString();
                SecurityManager.renderEmail(getUser(), message, email.getEmailAddress(), verificationUrl, out);
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
    public class ResetPasswordAction extends SimpleViewAction<EmailForm>
    {
        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            User user = getUser();
            StringBuilder sbReset = new StringBuilder();

            String rawEmail = form.getEmail();

            sbReset.append("<p>").append(rawEmail);

            ValidEmail email = new ValidEmail(rawEmail);

            // Make sure it exists
            if (!SecurityManager.loginExists(email))
            {
                sbReset.append(" failed: doesn't exist in Logins table.</p>");
            }
            else
            {
                // Create a placeholder password that's impossible to guess and a separate email
                // verification key that gets emailed.
                String tempPassword = SecurityManager.createTempPassword();
                String verification = SecurityManager.createTempPassword();

                try
                {
                    SecurityManager.setPassword(email, tempPassword);
                    SecurityManager.setVerification(email, verification);
                    sbReset.append(": password reset.</p>");

                    ActionURL actionURL = new ActionURL(ShowResetEmailAction.class, getContainer()).addParameter("email", email.toString());
                    String url = actionURL.getLocalURIString();
                    String href = "<a href=" + url + " target=\"_blank\">here</a>";

                    try
                    {
                        String verificationUrl = SecurityManager.createVerificationUrl(getContainer(), email.getEmailAddress(),
                                verification, null).getURIString();

                        SecurityManager.sendEmail(user, SecurityManager.getResetMessage(false), email.getEmailAddress(),
                                verificationUrl);
                        if (!user.getEmail().equals(email.getEmailAddress()))
                        {
                            SecurityMessage msg = SecurityManager.getResetMessage(true);
                            msg.setTo(email.getEmailAddress());
                            SecurityManager.sendEmail(user, msg, user.getEmail(), verificationUrl);
                        }
                        sbReset.append("Email sent. ");
                        sbReset.append("Click ").append(href).append(" to see the email.");
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " reset the password.");
                    }
                    catch (MessagingException e)
                    {
                        sbReset.append("Failed to send email due to: <pre>").append(e.getMessage()).append("</pre>");
                        appendMailHelpText(sbReset, url);
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " reset the password, but sending the email failed.");
                    }
                }
                catch (SecurityManager.UserManagementException e)
                {
                    sbReset.append(": failed to reset password due to: ").append(e.getMessage());
                    UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " attempted to reset the password, but the reset failed: " + e.getMessage());
                }
            }
            sbReset.append("<br>");
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
            sb.append((new HelpTopic("cpasxml", HelpTopic.Area.SERVER)).getHelpTopicLink());
            sb.append("\" target=\"_blank\">LabKey Server documentation on modifying your configuration file</a>.</p>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }
        
        public TestCase(String name)
        {
            super(name);
        }

        Container c;

        public void testAnnotations() throws Exception
        {
            Container junit = ContainerManager.ensureContainer("/Shared/_junit");
            c = ContainerManager.createContainer(junit, "SecurityController-" + GUID.makeGUID());

            User site = TestContext.get().getUser();
            assertTrue(site.isAdministrator());
            User guest = new TestUser("guest", 0, Group.groupGuests);
            User user = new TestUser("user", 1, Group.groupGuests, Group.groupUsers);
            User admin = new TestUser("admin", 2, Group.groupGuests, Group.groupUsers);

            ACL acl = c.getAcl();
            acl.setPermission(admin, ACL.PERM_ALLOWALL);
            acl.setPermission(guest, ACL.PERM_READ);
            acl.setPermission(user, ACL.PERM_UPDATE);
            SecurityManager.updateACL(c, acl);            

            // @RequiresPermission(ACL.PERM_NONE)
            assertPermission(guest, BeginAction.class);
            assertPermission(user, BeginAction.class);
            assertPermission(admin, BeginAction.class);
            assertPermission(site, BeginAction.class);

            // @RequiresPermission(ACL.PERM_ADMIN)
            assertNoPermission(guest, GroupsAction.class);
            assertNoPermission(user, GroupsAction.class);
            assertPermission(admin, GroupsAction.class);
            assertPermission(site, GroupsAction.class);

            // @RequiresSiteAdmin
            assertNoPermission(guest, AddUsersAction.class);
            assertNoPermission(user, AddUsersAction.class);
            assertNoPermission(admin, AddUsersAction.class);
            assertPermission(site, AddUsersAction.class);

            assertTrue(ContainerManager.delete(c, null));
        }


        void assertPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, c, u);
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission");
            }
        }


        void assertNoPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, c, u);
                fail("Should not have permission");
            }
            catch (UnauthorizedException x)
            {
                // expected
            }
        }


        static private class TestUser extends User
        {
            TestUser(String name, int id, Integer...groups)
            {
                super(name,id);
                _groups = new int[groups.length+1];
                for (int i=0 ; i<groups.length; i++)
                    _groups[i] = groups[i];
                _groups[groups.length] = id;
                Arrays.sort(_groups);
            }
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
