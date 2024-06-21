/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import com.google.common.collect.Streams;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Context that describes the way in which a user is operating within the system. They may be logged in normally,
 * or they may be impersonating a specific user or a group, depending on the implementation.
 */
public interface ImpersonationContext extends Serializable
{
    /** @return whether the user is impersonating some user, group, or role, or working as their normal self */
    boolean isImpersonating();
    /** @return if non-null, the container to which the impersonation should be restricted */
    @Nullable Container getImpersonationProject();
    /** @return the user who is actually performing the operation, not the user that they might be impersonating */
    User getAdminUser();
    String getCacheKey();  // Caching permission-related state is very tricky with impersonation; context provides a cache key suffix that captures the current impersonation state
    /** @return the URL to which the user should be returned when impersonation is over */
    ActionURL getReturnURL();
    PrincipalArray getGroups(User user);

    /**
     * @return The roles assigned to this user in the provided resource's policy as well as the root. The roles may be
     * modified and/or filtered by the impersonation context.
     */
    default Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        Stream<Role> roles = getSiteRoles(user);
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(resource);
        return Streams.concat(roles, policy.getRoles(user.getGroups()).stream()).distinct();
    }

    /**
     * @return The roles assigned to this user in the root. The roles may be modified and/or filtered by the
     * impersonation context.
     */
    default Stream<Role> getSiteRoles(User user)
    {
        Container root = ContainerManager.getRoot();
        SecurityPolicy policy = root.getPolicy();
        Set<Role> roles = policy.getRoles(getGroups(user));
        roles.remove(RoleManager.getRole(NoPermissionsRole.class));
        for (Role role : roles)
            assert role.isApplicable(policy, root);

        return roles.stream();
    }

    ImpersonationContextFactory getFactory();

    /** Responsible for adding menu items to allow the user to initiate or stop impersonating, based on the current state */
    void addMenu(NavTree menu, Container c, User user, ActionURL currentURL);

    // restrict the permissions this user is allowed
    default Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
    {
        return perms;
    }
}
