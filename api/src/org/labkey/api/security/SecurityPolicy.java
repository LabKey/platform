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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Represents a security policy for a {@link org.labkey.api.security.SecurableResource}. You can get a security policy for a resource
 * using SecurityManager.getPolicy(). Note that this class is immutable once constructed, so it may
 * be used by multiple threads at the same time. To make changes to an existing policy, construct a new
 * {@link MutableSecurityPolicy} passing the existing SecurityPolicy instance in the constructor.
 * Note: intentionally does not implement HasPermission, use that interface for things that have a SecurityPolicy
 */
public class SecurityPolicy
{
    private static final Logger LOG = LogManager.getLogger(SecurityPolicy.class);

    protected final SortedSet<RoleAssignment> _assignments = new TreeSet<>();
    protected final String _resourceId;
    protected final String _containerId;
    protected final String _resourceClass;

    protected Date _modified; // Updated in MutableSecurityPolicy subclass

    public SecurityPolicy(@NotNull String resourceId, @NotNull String resourceClass, @NotNull String containerId, @NotNull Collection<RoleAssignment> assignments, @Nullable Date lastModified)
    {
        _resourceId = resourceId;
        _resourceClass = resourceClass;
        _containerId = containerId;

        for (RoleAssignment ra : assignments)
        {
            if (null == ra.getRole())
                continue;
            _assignments.add(ra);
        }

        _modified = lastModified;
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull Collection<RoleAssignment> assignments, @Nullable Date lastModified)
    {
        this(resource.getResourceId(), resource.getClass().getName(), resource.getResourceContainer().getId(), assignments, lastModified);
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull Collection<RoleAssignment> assignments)
    {
        this(resource, assignments, null);
    }

    public SecurityPolicy(@NotNull SecurableResource resource)
    {
        this(resource, Collections.emptyList());
    }

    /**
     * Creates a new policy for the given securable resource, using the other policy's role assignments
     * as a template.
     * @param resource The resource for this policy
     * @param otherPolicy Another policy to use as a template
     */
    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull SecurityPolicy otherPolicy)
    {
        this(resource, copyAssignments(otherPolicy, resource.getResourceId()));
    }

    /**
     * Creates a new policy for the same resource as the other policy, with the same role assignments
     * @param otherPolicy A template policy
     */
    public SecurityPolicy(@NotNull SecurityPolicy otherPolicy)
    {
        this(otherPolicy.getResourceId(), otherPolicy.getResourceClass(), otherPolicy.getContainerId(), copyAssignments(otherPolicy, otherPolicy.getResourceId()), otherPolicy.getModified());
    }

    private static List<RoleAssignment> copyAssignments(@NotNull SecurityPolicy otherPolicy, @NotNull String newResourceId)
    {
        List<RoleAssignment> assignments = new ArrayList<>();

        for (RoleAssignment assignment : otherPolicy.getAssignments())
        {
            RoleAssignment newAssignment = new RoleAssignment();
            newAssignment.setResourceId(newResourceId);
            newAssignment.setUserId(assignment.getUserId());
            newAssignment.setRole(assignment.getRole());
            assignments.add(newAssignment);
        }

        return assignments;
    }

    @NotNull
    public String getResourceId()
    {
        return _resourceId;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public String getResourceClass()
    {
        return _resourceClass;
    }

    @NotNull
    public SortedSet<RoleAssignment> getAssignments()
    {
        return Collections.unmodifiableSortedSet(_assignments);
    }

    /**
     * Returns only the roles directly assigned to this principal
     * (not other roles the principal is playing due to group
     * memberships).
     * @param principal The principal
     * @return The roles this principal is directly assigned
     */
    @NotNull
    public List<Role> getAssignedRoles(@NotNull UserPrincipal principal)
    {
        List<Role> roles = new ArrayList<>();
        for (RoleAssignment assignment : _assignments)
        {
            if (assignment.getUserId() == principal.getUserId())
                roles.add(assignment.getRole());
        }
        return roles;
    }


    /**
     * Return set of permissions explicitly granted by this SecurityPolicy, will not inspect any
     * contextual roles (does not call UserPrincipal.getContextualRoles()). E.g. this will not
     * reflect any permission granted due to assignment of site-wide roles, and it will not reflect
     * permission filtering by the impersonation context.
     */
    @NotNull
    public Set<Class<? extends Permission>> getOwnPermissions(@NotNull UserPrincipal principal)
    {
        return getOwnPermissions(principal.getGroups());
    }


    /**
     * Returns true if this policy is empty (i.e., no role assignments).
     * This method is useful for distinguishing between a policy that has
     * been established for a SecurableResource and a cached "miss"
     * (i.e., no explicit policy defined).
     * @return True if this policy is empty
     */
    public boolean isEmpty()
    {
        return _assignments.isEmpty();
    }

    // Throttle that limits warning logging to once per hour per permission class
    private static final Throttle<Class<? extends Permission>> NOT_REGISTERED_PERMISSION_THROTTLE = new Throttle<>("unregistered permissions", 100, CacheManager.HOUR, permission -> LOG.warn(permission + " is not registered!"));

    static void testPermissionIsRegistered(Class<? extends Permission> permission)
    {
        if (!RoleManager.isPermissionRegistered(permission))
        {
            NOT_REGISTERED_PERMISSION_THROTTLE.execute(permission);
        }
    }

    /* Does not inspect any contextual roles, just the roles explicitly given by this SecurityPolicy */
    @NotNull
    private Set<Class<? extends Permission>> getOwnPermissions(PrincipalArray principalArray)
    {
        Set<Class<? extends Permission>> permClasses = new HashSet<>();
        handleRoles(principalArray, role -> permClasses.addAll(role.getPermissions()));

        return permClasses;
    }

    /* Does not inspect any contextual roles, just the roles explicitly given by this SecurityPolicy */
    @NotNull
    public Set<Role> getRoles(PrincipalArray principalArray)
    {
        Set<Role> roles = new HashSet<>();
        handleRoles(principalArray, roles::add);

        return roles;
    }

    /* Does not inspect any contextual roles, just the roles explicitly given by this SecurityPolicy */
    public boolean hasRole(UserPrincipal principal, Class<? extends Role> roleClass)
    {
        return getRoles(principal.getGroups()).contains(RoleManager.getRole(roleClass));
    }

    private void handleRoles(PrincipalArray principalArray, Consumer<Role> consumer)
    {
        List<Integer> principals = principalArray.getList();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while (null != assignment && principalsIdx < principals.size())
        {
            int principalId = principals.get(principalsIdx);
            if (assignment.getUserId() == principalId)
            {
                Role role = assignment.getRole();
                if (null != role)
                    consumer.accept(role);

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if (assignment.getUserId() < principalId)
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }
    }

    @Nullable
    public Date getModified()
    {
        return _modified;
    }

    @NotNull
    public SecurityPolicyBean getBean()
    {
        return new SecurityPolicyBean(_resourceId, _resourceClass, ContainerManager.getForId(_containerId), _modified);
    }

    /**
     * Serializes this policy into a JSONObject suitable for returning via an API action
     * @return The serialized policy
     */
    @NotNull
    public JSONObject toJson()
    {
        JSONObject json = new JSONObject();

        //modified
        Date modified = getModified();
        json.put("modified", modified);  // Standard JSON format for dates is only accurate to the second

        //modifiedMillis
        if (null != modified)
            json.put("modifiedMillis", modified.getTime());  // Add a more accurate timestamp for optimistic concurrency purposes

        //resource id
        json.put("resourceId", getResourceId());

        //role assignments
        List<Map<String, Object>> assignments = new ArrayList<>();
        for (RoleAssignment assignment : getAssignments())
        {
            Map<String, Object> assignmentProps = new HashMap<>();
            try
            {
                assignmentProps.put("userId", assignment.getUserId());
                assignmentProps.put("role", assignment.getRole().getUniqueName());
            }
            catch (NullPointerException x)
            {

            }
            assignments.add(assignmentProps);
        }
        json.put("assignments", assignments);

        return json;
    }

    /**
     * Create a map of the roleAssignments with the key as the role name and the value as a map
     * between the principalType and the list of UserPrincipals of that type assigned the particular role
     * @return a map representing the list of users and groups assigned to each role in this policy
     */
    @NotNull
    public Map<String, Map<PrincipalType, List<UserPrincipal>>> getAssignmentsAsMap()
    {
        Map<String, Map<PrincipalType, List<UserPrincipal>>> assignmentsMap = new HashMap<>();
        for (RoleAssignment assignment : getAssignments())
        {
            // userId may be the id of either a group or a user.  Find out which.
            UserPrincipal principal = SecurityManager.getGroup(assignment.getUserId());
            if (principal == null)
            {
                principal = UserManager.getUser(assignment.getUserId());
            }
            if (principal != null)
            {
                if (!assignmentsMap.containsKey(assignment.getRole().getUniqueName()))
                    assignmentsMap.put(assignment.getRole().getUniqueName(), new HashMap<>());
                Map<PrincipalType, List<UserPrincipal>> assignees = assignmentsMap.get(assignment.getRole().getUniqueName());
                if (!assignees.containsKey(principal.getPrincipalType()))
                    assignees.put(principal.getPrincipalType(), new ArrayList<>());
                List<UserPrincipal> principalsList = assignees.get(principal.getPrincipalType());
                principalsList.add(principal);
            }
        }
        return assignmentsMap;
    }

    public boolean hasNonInheritedPermission(@NotNull UserPrincipal principal, Class<? extends Permission> perm)
    {
        for (Role role : getRoles(new PrincipalArray(List.of(principal.getUserId()))))
        {
            if (role.getPermissions().contains(perm))
                return true;
        }

        return false;
    }
}
