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
package org.labkey.api.security;

import com.google.common.collect.Streams;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.AbstractRootContainerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.io.Serializable;
import java.util.stream.Stream;

/**
 * Context that conveys the current permissions for this user. They may be logged in normally, or they may be
 * impersonating a specific user, group, or role, or some other specialized situation.
 */
public interface PermissionsContext extends Serializable
{
    /** @return whether the user is impersonating some user, group, or role, or working as their normal self */
    boolean isImpersonating();
    /** @return if non-null, the container to which the impersonation should be restricted */
    @Nullable Container getImpersonationProject();
    /** @return the user who is actually performing the operation, not the user that they might be impersonating */
    User getAdminUser();
    String getCacheKey();  // Caching permission-related state is very tricky with impersonation; context provides a cache key suffix that captures the current impersonation state
    /** @return the URL to which the user should be returned when impersonation is over */
    ActionURL getReturnUrl();
    PrincipalArray getGroups(User user);

    /**
     * @return The roles assigned to this user in the provided resource's policy as well as the root. The roles may be
     * modified and/or filtered by the impersonation context. Note: The returned stream may duplicate some roles; if a
     * distinct stream of roles is required, callers should invoke {@code distinct()} or collect to a set.
     */
    default Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        // Collect the site roles first. By default, they are applicable everywhere.
        PrincipalArray groups = getGroups(user);
        Container root = ContainerManager.getRoot();
        SecurityPolicy rootPolicy = root.getPolicy();
        Stream<Role> ret = rootPolicy.getRoles(groups)
            .filter(role -> {
                if (!role.isApplicable(rootPolicy, root))
                    throw new IllegalStateException("Root role " + role.getName() + " is not applicable");
                if (!(role instanceof AbstractRootContainerRole siteRole))
                    throw new IllegalStateException("Root roles should all be AbstractRootContainerRole");

                return siteRole.isApplicableOutsideRoot() || resource.equals(root);
            });

        if (!resource.equals(root))
        {
            // Add the roles assigned in the project or folder
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(resource);
            ret = Streams.concat(ret, policy.getRoles(groups));
        }

        return ret;
    }

    ImpersonationContextFactory getFactory();

    /** Responsible for adding menu items to allow the user to initiate, adjust, or stop impersonating, based on the current state */
    void addMenu(NavTree menu, Container c, User user, ActionURL currentURL);

    // restrict the permissions this user is allowed
    default Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
    {
        return perms;
    }
}
