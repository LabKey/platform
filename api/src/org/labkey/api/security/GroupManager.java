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
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;

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
    static int[] getAllGroupsForPrincipal(@Nullable UserPrincipal user)
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
    }
}
