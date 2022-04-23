/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ViewContext;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
* User: Dave
* Date: Apr 22, 2009
* Time: 2:09:39 PM
*/

/**
 * Global role manager
 */
public class RoleManager
{
    public static final Role siteAdminRole = new SiteAdminRole();
    public static final Set<Class<? extends Permission>> BasicPermissions = new HashSet<>();

    static
    {
        BasicPermissions.add(ReadPermission.class);
        BasicPermissions.add(InsertPermission.class);
        BasicPermissions.add(UpdatePermission.class);
        BasicPermissions.add(DeletePermission.class);
    }

    // A simple Comparator that orders Roles by approximate permission level (lowest to highest) and then alphabetically
    // within each level. This could be extended to consider other permissions.
    public static final Comparator<Role> ROLE_COMPARATOR = new Comparator<>()
    {
        @Override
        public int compare(Role o1, Role o2)
        {
            int levelCompare = getPermLevel(o1).compareTo(getPermLevel(o2));

            if (0 == levelCompare)
                return o1.getDisplayName().compareTo(o2.getDisplayName());

            return levelCompare;
        }

        private Integer getPermLevel(Role r)
        {
            Set<Class<? extends Permission>> set = r.getPermissions();
            return
                (set.contains(ReadPermission.class) ? 1 : 0) +
                (set.contains(InsertPermission.class) ? 2 : 0) +
                (set.contains(UpdatePermission.class) ? 4 : 0) +
                (set.contains(DeletePermission.class) ? 8 : 0) +
                (set.contains(AdminPermission.class) ? 16 : 0);
        }
    };

    //global map from role name to Role instance
    private static final Map<String, Role> _nameToRoleMap = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Role>, Role> _classToRoleMap = new ConcurrentHashMap<>();
    private static final List<Role> _roles = new CopyOnWriteArrayList<>();
    private static final List<AdminRoleListener> _adminRolelisteners = new CopyOnWriteArrayList<>();

    //register all core roles
    static
    {
        registerAdminRole(siteAdminRole);
        registerAdminRole(new ApplicationAdminRole());
        registerAdminRole(new ProjectAdminRole());
        registerAdminRole(new FolderAdminRole());
        registerRole(new EditorRole());
        registerRole(new AuthorRole());
        registerRole(new ReaderRole());
        registerRole(new RestrictedReaderRole());
        registerRole(new SubmitterRole());
        registerRole(new NoPermissionsRole());
        registerRole(new OwnerRole());
        registerRole(new TroubleshooterRole(), false);
        registerRole(new SeeUserAndGroupDetailsRole());
        registerRole(new CanSeeAuditLogRole());
        registerRole(new SharedViewEditorRole());
        registerRole(new EmailNonUsersRole(), false);
        registerRole(new SeeFilePathsRole(), false);
        registerRole(new CanUseSendMessageApi(), false);
        registerRole(new PlatformDeveloperRole(), false);
        registerRole(new ProjectCreatorRole());
    }

    public static void addAdminRoleListener(AdminRoleListener listener)
    {
        _adminRolelisteners.add(listener);
    }

    public static Role getRole(String name)
    {
        Role role = _nameToRoleMap.get(name);
        if (null == role)
            LogManager.getLogger(RoleManager.class).warn("Could not resolve the role " + name + "! The role may no longer exist, or may not yet be registered.");
        return role;
    }

    public static Role getRole(Class<? extends Role> clazz)
    {
        Role role = _classToRoleMap.get(clazz);
        if (null == role)
            LogManager.getLogger(RoleManager.class).warn("Could not resolve the role " + clazz.getName() + "! Did you forget to register the role with RoleManager.register()?");
        return role;
    }

    // Quick check for existence, without warnings
    public static boolean isPermissionRegistered(Class<? extends Permission> clazz)
    {
        return _classToRoleMap.containsKey(clazz);
    }

    public static Permission getPermission(Class<? extends Permission> clazz)
    {
        Permission perm = (Permission) _classToRoleMap.get(clazz);
        if (null == perm)
            LogManager.getLogger(RoleManager.class).warn("Could not resolve the permission " + clazz.getName() + "! If this is not part of a role, you must register it separately with RoleManager.register().");
        return perm;
    }

    public static Permission getPermission(String uniqueName)
    {
        Permission perm = (Permission) _nameToRoleMap.get(uniqueName);
        if (null == perm)
            LogManager.getLogger(RoleManager.class).warn("Could not resolve the permission " + uniqueName + "! The permission may no longer exist, or may not yet be registered.");
        return perm;
    }


    public static List<Role> getAllRoles()
    {
        return _roles;
    }

    public static Set<Role> getSiteRoles()
    {
        SecurityPolicy policy = ContainerManager.getRoot().getPolicy();
        return _roles.stream().
                filter(r -> r.isAssignable() && r.isApplicable(policy, ContainerManager.getRoot())).
                collect(Collectors.toSet());
    }

    private static void registerAdminRole(Role role)
    {
        registerRole(role);
        addAdminRoleListener((AdminRoleListener)role);
    }

    public static void registerRole(Role role)
    {
        registerRole(role, true);
    }

    public static void registerRole(Role role, boolean addPermissionsToAdminRoles)
    {
        // The isNull nameVerifier ensures that every role name is unique (registered only once)
        addToMaps(role, Objects::isNull);
        _roles.add(role);

        boolean addToAdminRoles = addPermissionsToAdminRoles && !(role instanceof SiteAdminRole
                || role instanceof ApplicationAdminRole || role instanceof ProjectAdminRole
                || role instanceof FolderAdminRole);

        //register all exposed permissions in the name and class maps
        for (Class<? extends Permission> permClass : role.getPermissions())
        {
            try
            {
                Permission perm = permClass.newInstance();
                registerPermission(perm, addToAdminRoles);
            }
            catch (InstantiationException | IllegalAccessException e)
            {
                LogManager.getLogger(RoleManager.class).error("Exception while instantiating permission " + permClass, e);
            }
        }

        // It's possible that a security policy refers to this role but it wasn't available when it was loaded.
        // Clear the cache so that it resolves correctly going forward.
        SecurityPolicyManager.removeAll();
    }

    private static void addToMaps(Role role, Predicate<Role> nameVerifier)
    {
        _classToRoleMap.put(role.getClass(), role);
        addToNameToRoleMap(role.getUniqueName(), role, nameVerifier);

        role.getSerializationAliases()
            .forEach(alias-> addToNameToRoleMap(alias, role, nameVerifier));
    }

    private static void addToNameToRoleMap(String name, Role role, Predicate<Role> nameVerifier)
    {
        Role previous = _nameToRoleMap.put(name, role);

        if (!nameVerifier.test(previous))
            throw new IllegalStateException("A role was already registered with name \"" + name + "\" by " + previous);
    }

    /**
     * This method is equivalent to registerPermission(perm, true)
     * @param perm The permission singleton instance
     */
    public static void registerPermission(Permission perm)
    {
        registerPermission(perm, true);
    }

    /**
     * Registers a new permission instance.
     * If your permission is already statically defined as part of a role
     * (i.e., it is added to a defined role before that role is registered)
     * you do not need to call this method.
     * This method should be called only when your module
     * defines a new permission class that is not statically defined as part of any role.
     * This method will ensure that the singleton instance of the permission class can
     * be retrieved using the getPermission() method.
     * @param perm The permission singleton instance
     * @param addToAdminRoles true to add this to all admin roles, or false to not.
     */
    public static void registerPermission(Permission perm, boolean addToAdminRoles)
    {
        // Permissions are registered multiple times (e.g., once for each role that includes them), so nameVerifier
        // simply ensures that any previously registered permission equals the current permission object
        addToMaps(perm, p->null == p || p.equals(perm));
        if (addToAdminRoles)
            addPermissionToAdminRoles(perm.getClass());
    }

    public static void addPermissionToAdminRoles(Class<? extends Permission> perm)
    {
        for (AdminRoleListener listener : _adminRolelisteners)
        {
            listener.permissionRegistered(perm);
        }
    }

    /**
     * Returns a set of Role objects based on the provided role classes
     * @param roleClasses The role classes
     * @return A set of Role objects corresponding to the role classes
     */
    @NotNull
    @SafeVarargs
    public static Set<Role> roleSet(Class<? extends Role>... roleClasses)
    {
        Set<Role> roles = new HashSet<>();
        if(null != roleClasses && roleClasses.length > 0)
        {
            for(Class<? extends Role> roleClass : roleClasses)
            {
                if (null != roleClass)
                    roles.add(getRole(roleClass));
            }
        }
        return roles;
    }

    @SafeVarargs
    public static void testPermissionsInAdminRoles(boolean shouldBePresent, Class<? extends Permission>... permissionsToTest)
    {
        Set<Role> adminRoleSet = RoleManager.roleSet(SiteAdminRole.class, ApplicationAdminRole.class, ProjectAdminRole.class, FolderAdminRole.class);
        testPermissionsInAdminRoles(shouldBePresent, adminRoleSet, permissionsToTest);
    }

    /**
     * Helper method for tests to verify whether the given permissions are present in the administrator roles.
     *
     * @param shouldBePresent True to verify that the permissions are all present in the admin roles; false to verify that they're not
     * @param permissionsToTest Permission classes to test
     */
    @SafeVarargs
    public static void testPermissionsInAdminRoles(boolean shouldBePresent, Set<Role> adminRoleSet, Class<? extends Permission>... permissionsToTest)
    {
        Collection<Class<? extends Permission>> permCollection = Arrays.asList(permissionsToTest);

        adminRoleSet.forEach(role->{
            Collection<Class<? extends Permission>> permissions = CollectionUtils.intersection(role.getPermissions(), permCollection);

            if (shouldBePresent)
                Assert.assertTrue(role.getName() + " should have been granted these permissions: " + permCollection, CollectionUtils.isEqualCollection(permCollection, permissions));
            else
                Assert.assertTrue(role.getName() + " should not have been granted these permissions: " + permissions, permissions.isEmpty());
        });
    }

    /**
     * Returns a set of contextual {@link Role}s created from
     * the view context and the given role factories.
     *
     * @param context The view context
     * @param roleFactories Queried for contextual roles.
     * @param contextualRoles Any additional contextual roles.
     * @return A set of Role objects corresponding to the role classes
     */
    @NotNull
    public static Set<Role> mergeContextualRoles(ViewContext context, Class<? extends HasContextualRoles>[] roleFactories, Set<Role> contextualRoles)
    {
        if (roleFactories == null || roleFactories.length == 0)
            return contextualRoles;

        Set<Role> allContextualRoles = new HashSet<>();
        if (contextualRoles != null)
            allContextualRoles.addAll(contextualRoles);
        
        for (Class<? extends HasContextualRoles> roleFactory : roleFactories)
        {
            HasContextualRoles factory;
            try
            {
                Constructor ctor = roleFactory.getConstructor();
                if (ctor == null)
                    throw new IllegalStateException("Failed to find no-arg constructor.");
                factory = (HasContextualRoles)ctor.newInstance();
            }
            catch (Exception e)
            {
                if (e instanceof RuntimeException)
                    throw (RuntimeException)e;
                throw new RuntimeException(e);
            }

            Set<Role> roles = factory.getContextualRoles(context);
            if (roles != null)
                allContextualRoles.addAll(roles);
        }

        return allContextualRoles;
    }
}