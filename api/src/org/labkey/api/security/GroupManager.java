/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.api.security;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: adam
 * Date: Jul 19, 2006
 * Time: 11:13:21 PM
 */
public class GroupManager
{
    private static final Logger _log = Logger.getLogger(GroupManager.class);
    private static final CoreSchema _core = CoreSchema.getInstance();
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    public static final String GROUP_AUDIT_EVENT = "GroupAuditEvent";

    public enum PrincipalType
    {
        USER('u'),
        GROUP('g'),
        ROLE('r'),
        MODULE('m');

        final char typeChar;
        PrincipalType(char type)
        {
            typeChar = type;
        }

        static PrincipalType forChar(char type)
        {
            switch (type)
            {
                case 'u': return USER;
                case 'g': return GROUP;
                case 'r': return ROLE;
                case 'm': return MODULE;
                default : return null;
            }
        }
    }

    static
    {
        SecurityManager.addGroupListener(new GroupListener());
    }

    // Returns the FLATTENED group list for this principal
    public static int[] getAllGroupsForPrincipal(@Nullable UserPrincipal user)
    {
        if (user == null)
            return EMPTY_INT_ARRAY;

        return GroupMembershipCache.getAllGroupsForPrincipal(user);
    }

    // Create an initial group; warn if userId of created group does not match desired userId.
    public static void bootstrapGroup(int userId, String name)
    {
        bootstrapGroup(userId, name, PrincipalType.GROUP);
    }

    public static void bootstrapGroup(int userId, String name, PrincipalType type)
    {
        int gotUserId;
        try
        {
            if ((gotUserId = createSystemGroup(userId, name, type)) != userId)
                _log.warn(name + " group exists but has an unexpected UserId (is " + gotUserId + ", should be " + userId + ")");
        }
        catch (SQLException e)
        {
            _log.error("Error setting up " + name + " group", e);
        }
    }


    private static final String _insertGroupSql;

    static
    {
        StringBuilder ins = new StringBuilder();
        ins.append("INSERT INTO ");
        ins.append(_core.getTableInfoPrincipals());
        ins.append(" (UserId, Name, Type) VALUES (?, ?, ?)");
        _core.getSqlDialect().overrideAutoIncrement(ins, _core.getTableInfoPrincipals());
        _insertGroupSql = ins.toString();
    }


    // Create a group in the Principals table
    private static int createSystemGroup(int userId, String name, PrincipalType type) throws SQLException
    {
        // See if principal with the given name already exists
        Integer id = Table.executeSingleton(_core.getSchema(),
                "SELECT UserId FROM " + _core.getTableInfoPrincipals() + " WHERE Name = ?",
                new Object[]{name}, Integer.class);

        if (id != null)
            return id;

        Table.execute(_core.getSchema(), _insertGroupSql, userId, name, type.typeChar);

        return userId;
    }


    // groups is a collection of one or more root groups to diagram
    public static String getGroupGraphSvg(Collection<Group> groups, User user)
    {
        StringBuilder sb = new StringBuilder("digraph groups\n{\n");
        HashSet<Group> groupSet = new HashSet<Group>();
        LinkedList<Group> recurse = new LinkedList<Group>();
        recurse.addAll(groups);
        groupSet.addAll(groups);

        while (!recurse.isEmpty())
        {
            Group group = recurse.removeFirst();
            Set<UserPrincipal> set = SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Groups);

            for (UserPrincipal principal : set)
            {
                Group g = (Group)principal;
                sb.append("\t").append(group.getUserId()).append("->").append(g.getUserId()).append(";\n");

                if (!groupSet.contains(g))
                {
                    recurse.addLast(g);
                    groupSet.add(g);
                }
            }
        }

        for (Group g : groupSet)
        {
            int userCount = SecurityManager.getGroupMembers(g, SecurityManager.GroupMemberType.Users).size();

            sb.append("\t").append(g.getUserId()).append(" [");
            appendDotAttribute(sb, false, "label", g.getName() + "\\n" + userCount + " users");

            if (g.isProjectGroup() || user.isAdministrator())
            {
                appendDotAttribute(sb, true, "URL", "javascript:window.parent.showPopupId(" + g.getUserId() + ")");
                appendDotAttribute(sb, true, "tooltip", "Click to manage the '" + g.getName() + "' " + (g.isProjectGroup() ? "project" : "site") + " group");
            }
            else
            {
                appendDotAttribute(sb, true, "URL", "javascript:void()");
                appendDotAttribute(sb, true, "tooltip", "You must be a site administrator to manage site groups");
            }

            if (!g.isProjectGroup())
                sb.append(", shape=box");

            sb.append("]\n");
        }

        sb.append("}");

        return sb.toString();
    }


    private static void appendDotAttribute(StringBuilder sb, boolean prependComma, String name, String value)
    {
        if (prependComma)
            sb.append(", ");

        sb.append(name).append("=\"").append(value).append("\"");
    }


    public static class GroupListener implements SecurityManager.GroupListener
    {
        public void principalAddedToGroup(Group group, UserPrincipal principal)
        {
            GroupMembershipCache.handleGroupChange(group, principal);
            addAuditEvent(group, principal, (principal.getType().equals("g") ? "Group: " : "User: ") + principal.getName() + " was added as a member to Group: " + group.getName());
        }

        public void principalDeletedFromGroup(Group group, UserPrincipal principal)
        {
            GroupMembershipCache.handleGroupChange(group, principal);
            addAuditEvent(group, principal, (principal.getType().equals("g") ? "Group: " : "User: ") + principal.getName() + " was deleted from Group: " + group.getName());
        }

        private void addAuditEvent(Group group, UserPrincipal principal, String message)
        {
            User user = UserManager.getGuestUser();
            try
            {
                ViewContext context = HttpView.currentContext();
                if (context != null)
                {
                    user = context.getUser();
                }
            }
            catch (RuntimeException e)
            {
                // ignore
            }

            AuditLogEvent event = new AuditLogEvent();

            event.setEventType(GROUP_AUDIT_EVENT);
            event.setCreatedBy(user);
            event.setIntKey1(principal.getUserId());
            event.setIntKey2(group.getUserId());

            Container c = ContainerManager.getForId(group.getContainer());
            if (c != null && c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            event.setContainerId(group.getContainer() != null ? group.getContainer() : ContainerManager.getRoot().getId());
            event.setComment(message);

            AuditLogService.get().addEvent(event);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }
    }


    public static class TestCase extends Assert
    {
        private User _user;

        private User getUser()
        {
            _user._groups = null;
            return _user;
        }

        @Test
        public void testGroupPermissions() throws Exception
        {
            TestContext context = TestContext.get();
            User loggedIn = context.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = context.getUser().cloneUser();

            Container project = JunitUtil.getTestContainer().getProject();

            if (null != SecurityManager.getGroupId(project, "a", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "a"));
            if (null != SecurityManager.getGroupId(project, "b", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "b"));

            Group groupA = SecurityManager.createGroup(project, "a");
            Group groupB = SecurityManager.createGroup(project, "b");

            ACL acl = new ACL();
            assertFalse(acl.hasPermission(getUser(), ACL.PERM_READ));
            acl.setPermission(groupA, ACL.PERM_READ);
            assertFalse(acl.hasPermission(getUser(), ACL.PERM_READ));
            SecurityManager.addMember(groupA, getUser());
            assertTrue(acl.hasPermission(getUser(), ACL.PERM_READ));

            assertFalse(acl.hasPermission(getUser(), ACL.PERM_UPDATE));
            acl.setPermission(groupB, ACL.PERM_UPDATE);
            assertFalse(acl.hasPermission(getUser(), ACL.PERM_UPDATE));
            SecurityManager.addMember(groupB, groupA);
            assertTrue(acl.hasPermission(getUser(), ACL.PERM_UPDATE));

            SecurityManager.deleteMember(groupB, groupA);
            assertFalse(acl.hasPermission(getUser(), ACL.PERM_UPDATE));
            assertTrue(acl.hasPermission(getUser(), ACL.PERM_READ));

            SecurityManager.deleteMember(groupA, getUser());
            assertFalse(acl.hasPermission(getUser(), ACL.PERM_READ));

            SecurityManager.deleteGroup(groupA);
            SecurityManager.deleteGroup(groupB);
        }

        @Test
        public void testCopyGroupToContainer() throws Exception
        {
            TestContext context = TestContext.get();
            User loggedIn = context.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = context.getUser().cloneUser();

            Container project = JunitUtil.getTestContainer().getProject();

            if (null != SecurityManager.getGroupId(project, "a", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "a"));
            if (null != SecurityManager.getGroupId(project, "b", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "b"));

            Group groupA = SecurityManager.createGroup(project, "a");
            Group groupB = SecurityManager.createGroup(project, "b");

            SecurityManager.addMember(groupA, groupB);
            SecurityManager.addMember(groupB, getUser());

            MutableSecurityPolicy op = new MutableSecurityPolicy(project);
            op.addRoleAssignment(groupA, ReaderRole.class);
            SecurityManager.savePolicy(op);

            String newContainerPath = "GroupManagerJunitTestProject";
            Container newProject = ContainerManager.getContainerService().getForPath("GroupManagerJunitTestProject");
            if(newProject != null)
                ContainerManager.delete(newProject, getUser());

            newProject = ContainerManager.createContainer(ContainerManager.getRoot(), newContainerPath);

            UserPrincipal newGroupA = GroupManager.copyGroupToContainer(groupA, newProject);
            UserPrincipal newGroupB = SecurityManager.getGroup(SecurityManager.getGroupId(newProject, "b"));

            MutableSecurityPolicy np = new MutableSecurityPolicy(newProject);
            np.addRoleAssignment(newGroupA, ReaderRole.class);
            SecurityManager.savePolicy(np);

            //should be copied from the previous project though groupB membership
            assertTrue(np.hasPermission(getUser(), ReadPermission.class));

            //groups were copied, so the originals should not have read permission
            assertFalse(np.hasPermission(groupA, ReadPermission.class));
            assertFalse(np.hasPermission(groupB, ReadPermission.class));

            int[] members = GroupManager.getAllGroupsForPrincipal(newGroupB);
            Arrays.sort(members);
            assertTrue(Arrays.binarySearch(members, newGroupA.getUserId()) > -1);

            members = GroupManager.getAllGroupsForPrincipal(getUser());
            Arrays.sort(members);
            assertTrue(Arrays.binarySearch(members, newGroupB.getUserId()) > -1);

            assertTrue(np.hasPermission(newGroupA, ReadPermission.class));
            assertTrue(np.hasPermission(newGroupB, ReadPermission.class));

            //cleanup
            SecurityManager.deleteGroup(groupA);
            SecurityManager.deleteGroup(groupB);
            SecurityManager.deleteGroup((Group)newGroupA);
            SecurityManager.deleteGroup((Group)newGroupB);
            ContainerManager.delete(newProject, getUser());
        }
    }

    public static Group copyGroupToContainer(Group g, Container c)
    {
        return copyGroupToContainer(g, c, new HashMap<UserPrincipal, UserPrincipal>(), 1);
    }

    private static Group copyGroupToContainer(Group g, Container c, HashMap<UserPrincipal, UserPrincipal> groupMap)
    {
        return copyGroupToContainer(g, c, groupMap, 1);
    }

    private static Group copyGroupToContainer(Group g, Container c, HashMap<UserPrincipal, UserPrincipal> groupMap, int suffix)
    {
        if (!g.isProjectGroup())
        {
            return g;
        }

        if (groupMap.get(g) != null)
        {
            return (Group)groupMap.get(g);  //it has already been copied
        }

        Set<UserPrincipal> members = SecurityManager.getGroupMembers(g, SecurityManager.GroupMemberType.Both);
        Set<UserPrincipal> translatedMembers = new LinkedHashSet<UserPrincipal>();

        for (UserPrincipal m : members)
        {
            if (groupMap.get(m) != null)
            {
                translatedMembers.add(groupMap.get(m));
            }
            else if (m instanceof Group && ((Group) m).isProjectGroup())
            {
                Group copiedGroup = GroupManager.copyGroupToContainer((Group)m, c, groupMap);
                groupMap.put(m, copiedGroup);
                translatedMembers.add(copiedGroup);
            }
            else
            {
                translatedMembers.add(m);
            }
        }

        String newGroupName = g.getName() + (suffix > 1 ? " " + suffix : "");

        //test whether a group of this name already exists in the container
        if (SecurityManager.getGroupId(c, newGroupName, null, false) != null)
        {
            Group existingGroup = SecurityManager.getGroup(SecurityManager.getGroupId(c, newGroupName));
            Set<UserPrincipal> existingMembers = SecurityManager.getGroupMembers(existingGroup, SecurityManager.GroupMemberType.Both);

            if(existingMembers.equals(translatedMembers))
            {
                return existingGroup; //groups are the same. nothing needed
            }
            else
            {
                //a different group of the same name already exists.  modify name and try again
                suffix++;
                Group newGroup = GroupManager.copyGroupToContainer(g, c, groupMap, suffix);
                groupMap.put(g, newGroup);
                return newGroup;
            }
        }

        Group newGroup = SecurityManager.createGroup(c, newGroupName);
        groupMap.put(g, newGroup);
        SecurityManager.addMembers(newGroup, translatedMembers);
        return newGroup;
    }

    public static HashMap<Group, Group> copyGroupsToContainer(Container source, Container target)
    {
        //copy all project groups to new project.  returns a map between old groups and new groups
        //note: site-groups are not copied, but the map will contain them anyway
        HashMap<Group, Group> groupMap = new HashMap<Group, Group>();
        for (Group g : SecurityManager.getGroups(source, false))
        {
            groupMap.put(g, GroupManager.copyGroupToContainer(g, target));
        }

        return groupMap;
    }
}
