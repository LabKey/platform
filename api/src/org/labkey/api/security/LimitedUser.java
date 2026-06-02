/*
 * Copyright (c) 2011-2026 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.SubmitterRole;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A cloned user that limits the permissions associated with that user to only the passed in roles.
 * WARNING: The supplied roles apply UNCONDITIONALLY, in all containers and resources. You must ensure
 * that the scope of use is constrained appropriately.
 */
public class LimitedUser extends ClonedUser
{
    // Must be a named class to allow Jackson deserialization (e.g., Evaluation Content loads folder archives via the pipeline using AdminUser)
    private static class LimitedUserPermissionContext extends NormalPermissionsContext
    {
        private final Set<Role> _roles;

        @SuppressWarnings("unused") // Needed for deserialization
        private LimitedUserPermissionContext()
        {
            _roles = null;
        }

        private LimitedUserPermissionContext(Set<Role> roles)
        {
            _roles = roles;
        }

        @Override
        public PrincipalArray getGroups(User user)
        {
            return PrincipalArray.getEmptyPrincipalArray(); // No groups!
        }

        @Override
        public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
        {
            return Objects.requireNonNull(_roles).stream();
        }
    }

    @SafeVarargs
    public LimitedUser(User user, Class<? extends Role>... roleClasses)
    {
        super(user, new LimitedUserPermissionContext(getRoles(roleClasses)));
    }

    @JsonCreator
    private LimitedUser(
        @JsonProperty("_name") String name,
        @JsonProperty("_userId") int userId,
        @JsonProperty("_displayName") String displayName,
        @JsonProperty("_firstName") String firstName,
        @JsonProperty("_lastName") String lastName,
        @JsonProperty("_active") boolean active,
        @JsonProperty("_lastLogin") Date lastLogin,
        @JsonProperty("_phone") String phone,
        @JsonProperty("_lastActivity") Date lastActivity,
        @JsonProperty("_impersonationContext") PermissionsContext ctx
    )
    {
        super(name, userId, displayName, firstName, lastName, active, lastLogin, phone, lastActivity, ctx);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testLimitedUser()
        {
            User user = TestContext.get().getUser();

            testPermissions(new LimitedUser(user), 0, 0, false, false, false, false, false);
            testPermissions(new LimitedUser(user, ReaderRole.class), 1, 0, true, false, false, false, false);
            testPermissions(new LimitedUser(user, EditorRole.class), 1, 0, true, true, true, false, false);
            testPermissions(new LimitedUser(user, FolderAdminRole.class), 1, 0, true, true, true, true, true);
            testPermissions(new LimitedUser(new LimitedUser(user, FolderAdminRole.class), ReaderRole.class), 1, 0, true, false, false, false, false);
        }

        @Test
        public void testElevatedUser()
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Container root = ContainerManager.getRoot();

            testPermissions(ElevatedUser.getElevatedUser(new LimitedUser(user, SubmitterRole.class, null), ReaderRole.class, null), 2, 0, true, true, false, false, false);
            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, new LimitedUser(user)), 1, 1, false, false, false, false, true);
            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, new LimitedUser(user, ReaderRole.class)), 2, 1, true, false, false, false, true);
            testPermissions(ElevatedUser.ensureCanSeeAuditLogRole(c, ElevatedUser.getElevatedUser(new LimitedUser(user, ReaderRole.class), EditorRole.class)), 3, 1, true, true, true, false, true);

            int groupCount = user.getGroups().size();
            int roleCount = (int)user.getAssignedRoles(c).count();
            int siteRolesCount = (int)user.getSiteRoles(root).count();
            User elevated = ElevatedUser.getElevatedUser(user);
            assertEquals(groupCount, elevated.getGroups().size());
            assertEquals(roleCount, (int)elevated.getAssignedRoles(c).count());
            assertEquals(siteRolesCount, (int)elevated.getSiteRoles(root).count());
        }

        private void testPermissions(User user, int roleCount, int siteRoleCount, boolean hasRead, boolean hasInsert, boolean hasUpdate, boolean hasAdmin, boolean hasCanSeeAuditLog)
        {
            Container c = JunitUtil.getTestContainer();
            assertEquals(roleCount, (int)user.getAssignedRoles(c).count());
            assertEquals(siteRoleCount, user.getSiteRoles(ContainerManager.getRoot()).count());
            assertFalse(user.hasSiteAdminPermission());
            assertEquals(0, user.getGroups().stream().count());
            assertFalse(user.hasPrivilegedRole());
            assertFalse(user.isPlatformDeveloper());
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

        @Test
        public void testSerialization() throws JsonProcessingException
        {
            // Serialize/deserialize admin service user
            User admin = User.getAdminServiceUser();
            ObjectMapper mapper = PipelineJob.createObjectMapper();
            String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(admin);
            User reconstitutedAdmin = mapper.readValue(serialized, User.class);
            assertEquals(admin, reconstitutedAdmin);
            assertEquals(
                admin.getAssignedRoles(ContainerManager.getRoot()).collect(Collectors.toSet()),
                reconstitutedAdmin.getAssignedRoles(ContainerManager.getRoot()).collect(Collectors.toSet())
            );

            // Serialize/deserialize search user
            User user = User.getSearchUser();
            String json = mapper.writeValueAsString(user);
            User limitedUser = mapper.readValue(json, LimitedUser.class);
            assertEquals(user, limitedUser);
            assertEquals(user.getEmail(), limitedUser.getEmail());
        }
    }
}
