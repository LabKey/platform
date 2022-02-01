/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.security.permissions;

import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.security.MethodsAllowed;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.security.roles.SubmitterRole;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractActionPermissionTest extends Assert
{
    private static final String SITE_ADMIN_EMAIL = "sadmin@actionpermission.test";
    private static final String APPLICATION_ADMIN_EMAIL = "aadmin@actionpermission.test";
    private static final String PROJECT_ADMIN_EMAIL = "padmin@actionpermission.test";
    private static final String FOLDER_ADMIN_EMAIL = "fadmin@actionpermission.test";
    private static final String EDITOR_EMAIL = "editor@actionpermission.test";
    private static final String AUTHOR_EMAIL = "author@actionpermission.test";
    private static final String READER_EMAIL = "reader@actionpermission.test";
    private static final String SUBMITTER_EMAIL = "submitter@actionpermission.test";
    private static final String TRUSTED_EDITOR_EMAIL = "trustededitor@actionpermission.test";
    private static final String TRUSTED_AUTHOR_EMAIL = "trustedauthor@actionpermission.test";
    protected static final String[] LKS_ROLE_EMAILS = {
            SITE_ADMIN_EMAIL, APPLICATION_ADMIN_EMAIL, PROJECT_ADMIN_EMAIL, FOLDER_ADMIN_EMAIL, EDITOR_EMAIL,
            AUTHOR_EMAIL, READER_EMAIL, SUBMITTER_EMAIL, TRUSTED_EDITOR_EMAIL, TRUSTED_AUTHOR_EMAIL
    };

    protected static Container _c;
    protected static Map<String, User> _users;
    private static final @Nullable Role TRUSTED_ANALYST_ROLE = RoleManager.getRole("org.labkey.api.security.roles.TrustedAnalystRole");

    @BeforeClass
    public static void initialize()
    {
        cleanupUsers(LKS_ROLE_EMAILS);
        Container junit = JunitUtil.getTestContainer();
        _c = ContainerManager.createContainer(junit, "ActionPermissionTest-" + GUID.makeGUID(), null, null, NormalContainerType.NAME, TestContext.get().getUser());
        _users = createUsersInLKSRoles(_c);
    }

    protected static Map<String, User> createUsersInLKSRoles(Container c)
    {
        Map<String, User> users = createUsers(LKS_ROLE_EMAILS);

        MutableSecurityPolicy policy = new MutableSecurityPolicy(c, c.getPolicy());
        policy.addRoleAssignment(users.get(FOLDER_ADMIN_EMAIL), RoleManager.getRole(FolderAdminRole.class));
        policy.addRoleAssignment(users.get(EDITOR_EMAIL), RoleManager.getRole(EditorRole.class));
        policy.addRoleAssignment(users.get(AUTHOR_EMAIL), RoleManager.getRole(AuthorRole.class));
        policy.addRoleAssignment(users.get(READER_EMAIL), RoleManager.getRole(ReaderRole.class));
        policy.addRoleAssignment(users.get(SUBMITTER_EMAIL), RoleManager.getRole(SubmitterRole.class));
        policy.addRoleAssignment(users.get(TRUSTED_EDITOR_EMAIL), RoleManager.getRole(EditorRole.class));
        policy.addRoleAssignment(users.get(TRUSTED_AUTHOR_EMAIL), RoleManager.getRole(AuthorRole.class));
        SecurityPolicyManager.savePolicy(policy);

        MutableSecurityPolicy projectPolicy = new MutableSecurityPolicy(c.getProject(), c.getProject().getPolicy());
        projectPolicy.addRoleAssignment(users.get(PROJECT_ADMIN_EMAIL), RoleManager.getRole(ProjectAdminRole.class));

        MutableSecurityPolicy rootPolicy = new MutableSecurityPolicy(ContainerManager.getRoot(), ContainerManager.getRoot().getPolicy());
        rootPolicy.addRoleAssignment(users.get(SITE_ADMIN_EMAIL), RoleManager.getRole(SiteAdminRole.class));
        rootPolicy.addRoleAssignment(users.get(APPLICATION_ADMIN_EMAIL), RoleManager.getRole(ApplicationAdminRole.class));
        if (null != TRUSTED_ANALYST_ROLE)
        {
            rootPolicy.addRoleAssignment(users.get(TRUSTED_EDITOR_EMAIL), TRUSTED_ANALYST_ROLE);
            rootPolicy.addRoleAssignment(users.get(TRUSTED_AUTHOR_EMAIL), TRUSTED_ANALYST_ROLE);
        }
        SecurityPolicyManager.savePolicy(rootPolicy);
        return users;
    }

    @AfterClass
    public static void tearDown()
    {
        assertTrue(ContainerManager.delete(_c, TestContext.get().getUser()));

        MutableSecurityPolicy rootPolicy = new MutableSecurityPolicy(ContainerManager.getRoot(), ContainerManager.getRoot().getPolicy());
        rootPolicy.removeRoleAssignment(_users.get(SITE_ADMIN_EMAIL), RoleManager.getRole(SiteAdminRole.class));
        rootPolicy.removeRoleAssignment(_users.get(APPLICATION_ADMIN_EMAIL), RoleManager.getRole(ApplicationAdminRole.class));
        if (null != TRUSTED_ANALYST_ROLE)
        {
            rootPolicy.removeRoleAssignment(_users.get(TRUSTED_EDITOR_EMAIL), TRUSTED_ANALYST_ROLE);
            rootPolicy.removeRoleAssignment(_users.get(TRUSTED_AUTHOR_EMAIL), TRUSTED_ANALYST_ROLE);
        }
        SecurityPolicyManager.savePolicy(rootPolicy);

        cleanupUsers(LKS_ROLE_EMAILS);
    }

    protected Container getContainer()
    {
        return _c;
    }

    @Test
    public abstract void testActionPermissions();

    protected static void cleanupUsers(String[] userEmails)
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
        catch (Exception e)
        {}
    }

    protected static Map<String, User> createUsers(String[] userEmails)
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
        catch (Exception e)
        {}

        return users;
    }

    public void assertForReadPermission(User user, PermissionCheckableAction... actions)
    {
        assertForReadPermission(user, false, actions);
    }

    public void assertForReadPermission(User user, boolean submitterAllowed, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(_c, action, user);

            assertPermission(_c, action,
                    _users.get(READER_EMAIL), _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                    _users.get(FOLDER_ADMIN_EMAIL),
                /*
                ProjectAdmin doesn't automatically get read permission in subfolder!
                _users.get(PROJECT_ADMIN_EMAIL),
                */
                    _users.get(APPLICATION_ADMIN_EMAIL), _users.get(SITE_ADMIN_EMAIL)
            );

            if (!submitterAllowed)
            {
                assertNoPermission(_c, action,
                        _users.get(SUBMITTER_EMAIL)
                );
            }
            else
            {
                assertPermission(_c, action, _users.get(SUBMITTER_EMAIL));
            }
        }
    }

    public void assertForInsertPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(_c, action, user);

            assertPermission(_c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(AUTHOR_EMAIL),
                    _users.get(EDITOR_EMAIL), _users.get(FOLDER_ADMIN_EMAIL),
                    /* _users.get(PROJECT_ADMIN_EMAIL), */
                    _users.get(APPLICATION_ADMIN_EMAIL),
                    _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(_c, action,
                    _users.get(READER_EMAIL)
            );
        }
    }

    public void assertForUpdateOrDeletePermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(_c, action, user);

            assertPermission(_c, action,
                    _users.get(EDITOR_EMAIL), _users.get(FOLDER_ADMIN_EMAIL),
                    /* _users.get(PROJECT_ADMIN_EMAIL), */
                    _users.get(APPLICATION_ADMIN_EMAIL),
                    _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(_c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL), _users.get(AUTHOR_EMAIL)
            );
        }
    }

    public void assertForAdminPermission(User user, PermissionCheckableAction... actions)
    {
        assertForAdminPermission(_c, user, actions);
    }

    // NOTE: Some actions vary required permissions based on GET vs POST
    // choose supported Http method for permission test
    private HttpUtil.Method supportedMethod(PermissionCheckableAction action, HttpUtil.Method m)
    {
        MethodsAllowed annotation = action.getClass().getAnnotation(MethodsAllowed.class);
        if (null == annotation)
            return m;
        HttpUtil.Method[] allowed = annotation.value();
        if (Arrays.stream(allowed).anyMatch(a -> m==a))
            return m;
        return m== HttpUtil.Method.POST ? HttpUtil.Method.GET : HttpUtil.Method.POST;
    }

    public void assertForAdminPermission(Container c, User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(c, action, user);

            assertPermission(c, action, _users.get(SITE_ADMIN_EMAIL));

            assertPermission(HttpUtil.Method.GET, c, action, _users.get(APPLICATION_ADMIN_EMAIL));

            if (c.isRoot())
                assertNoPermission(c, action, _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL));
            else if (c.isProject())
                assertPermission(c, action, _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL));
            else
                assertPermission(c, action, _users.get(FOLDER_ADMIN_EMAIL));

            assertNoPermission(c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                    _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL)
            );
        }
    }

    public void assertForAdminOperationsPermission(User user, PermissionCheckableAction... actions)
    {
        assertForAdminOperationsPermission(_c, user, actions);
    }

    public void assertForAdminOperationsPermission(Container c, User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(c, action, user);

            assertPermission(c, action,
                    _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                    _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                    _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL),
                    _users.get(APPLICATION_ADMIN_EMAIL)
            );
        }
    }

    public void assertForUserPermissions(User user, PermissionCheckableAction... actions)
    {
        assertForUserPermissions(_c, user, actions);
    }

    public void assertForUserPermissions(Container c, User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(c, action, user);

            assertPermission(c, action,
                    _users.get(APPLICATION_ADMIN_EMAIL), _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                    _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                    _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL)
            );
        }
    }

    public void assertForRequiresSiteAdmin(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(_c, action, user, _users.get(SITE_ADMIN_EMAIL));

            assertNoPermission(_c, action,
                    _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                    _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                    _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL),
                    _users.get(APPLICATION_ADMIN_EMAIL)
            );
        }
    }

    public void assertTrustedEditorPermission(PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(_c, action,
                    _users.get(FOLDER_ADMIN_EMAIL),
                    _users.get(APPLICATION_ADMIN_EMAIL),
                    _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(_c, action,
                    _users.get(SUBMITTER_EMAIL),
                    _users.get(READER_EMAIL),
                    _users.get(AUTHOR_EMAIL),
                    _users.get(EDITOR_EMAIL),
                    _users.get(TRUSTED_AUTHOR_EMAIL)
            );

            // If TrustedAnalystRole exists then TRUSTED_EDITOR_EMAIL user should have permission to execute this action; if not, it shouldn't
            if (null != TRUSTED_ANALYST_ROLE)
            {
                assertPermission(_c, action,
                        _users.get(TRUSTED_EDITOR_EMAIL)
                );
            }
            else
            {
                assertNoPermission(_c, action,
                        _users.get(TRUSTED_EDITOR_EMAIL)
                );
            }
        }
    }

    protected void assertPermission(Container c, PermissionCheckableAction action, User... users)
    {
        assertPermission(HttpUtil.Method.POST, c, action, users);
    }

    private void assertPermission(HttpUtil.Method defaultMethod, Container c, PermissionCheckableAction action, User... users)
    {
        HttpUtil.Method method = supportedMethod(action, defaultMethod);
        for (User u : users)
        {
            action.setViewContext(makeContext(u, c, method));

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

    protected void assertNoPermission(Container c, PermissionCheckableAction action, User... users)
    {
        for (User u : users)
        {
            action.setViewContext(makeContext(u, c));

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

    private ViewContext makeContext(User u, Container c)
    {
        // use POST by default, because MutableApiAction does not support GET and will throw BadRequestException
        return makeContext(u, c, HttpUtil.Method.POST);
    }

    private ViewContext makeContext(User u, Container c, final HttpUtil.Method method)
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

            @Override
            public String getMethod()
            {
                return method.name();
            }
        };

        ViewContext context = new ViewContext();
        context.setContainer(c);
        context.setUser(u);
        context.setRequest(w);
        return context;
    }
}
