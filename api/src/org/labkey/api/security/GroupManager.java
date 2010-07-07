/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.*;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
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
    private static final String USER_CACHE_PREFIX = "Groups/AllGroups=";
    private static final String GROUP_CACHE_PREFIX = "Groups/Groups=";
    public static final String GROUP_AUDIT_EVENT = "GroupAuditEvent";

    private static final StringKeyCache<int[]> GROUP_ID_CACHE = CacheManager.getSharedCache();

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
        SecurityManager.addGroupListener(new GroupCacheListener());
        UserManager.addUserListener(new GroupUserListener());
    }

    /** this method returns the FLATTENED group list for this principal */
    static int[] getAllGroupsForPrincipal(UserPrincipal user)
    {
        int userId = user.getUserId();
        int[] groups = GROUP_ID_CACHE.get(USER_CACHE_PREFIX + userId);

        if (null == groups)
        {
            groups = computeAllGroups(user);
            GROUP_ID_CACHE.put(USER_CACHE_PREFIX + userId, groups);
        }

        return groups;
    }

    static int[] getAllGroupsForUser(User user)
    {
        return getAllGroupsForPrincipal(user);
    }


    private static void removeFromCache(UserPrincipal principal)
    {
        GROUP_ID_CACHE.remove(USER_CACHE_PREFIX + principal.getUserId());
        GROUP_ID_CACHE.remove(GROUP_CACHE_PREFIX + principal.getUserId());
    }


    /** this method returns the immediate group membership for this principal (non-recursive) */
    public static int[] getGroupsForPrincipal(int groupId) throws SQLException
    {
        int[] groups = GROUP_ID_CACHE.get(GROUP_CACHE_PREFIX + groupId);
        if (null == groups)
        {
            Integer[] groupsInt = Table.executeArray(_core.getSchema(), "SELECT GroupId FROM " + _core.getTableInfoMembers() + " WHERE UserId = ?", new Object[]{groupId}, Integer.class);
            groups = _toIntArray(groupsInt);
            GROUP_ID_CACHE.put(GROUP_CACHE_PREFIX + groupId, groups);
        }
        return groups;
    }


    private static int[] _toIntArray(Integer[] groupsInt)
    {
        int[] arr = new int[groupsInt.length];
        for (int i=0 ; i<groupsInt.length ; i++)
            arr[i] = groupsInt[i];
        return arr;
    }


    private static int[] _toIntArray(Set<Integer> groupsInt)
    {
        int[] arr = new int[groupsInt.size()];
        int i = 0;
        for (int group : groupsInt)
            arr[i++] = group;
        Arrays.sort(arr);
        return arr;
    }


    private static int[] computeAllGroups(UserPrincipal user)
    {
        int userId = user.getUserId();

        try
        {
            HashSet<Integer> groupSet = new HashSet<Integer>();
            LinkedList<Integer> recurse = new LinkedList<Integer>();
            recurse.add(Group.groupGuests);
            if (user.getUserId() != User.guest.getUserId())
                recurse.add(Group.groupUsers);
            recurse.add(userId);

            while (!recurse.isEmpty())
            {
                int id = recurse.removeFirst();
                groupSet.add(id);
                int[] groups = getGroupsForPrincipal(id);
                for (int g : groups)
                    if (!groupSet.contains(g))
                        recurse.addLast(g);
            }

            return _toIntArray(groupSet);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
            //_log.error("getAllGroupsFromDatabase() for " + userId,  e);
        }
    }


    /**
     * Create an initial group; warn if userId of created group does not match desired userId.
     */
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


    /**
     * Create a group in the Principals table
     */
    private static int createSystemGroup(int userId, String name, PrincipalType type)
            throws SQLException
    {
        // See if principal with the given name already exists
        Integer id = Table.executeSingleton(_core.getSchema(),
                "SELECT UserId FROM " + _core.getTableInfoPrincipals() + " WHERE Name = ?",
                new Object[]{name}, Integer.class);

        if (id != null)
            return id.intValue();

        Table.execute(_core.getSchema(), _insertGroupSql, new Object[]{userId, name, type.typeChar});

        return userId;
    }


    public static class GroupCacheListener implements SecurityManager.GroupListener
    {
        private void handleChange(Group group, UserPrincipal principal)
        {
            // very slight overkill
            removeFromCache(group);
            removeFromCache(principal);

            // invalidate all computed group lists (getAllGroups())
            if (principal instanceof Group)
                GROUP_ID_CACHE.removeUsingPrefix(USER_CACHE_PREFIX);
        }

        public void principalAddedToGroup(Group group, UserPrincipal user)
        {
            handleChange(group, user);
            addAuditEvent(group, user, "User: " + user.getName() + " was added as a member to Group: " + group.getName());
        }

        public void principalDeletedFromGroup(Group group, UserPrincipal user)
        {
            handleChange(group, user);
            addAuditEvent(group, user, "User: " + user.getName() + " was deleted from Group: " + group.getName());
        }

        private void addAuditEvent(Group group, UserPrincipal principal, String message)
        {
            User user = UserManager.getGuestUser();
            try {
                ViewContext context = HttpView.currentContext();
                if (context != null)
                {
                    user = context.getUser();
                }
            }
            catch (RuntimeException e){}

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


    public static class GroupUserListener implements UserManager.UserListener
    {
        public void userAddedToSite(User user)
        {
        }

        public void userDeletedFromSite(User user)
        {
            // Blow away groups immediately after user is deleted (otherwise this user's groups, and therefore permissions, will remain active
            // until the user choses to sign out.
            removeFromCache(user);
        }

        public void userAccountDisabled(User user)
        {
            removeFromCache(user);

        }

        public void userAccountEnabled(User user)
        {
            removeFromCache(user);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }
    }


    public static class TestCase extends junit.framework.TestCase
    {
        private User _user;

        private User getUser()
        {
            _user._groups = null;
            return _user;
        }

        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testGroupPermissions()
                throws Exception
        {
            TestContext context = TestContext.get();
            User loggedIn = context.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = context.getUser().cloneUser();

            Container c = JunitUtil.getTestContainer();
            ACL acl = new ACL();

            if (null != SecurityManager.getGroupId(c,"a",false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(c,"a"));
            if (null != SecurityManager.getGroupId(c,"b",false))
                SecurityManager.deleteGroup(SecurityManager.getGroupId(c,"b"));

            Group groupA = SecurityManager.createGroup(c, "a");
            Group groupB = SecurityManager.createGroup(c, "b");

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


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
