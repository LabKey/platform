/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
package org.labkey.api.search;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.Container.LockState;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.security.ClonedUser;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PermissionsContext;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.TestContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Options for how widely or narrowly to search on the server, based on the number of containers to include.
 */
public enum SearchScope implements SafeToRenderEnum
{
    All(true,true) {
        @Override
        public Container getRoot(Container c)
        {
            return ContainerManager.getRoot();
        }
    },
    Project(true, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c.getProject();
        }
    },
    ProjectAndShared(true, true) {
        @Override
        public Container getRoot(Container c) { return Project.getRoot(c); }
    },
    FolderAndSubfolders(true, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    FolderAndSubfoldersAndShared(true, true) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    Folder(false, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    FolderAndShared(false, true) {
        @Override
        public Container getRoot(Container c)
        {
            return Folder.getRoot(c);
        }
    },
    FolderAndProject(false, false) {
        @Override
        public Container getRoot(Container c)
        {
            return Folder.getRoot(c);
        }

        @Override
        protected Stream<Container> getCandidateContainers(User user, Container searchRoot, Container currentContainer)
        {
            return Stream.of(searchRoot, Project.getRoot(searchRoot));
        }
    },
    FolderAndProjectAndShared(false, true) {
        @Override
        public Container getRoot(Container c)
        {
            return FolderAndProject.getRoot(c);
        }

        @Override
        protected Stream<Container> getCandidateContainers(User user, Container searchRoot, Container currentContainer)
        {
            return FolderAndProject.getCandidateContainers(user, searchRoot, currentContainer);
        }
    };

    private final boolean _recursive;
    private final boolean _includeShared;

    SearchScope(boolean recursive, boolean includeShared)
    {
        _recursive = recursive;
        _includeShared = includeShared;
    }

    public abstract Container getRoot(Container c);

    public boolean isRecursive()
    {
        return _recursive;
    }

    public boolean includeShared()
    {
        return _includeShared;
    }

    /**
     * @param readable Containers in scope where the user has read permission, keyed by container ID
     * @param containerIdsByPermission For each requested additional permission, the IDs of readable containers where the user also holds it
     */
    public record SearchableContainers(Map<String, Container> readable, Map<Class<? extends Permission>, Set<String>> containerIdsByPermission) {}

    public SearchableContainers getSearchableContainers(User user, Container currentContainer, Set<Class<? extends Permission>> additionalPermissions)
    {
        Stream<Container> candidates = getCandidateContainers(user, getRoot(currentContainer), currentContainer);

        if (includeShared())
            candidates = Stream.concat(candidates, Stream.of(ContainerManager.getSharedContainer()));

        return resolvePermissions(user, candidates, additionalPermissions);
    }

    /** Containers to consider for this scope, prior to any permission check */
    protected Stream<Container> getCandidateContainers(User user, Container searchRoot, Container currentContainer)
    {
        if (!isRecursive())
            return Stream.of(searchRoot);

        // Root plus all children, including workbooks & tabs
        return ContainerManager.getAllChildren(searchRoot).stream()
            .filter(c -> (c.isSearchable() || c.equals(currentContainer)) && (c.isContainerFor(ContainerType.DataType.search) || c.shouldDisplay(user)));
    }

    /**
     * Resolves the user's permissions once per distinct policy instead of once per container, since containers that
     * inherit their policy (e.g., workbooks) resolve identically to the ancestor that holds it.
     */
    static SearchableContainers resolvePermissions(User user, Stream<Container> candidates, Set<Class<? extends Permission>> additionalPermissions)
    {
        Map<Class<? extends Permission>, Long> bitByPermission = new HashMap<>();
        bitByPermission.put(ReadPermission.class, READ_BIT);
        additionalPermissions.forEach(permission -> bitByPermission.putIfAbsent(permission, 1L << bitByPermission.size()));

        if (bitByPermission.size() > Long.SIZE)
            throw new IllegalStateException("Too many additional permissions to track: " + bitByPermission.size());

        long allBits = bitByPermission.values().stream().reduce(0L, (a, b) -> a | b);

        Map<String, Long> grantedByPolicy = new HashMap<>();
        Map<String, Container> readable = new HashMap<>();
        Map<Class<? extends Permission>, Set<String>> containerIdsByPermission = additionalPermissions.stream()
            .collect(Collectors.toMap(permission -> permission, _ -> new HashSet<>()));

        candidates.forEach(c -> {
            long granted = grantedByPolicy.computeIfAbsent(getPolicyKey(c), _ -> getGrantedBits(c, user, bitByPermission, allBits));

            if ((granted & READ_BIT) != 0)
            {
                readable.put(c.getId(), c);

                containerIdsByPermission.forEach((permission, containerIds) -> {
                    if ((granted & bitByPermission.get(permission)) != 0)
                        containerIds.add(c.getId());
                });
            }
        });

        return new SearchableContainers(readable, containerIdsByPermission);
    }

    private static final long READ_BIT = 1L;

    // Stops consuming the stream once every tracked permission is found, since some users are granted 100+ permissions
    private static long getGrantedBits(Container c, User user, Map<Class<? extends Permission>, Long> bitByPermission, long allBits)
    {
        long granted = 0;
        Iterator<Class<? extends Permission>> permissions = SecurityManager.getPermissions(c, user, Set.of()).iterator();

        while (granted != allBits && permissions.hasNext())
        {
            Long bit = bitByPermission.get(permissions.next());
            if (null != bit)
                granted |= bit;
        }

        return granted;
    }

    // Permissions also depend on root-ness and project (locked/impersonation checks), and a project without a policy inherits root's
    private static String getPolicyKey(Container c)
    {
        Container project = c.getProject();
        return c.getPolicy().getResourceId() + "|" + (null == project ? "" : project.getId());
    }

    // Container deletion dominates; each test container takes several seconds to delete
    @TestTimeout(240)
    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "SearchScopeTestProject";
        private static final String OTHER_PROJECT_NAME = "SearchScopeTestOtherProject";
        private static final String ROOT_POLICY_PROJECT_NAME = "SearchScopeTestRootPolicyProject";
        private static final String EMAIL = "search_scope_test@test.com";
        private static final String GROUP_MEMBER_EMAIL = "search_scope_group_test@test.com";
        private static final Set<Class<? extends Permission>> EXTRA_PERMISSIONS = Set.of(InsertPermission.class, DeletePermission.class);

        private static User _admin;
        private static User _user;
        private static User _groupMember;
        private static Container _project;
        private static Container _inherited;
        private static Container _workbook;
        private static Container _editable;
        private static Container _restricted;
        private static Container _unsearchable;
        private static Container _otherProject;
        private static Container _rootPolicyProject;

        @BeforeClass
        public static void setUp() throws Exception
        {
            cleanup();
            _admin = TestContext.get().getUser();
            _user = SecurityManager.addUser(new ValidEmail(EMAIL), null).getUser();
            _groupMember = SecurityManager.addUser(new ValidEmail(GROUP_MEMBER_EMAIL), null).getUser();

            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, _admin);
            Group readers = SecurityManager.createGroup(_project, "Readers", _admin);
            SecurityManager.addMember(readers, _groupMember);
            MutableSecurityPolicy projectPolicy = new MutableSecurityPolicy(_project.getPolicy());
            projectPolicy.addRoleAssignment(_user, ReaderRole.class);
            projectPolicy.addRoleAssignment(readers, ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(projectPolicy, _admin);

            // Subfolders created by an admin get an admin-only policy, so explicitly inherit
            _inherited = createInheritingFolder(_project, "Inherited");
            _workbook = ContainerManager.createContainer(_inherited, null, "Workbook", null, WorkbookContainerType.NAME, _admin);
            assertEquals(_project.getPolicy().getResourceId(), _workbook.getPolicy().getResourceId());

            _editable = ContainerManager.createContainer(_project, "Editable", _admin);
            MutableSecurityPolicy editablePolicy = new MutableSecurityPolicy(_editable);
            editablePolicy.addRoleAssignment(_user, EditorRole.class);
            SecurityPolicyManager.savePolicyForTests(editablePolicy, _admin);

            _restricted = ContainerManager.createContainer(_project, "Restricted", _admin);
            SecurityPolicyManager.savePolicyForTests(new MutableSecurityPolicy(_restricted), _admin);

            _unsearchable = createInheritingFolder(_project, "Unsearchable");
            ContainerManager.updateSearchable(_unsearchable, false, _admin);
            _unsearchable = ContainerManager.getForId(_unsearchable.getId());

            _otherProject = ContainerManager.createContainer(ContainerManager.getRoot(), OTHER_PROJECT_NAME, _admin);
            MutableSecurityPolicy otherPolicy = new MutableSecurityPolicy(_otherProject.getPolicy());
            otherPolicy.addRoleAssignment(_user, ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(otherPolicy, _admin);

            // A project with no policy of its own resolves to root's policy, sharing its resource ID with root and every other such project
            _rootPolicyProject = ContainerManager.createContainer(ContainerManager.getRoot(), ROOT_POLICY_PROJECT_NAME, _admin);
            SecurityPolicyManager.deletePolicy(_rootPolicyProject);
            assertEquals(ContainerManager.getRoot().getPolicy().getResourceId(), _rootPolicyProject.getPolicy().getResourceId());
        }

        private static Container createInheritingFolder(Container parent, String name)
        {
            Container c = ContainerManager.createContainer(parent, name, _admin);
            SecurityManager.setInheritPermissions(c);
            return c;
        }

        @AfterClass
        public static void cleanup() throws Exception
        {
            for (String name : List.of(PROJECT_NAME, OTHER_PROJECT_NAME, ROOT_POLICY_PROJECT_NAME))
            {
                Container project = ContainerManager.getForPath(name);
                if (null != project)
                    ContainerManager.deleteAll(project, TestContext.get().getUser());
            }

            for (String email : List.of(EMAIL, GROUP_MEMBER_EMAIL))
            {
                User user = UserManager.getUser(new ValidEmail(email));
                if (null != user)
                    UserManager.deleteUser(user.getUserId());
            }
        }

        @Test
        public void testResolvedPermissionsMatchPerContainerChecks()
        {
            Set<Container> candidates = ContainerManager.getAllChildren(_project);
            SearchableContainers result = resolvePermissions(_user, candidates.stream(), Set.of(InsertPermission.class));
            Set<String> insertable = result.containerIdsByPermission().get(InsertPermission.class);

            for (Container c : candidates)
            {
                boolean canRead = c.hasPermission(_user, ReadPermission.class);
                assertEquals(c.getPath(), canRead, result.readable().containsKey(c.getId()));
                assertEquals(c.getPath(), canRead && c.hasPermission(_user, InsertPermission.class), insertable.contains(c.getId()));
            }

            assertEquals(Set.of(_project.getId(), _inherited.getId(), _workbook.getId(), _editable.getId(), _unsearchable.getId()), result.readable().keySet());
            assertEquals(Set.of(_editable.getId()), insertable);
        }

        @Test
        public void testScopes()
        {
            assertEquals(Set.of(_inherited.getId()), Folder.getSearchableContainers(_user, _inherited, Set.of()).readable().keySet());
            assertEquals(Set.of(), Folder.getSearchableContainers(_user, _restricted, Set.of()).readable().keySet());
            assertEquals(Set.of(_editable.getId(), _project.getId()), FolderAndProject.getSearchableContainers(_user, _editable, Set.of()).readable().keySet());

            Set<String> recursive = FolderAndSubfolders.getSearchableContainers(_user, _project, Set.of()).readable().keySet();
            assertTrue(recursive.containsAll(Set.of(_project.getId(), _inherited.getId(), _editable.getId())));
            assertFalse(recursive.contains(_restricted.getId()));
            assertFalse(recursive.contains(_unsearchable.getId()));

            // Searching from within an unsearchable folder still includes it
            assertTrue(FolderAndSubfolders.getSearchableContainers(_user, _unsearchable, Set.of()).readable().containsKey(_unsearchable.getId()));
        }

        @Test
        public void testEveryScopeMatchesPerContainerChecks()
        {
            assertMatchesPerContainerChecks(getUsers(), List.of(_project, _inherited, _workbook, _editable, _restricted, _unsearchable, _otherProject, _rootPolicyProject));
        }

        @Test
        public void testImpersonationRestrictedToProject()
        {
            User impersonator = getProjectImpersonator();
            assertEquals(Set.of(), Folder.getSearchableContainers(impersonator, _otherProject, Set.of()).readable().keySet());

            Set<String> all = All.getSearchableContainers(impersonator, _project, Set.of()).readable().keySet();
            assertTrue(all.contains(_project.getId()));
            assertFalse(all.contains(_otherProject.getId()));
            assertFalse(all.contains(_rootPolicyProject.getId()));
        }

        @Test
        public void testLockedProject()
        {
            ContainerManager.updateLockState(_otherProject, LockState.Inaccessible, () -> {});

            try
            {
                assertMatchesPerContainerChecks(getUsers(), List.of(_project, ContainerManager.getForId(_otherProject.getId())));
            }
            finally
            {
                ContainerManager.updateLockState(_otherProject, LockState.Unlocked, () -> {});
            }
        }

        private Map<String, User> getUsers()
        {
            Map<String, User> users = new LinkedHashMap<>();
            users.put("reader", _user);
            users.put("group member", _groupMember);
            users.put("site admin", _admin);
            users.put("guest", User.guest);
            users.put("project impersonator", getProjectImpersonator());
            return users;
        }

        // Admin impersonating Reader, restricted to _project
        private User getProjectImpersonator()
        {
            RoleImpersonationContextFactory factory = new RoleImpersonationContextFactory(_project, _admin, Set.of(RoleManager.getRole(ReaderRole.class)), Set.of(), null);
            return new ImpersonatingUser(_admin, factory.getImpersonationContext());
        }

        private static class ImpersonatingUser extends ClonedUser
        {
            ImpersonatingUser(User user, PermissionsContext ctx)
            {
                super(user, ctx);
            }
        }

        private void assertMatchesPerContainerChecks(Map<String, User> users, List<Container> currentContainers)
        {
            for (SearchScope scope : SearchScope.values())
            {
                for (Map.Entry<String, User> entry : users.entrySet())
                {
                    for (Container current : currentContainers)
                    {
                        // All is rooted at the root regardless of current container; one pass is enough
                        if (scope == All && !current.equals(currentContainers.getFirst()))
                            continue;

                        User user = entry.getValue();
                        String msg = scope + " as " + entry.getKey() + " from " + current.getPath();
                        Set<Container> expected = getExpectedReadable(scope, user, current);
                        SearchableContainers actual = scope.getSearchableContainers(user, current, EXTRA_PERMISSIONS);

                        assertEquals(msg, getIds(expected.stream()), actual.readable().keySet());

                        for (Class<? extends Permission> permission : EXTRA_PERMISSIONS)
                            assertEquals(msg + ", " + permission.getSimpleName(), getIds(expected.stream().filter(c -> c.hasPermission(user, permission))), actual.containerIdsByPermission().get(permission));
                    }
                }
            }
        }

        // The pre-optimization algorithm, which checks permissions on every container individually
        private static Set<Container> getExpectedReadable(SearchScope scope, User user, Container current)
        {
            Container root = scope.getRoot(current);
            Set<Container> expected = new HashSet<>();

            if (scope.isRecursive())
            {
                ContainerManager.getAllChildren(root, user).stream()
                    .filter(c -> (c.isSearchable() || c.equals(current)) && (c.isContainerFor(ContainerType.DataType.search) || c.shouldDisplay(user)))
                    .forEach(expected::add);
            }
            else
            {
                List<Container> roots = scope == FolderAndProject || scope == FolderAndProjectAndShared ? List.of(root, root.getProject()) : List.of(root);
                roots.stream()
                    .filter(c -> c.hasPermission(user, ReadPermission.class))
                    .forEach(expected::add);
            }

            Container shared = ContainerManager.getSharedContainer();
            if (scope.includeShared() && shared.hasPermission(user, ReadPermission.class))
                expected.add(shared);

            return expected;
        }

        private static Set<String> getIds(Stream<Container> containers)
        {
            return containers.map(Container::getId).collect(Collectors.toSet());
        }
    }
}
