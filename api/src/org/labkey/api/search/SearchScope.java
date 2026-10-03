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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.TestContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "SearchScopeTestProject";
        private static final String EMAIL = "search_scope_test@test.com";

        private User _user;
        private Container _project;
        private Container _inherited;
        private Container _workbook;
        private Container _editable;
        private Container _restricted;

        @Before
        public void setUp() throws Exception
        {
            cleanup();
            User admin = TestContext.get().getUser();
            _user = SecurityManager.addUser(new ValidEmail(EMAIL), null).getUser();

            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, admin);
            MutableSecurityPolicy projectPolicy = new MutableSecurityPolicy(_project.getPolicy());
            projectPolicy.addRoleAssignment(_user, ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(projectPolicy, admin);

            // Subfolders created by an admin get an admin-only policy, so explicitly inherit
            _inherited = ContainerManager.createContainer(_project, "Inherited", admin);
            SecurityManager.setInheritPermissions(_inherited);
            _workbook = ContainerManager.createContainer(_inherited, null, "Workbook", null, WorkbookContainerType.NAME, admin);
            assertEquals(_project.getPolicy().getResourceId(), _workbook.getPolicy().getResourceId());

            _editable = ContainerManager.createContainer(_project, "Editable", admin);
            MutableSecurityPolicy editablePolicy = new MutableSecurityPolicy(_editable);
            editablePolicy.addRoleAssignment(_user, EditorRole.class);
            SecurityPolicyManager.savePolicyForTests(editablePolicy, admin);

            _restricted = ContainerManager.createContainer(_project, "Restricted", admin);
            SecurityPolicyManager.savePolicyForTests(new MutableSecurityPolicy(_restricted), admin);
        }

        @After
        public void cleanup() throws Exception
        {
            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (null != project)
                ContainerManager.deleteAll(project, TestContext.get().getUser());

            User user = UserManager.getUser(new ValidEmail(EMAIL));
            if (null != user)
                UserManager.deleteUser(user.getUserId());
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

            assertEquals(Set.of(_project.getId(), _inherited.getId(), _workbook.getId(), _editable.getId()), result.readable().keySet());
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
        }
    }
}
