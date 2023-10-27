/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.data.Container;
import org.labkey.api.security.impersonation.NotImpersonatingContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A cloned user that limits the permissions associated with that user to only the passed in roles.
 * WARNING: The supplied roles apply UNCONDITIONALLY, in all containers and resources. You must ensure
 * that the scope of use is constrained appropriately.
 */
public class LimitedUser extends ClonedUser
{
    @SafeVarargs
    public LimitedUser(User user, Class<? extends Role>... roleClasses)
    {
        this(user, Arrays.stream(roleClasses).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    private LimitedUser(User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        super(user, new NotImpersonatingContext()
        {
            private final Set<Role> _roles = getRoles(rolesToAdd);

            @Override
            public PrincipalArray getGroups(User user)
            {
                return PrincipalArray.getEmptyPrincipalArray(); // No groups!
            }

            @Override
            public Set<Role> getAssignedRoles(User user, SecurityPolicy policy)
            {
                return _roles;
            }
        });
    }

    @Deprecated // Call ElevatedUser!
    @SafeVarargs
    public static User getElevatedUser(Container container, User user, Pair<Class<? extends Permission>, Class<? extends Role>>... pairs)
    {
        return ElevatedUser.ensureContextualRoles(container, user, pairs);
    }

    @Deprecated // Call ElevatedUser!
    public static User getElevatedUser(Container container, User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        return ElevatedUser.getElevatedUser(user, rolesToAdd);
    }

    @Deprecated // Call ElevatedUser!
    public static User getCanSeeAuditLogUser(Container container, User user)
    {
        return ElevatedUser.ensureCanSeeAuditLogRole(container, user);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testLimitedUser()
        {
            User user = TestContext.get().getUser();

            testPermissions(new LimitedUser(user), 0, false, false, false, false, false);
            testPermissions(new LimitedUser(user, ReaderRole.class), 1, true, false, false, false, false);
            testPermissions(new LimitedUser(user, EditorRole.class), 1, true, true, true, false, false);
            testPermissions(new LimitedUser(user, FolderAdminRole.class), 1, true, true, true, true, true);
            testPermissions(new LimitedUser(new LimitedUser(user, FolderAdminRole.class), ReaderRole.class), 1, true, false, false, false, false);
        }

        @Test
        public void testElevatedUser()
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, new LimitedUser(user)), 1, false, false, false, false, true);
            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, new LimitedUser(user, ReaderRole.class)), 2, true, false, false, false, true);
            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, ElevatedUser.getElevatedUser(new LimitedUser(user, ReaderRole.class), EditorRole.class)), 3, true, true, true, false, true);

            int groupCount = (int)user.getGroups().stream().count();
            int roleCount = user.getAssignedRoles(c.getPolicy()).size();
            int siteRolesCount = user.getSiteRoles().size();
            User elevated = ElevatedUser.getElevatedUser(user);
            assertEquals(groupCount, elevated.getGroups().stream().count());
            assertEquals(roleCount, elevated.getAssignedRoles(c.getPolicy()).size());
            assertEquals(siteRolesCount, elevated.getSiteRoles().size());
        }

        private void testPermissions(User user, int roleCount, boolean hasRead, boolean hasInsert, boolean hasUpdate, boolean hasAdmin, boolean hasCanSeeAuditLog)
        {
            Container c = JunitUtil.getTestContainer();
            assertEquals(roleCount, user.getAssignedRoles(c.getPolicy()).size());
            assertTrue(user.getSiteRoles().isEmpty());
            assertFalse(user.hasSiteAdminPermission());
            assertEquals(0, user.getGroups().stream().count());
            assertFalse(user.hasPrivilegedRole());
            assertFalse(user.isPlatformDeveloper());
            assertFalse(user.isInGroup(Group.groupAdministrators));
            assertFalse(user.isImpersonated());
            assertNull(user.getImpersonatingUser());
            assertNull(user.getImpersonationProject());
            assertFalse(user.isGuest());

            assertEquals(hasRead, c.hasPermission(user, ReadPermission.class));
            assertEquals(hasInsert, c.hasPermission(user, InsertPermission.class));
            assertEquals(hasUpdate, c.hasPermission(user, UpdatePermission.class));
            assertEquals(hasAdmin, c.hasPermission(user, AdminPermission.class));
            assertEquals(hasCanSeeAuditLog, c.hasPermission(user, CanSeeAuditLogPermission.class));
        }
    }
}
