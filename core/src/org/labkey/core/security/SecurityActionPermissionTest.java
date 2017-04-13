package org.labkey.core.security;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AccountManagementPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SecurityActionPermissionTest extends Assert
{
    private static final String SITE_ADMIN_EMAIL = "sadmin@scjutc.com";
    private static final String APPLICATION_ADMIN_EMAIL = "aadmin@scjutc.com";
    private static final String PROJECT_ADMIN_EMAIL = "padmin@scjutc.com";
    private static final String FOLDER_ADMIN_EMAIL = "fadmin@scjutc.com";
    private static final String USER_EMAIL = "user@scjutc.com";
    private static final String GUEST_EMAIL = "guest@scjutc.com";
    private static final String[] ALL_EMAILS = {
            SITE_ADMIN_EMAIL, APPLICATION_ADMIN_EMAIL, PROJECT_ADMIN_EMAIL,
            FOLDER_ADMIN_EMAIL, USER_EMAIL, GUEST_EMAIL
    };

    private static Container _c;
    private static Map<String, User> _users;

    @BeforeClass
    public static void initialize()
    {
        cleanupUsers(ALL_EMAILS);
        Container junit = JunitUtil.getTestContainer();
        _c = ContainerManager.createContainer(junit, "SecurityActionPermissionTest-" + GUID.makeGUID());
        _users = createUsers(ALL_EMAILS);

        MutableSecurityPolicy policy = new MutableSecurityPolicy(_c, _c.getPolicy());
        policy.addRoleAssignment(_users.get(SITE_ADMIN_EMAIL), RoleManager.getRole(SiteAdminRole.class));
        policy.addRoleAssignment(_users.get(APPLICATION_ADMIN_EMAIL), RoleManager.getRole(ApplicationAdminRole.class));
        policy.addRoleAssignment(_users.get(PROJECT_ADMIN_EMAIL), RoleManager.getRole(ProjectAdminRole.class));
        policy.addRoleAssignment(_users.get(FOLDER_ADMIN_EMAIL), RoleManager.getRole(FolderAdminRole.class));
        policy.addRoleAssignment(_users.get(USER_EMAIL), RoleManager.getRole(EditorRole.class));
        policy.addRoleAssignment(_users.get(GUEST_EMAIL), RoleManager.getRole(ReaderRole.class));
        SecurityPolicyManager.savePolicy(policy);
    }

    @AfterClass
    public static void tearDown()
    {
        assertTrue(ContainerManager.delete(_c, TestContext.get().getUser()));
        cleanupUsers(ALL_EMAILS);
    }

    @Test
    public void testSecurityControllerActions()
    {
        User site = TestContext.get().getUser();
        assertTrue(site.isInSiteAdminGroup());
        User reader = _users.get(GUEST_EMAIL);
        User editor = _users.get(USER_EMAIL);
        User folderAdmin = _users.get(FOLDER_ADMIN_EMAIL);
        User projectAdmin = _users.get(PROJECT_ADMIN_EMAIL);
        User applicationAdmin = _users.get(APPLICATION_ADMIN_EMAIL);
        User siteAdmin = _users.get(SITE_ADMIN_EMAIL);

        SecurityController controller = new SecurityController();

        // @RequiresNoPermission, @RequiresLogin, @RequiresPermission(ReadPermission.class)
        PermissionCheckableAction[] readActions = {
                controller.new BeginAction(),
                controller.new ApiKeyAction(),
                controller.new CompleteUserReadAction()
        };
        for (PermissionCheckableAction action : readActions)
        {
            assertPermission(action, reader, editor, folderAdmin, projectAdmin, applicationAdmin, siteAdmin, site);
        }

        // @RequiresPermission(AdminPermission.class)
        PermissionCheckableAction[] adminActions = {
                controller.new PermissionsAction(),
                controller.new StandardDeleteGroupAction(),
                controller.new UpdateMembersAction(),
                controller.new GroupAction(),
                controller.new CompleteMemberAction(),
                controller.new CompleteUserAction(),
                controller.new GroupExportAction(),
                controller.new GroupPermissionAction(),
                controller.new UpdatePermissionsAction(),
                controller.new ShowRegistrationEmailAction(),
                controller.new GroupDiagramAction(),
                controller.new FolderAccessAction()
        };
        for (PermissionCheckableAction action : adminActions)
        {
            assertPermission(action, folderAdmin, projectAdmin, applicationAdmin, siteAdmin, site);
            assertNoPermission(action, reader, editor);
        }

        // @RequiresPermission(AccountManagementPermission.class)
        PermissionCheckableAction[] accountManagementActions = {
                controller.new AddUsersAction(),
                controller.new ShowResetEmailAction(),
                controller.new AdminResetPasswordAction()
        };
        for (PermissionCheckableAction action : accountManagementActions)
        {
            assertPermission(action, applicationAdmin, siteAdmin, site);
            assertNoPermission(action, reader, editor, folderAdmin, projectAdmin);
        }
    }

    @Test
    public void testSecurityApiActions()
    {
        User site = TestContext.get().getUser();
        assertTrue(site.isInSiteAdminGroup());
        User reader = _users.get(GUEST_EMAIL);
        User editor = _users.get(USER_EMAIL);
        User folderAdmin = _users.get(FOLDER_ADMIN_EMAIL);
        User projectAdmin = _users.get(PROJECT_ADMIN_EMAIL);
        User applicationAdmin = _users.get(APPLICATION_ADMIN_EMAIL);
        User siteAdmin = _users.get(SITE_ADMIN_EMAIL);

        // @RequiresNoPermission, @RequiresLogin, @RequiresPermission(ReadPermission.class)
        PermissionCheckableAction[] readActions = {
                new SecurityApiActions.EnsureLoginAction(),
                new SecurityApiActions.GetGroupPermsAction(),
                new SecurityApiActions.GetUserPermsAction(),
                new SecurityApiActions.GetGroupsForCurrentUserAction(),
                new SecurityApiActions.GetRolesAction(),
                new SecurityApiActions.GetSecurableResourcesAction()
        };
        for (PermissionCheckableAction action : readActions)
        {
            assertPermission(action, reader, editor, folderAdmin, projectAdmin, applicationAdmin, siteAdmin, site);
        }

        // @RequiresPermission(AdminPermission.class)
        PermissionCheckableAction[] adminActions = {
                new SecurityApiActions.GetPolicyAction(),
                new SecurityApiActions.SavePolicyAction(),
                new SecurityApiActions.DeletePolicyAction(),
                new SecurityApiActions.AddAssignmentAction(),
                new SecurityApiActions.RemoveAssignmentAction(),
                new SecurityApiActions.ClearAssignedRolesAction(),
                new SecurityApiActions.CreateGroupAction(),
                new SecurityApiActions.BulkUpdateGroupAction(),
                new SecurityApiActions.DeleteGroupAction(),
                new SecurityApiActions.RenameGroupAction(),
                new SecurityApiActions.AddGroupMemberAction(),
                new SecurityApiActions.RemoveGroupMemberAction(),
                new SecurityApiActions.CreateNewUserAction()
        };
        for (PermissionCheckableAction action : adminActions)
        {
            assertPermission(action, folderAdmin, projectAdmin, applicationAdmin, siteAdmin, site);
            assertNoPermission(action, reader, editor);
        }

        // @RequiresPermission(AccountManagementPermission.class)
        PermissionCheckableAction[] accountManagementActions = {
                new SecurityApiActions.DeleteUserAction(),
                new SecurityApiActions.AdminRotatePasswordAction(),
                new SecurityApiActions.ListProjectGroupsAction()
        };
        for (PermissionCheckableAction action : accountManagementActions)
        {
            assertPermission(action, applicationAdmin, siteAdmin, site);
            assertNoPermission(action, reader, editor, folderAdmin, projectAdmin);
        }
    }

    private static void cleanupUsers(String[] userEmails)
    {
        //clean up users in case this failed part way through
        try
        {
            for (String email : userEmails)
            {
                User oldUser = UserManager.getUser(new ValidEmail(email));
                if (null != oldUser)
                    UserManager.deleteUser(oldUser.getUserId());
            }
        }
        catch(Exception e)
        {}
    }

    private static Map<String, User> createUsers(String[] userEmails)
    {
        Map<String, User> users = new HashMap<>();

        try
        {
            for (String email : userEmails)
            {
                User user = SecurityManager.addUser(new ValidEmail(email), null).getUser();
                users.put(email, user);
            }
        }
        catch(Exception e)
        {}

        return users;
    }

    private void assertPermission(PermissionCheckableAction action, User... users)
    {
        for (User u : users)
        {
            action.setViewContext(makeContext(u));

            try
            {
                action.checkPermissions();
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission: user (" + u.getEmail() + "), action (" + action.getClass().getName() + ")");
            }
        }
    }

    private void assertNoPermission(PermissionCheckableAction action, User... users)
    {
        for (User u : users)
        {
            action.setViewContext(makeContext(u));

            try
            {
                action.checkPermissions();
                fail("Should not have permission: user (" + u.getEmail() + "), action (" + action.getClass().getName() + ")");
            }
            catch (UnauthorizedException x)
            {
                // expected
            }
        }
    }

    private ViewContext makeContext(User u)
    {
        HttpServletRequest w = new HttpServletRequestWrapper(TestContext.get().getRequest())
        {
            @Override
            public String getParameter(String name)
            {
                if (CSRFUtil.csrfName.equals(name))
                    return CSRFUtil.getExpectedToken(TestContext.get().getRequest(), null);
                return super.getParameter(name);
            }
        };

        ViewContext context = new ViewContext();
        context.setContainer(_c);
        context.setUser(u);
        context.setRequest(w);
        return context;
    }

    @Test
    public void validateAdministratorRolePermissionAssignment()
    {
        Set<Role> siteAdminRoleSet = RoleManager.roleSet(SiteAdminRole.class);
        Set<Role> appAdminRoleSet = RoleManager.roleSet(ApplicationAdminRole.class);
        Set<Role> otherAdminRoleSet = RoleManager.roleSet(ProjectAdminRole.class, FolderAdminRole.class);

        RoleManager.testPermissionsInAdminRoles(true, siteAdminRoleSet, AdminOperationsPermission.class);
        RoleManager.testPermissionsInAdminRoles(false, appAdminRoleSet, AdminOperationsPermission.class);
        RoleManager.testPermissionsInAdminRoles(false, otherAdminRoleSet, AdminOperationsPermission.class);

        RoleManager.testPermissionsInAdminRoles(true, siteAdminRoleSet, AccountManagementPermission.class);
        RoleManager.testPermissionsInAdminRoles(true, appAdminRoleSet, AccountManagementPermission.class);
        RoleManager.testPermissionsInAdminRoles(false, otherAdminRoleSet, AccountManagementPermission.class);
    }
}
