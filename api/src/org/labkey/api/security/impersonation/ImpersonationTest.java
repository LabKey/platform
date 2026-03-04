package org.labkey.api.security.impersonation;

import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.UserManagementException;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * We have many impersonation permissions rules. This attempts to test them all.
 */
public class ImpersonationTest extends Assert
{
    private static final Logger LOG = LogHelper.getLogger(ImpersonationTest.class, "Progress of ImpersonationTest");

    @Test
    public void testPermissions() throws Exception
    {
        Container root = ContainerManager.getRoot();
        User testUser = TestContext.get().getUser();

        Container projectToDelete = null;
        User noPermUser = null;
        User readPermUser = null;
        Group readPermGroup = null;
        Group priviledgedGroup = null;
        try
        {
            // Need to test in a new project since all users get read permission in /Shared, which we don't want
            Container project = projectToDelete = ContainerManager.createContainer(root, "_testImpersonationPermissions", testUser);

            // Ensure there's at least one user without permissions in the project
            noPermUser = SecurityManager.addUser(new ValidEmail("test_no_permissions@test.com"), null).getUser();

            // Ensure there's a user assigned read permissions in the project
            readPermUser = SecurityManager.addUser(new ValidEmail("test_read_permissions@test.com"), null).getUser();
            MutableSecurityPolicy projectPolicy = new MutableSecurityPolicy(project.getPolicy());
            projectPolicy.addRoleAssignment(readPermUser, ReaderRole.class);
            SecurityPolicyManager.savePolicy(projectPolicy, testUser);

            // Ensure there's another project group
            readPermGroup = SecurityManager.createGroup(root, "Test_Read_Perm", null);

            // Ensure there's at least one site group assigned to a privileged role
            priviledgedGroup = SecurityManager.createGroup(root, "Test_Privileged", null);
            MutableSecurityPolicy policy = new MutableSecurityPolicy(root.getPolicy());
            Role privilegedRole = RoleManager.getRole(PlatformDeveloperRole.class);
            policy.addRoleAssignment(priviledgedGroup, privilegedRole);
            SecurityPolicyManager.savePolicy(policy, testUser);

            // Site-related counts
            int siteUserCount = UserManager.getUserIds().size(); // Includes inactive
            List<Group> siteGroups = SecurityManager.getGroups(null, false);
            int siteGroupCount = siteGroups.size() - 1; // Can't impersonate the guests group
            int privilegedSiteGroupCount = (int)siteGroups.stream().filter(UserPrincipal::hasPrivilegedRole).count();
            int siteRoleCount = RoleManager.getSiteRoles().size();

            // Project-related counts
            int projectUserCount = SecurityManager.getUsersWithPermissions(project, Set.of(ReadPermission.class)).size();
            assertTrue(projectUserCount < siteUserCount); // Should be at least one less because test_no_permissions@test.com doesn't have read
            int projectGroupCount = SecurityManager.getGroups(project, false).size();
            int allGroupCount = siteGroupCount + projectGroupCount;
            List<Role> projectRoles = RoleManager.getAllRoles().stream().filter(role -> role.isAssignable() && role.isApplicable(projectPolicy, project)).toList();
            int projectRoleCount = projectRoles.size();

            testImpersonator("SiteAdminRole", true, project, siteUserCount, siteGroupCount, siteRoleCount, siteUserCount, allGroupCount, projectRoleCount);
            testImpersonator("ImpersonatingTroubleshooterRole", true, project, siteUserCount, siteGroupCount, siteRoleCount, siteUserCount, allGroupCount, projectRoleCount);
            // Can't impersonate privileged roles, so SiteAdmin, ImpersonatingTroubleshooter, and PlatformDeveloper should not appear as valid site roles (siteRoleCount - 3)
            // Can't impersonate a group with a privileged role (siteGroupCount - privilegedSiteGroupCount)
            testImpersonator("ApplicationAdminRole", true, project, siteUserCount, siteGroupCount - privilegedSiteGroupCount, siteRoleCount - 3, siteUserCount, allGroupCount - privilegedSiteGroupCount, projectRoleCount);

            // Can impersonate any project group plus any site group where project admin has read permission. As a
            // brand-new user, Site:Users is the only site group where they have read and we created one project group,
            // so they can impersonate two groups at the project level.
            testImpersonator("ProjectAdminRole", false, project, 0, 0, 0, projectUserCount, 2, projectRoleCount);

            // Can't impersonate anything anywhere
            testImpersonator("FolderAdminRole", false, project, 0, 0, 0, 0, 0, 0);
            testImpersonator("EditorRole", false, project, 0, 0, 0, 0, 0, 0);
            testImpersonator("ReaderRole", false, project, 0, 0, 0, 0, 0, 0);
        }
        finally
        {
            if (priviledgedGroup != null)
                SecurityManager.deleteGroup(priviledgedGroup, testUser);
            if (readPermGroup != null)
                SecurityManager.deleteGroup(readPermGroup, testUser);
            if (readPermUser != null)
                UserManager.deleteUser(readPermUser.getUserId());
            if (noPermUser != null)
                UserManager.deleteUser(noPermUser.getUserId());
            if (projectToDelete != null)
                ContainerManager.delete(projectToDelete, testUser);
        }
    }

    private void testImpersonator(String roleName, boolean siteRole, Container project, int expectedSiteUsers, int expectedSiteGroups, int expectedSiteRoles, int expectedProjectUsers, int expectedProjectGroups, int expectedProjectRoles) throws InvalidEmailException, UserManagementException
    {
        LOG.info("Testing {}", roleName);
        Container root = ContainerManager.getRoot();
        User userToDelete = null;

        try
        {
            // Create user and assign role
            String email = roleName + "@test.com";
            User user = userToDelete = SecurityManager.addUser(new ValidEmail(email), null).getUser();
            Role role = RoleManager.getRole("org.labkey.api.security.roles." + roleName);
            assertNotNull("Could not resolve " + roleName, role);
            Container roleContainer = siteRole ? root : project;
            MutableSecurityPolicy rootPolicy = new MutableSecurityPolicy(roleContainer.getPolicy());
            rootPolicy.addRoleAssignment(user, role);
            SecurityPolicyManager.savePolicyForTests(rootPolicy, TestContext.get().getUser());

            // Test impersonating at the site level
            // TODO: Active vs. all?
            Collection<User> validUsers = UserImpersonationContextFactory.getValidImpersonationUsers(null, user);
            assertEquals(expectedSiteUsers, validUsers.size());
            assertTrue(user + " is able to impersonate themselves!", validUsers.stream().noneMatch(u -> u.equals(user)));
            Collection<Group> validSiteGroups = GroupImpersonationContextFactory.getValidImpersonationGroups(root, user);
            assertEquals(expectedSiteGroups, validSiteGroups.size());
            Collection<Role> validSiteRoles = RoleImpersonationContextFactory.filterImpersonationRoles(root, user, RoleManager.getAllRoles());
            assertEquals(expectedSiteRoles, validSiteRoles.size());

            // Test impersonating at the project level
            Collection<User> projectUsers = UserImpersonationContextFactory.getValidImpersonationUsers(project, user);
            assertEquals(expectedProjectUsers, projectUsers.size());
            Collection<Group> validProjectGroups = GroupImpersonationContextFactory.getValidImpersonationGroups(project, user);
            assertEquals(expectedProjectGroups, validProjectGroups.size());
            Collection<Role> validProjectRoles = RoleImpersonationContextFactory.filterImpersonationRoles(project, user, RoleManager.getAllRoles());
            assertEquals(expectedProjectRoles, validProjectRoles.size());
        }
        finally
        {
            if (userToDelete != null)
                UserManager.deleteUser(userToDelete.getUserId());
        }
    }
}
