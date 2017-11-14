/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Represents a security policy for a {@link org.labkey.api.security.SecurableResource}. You can get a security policy for a resource
 * using SecurityMananger.getPolicy(). Note that this class is immutable once constructed, so it may
 * be used by multiple threads at the same time. To make changes to an existing policy, construct a new
 * {@link MutableSecurityPolicy} passing the existing SecurityPolicy instance in the constructor.

 * User: Dave
 * Date: Apr 27, 2009
 */
public class SecurityPolicy implements HasPermission
{
    private static final Logger LOG = Logger.getLogger(SecurityPolicy.class);

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
     * Returns the roles the principal is playing, either due to
     * direct assignment, or due to membership in a group that is
     * assigned the role.
     * @param principal The principal
     * @return The roles this principal is playing
     */
    @NotNull
    public Set<Role> getEffectiveRoles(@NotNull UserPrincipal principal)
    {
        return getEffectiveRoles(principal, true);
    }

    @NotNull
    public Set<Role> getEffectiveRoles(@NotNull UserPrincipal principal, boolean includeContextualRoles)
    {
        Set<Role> roles = getRoles(principal.getGroups());
        roles.addAll(getAssignedRoles(principal));
        if (includeContextualRoles)
            roles.addAll(getContextualRoles(principal));

        return roles;
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal)
    {
        return getPermissions(principal, null);
    }

    @NotNull
    public List<String> getPermissionNames(@NotNull UserPrincipal principal)
    {
        Set<Class<? extends Permission>> perms = getPermissions(principal);
        List<String> names = new ArrayList<>(perms.size());
        for (Class<? extends Permission> perm : perms)
        {
            Permission permInst = RoleManager.getPermission(perm);
            if (null != permInst)
                names.add(permInst.getUniqueName());
        }
        return names;
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal, @Nullable Set<Role> contextualRoles)
    {
        // TODO: Should we be mutating the result of getContextualRoles()?  Some implementations would like to return unmodifiable collections...
        Set<Role> allContextualRoles = getContextualRoles(principal);
        if (contextualRoles != null)
            allContextualRoles.addAll(contextualRoles);

        return getPermissions(principal.getGroups(), allContextualRoles);
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
        return _assignments.size() == 0;
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        try
        {
            SecurityLogger.indent(logMsg);
            return hasPermission(principal, permission, null);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        return hasPermission(principal, permission, null);
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission, @Nullable Set<Role> contextualRoles)
    {
        try
        {
            SecurityLogger.indent(logMsg);
            return hasPermission(principal, permission, contextualRoles);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission, @Nullable Set<Role> contextualRoles)
    {
        testPermissionIsRegistered(permission);
        boolean ret = getPermissions(principal, contextualRoles).contains(permission);
        SecurityLogger.log("SecurityPolicy.hasPermission " + permission.getSimpleName(), principal, this, ret);
        return ret;
    }


    public boolean hasPermissions(@NotNull UserPrincipal principal, Class<? extends Permission>... permissions)
    {
        Set<Class<? extends Permission>> permsSet = new HashSet<>();
        permsSet.addAll(Arrays.asList(permissions));
        return hasPermissions(principal, permsSet);
    }


    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions)
    {
        return hasPermissions(principal, permissions, null);
    }


    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        permissions.forEach(this::testPermissionIsRegistered);
        boolean ret = getPermissions(principal, contextualRoles).containsAll(permissions);
        SecurityLogger.log("SecurityPolicy.hasPermissions " + permissions.toString(), principal, this, ret);
        return ret;
    }

    /**
     * Returns true if the principal has at least one of the required permissions.
     * @param principal The principal.
     * @param permissions The set of required permissions.
     * @param contextualRoles An optional set of contextual roles (or null)
     * @return True if the principal has at least one of the required permissions.
     */
    public boolean hasOneOf(@NotNull UserPrincipal principal, @NotNull Collection<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        permissions.forEach(this::testPermissionIsRegistered);
        boolean ret = false;
        Set<Class<? extends Permission>> grantedPerms = getPermissions(principal, contextualRoles);
        for (Class<? extends Permission> requiredPerm : permissions)
        {
            if (grantedPerms.contains(requiredPerm))
            {
                ret = true;
                break;
            }
        }
        SecurityLogger.log("SecurityPolicy.hasOneOf " + permissions.toString(), principal, this, ret);
        return ret;
    }

    // Throttle that limits warning logging to once per hour per permission class
    private static final Throttle<Class<? extends Permission>> NOT_REGISTERED_PERMISSION_THROTTLE = new Throttle<>("unregistered permissions", 100, CacheManager.HOUR, permission -> LOG.warn(permission + " is not registered!"));

    private void testPermissionIsRegistered(Class<? extends Permission> permission)
    {
        if (!RoleManager.isPermissionRegistered(permission))
        {
            NOT_REGISTERED_PERMISSION_THROTTLE.execute(permission);
        }
    }

    protected Set<Class<? extends Permission>> getPermissions(@NotNull int[] principals, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> perms = new HashSet<>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while (null != assignment && principalsIdx < principals.length)
        {
            if (assignment.getUserId() == principals[principalsIdx])
            {
                if (null != assignment.getRole())
                    perms.addAll(assignment.getRole().getPermissions());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if (assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        //apply contextual roles if any
        if (null != contextualRoles)
        {
            for (Role role : contextualRoles)
            {
                perms.addAll(role.getPermissions());
            }
        }

        return perms;
    }


    @NotNull
    protected Set<Role> getRoles(@NotNull int[] principals)
    {
        Set<Role> roles = new HashSet<>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while (null != assignment && principalsIdx < principals.length)
        {
            if (assignment.getUserId() == principals[principalsIdx])
            {
                if (null != assignment.getRole())
                    roles.add(assignment.getRole());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if (assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        return roles;
    }

    /**
     * This is purely for backwards compatibility with HTTP APIs--Do not use for new code!
     * @param principal the user/group
     * @return old-style bitmask for basic permissions
     */
    @Deprecated // Use getPermissions() instead.
    public int getPermsAsOldBitMask(UserPrincipal principal)
    {
        int perms = 0;
        Set<Class<? extends Permission>> permClasses = getPermissions(principal);
        if (permClasses.contains(ReadPermission.class))
            perms |= ACL.PERM_READ;
        if (permClasses.contains(InsertPermission.class))
            perms |= ACL.PERM_INSERT;
        if (permClasses.contains(UpdatePermission.class))
            perms |= ACL.PERM_UPDATE;
        if (permClasses.contains(DeletePermission.class))
            perms |= ACL.PERM_DELETE;
        if (permClasses.contains(AdminPermission.class))
            perms |= ACL.PERM_ADMIN;

        return perms;
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
     * Serializes this policy into a map suitable for returning via an API action
     * @return The serialized policy
     */
    @NotNull
    public Map<String, Object> toMap()
    {
        Map<String, Object> props = new HashMap<>();

        //modified
        props.put("modified", getModified());

        //resource id
        props.put("resourceId", getResourceId());

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
        props.put("assignments", assignments);
        return props;
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

    @NotNull
    protected Set<Role> getContextualRoles(UserPrincipal principal)
    {
        return principal.getContextualRoles(this);
    }

    public boolean hasNonInheritedPermission(@NotNull UserPrincipal principal, Class<? extends Permission> perm)
    {
        for (Role role : getRoles(new int[]{principal.getUserId()}))
        {
            if (role.getPermissions().contains(perm))
                return true;
        }

        return false;
    }
}
