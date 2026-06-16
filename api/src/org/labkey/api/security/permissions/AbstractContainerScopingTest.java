/*
 * Copyright (c) 2026 LabKey Corporation
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

import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for "container scoping" (a.k.a. broken-object-level-authorization / BOLA / IDOR) integration tests. These
 * tests verify that an action whose {@code @RequiresPermission} annotation is correct for the current container still
 * rejects an object resolved by a global id that belongs to a <em>different</em> container.
 *
 * <p>The repeated scaffolding lives here so each subclass keeps only its data fixture and the action under test:
 * <ul>
 *   <li>{@link #createContainer(String)} — make a throwaway child of the junit container (auto-cleaned).</li>
 *   <li>{@link #createUserInRole(Container, Class)} — make a user with a role assigned in <em>one</em> folder only
 *       (auto-cleaned). Use this to obtain a caller who is, say, admin in folder A but has no rights in folder B.</li>
 *   <li>{@link #get(ActionURL, User)} / {@link #post(ActionURL, User)} — dispatch an in-JVM request as a given user
 *       and inspect the {@link MockHttpServletResponse} status. Parameters travel on the {@link ActionURL}.</li>
 * </ul>
 *
 * <p>Note: WebDAV verbs (MOVE, PROPPATCH, ...) are not served through {@link ViewServlet} dispatch, so a WebDAV test
 * should still use this class for its container/user fixtures but drive the verb through {@code WebdavServlet} itself.
 */
public abstract class AbstractContainerScopingTest extends Assert
{
    private static final Map<String, Object> FORM_HEADERS = Map.of("Content-Type", "application/x-www-form-urlencoded");

    private final List<Container> _containers = new ArrayList<>();
    private final List<User> _users = new ArrayList<>();

    /** The site-admin user (from {@link TestContext}) that owns the test fixtures. */
    protected User getAdmin()
    {
        return TestContext.get().getUser();
    }

    /**
     * Create a throwaway child container of the junit container, named uniquely per test class, registered for
     * automatic cleanup. Callers pass a short local name (e.g. "A"/"B"/"Source"); the class name is prepended so two
     * test classes can both ask for "A" without colliding.
     */
    protected Container createContainer(String name)
    {
        Container junit = JunitUtil.getTestContainer();
        Container c = ContainerManager.ensureContainer(junit.getParsedPath().append(getClass().getSimpleName() + "-" + name, true), getAdmin());
        _containers.add(c);
        return c;
    }

    /**
     * Create a user that has {@code role} assigned in {@code scope} <em>only</em> (it has no rights in any other
     * container), registered for automatic cleanup. This is the canonical way to build a caller who is privileged in
     * one folder but not another. Do not use {@code LimitedUser} for this — that grants roles unconditionally in every
     * container.
     */
    protected User createUserInRole(Container scope, Class<? extends Role> role) throws Exception
    {
        String email = getClass().getSimpleName().toLowerCase() + "-" + _users.size() + "@containerscoping.test";
        User user = SecurityManager.addUser(new ValidEmail(email), null).getUser();
        _users.add(user);
        grantRole(user, scope, role);
        return user;
    }

    /**
     * Grant {@code role} to an existing {@code user} in {@code scope}, on top of any roles it already holds in that
     * container. Use this to build a caller with different roles in different folders (e.g. delete rights in a source
     * folder but only read access in a target folder).
     */
    protected void grantRole(User user, Container scope, Class<? extends Role> role) throws Exception
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(scope.getPolicy());
        policy.addRoleAssignment(user, role);
        SecurityPolicyManager.savePolicyForTests(policy, getAdmin());
    }

    /**
     * Dispatch a GET to the action addressed by {@code url} as {@code user}. Put request parameters on the URL. No
     * request-body Content-Type is sent: a GET carries no body, and an "application/json" Content-Type would make an
     * API action ({@code ReadOnlyApiAction}) try to parse the empty body as JSON and fail with 400 before its
     * {@code execute()} runs. With no Content-Type the form binds from the URL parameters, as a real GET would.
     */
    protected MockHttpServletResponse get(ActionURL url, User user) throws Exception
    {
        return ViewServlet.GET(url, user, Map.of());
    }

    /** Dispatch a POST to the action addressed by {@code url} as {@code user}. Put request parameters on the URL. */
    protected MockHttpServletResponse post(ActionURL url, User user) throws Exception
    {
        return ViewServlet.POST(url, user, FORM_HEADERS, null);
    }

    /** Assert that a dispatched response has the expected HTTP status code. */
    protected void assertStatus(int expected, HttpServletResponse response)
    {
        assertEquals("Unexpected HTTP status", expected, response.getStatus());
    }

    @After
    public void cleanupContainerScopingFixtures()
    {
        User admin = getAdmin();

        for (User user : _users)
        {
            try
            {
                UserManager.deleteUser(user.getUserId());
            }
            catch (Exception ignored)
            {
            }
        }
        _users.clear();

        for (Container c : _containers)
        {
            try
            {
                ContainerManager.deleteAll(c, admin);
            }
            catch (Exception ignored)
            {
            }
        }
        _containers.clear();
    }
}
