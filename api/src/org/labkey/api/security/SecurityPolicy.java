/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a security policy for a {@link org.labkey.api.security.SecurableResource}. You can get a security policy
 * for a resource using SecurityManager.getPolicy(). Note that this class is immutable once constructed, so it may be
 * used by multiple threads at the same time. To make changes to an existing policy, construct a new
 * {@link MutableSecurityPolicy} passing the existing SecurityPolicy instance in the constructor.
 * Note: intentionally does not implement HasPermission; use that interface for things that have a SecurityPolicy.
 */
public class SecurityPolicy
{
    private static final Logger LOG = LogHelper.getLogger(SecurityPolicy.class, "Unregistered permission warnings");

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
     * Returns only the roles directly assigned to this principal (not other roles the principal is playing due to group
     * memberships). Since a single principal can't be directly assigned the same role twice, the returned roles should
     * be distinct (no duplicates).
     * @param principal The principal
     * @return The roles this principal is directly assigned
     */
    @NotNull
    public Stream<Role> getAssignedRoles(@NotNull UserPrincipal principal)
    {
        return _assignments.stream()
            .filter(assignment -> assignment.getUserId() == principal.getUserId())
            .map(RoleAssignment::getRole);
    }

    /**
     * Return stream of permissions explicitly granted by this SecurityPolicy, will not inspect any contextual roles
     * (does not call UserPrincipal.getContextualRoles()). E.g. this will not reflect any permission granted due to
     * assignment of site-wide roles, and it will not reflect permission filtering by the impersonation context.
     * Note: The permissions returned are not distinct, i.e., they may be duplicated. This shouldn't matter for most
     * cases (e.g., existence checks); if a distinct set of permissions is needed, invoke distinct() or collect to a set.
     */
    @NotNull
    public Stream<Class<? extends Permission>> getOwnPermissions(@NotNull UserPrincipal principal)
    {
        return getRoles(principal.getGroups())
            .flatMap(role -> role.getPermissions().stream());
    }

    /**
     * Returns true if this policy is empty (i.e., no role assignments). This method is useful for distinguishing
     * between a policy that has been established for a SecurableResource and a cached "miss" (i.e., no explicit
     * policy defined).
     * @return True if this policy is empty
     */
    public boolean isEmpty()
    {
        return _assignments.isEmpty();
    }

    // Throttle that limits warning logging to once per hour per permission class
    private static final Throttle<Class<? extends Permission>> NOT_REGISTERED_PERMISSION_THROTTLE = new Throttle<>("unregistered permissions", 100, CacheManager.HOUR, permission -> LOG.warn("{} is not registered!", permission));

    static void testPermissionIsRegistered(Class<? extends Permission> permission)
    {
        if (!RoleManager.isPermissionRegistered(permission))
        {
            NOT_REGISTERED_PERMISSION_THROTTLE.execute(permission);
        }
    }

    /* Does not inspect any contextual roles, just the roles explicitly given by this SecurityPolicy */
    public boolean hasRole(UserPrincipal principal, Class<? extends Role> roleClass)
    {
        Role targetRole = RoleManager.getRole(roleClass);
        return getRoles(principal.getGroups())
            .anyMatch(r -> r.equals(targetRole));
    }

    /**
     * Does not return any contextual roles, just the roles explicitly granted by this SecurityPolicy.
     * Note: The returned stream may duplicate some roles; if a distinct stream of roles is required, callers should
     * invoke {@code distinct()} or collect to a set.
     **/
    @NotNull
    public Stream<Role> getRoles(PrincipalArray principalArray)
    {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                new RoleIterator(principalArray, getAssignments()),
                // Not DISTINCT. Not SIZED.
                // Likely that these characteristics aren't important, since we're not specifying parallel.
                Spliterator.IMMUTABLE | Spliterator.NONNULL
            ),
            // Iterator-based Spliterators don't work well with parallel. Plus the iterator is computationally simple.
            false
        );
    }

    private static class RoleIterator implements Iterator<Role>
    {
        private final Iterator<RoleAssignment> _assignmentIterator;
        private final List<Integer> _principals;

        private RoleAssignment _assignment;
        private int _principalsIdx = 0;
        private Role _nextRole = null;

        public RoleIterator(PrincipalArray principals, SortedSet<RoleAssignment> roleAssignments)
        {
            _assignmentIterator = roleAssignments.iterator();
            _principals = principals.getList();
            _assignment = _assignmentIterator.hasNext() ? _assignmentIterator.next() : null;
        }

        private @Nullable Role getNextRole()
        {
            while (null != _assignment && _principalsIdx < _principals.size())
            {
                int principalId = _principals.get(_principalsIdx);
                if (_assignment.getUserId() == principalId)
                {
                    Role role = _assignment.getRole();
                    _assignment = _assignmentIterator.hasNext() ? _assignmentIterator.next() : null;
                    if (null != role && role.getClass() != NoPermissionsRole.class)
                        return role;
                }
                else if (_assignment.getUserId() < principalId)
                    _assignment = _assignmentIterator.hasNext() ? _assignmentIterator.next() : null;
                else
                    ++_principalsIdx;
            }

            return null;
        }

        @Override
        public boolean hasNext()
        {
            _nextRole = getNextRole();
            return _nextRole != null;
        }

        @Override
        public Role next()
        {
            return _nextRole;
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
        return getRoles(new PrincipalArray(List.of(principal.getUserId())))
            .anyMatch(role -> role.getPermissions().contains(perm));
    }
}
