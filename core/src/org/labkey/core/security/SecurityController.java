/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionMapping;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.ViewFactory;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.common.util.Pair;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.user.UserController;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;


@Jpf.Controller
public class SecurityController extends ViewController
{
    private static Logger _log = Logger.getLogger(SecurityController.class);


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward begin()
            throws Exception
    {
        if (null == getContainer() || getContainer().isRoot())
            return new ViewForward("Security", "showAddUsers", "/");
        else
            return new ViewForward("Security", "project", getContainer().getPath());
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward groups() throws Exception
    {
        return _renderInTemplate(getGroupsView(getContainer(), null, Collections.<String>emptyList(), Collections.<String>emptyList()), "Groups");
    }


    private HttpView getGroupsView(Container container, String expandedGroup, List<String> errors, List<String> messages)
    {
        JspView<GroupsBean> groupsView = new JspView<GroupsBean>("/org/labkey/core/security/groups.jsp", new GroupsBean(container, expandedGroup, errors, messages));
        if (null == container || container.isRoot())
            groupsView.setTitle("Global Groups");
        else
            groupsView.setTitle("Groups for project " + container.getProject().getName());
        groupsView.setBodyClass("normal");
        return groupsView;
    }


    public static class CompleteUserForm extends FormData
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


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward completeUser(CompleteUserForm form) throws Exception
    {
        List<AjaxCompletion> completions = UserManager.getAjaxCompletions(form.getPrefix(), HttpView.currentContext());
        return sendAjaxCompletions(completions);
    }


    private Forward renderGroup(String group, List<String> errors, List<String> messages) throws Exception
    {
        requiresAdmin();
        if (group.startsWith("/"))
            group = group.substring(1);

        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group);

        if (null == members)
            HttpView.throwNotFound();

        String title;
        if (-1 == group.indexOf('/'))
            title = group;
        else
            title = group.substring(group.lastIndexOf('/') + 1);
        ActionURL permissionsLink = cloneActionURL().setAction("container");
        permissionsLink.deleteParameters();
        NavTree[] navTrail = new NavTree[] {
                new NavTree("Permissions", permissionsLink),
                new NavTree("Manage Group", (String)null)};

        VBox view = new VBox(new GroupView(group, members, errors, messages, c.isRoot()));
        if (getUser().isAdministrator())
        {
            Integer id = SecurityManager.getGroupId(group);
            if (id != null)
            {
                view.addView(GroupAuditViewFactory.getInstance().createGroupView(getViewContext(), id));
            }
        }
        return _renderInTemplate(view, title + " Group", navTrail);
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward groupExport() throws Exception
    {
        ActionURL url = getActionURL();
        String group = url.getParameter("group");
        requiresAdmin();
        if (group.startsWith("/"))
            group = group.substring(1);
        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group);

        try
        {
            DataRegion rgn = new DataRegion();
            ColumnInfo[] columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(UserController.getUserColumnNames(getUser()));
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
            ew.write(getResponse());
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


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward group() throws Exception
    {
        ActionURL url = getActionURL();
        String group = url.getParameter("group");
        return renderGroup(group, Collections.<String>emptyList(), Collections.<String>emptyList());
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward groupPermission() throws Exception
    {
        ActionURL url = getActionURL();
        final String group = url.getParameter("group");

        final int groupId = Integer.parseInt(group);
        List<UserController.AccessDetailRow> rows = new ArrayList<UserController.AccessDetailRow>();
        Group requestedGroup = SecurityManager.getGroup(groupId);
        buildAccessDetailList(Collections.singletonList(getContainer().getProject()), rows, requestedGroup, 0);

        UserController.AccessDetail bean = new UserController.AccessDetail(rows, false);
        JspView<UserController.AccessDetail> view = new JspView<UserController.AccessDetail>("/org/labkey/core/user/userAccess.jsp", bean);

        ActionURL permissionsLink = cloneActionURL().setAction("container");
        permissionsLink.deleteParameters();
        NavTree[] navTrail = new NavTree[] {
                new NavTree("Permissions", permissionsLink),
                new NavTree("Group Permissions", (String)null)};
        NavTrailConfig config = new NavTrailConfig(getViewContext());

        final String title = requestedGroup.isUsers() ? "Access Details: Site Users" : "Access Details: " + requestedGroup.getName();
        config.setTitle(title, false);
        config.setExtraChildren(navTrail);
        return _renderInTemplate(view, config);
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


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteGroup(DeleteGroupForm form) throws Exception
    {
        String groupPath = form.getGroup();
        ensureGroupInContainer(groupPath, getContainer());
        Integer gid = SecurityManager.getGroupId(groupPath);
        if (gid != null)
        {
            Group group = SecurityManager.getGroup(gid);
            addGroupAuditEvent(group, "The group: " + form.getGroup() + " was deleted.");
        }
        SecurityManager.deleteGroup(groupPath);
        ActionURL redir = cloneActionURL();
        if (getContainer().isRoot())
            redir.setAction("groups");
        else
            redir.setAction("project");
        return new ViewForward(redir);
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


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward updateMembers(UpdateMembersForm form) throws Exception
    {
        // 1 - Global admins group cannot be empty
        // 2 - warn if you are deleting yourself from global or project admins
        // 3 - if user confirms delete, post to action again, with list of users to delete and confirmation flag.

        String groupName = StringUtils.trimToNull(form.getGroup());
        if (null == groupName)
            HttpView.throwNotFound();
        Container container = getContainer();
        Container project = container.isRoot() ? null : container.getProject();

        if (!container.isRoot() && !container.isProject())
            container = container.getProject();
        ensureGroupInContainer(groupName, container);

        List<String> errors = new ArrayList<String>();
        List<String> messages = new ArrayList<String>();

        // UNDONE: need SecurityManager.getGroup()
        Integer groupId = SecurityManager.getGroupId(groupName);
        Group group = null == groupId ? null : SecurityManager.getGroup(groupId);

        //check for new users to add.
        String[] allNames = _toString(form.getNames()).split("\n");

        List<String> emails = new ArrayList<String>();
        List<String> addGroups = new ArrayList<String>();

        emails.addAll(Arrays.asList(allNames));

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
                    errors.add("The Site Administrators group must always contain at least one member. You cannot remove all members of this group.");
                }
                //if this is site or project admins group and user is removing themselves, display warning.
                else if (groupName.compareToIgnoreCase("Administrators") == 0
                        && Arrays.asList(removeNames).contains(getUser().getEmail())
                        && !form.isConfirmed())
                {
                    //display warning form, including users to delete and add
                    HttpView<UpdateMembersBean> v = new JspView<UpdateMembersBean>("/org/labkey/core/security/deleteUser.jsp", new UpdateMembersBean());

                    UpdateMembersBean bean = v.getModelBean();
                    bean.addnames = addEmails;
                    bean.removenames = removeNames;
                    bean.groupName = groupName;
                    bean.mailPrefix = form.getMailPrefix();

                    HttpView template = new DialogTemplate(v);
                    includeView(template);
                    return null;
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
                    String addMessage = SecurityManager.addUser(getViewContext(), email, form.getSendEmail(), form.getMailPrefix());
                    if (addMessage != null)
                        messages.add(addMessage);
                }

                try
                {
                    SecurityManager.addMembers(group, addEmails);
                }
                catch (SQLException e)
                {
                    errors.add("A failure occurred adding users to the group: " + e.getMessage());
                }
            }

            if (addGroups.size() > 0)
            {
                for (String name : addGroups)
                {
                    Integer id = SecurityManager.getGroupId(project, name);
                    if (id != 0 && id > 0)
                    {
                        Group groupMember = SecurityManager.getGroup(id);
                        SecurityManager.addMember(group, groupMember);
                    }
                }
            }
        }

        if (form.isQuickUI())
            return renderContainerPermissions(groupName, errors, messages);
        else
            return renderGroup(groupName, errors, messages);
    }


    public static class UpdateMembersBean
    {
        public List<ValidEmail> addnames;
        public String[] removenames;
        public String groupName;
        public String mailPrefix;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward newGroup(NewGroupForm form)
            throws Exception
    {
        ActionURL url = getActionURL();
        String extraPath = url.getExtraPath();
        Container c = ContainerManager.getForPath(extraPath);
        ActionURL redir = cloneActionURL();
        if (c.isRoot())
            redir.setAction("groups");
        else
            redir.setAction("project");

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
                Group group = SecurityManager.createGroup(c.getProject(), name);
                addGroupAuditEvent(group, "The group: " + name + " was created.");
            }
            catch (IllegalArgumentException e)
            {
                error = e.getMessage();
            }
        }
        List<String> errorList = null;
        if (error != null)
        {
            errorList = new ArrayList<String>(1);
            errorList.add(error);
        }
        return renderContainerPermissions(null, errorList, null);
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

    private String _toString(String a)
    {
        return null == a ? "" : a;
    }

    private Forward _renderInTemplate(HttpView view, NavTrailConfig navTrail) throws Exception
    {
        HttpView template = new HomeTemplate(getViewContext(), getContainer(), view, navTrail);
        includeView(template);
        return null;
    }

    private Forward _renderInTemplate(HttpView view, String title) throws Exception
    {
        return _renderInTemplate(view, title, (NavTree[]) null);
    }

    private Forward _renderInTemplate(HttpView view, String title, NavTree... navTrail) throws Exception
    {
        return _renderInTemplate(view, title, null, navTrail);
    }

    private Forward _renderInTemplate(HttpView view, String title, String helpTopic, NavTree... navTrail) throws Exception
    {
        if (helpTopic == null)
            helpTopic = "security";

        NavTrailConfig trailConfig = new NavTrailConfig(getViewContext());
        if (navTrail != null)
            trailConfig.setExtraChildren(navTrail);

        trailConfig.setHelpTopic(new HelpTopic(helpTopic, HelpTopic.Area.SERVER));
        trailConfig.setTitle(title);
        HomeTemplate template = new HomeTemplate(getViewContext(), view, trailConfig);
        includeView(template);
        return null;
    }


    public static class ExpandedGroupForm extends FormData
    {
        private String _expandGroup;

        public String getExpandGroup()
        {
            return _expandGroup;
        }

        public void setExpandGroup(String expandGroup)
        {
            _expandGroup = expandGroup;
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward container(PermissionsForm form) throws Exception
    {
        return renderContainerPermissions(null, Collections.<String>emptyList(), Collections.<String>emptyList(), form.isWizard());
    }

    private Forward renderContainerPermissions(String expandedGroup, List<String> errors, List<String> messages) throws Exception
    {
        return renderContainerPermissions(expandedGroup, errors, messages, false);
    }

    private Forward renderContainerPermissions(String expandedGroup, List<String> errors, List<String> messages, boolean wizard) throws Exception
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
        body.addView(new DetailsView(c, "container"), "40%");

        if (wizard)
        {
            VBox outer = new VBox();
            String message = "Use this page to ensure that only appropriate users have permission to view and edit the new " + (c.isProject() ? "Project" : "folder");
            outer.addView(new HtmlView(message));
            outer.addView(body);
            return _renderInTemplate(outer, "Permissions");
        }

        return _renderInTemplate(body, "Permissions");
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward project(PermissionsForm form) throws Exception
    {
        return renderContainerPermissions(null, null, null, form.isWizard());
    }
    
    protected enum AuditChangeType {
        explicit,
        fromInherited,
        toInherited,
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward updatePermissions()
            throws ServletException, SQLException, URISyntaxException
    {
        ViewContext ctx = getViewContext();
        Container c = getContainer();
        AuditChangeType changeType = AuditChangeType.explicit;

        // UNDONE: remove objectId from the form
        assert c.getId().equals(ctx.get("objectId"));

        boolean inherit = "on".equals(ctx.get("inheritPermissions"));

        if (inherit)
        {
            _addAuditEvent(getUser(), String.format("Container %s was updated to inherit security permissions", c.getName()), 0);

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

            HttpServletRequest request = getRequest();
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

        return new ViewForward("Security", (String) ctx.get("view"), getActionURL().getExtraPath());
    }

    public static class PermissionsForm extends FormData
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
                        _addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s",
                                g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                        break;
                    case fromInherited:
                        _addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s (inherited) to %s",
                                g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                        break;
                    case toInherited:
                        _addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s (inherited)",
                                g.getName(), oldSet.getLabel(), newSet.getLabel()), groupId);
                        break;
                }
            }
        }
    }

    private void _addAuditEvent(User user, String comment, int groupId)
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

    @Jpf.Action @RequiresSiteAdmin
    protected Forward showAddUsers(MessageForm form) throws Exception
    {
        AddUsersPage page = (AddUsersPage) JspLoader.createPage(getRequest(), SecurityController.class, "addUsers.jsp");
        page.setMessage(form.getMessage());
        HttpView addView = new JspView(page);

        return _renderInTemplate(addView, "Add Users");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward addUsers(AddUsersForm form) throws Exception
    {
        String[] rawEmails = _toString(form.getNewUsers()).split("\n");
        List<String> invalidEmails = new ArrayList<String>();
        List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);
        User userToClone = null;

        StringBuilder message = new StringBuilder();

        final String cloneUser = form.getCloneUser();
        if (cloneUser != null && cloneUser.length() > 0)
        {
            try {
                final ValidEmail emailToClone = new ValidEmail(cloneUser);
                userToClone = UserManager.getUser(emailToClone);
                if (userToClone == null)
                    message.append("Failed to clone user permissions ").append(emailToClone).append(": User email does not exist in the system<br>");
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                message.append("Failed to clone user permissions ").append(cloneUser.trim()).append(": Invalid email address<br>");
            }
        }

        // don't attempt to create the users if the user to clone is invalid
        if (message.length() > 0)
        {
            MessageForm messageForm = new MessageForm();
            messageForm.setMessage(message.toString());

            return showAddUsers(messageForm);
        }

        for (String rawEmail : invalidEmails)
        {
            // Ignore lines of all whitespace, otherwise show an error.
            if (!"".equals(rawEmail.trim()))
                message.append("Failed to create user ").append(rawEmail.trim()).append(": Invalid email address<br>");
        }

        for (ValidEmail email : emails)
        {
            String result = SecurityManager.addUser(getViewContext(), email, form.getSendMail(), null);

            if (result == null)
            {
                User user = UserManager.getUser(email);
                ActionURL url = new ActionURL("User", "details", ContainerManager.getRoot());
                url.addParameter("userId", String.valueOf(user.getUserId()));
                result = email + " was already a registered system user.  Click <a href=\"" + url.getEncodedLocalURIString() + "\">here</a> to see this user's profile and history.";
            }
            else if (userToClone != null)
            {
                clonePermissions(userToClone, email);
            }
            message.append(result).append("<br>");
        }

        MessageForm messageForm = new MessageForm();

        if (message.length() > 0)
            messageForm.setMessage(message.toString());

        return showAddUsers(messageForm);
    }


    private void clonePermissions(User clone, ValidEmail userEmail) throws ServletException
    {
        requiresGlobalAdmin();

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


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward userAccess(UserController.UserForm form) throws Exception
    {
        final String email = form.getNewEmail();
        if (email != null)
        {
            User user = UserManager.getUser(new ValidEmail(email));

            if (user != null)
            {
                ActionURL url = new ActionURL("User", "userAccess", getContainer());
                url.addParameter("userId", Integer.toString(user.getUserId()));
                url.addParameter("renderInHomeTemplate", "false");
                return new ViewForward(url);
            }
        }

        WebPartView error = new WebPartView()
        {
            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                out.println(PageFlowUtil.getStandardIncludes());
                out.println("<p/><table align=center><tr><td>");
                out.println("<span class=\"labkey-error\">");
                if (email == null)
                    out.println("No user email specified");
                else
                    out.println("User email does not exist in the system: " + email);
                out.println("</span>&nbsp;");
                out.println("</td></tr></table>");
            }
        };
        includeView(error);
        return null;
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showRegistrationEmail(EmailForm form) throws Exception
    {
        // TODO: Allow project admins to view verification emails for users they've added?
        return showSecurityEmail(form, SecurityManager.getRegistrationMessage(form.getMailPrefix(), false));
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showResetEmail(EmailForm form) throws Exception
    {
        return showSecurityEmail(form, SecurityManager.getResetMessage(false));
    }


    private Forward showSecurityEmail(EmailForm form, SecurityMessage message) throws Exception
    {
        requiresGlobalAdmin();

        Writer out = getResponse().getWriter();
        String rawEmail = form.getEmail();
        ValidEmail email = new ValidEmail(rawEmail);

        if (SecurityManager.isVerified(email))
            out.write("Can't display " + message.getType().toLowerCase() + "; " + email + " has already chosen a password.");
        else
        {
            String verification = SecurityManager.getVerification(email);
            String verificationUrl = SecurityManager.createVerificationUrl(getContainer(), email.getEmailAddress(),
                    verification).getURIString();
            SecurityManager.renderEmail(getUser(), message, email.getEmailAddress(), verificationUrl, out);
        }

        return null;
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward resetPassword() throws Exception
    {
        User user = getUser();
        StringBuilder sbReset = new StringBuilder();

        String rawEmail = getActionURL().getParameter("email");

        sbReset.append("<p>").append(rawEmail);

        ValidEmail email = new ValidEmail(rawEmail);

        // Make sure it exists
        if (!SecurityManager.loginExists(email))
        {
            _log.error("resetPassword: " + email + " doesn't exist in Logins.");
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

                ActionURL actionURL = cloneActionURL();
                actionURL.setAction("showResetEmail");
                String url = actionURL.getLocalURIString();
                String href = "<a href=" + url + " target=\"_blank\">here</a>";

                try
                {
                    String verificationUrl = SecurityManager.createVerificationUrl(getContainer(), email.getEmailAddress(),
                            verification).getURIString();

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

        HttpView body = new HtmlView(sbReset.toString());
        return includeView(new DialogTemplate(body));
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


    private static class GroupView extends JspView<GroupBean>
    {
        public GroupView(String groupName, List<Pair<Integer,String>> members, List<String> errors, List<String> messages, boolean globalGroup)
        {
            super("/org/labkey/core/security/group.jsp", new GroupBean());

            GroupBean bean = getModelBean();

            bean.groupName = groupName;
            bean.members = members;
            bean.errors = errors;
            bean.messages = messages;
            bean.isGlobalGroup = globalGroup;
            bean.ldapDomain = AppProps.getInstance().getLDAPDomain();
            bean.basePermissionsURL = ActionURL.toPathString("User", "userAccess", getViewContext().getContainer()) + "?userId=";
        }
    }


    public static class GroupBean
    {
        public String groupName;
        public List<Pair<Integer, String>> members;
        public List<String> errors;
        public List<String> messages;
        public boolean isGlobalGroup;
        public String ldapDomain;
        public String basePermissionsURL;
    }


    //
    // VIEWS
    //
    public class GroupsBean
    {
        Container _container;
        Group[] _groups;
        String _expandedGroupPath;
        List<String> _errors;
        List<String> _messages;

        public GroupsBean(Container c, String expandedGroupPath, List<String> errors, List<String> messages)
        {
            if (null == c || c.isRoot())
            {
                _groups = SecurityManager.getGroups(null, false);
                _container = ContainerManager.getRoot();
            }
            else
            {
                _groups = SecurityManager.getGroups(c.getProject(), false);
                _container = c;
            }
            _expandedGroupPath = expandedGroupPath;
            _errors = errors != null ? errors : Collections.<String>emptyList();
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

        public List<String> getErrors()
        {
            return _errors;
        }

        public List<String> getMessages()
        {
            return _messages;
        }
    }

    static class ContainersView extends WebPartView
    {
        Container _c;

        public ContainersView(Container c)
        {
            setTitle("Folders in project " + c.getName());
            setBodyClass("normal");
            _c = c;
        }


        public void renderView(Object model, PrintWriter out) throws IOException, ServletException
        {
            ActionURL url = new ActionURL("Security", "container", "");
            PermissionsContainerTree ct = new PermissionsContainerTree(_c.getPath(), getViewContext().getUser(), ACL.PERM_ADMIN, url);
            ct.setCurrent(getViewContext().getContainer());
            StringBuilder html = new StringBuilder("<table class=\"dataRegion\">");
            ct.render(html);
            html.append("</table><br>");
            ActionURL manageFoldersURL = getViewContext().cloneActionURL();
            manageFoldersURL.setAction("manageFolders").setPageFlow("admin");
            html.append("*Indicates that this folder's permissions are inherited from the parent folder<br><br>");
            html.append("[<a href=\"").append(manageFoldersURL.getURIString()).append("\">manage folders</a>]");

            out.println(html.toString());
        }
    }

    public static class PermissionsContainerTree extends ContainerTreeSelected
    {
        public PermissionsContainerTree(String rootPath, User user, int perm, ActionURL url)
        {
            super(rootPath, user, perm, url);
        }

        protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
        {
            if (c.equals(current))
                html.append("<span class=\"labkey-navtree-selected\">");

            if (null != url)
            {
                html.append("<a href=\"");
                url.setExtraPath(c.getPath());
                html.append(url.getEncodedLocalURIString());
                html.append("\">");
                if (c.isInheritedAcl())
                    html.append('*');
                html.append(PageFlowUtil.filter(c.getName()));
                html.append("</a>");
            }
            else
            {
                html.append(PageFlowUtil.filter(c.getName()));
            }
            if (c.equals(current))
                html.append("</span>");
        }
    }

    static class DetailsView extends WebPartView
    {
        String optionsAll =
                "<option value=" + SecurityManager.PermissionSet.ADMIN.getPermissions() + ">" + SecurityManager.PermissionSet.ADMIN.getLabel() + "</option>";
        String options =
                "<option value=" + SecurityManager.PermissionSet.EDITOR.getPermissions() + ">" + SecurityManager.PermissionSet.EDITOR.getLabel() + "</option>" +
                        "<option value=" + SecurityManager.PermissionSet.AUTHOR.getPermissions() + ">" + SecurityManager.PermissionSet.AUTHOR.getLabel() + "</option>" +
                        "<option value=" + SecurityManager.PermissionSet.READER.getPermissions() + ">" + SecurityManager.PermissionSet.READER.getLabel() + "</option>" +
                        "<option value=" + SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() + ">" + SecurityManager.PermissionSet.RESTRICTED_READER.getLabel() + "</option>" +
                        "<option value=" + SecurityManager.PermissionSet.SUBMITTER.getPermissions() + ">" + SecurityManager.PermissionSet.SUBMITTER.getLabel() + "</option>" +
                        "<option value=" + SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() + ">" + SecurityManager.PermissionSet.NO_PERMISSIONS.getLabel() + "</option>";

        String helpText = "<b>Admin:</b> Users have all permissions on a folder.<br><br>" +
                "<b>Editor:</b> Users can modify data, but cannot perform administrative actions.<br><br>" +
                "<b>Author:</b> Users can modify their own data, but can only read others' data.<br><br>" +
                "<b>Reader:</b> Users can read text and data, but cannot modify it.<br><br>" +
                "<b>Submitter:</b> Users can insert new records, but cannot view or change other records.<br><br>" +
                "<b>No Permissions:</b> Users cannot view or modify any information in a folder.<br><br>" +
                "See the LabKey Server <a target=\"_new\" href=\"" + (new HelpTopic("configuringPerms", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\">security</a> help topics for more information.";

        Container _c;
        Container _project;


        DetailsView(Container c, String view)
        {
            _c = c;

            // get project for this container
            String path = _c.getPath();
            int i = path.indexOf('/', 1);
            if (i == -1) i = path.length();
            String project = path.substring(0, i);
            _project = ContainerManager.getForPath(project);

            addObject("view", view);

            if(c.isRoot())
                setTitle("Default permissions for new projects");
            else
                setTitle("Permissions for " + _c.getPath());
            setBodyClass("normal");
        }

        private void renderGroupTableRow(Group group, ACL acl, PrintWriter out, String displayName)
        {
            int id = group.getUserId();
            int perm = acl.getPermissions(id);
            if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
            String htmlId = "group." + Integer.toHexString(id);
            out.print("<tr><td class=ms-searchform>");
            out.print(displayName);
            out.print("</td><td><select onchange=\"document.updatePermissions.inheritPermissions.checked=false;\" id=");
            out.print(htmlId);
            out.print(" name=");
            out.print(htmlId);
            out.print(">");
            if (!group.isGuests() || perm == ACL.PERM_ALLOWALL)
                out.print(optionsAll);
            out.print(options);
            SecurityManager.PermissionSet permSet = SecurityManager.PermissionSet.findPermissionSet(perm);
            if (permSet == null)
                out.print("<option value=" + perm + ">" + perm + "</option>");
            out.print("</select>");
            out.print("</td>");
            out.print("<td>");
            out.print(PageFlowUtil.helpPopup("LabKey Server Security Roles", helpText, true));
            out.print("</td>");

            if (!_c.isRoot())
            {
                out.print("<td class=\"normal\">");
                out.print("&nbsp;[<a href=\"" + ActionURL.toPathString("Security", "groupPermission", _c.getPath()) + "?group=" + group.getUserId() + "\">");
                out.print("permissions</a>]</td>");
            }
            out.print("</tr>");
            out.print("<script><!--\n");
            out.print("document.getElementById('");
            out.print(htmlId);
            out.print("').value = '" + perm + "';\n");
            out.print("--></script>\n");
        }


        @Override
        public void renderView(Object model, PrintWriter out) throws IOException, ServletException
        {
            boolean inherited = false;
            ACL acl = SecurityManager.getACL(_c, _c.getId());
            if (acl.isEmpty())
            {
                acl = _c.getAcl();
                inherited = true;
            }

            if (SecurityManager.isAdminOnlyPermissions(_c))
            {
                out.println("<b>Note: </b> Only administrators currently have access to this " + (_c.isProject() ? "project" : "") + " folder. <br>");
                if (_c.hasChildren())
                {
                    Container[] children = ContainerManager.getAllChildren(_c);
                    boolean childrenAdminOnly = true;
                    for (Container child : children)
                    {
                        if (!SecurityManager.isAdminOnlyPermissions(child))
                        {
                            childrenAdminOnly = false;
                            break;
                        }
                    }
                    out.println((childrenAdminOnly ? "No" : "Some") + " child folders can be accessed by non-administrators.");
                }
            }
            Group[] groups = SecurityManager.getGroups(_project, true);

            // browse link
            //out.println("Go back to <a href=\"" + ActionURL.toPathString("Project", "begin", _c.getPath()) + "\">" + _c.getPath() + "</a>");

            out.println("<form name=\"updatePermissions\" action=\"updatePermissions.post\" method=\"POST\">");

            if (!_c.isRoot())
            {
                if (!_c.isProject())
                    out.println("<input type=checkbox name=inheritPermissions " + (inherited ? "checked" : "") + "> inherit permissions from " + _c.getParent().getPath());
                else
                    out.println("<input type=hidden name=inheritPermissions value=off>");
            }

            out.println("<table>");
            // the first pass through will output only the project groups:
            Group guestsGroup = null;
            Group usersGroup = null;
            for (Group group : groups)
            {
                if (group.isGuests())
                    guestsGroup = group;
                else if (group.isUsers())
                    usersGroup = group;
                else if (group.isProjectGroup())
                    renderGroupTableRow(group, acl, out, group.getName());
                else
                {
                    // for groups that we don't want to display, we still have to output a hidden input
                    // for the ACL value; otherwise, a submit with 'inherit' turned off will result in the
                    // hidden groups having all permissions set to no-access.
                    int id = group.getUserId();
                    int perm = acl.getPermissions(id);
                    if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
                    String htmlId = "group." + Integer.toHexString(id);
                    out.println("<input type=\"hidden\" name=\"" + htmlId + "\" value=\"" + perm + "\">");
                }
            }
            if (usersGroup != null)
                renderGroupTableRow(usersGroup, acl, out, "All site users");
            if (guestsGroup != null)
                renderGroupTableRow(guestsGroup, acl, out, "Guests");
            out.println("</table>");
            out.println("<input type=\"image\" src=\"" + PageFlowUtil.buttonSrc("Update") + "\">");
            out.println("<input name=objectId type=hidden value=\"" + _c.getId() + "\">");
            out.println("<input name=view type=hidden value=\"" + getViewContext().get("view") + "\">");
            out.println("</form><br>");

            // Now render all the module-specific views registered for this page
            VBox vbox = new VBox();
            List<ViewFactory> factories = SecurityManager.getViewFactories();

            for (ViewFactory factory : factories)
                vbox.addView(factory.createView(getViewContext()));

            try
            {
                ViewContext ctx = getViewContext();
                vbox.render(ctx.getRequest(), ctx.getResponse());
            }
            catch(Exception e)
            {
                _log.error("Error rendering permissions views", e);

                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
            }
        }
    }

    public static class UpdateMembersForm extends FormData
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


    /**
     * UNDONE: use validation 
     */
    public static class NewGroupForm extends FormData
    {
        private String name;


        public void setName(String name)
        {
            this.name = name;
        }


        public String getName()
        {
            return this.name;
        }
    }


    /**
     * FormData get and set methods may be overwritten by the Form Bean editor.
     */
    public static class AddUsersForm extends FormData
    {
        private boolean sendMail;
        private String newUsers;
        private String _cloneUser;


        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            sendMail = false;  // Assume false unless explicitly set to true
            super.reset(actionMapping, httpServletRequest);
        }

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
    }

    /**
     * FormData get and set methods may be overwritten by the Form Bean editor.
     */
    public static class EmailForm extends FormData
    {
        private String email;
        private String mailPrefix;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return this.email;
        }

        public String getMailPrefix()
        {
            return mailPrefix;
        }

        public void setMailPrefix(String mailPrefix)
        {
            this.mailPrefix = mailPrefix;
        }
    }

    public static class DeleteGroupForm extends FormData
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

    public static class MessageForm extends FormData
    {
        private String message;

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
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

            ACL acl = new ACL();
            acl.setPermission(admin, ACL.PERM_ALLOWALL);
            acl.setPermission(guest, ACL.PERM_READ);
            acl.setPermission(user, ACL.PERM_UPDATE);
            SecurityManager.updateACL(c, acl);            

            // @RequiresPermission(ACL.PERM_NONE)
            assertPermission(guest, "begin");
            assertPermission(user, "begin");
            assertPermission(admin, "begin");
            assertPermission(site, "begin");

            // @RequiresPermission(ACL.PERM_ADMIN)
            assertNoPermission(guest, "groups");
            assertNoPermission(user, "groups");
            assertPermission(admin, "groups");
            assertPermission(site, "groups");

            // @RequiresSiteAdmin
            assertNoPermission(guest, "addUsers");
            assertNoPermission(user, "addUsers");
            assertNoPermission(admin, "addUsers");
            assertPermission(site, "addUsers");

            assertTrue(ContainerManager.delete(c, null));
        }


        void assertPermission(User u, String action) throws Exception
        {
            try
            {
                ViewController.checkRequiredPermissions(u,  c, SecurityController.class, action);
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission");
            }
        }


        void assertNoPermission(User u, String action) throws Exception
        {
            try
            {
                ViewController.checkRequiredPermissions(u,  c, SecurityController.class, action);
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
