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
package org.labkey.api.security;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JsonUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * A version of a security policy that may be changed and saved to the database. Note that this class
 * is <b>not thread-safe</b> so do not share an instance of this between threads. When modifying
 * an existing policy, create a new instance of this class passing the existing SecurityPolicy instance
 * to the constructor. This will create a copy of the role assignments that you can then modify.
 * To save the policy, pass the instance of this class to {@link SecurityPolicyManager#savePolicy(MutableSecurityPolicy, User)}.
 */
public class MutableSecurityPolicy extends SecurityPolicy
{
    @Nullable
    final SecurableResource _resource;

    public MutableSecurityPolicy(@NotNull SecurityPolicy sourcePolicy)
    {
        super(sourcePolicy);
        if (_containerId.equals(_resourceId))
            _resource = ContainerManager.getForId(_containerId);
        else
            _resource = null;
    }

    public MutableSecurityPolicy(@NotNull SecurableResource resource)
    {
        super(resource);
        _resource = resource;
    }

    public MutableSecurityPolicy(@NotNull SecurableResource resource, @NotNull SecurityPolicy sourcePolicy)
    {
        super(resource, sourcePolicy);
        _resource = resource;
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Class<? extends Role> roleClass)
    {
        addRoleAssignment(principal, RoleManager.getRole(roleClass));
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        addRoleAssignment(principal, role, true);
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Class<? extends Role> roleClass, boolean validate)
    {
        addRoleAssignment(principal, RoleManager.getRole(roleClass), validate);
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role, boolean validate)
    {
        if (!role.isAssignable() && !(role instanceof NoPermissionsRole))
            throw new IllegalArgumentException("This role may not be assigned: " + role.getName());
        if (role.isExcludedPrincipal(principal))
            throw new IllegalArgumentException("The principal " + principal.getName() + " may not be assigned the role " + role.getName() + "!");
        if (null != _resource && (validate && !role.isApplicable(this, _resource)))
            throw new IllegalArgumentException("The role " + role.getName() + " is not applicable to this resource '" + _resource.getDebugName() + "'!");

        RoleAssignment assignment = new RoleAssignment(getResourceId(), principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        addAssignment(assignment);
    }

    protected void addAssignment(RoleAssignment assignment)
    {
        _assignments.add(assignment);
    }

    public void removeRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        RoleAssignment assignment = new RoleAssignment(getResourceId(), principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        removeAssignment(assignment);
    }

    public void removeAssignment(RoleAssignment assignment)
    {
        _assignments.remove(assignment);
    }

    /**
     * Creates and initializes a policy from the supplied JSONObject. Most often, this content will have been generated
     * by the SecurityPolicy.toJson() method, sent to the client, modified, and sent back as JSON. A runtime exception
     * will be thrown if the JSON does not contain correct/sufficient information.
     * @param json A JSONObject containing policy information
     * @param resource The resource
     * @return An initialized SecurityPolicy
     */
    @NotNull
    public static MutableSecurityPolicy fromJson(@NotNull JSONObject json, @NotNull SecurableResource resource)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(resource);

        // Use millisecond precision, if present. Good for optimistic concurrency check.
        Object modifiedMillis = json.opt("modifiedMillis");
        if (modifiedMillis instanceof Long)
        {
            policy._modified = new Date((Long)modifiedMillis);
        }
        else
        {
            Object modified = json.opt("modified");
            if (modified instanceof Date)
            {
                policy._modified = (Date) modified;
            }
            else
            {
                String modifiedStr = Objects.toString(modified, null);
                try
                {
                    policy._modified = (modifiedStr == null || modifiedStr.length() == 0) ? null : new Date(DateUtil.parseDateTime(modifiedStr));
                }
                catch (ConversionException x)
                {
                    /* */
                }
            }
        }

        Object assignmentsJson = json.opt("assignments");

        //ensure that if there is a property called 'assignments', that it is indeed a list
        if (assignmentsJson != null)
        {
            if (!(assignmentsJson instanceof JSONArray assignments))
                throw new IllegalArgumentException("The assignments property does not contain a JSON array!");

            for (JSONObject assignment : JsonUtil.toJSONObjectList(assignments))
            {
                //assignment JSON must have userId and role props
                if (!assignment.has("userId") || !assignment.has("role"))
                    throw new IllegalArgumentException("A map within the assignments list did not have a userId or role property!");

                //resolve the role and principal
                String roleName = assignment.getString("role");
                Role role = RoleManager.getRole(roleName);
                if (null == role)
                    throw new IllegalArgumentException("The role '" + roleName + "' is not a valid role name");

                Integer userId = (Integer) assignment.opt("userId");
                if (null == userId)
                    throw new IllegalArgumentException("Null user id passed in role assignment!");

                UserPrincipal principal = SecurityManager.getPrincipal(userId.intValue());
                if (null == principal)
                    continue; //silently ignore--this could happen if the principal was deleted in between the get and save

                policy.addRoleAssignment(principal, role);
            }
        }

        return policy;
    }

    /**
     * This will normalize the policy by performing a few clean-up actions. For instance, it will
     * remove all redundant NoPermissionsRole assignments.
     */
    public void normalize()
    {
        if (isEmpty())
            return;

        //remove all NoPermissionsRole assignments
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        _assignments.removeIf(ra -> noPermsRole.equals(ra.getRole()));

        //if we are now empty, we need to add a no perms role assignment for guests to keep the Policy from
        //getting ignored. Otherwise, the SecurityManager will return the parent policy and potentially
        //grant users access who did not have access before
        if (isEmpty())
            addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), noPermsRole);
    }

    /**
     * Clears assigned roles for the user principal
     * @param principal The principal
     */
    public void clearAssignedRoles(@NotNull UserPrincipal principal)
    {
        List<RoleAssignment> toRemove = new ArrayList<>();
        for(RoleAssignment assignment : _assignments)
        {
            if(assignment.getUserId() == principal.getUserId())
                toRemove.add(assignment);
        }
        toRemove.forEach(_assignments::remove);
    }
}