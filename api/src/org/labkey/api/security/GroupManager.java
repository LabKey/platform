/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.security.xml.GroupEnumType;
import org.labkey.security.xml.GroupRefType;
import org.labkey.security.xml.GroupRefsType;
import org.labkey.security.xml.GroupType;
import org.labkey.security.xml.UserRefType;
import org.labkey.security.xml.UserRefsType;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
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

    static
    {
        SecurityManager.addGroupListener(new GroupListener());
    }

    // Returns the expanded group list for this principal
    public static int[] getAllGroupsForPrincipal(@Nullable UserPrincipal user)
    {
        if (user == null)
            return EMPTY_INT_ARRAY;

        return GroupMembershipCache.getAllGroupMemberships(user);
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
        Integer id = new SqlSelector(_core.getSchema(), "SELECT UserId FROM " + _core.getTableInfoPrincipals() + " WHERE Name = ?", name).getObject(Integer.class);

        if (id != null)
            return id;

        new SqlExecutor(_core.getSchema()).execute(_insertGroupSql, userId, name, type.getTypeChar());

        return userId;
    }


    // groups is a collection of one or more root groups to diagram
    public static String getGroupGraphSvg(Collection<Group> groups, User user, boolean hideUnconnected)
    {
        StringBuilder sb = new StringBuilder("digraph groups\n{\n");
        HashSet<Group> groupSet = new HashSet<>();
        HashSet<Integer> connected = new HashSet<>();
        LinkedList<Group> recurse = new LinkedList<>();
        recurse.addAll(groups);
        groupSet.addAll(groups);

        while (!recurse.isEmpty())
        {
            Group group = recurse.removeFirst();
            Set<Group> set = SecurityManager.getGroupMembers(group, MemberType.GROUPS);

            for (Group g : set)
            {
                sb.append("\t").append(group.getUserId()).append("->").append(g.getUserId()).append(";\n");
                connected.add(group.getUserId());
                connected.add(g.getUserId());

                if (!groupSet.contains(g))
                {
                    recurse.addLast(g);
                    groupSet.add(g);
                }
            }
        }

        for (Group g : groupSet)
        {
            if (!hideUnconnected || connected.contains(g.getUserId()))
            {
                int userCount = SecurityManager.getGroupMembers(g, MemberType.ACTIVE_AND_INACTIVE_USERS).size();
                int groupCount = SecurityManager.getGroupMembers(g, MemberType.GROUPS).size();
                boolean isUserManager = user.hasRootPermission(UserManagementPermission.class);

                sb.append("\t").append(g.getUserId()).append(" [");
                appendDotAttribute(sb, false, "label", g.getName() + (userCount > 0 ? "\\n " + StringUtilsLabKey.pluralize(userCount, "user") : "") + (groupCount > 0 ? "\\n" + StringUtilsLabKey.pluralize(groupCount, "group") : ""));

                if (g.isProjectGroup() || (isUserManager && !g.isSystemGroup()) || user.isInSiteAdminGroup())
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

    public static void exportGroupMembers(Group group, List<Group> memberGroups, List<User> memberUsers, GroupType xmlGroupType)
    {
        if (group == null)
            return;

        xmlGroupType.setName(group.getName());
        xmlGroupType.setType(group.isProjectGroup() ? GroupEnumType.PROJECT : GroupEnumType.SITE);

        if (memberGroups != null && memberGroups.size() > 0)
        {
            GroupRefsType xmlGroups = xmlGroupType.addNewGroups();
            for (Group member : memberGroups)
            {
                GroupRefType xmlGroupRefType = xmlGroups.addNewGroup();
                xmlGroupRefType.setName(member.getName());
                xmlGroupRefType.setType(member.isProjectGroup() ? GroupEnumType.PROJECT : GroupEnumType.SITE);
            }
        }

        if (memberUsers != null && memberUsers.size() > 0)
        {
            UserRefsType  xmlUsers = xmlGroupType.addNewUsers();
            for (User member : memberUsers)
            {
                xmlUsers.addNewUser().setName(member.getEmail());
            }
        }
    }

    /**
     * Get a group by a given name of a certain type (either site or project) for the container.  This is generally only used for importing
     * groups from a serialization that includes the name and type but not the id.  In this context, it is expected that there will be a
     * container.
     * @param container container in which the group is being referenced
     * @param name the unique name for the group (including the package)
     * @param groupType the type of group (either site or project)
     * @return The group referenced or null if no such group exists
     */
    @Nullable
    public static Group getGroup(@NotNull Container container, String name, GroupEnumType.Enum groupType)
    {
        Container project = null;
        if (groupType != GroupEnumType.SITE)
            project = container.getProject();

        Integer groupId = SecurityManager.getGroupId(project, name, false);
        if (groupId != null)
            return SecurityManager.getGroup(groupId);
        else
            return null;
    }

    public static void importGroupMembers(@Nullable Group group, GroupType xmlGroupType, Logger log, Container container)
    {
        // don't do anything if the group has no members
        if (group != null && xmlGroupType != null && (xmlGroupType.getGroups() != null || xmlGroupType.getUsers() != null))
        {
            // remove existing group members, full replacement
            List<UserPrincipal> membersToDelete = new ArrayList<>();
            membersToDelete.addAll(SecurityManager.getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS));
            SecurityManager.deleteMembers(group, membersToDelete);

            if (xmlGroupType.getGroups() != null)
            {
                for (GroupRefType xmlGroupMember : xmlGroupType.getGroups().getGroupArray())
                {
                    Group memberGroup = getGroup(container, xmlGroupMember.getName(), xmlGroupMember.getType());
                    if (memberGroup != null)
                    {
                        try
                        {
                            SecurityManager.addMember(group, memberGroup);
                        }
                        catch (InvalidGroupMembershipException e)
                        {
                            // Best effort, but log any exceptions
                            log.warn(e);
                        }
                    }
                    else
                    {
                        log.warn("Invalid group name for group member: " + xmlGroupMember.getName());
                    }
                }
            }

            if (xmlGroupType.getUsers() != null)
            {
                for (UserRefType xmlMember : xmlGroupType.getUsers().getUserArray())
                {
                    try
                    {
                        User user = UserManager.getUser(new ValidEmail(xmlMember.getName()));
                        if (user != null)
                        {
                            try
                            {
                                SecurityManager.addMember(group, user);
                            }
                            catch (InvalidGroupMembershipException e)
                            {
                                // Best effort, but log any exceptions
                                log.warn(e);
                            }
                        }
                        else
                        {
                            log.warn("User does not exist for group member: " + xmlMember.getName());
                        }
                    }
                    catch(ValidEmail.InvalidEmailException e)
                    {
                        log.warn("Invalid email address for group member: " + xmlMember.getName());
                    }
                }
            }
        }
    }

    public static class GroupListener implements SecurityManager.GroupListener
    {
        public void principalAddedToGroup(Group group, UserPrincipal principal)
        {
            GroupMembershipCache.handleGroupChange(group, principal);
            addAuditEvent(group, principal, principal.getPrincipalType().getDescription() + ": " + principal.getName() + " was added as a member to Group: " + group.getName());
        }

        public void principalDeletedFromGroup(Group group, UserPrincipal principal)
        {
            GroupMembershipCache.handleGroupChange(group, principal);
            addAuditEvent(group, principal, principal.getPrincipalType().getDescription() + ": " + principal.getName() + " was deleted from Group: " + group.getName());
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

            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(group.getContainer(), message);
            event.setUser(principal.getUserId());
            event.setGroup(group.getUserId());

            Container c = ContainerManager.getForId(group.getContainer());
            if (c != null && c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            if (c == null)
                event.setContainer(ContainerManager.getRoot().getId());

            AuditLogService.get().addEvent(user, event);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }
    }


    public static class TestCase extends Assert
    {
        private User _user;
        private User _testUser;
        private Container _project;
        private Group _groupA;
        private Group _groupB;

        private User getUser()
        {
            _user._groups = null;
            return _user;
        }

        @Before
        public void setUp() throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
        {
            _project = JunitUtil.getTestContainer().getProject();
            assertNotNull(_project);

            _groupA = SecurityManager.createGroup(_project, "a");
            _groupB = SecurityManager.createGroup(_project, "b");

            TestContext context = TestContext.get();
            User loggedIn = context.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = loggedIn.cloneUser();

            ValidEmail email = new ValidEmail("junit_test_user@test.com");
            SecurityManager.NewUserStatus status = SecurityManager.addUser(email, null);
            _testUser = status.getUser();
            assertNotNull(_testUser);
        }

        @After
        public void cleanUp() throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
        {
            ValidEmail email = new ValidEmail("junit_test_user@test.com");
            User existingUser = UserManager.getUser(email);
            if (null != existingUser)
                UserManager.deleteUser(existingUser.getUserId());

            Container project = JunitUtil.getTestContainer().getProject();

            if (null != SecurityManager.getGroupId(project, "a", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "a"));
            if (null != SecurityManager.getGroupId(project, "b", false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(project, "b"));
        }

        @Test
        public void testGroupPermissions() throws Exception
        {
            // Each User's groups are fixed on first read, so we'll clone before the changes
            User user = _testUser.cloneUser();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(_project);
            assertFalse(policy.hasPermission(user, ReadPermission.class));
            policy.addRoleAssignment(_groupA, ReaderRole.class);
            assertTrue(policy.hasPermission(_groupA, ReadPermission.class));
            assertFalse(policy.hasPermission(user, ReadPermission.class));
            SecurityManager.addMember(_groupA, user);
            user = _testUser.cloneUser();
            assertTrue(policy.hasPermission(user, ReadPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ);

            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            policy.addRoleAssignment(_groupB, AuthorRole.class);
            assertFalse(policy.hasPermission(_groupB, UpdatePermission.class));
            assertTrue(policy.hasPermission(_groupB, InsertPermission.class));
            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            assertFalse(policy.hasPermission(user, InsertPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ);

            SecurityManager.addMember(_groupB, _groupA);
            user = _testUser.cloneUser();
            assertFalse(policy.hasPermission(_groupA, UpdatePermission.class));
            assertTrue(policy.hasPermission(_groupA, InsertPermission.class));
            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            assertTrue(policy.hasPermission(user, InsertPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ | ACL.PERM_INSERT);

            policy.addRoleAssignment(user, EditorRole.class);
            assertFalse(policy.hasPermission(_groupA, UpdatePermission.class));
            assertFalse(policy.hasPermission(_groupB, UpdatePermission.class));
            assertTrue(policy.hasPermission(user, UpdatePermission.class));
            assertTrue(policy.hasPermission(user, DeletePermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ | ACL.PERM_INSERT | ACL.PERM_UPDATE | ACL.PERM_DELETE);

            policy.clearAssignedRoles(user);
            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            assertFalse(policy.hasPermission(user, DeletePermission.class));
            assertTrue(policy.hasPermission(user, InsertPermission.class));
            assertTrue(policy.hasPermission(user, ReadPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ | ACL.PERM_INSERT);

            SecurityManager.deleteMember(_groupB, _groupA);
            user = _testUser.cloneUser();
            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            assertFalse(policy.hasPermission(user, InsertPermission.class));
            assertTrue(policy.hasPermission(user, ReadPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_READ);

            SecurityManager.deleteMember(_groupA, user);
            user = _testUser.cloneUser();
            assertFalse(policy.hasPermission(user, UpdatePermission.class));
            assertFalse(policy.hasPermission(user, InsertPermission.class));
            assertFalse(policy.hasPermission(user, ReadPermission.class));
            assertEquals(policy.getPermsAsOldBitMask(user), ACL.PERM_NONE);
        }

        @Test
        public void testCopyGroupToContainer() throws Exception
        {
            SecurityManager.addMember(_groupA, _groupB);
            SecurityManager.addMember(_groupB, getUser());

            MutableSecurityPolicy op = new MutableSecurityPolicy(_project);
            op.addRoleAssignment(_groupA, ReaderRole.class);
            SecurityPolicyManager.savePolicy(op);

            String newContainerPath = "GroupManagerJunitTestProject";
            Container newProject = ContainerManager.getContainerService().getForPath("GroupManagerJunitTestProject");
            if (newProject != null)
                assertTrue(ContainerManager.delete(newProject, getUser()));

            newProject = ContainerManager.createContainer(ContainerManager.getRoot(), newContainerPath);

            UserPrincipal newGroupA = GroupManager.copyGroupToContainer(_groupA, newProject);
            UserPrincipal newGroupB = SecurityManager.getGroup(SecurityManager.getGroupId(newProject, "b"));

            MutableSecurityPolicy np = new MutableSecurityPolicy(newProject);
            np.addRoleAssignment(newGroupA, ReaderRole.class);
            SecurityPolicyManager.savePolicy(np);

            //should be copied from the previous project though groupB membership
            assertTrue(np.hasPermission(getUser(), ReadPermission.class));

            //groups were copied, so the originals should not have read permission
            assertFalse(np.hasPermission(_groupA, ReadPermission.class));
            assertFalse(np.hasPermission(_groupB, ReadPermission.class));

            int[] members = GroupManager.getAllGroupsForPrincipal(newGroupB);
            Arrays.sort(members);
            assertTrue(Arrays.binarySearch(members, newGroupA.getUserId()) > -1);

            members = GroupManager.getAllGroupsForPrincipal(getUser());
            Arrays.sort(members);
            assertTrue(Arrays.binarySearch(members, newGroupB.getUserId()) > -1);

            assertTrue(np.hasPermission(newGroupA, ReadPermission.class));
            assertTrue(np.hasPermission(newGroupB, ReadPermission.class));

            //cleanup
            SecurityManager.deleteGroup((Group)newGroupA);
            SecurityManager.deleteGroup((Group)newGroupB);
            assertTrue(ContainerManager.delete(newProject, getUser()));
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

        Set<UserPrincipal> members = SecurityManager.getGroupMembers(g, MemberType.ALL_GROUPS_AND_USERS);
        Set<UserPrincipal> translatedMembers = new LinkedHashSet<>();

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
            Set<UserPrincipal> existingMembers = SecurityManager.getGroupMembers(existingGroup, MemberType.ALL_GROUPS_AND_USERS);

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
        List<String> errors = SecurityManager.addMembers(newGroup, translatedMembers);

        for (String error : errors)
            _log.warn(error);

        return newGroup;
    }

    public static HashMap<UserPrincipal, UserPrincipal> copyGroupsToContainer(Container source, Container target)
    {
        //copy all project groups to new project.  returns a map between old groups and new groups
        //note: site-groups are not copied, but the map will contain them anyway
        HashMap<UserPrincipal, UserPrincipal> groupMap = new HashMap<>();
        for (Group g : SecurityManager.getGroups(source, false))
        {
            groupMap.put(g, GroupManager.copyGroupToContainer(g, target, groupMap));
        }

        return groupMap;
    }
}
