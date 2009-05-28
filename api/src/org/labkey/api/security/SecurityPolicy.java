/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.*;
import org.labkey.api.util.DateUtil;
import org.json.JSONArray;

import java.util.*;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 10:58:34 AM
*/
public class SecurityPolicy
{
    SortedSet<RoleAssignment> _assignments = new TreeSet<RoleAssignment>();
    private SecurableResource _resource;
    private Date _modified;

    public SecurityPolicy(@NotNull SecurableResource resource)
    {
        _resource = resource;
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull RoleAssignment[] assignments)
    {
        _assignments.addAll(Arrays.asList(assignments));
        _resource = resource;
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull RoleAssignment[] assignments, Date lastModified)
    {
        _assignments.addAll(Arrays.asList(assignments));
        _resource = resource;
        _modified = lastModified;
    }

    /**
     * Creates a new policy for the given securable resource, using the other policy's role assignments
     * as a template.
     * @param resource The resource for this policy
     * @param otherPolicy Another policy to use as a template
     */
    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull SecurityPolicy otherPolicy)
    {
        super();
        _resource = resource;
        for(RoleAssignment assignment : otherPolicy.getAssignments())
        {
            RoleAssignment newAssignment = new RoleAssignment();
            newAssignment.setResourceId(resource.getResourceId());
            newAssignment.setUserId(assignment.getUserId());
            newAssignment.setRole(assignment.getRole());
            _assignments.add(newAssignment);
        }
    }

    @NotNull
    public SecurableResource getResource()
    {
        return _resource;
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Class<? extends Role> roleClass)
    {
        addRoleAssignment(principal, RoleManager.getRole(roleClass));
    }
    
    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        if(null == principal || null == role)
            return;

        if(role.getExcludedPrincipals().contains(principal))
            throw new IllegalArgumentException("The principal " + principal.getName() + " may not be assigned the role " + role.getName() + "!");
        
        RoleAssignment assignment = new RoleAssignment(_resource, principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        addAssignment(assignment);
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
        List<Role> roles = new ArrayList<Role>();
        for(RoleAssignment assignment : _assignments)
        {
            if(assignment.getUserId() == principal.getUserId())
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
        Set<Role> roles = getRoles(GroupManager.getAllGroupsForPrincipal(principal));
        roles.addAll(getContextualRoles(principal));
        return roles;
    }

    /**
     * Clears assigned roles for the user principal
     * @param principal The principal
     */
    public void clearAssignedRoles(@NotNull UserPrincipal principal)
    {
        List<RoleAssignment> toRemove = new ArrayList<RoleAssignment>();
        for(RoleAssignment assignment : _assignments)
        {
            if(assignment.getUserId() == principal.getUserId())
                toRemove.add(assignment);
        }
        _assignments.removeAll(toRemove);
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal)
    {
        return getPermissions(principal, null);
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal, @Nullable Set<Role> contextualRoles)
    {
        Set<Role> allContextualRoles = getContextualRoles(principal);
        if (contextualRoles != null)
            allContextualRoles.addAll(contextualRoles);

        return getPermissions(GroupManager.getAllGroupsForPrincipal(principal), allContextualRoles);
    }

    /**
     * Returns true if this policy is empty (i.e., no role assignments).
     * This method is useful for distiguishing between a policy that has
     * been established for a SecurableResource and a cached "miss"
     * (i.e., no explicit policy defined).
     * @return True if this policy is empty
     */
    public boolean isEmpty()
    {
        return _assignments.size() == 0;
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        return hasPermission(principal, permission, null);
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission, @Nullable Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).contains(permission);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, Class<? extends Permission>... permissions)
    {
        Set<Class<? extends Permission>> permsSet = new HashSet<Class<? extends Permission>>();
        permsSet.addAll(Arrays.asList(permissions));
        return hasPermissions(principal, permsSet);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions)
    {
        return hasPermissions(principal, permissions, null);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).containsAll(permissions);
    }

    /**
     * Returns true if the principal has at least one of the required permissions.
     * @param principal The principal.
     * @param permissions The set of required permissions.
     * @param contextualRoles An optional set of contextual roles (or null)
     * @return True if the principal has at least one of the required permissions.
     */
    public boolean hasOneOf(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> grantedPerms = getPermissions(principal, contextualRoles);
        for(Class<? extends Permission> requiredPerm : permissions)
        {
            if(grantedPerms.contains(requiredPerm))
                return true;
        }
        return false;
    }

    protected Set<Class<? extends Permission>> getPermissions(@NotNull int[] principals, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> perms = new HashSet<Class<? extends Permission>>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterrate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while(null != assignment && principalsIdx < principals.length)
        {
            if(assignment.getUserId() == principals[principalsIdx])
            {
                if(null != assignment.getRole())
                    perms.addAll(assignment.getRole().getPermissions());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if(assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        //apply contextual roles if any
        if(null != contextualRoles)
        {
            for(Role role : contextualRoles)
            {
                perms.addAll(role.getPermissions());
            }
        }

        return perms;
    }

    @NotNull
    protected Set<Role> getRoles(@NotNull int[] principals)
    {
        Set<Role> roles = new HashSet<Role>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterrate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while(null != assignment && principalsIdx < principals.length)
        {
            if(assignment.getUserId() == principals[principalsIdx])
            {
                if(null != assignment.getRole())
                    roles.add(assignment.getRole());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if(assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        return roles;
    }

    protected void addAssignment(RoleAssignment assignment)
    {
        _assignments.add(assignment);
    }

    /**
     * This is purely for backwards compatibility with HTTP APIs--Do not use for new code!
     * @param principal the user/group
     * @return old-style bitmask for basic permissions
     * @deprecated Use getPermissions() instead.
     */
    public int getPermsAsOldBitMask(UserPrincipal principal)
    {
        int perms = 0;
        Set<Class<? extends Permission>> permClasses = getPermissions(principal);
        if(permClasses.contains(ReadPermission.class))
            perms |= ACL.PERM_READ;
        if(permClasses.contains(InsertPermission.class))
            perms |= ACL.PERM_INSERT;
        if(permClasses.contains(UpdatePermission.class))
            perms |= ACL.PERM_UPDATE;
        if(permClasses.contains(DeletePermission.class))
            perms |= ACL.PERM_DELETE;
        if(permClasses.contains(AdminPermission.class))
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
        return new SecurityPolicyBean(_resource, _modified);
    }

    /**
     * Serializes this policy into a map suitable for returning via an API action
     * @return The serialized policy
     */
    @NotNull
    public Map<String,Object> toMap()
    {
        Map<String,Object> props = new HashMap<String,Object>();

        //modified
        props.put("modified", getModified());

        //resource id
        props.put("resourceId", getResource().getResourceId());

        //role assignments
        List<Map<String,Object>> assignments = new ArrayList<Map<String,Object>>();
        for(RoleAssignment assignment : getAssignments())
        {
            Map<String,Object> assignmentProps = new HashMap<String,Object>();
            assignmentProps.put("userId", assignment.getUserId());
            assignmentProps.put("role", assignment.getRole().getUniqueName());
            assignments.add(assignmentProps);
        }
        props.put("assignments", assignments);
        return props;
    }

    /**
     * Creates and initializes a policy from the supplied map.
     * Most often, this map will have been generated by the toMap() method,
     * sent to the client, modified, and sent back.
     * A runtime exception will be thrown if the map does not contain
     * correct/sufficient information.
     * @param map A map of policy information
     * @param resource The resource
     * @return An initialized SecurityPolicy
     */
    @NotNull
    public static SecurityPolicy fromMap(@NotNull Map<String,Object> map, @NotNull SecurableResource resource)
    {
        SecurityPolicy policy = new SecurityPolicy(resource);
        String modified = (String)map.get("modified");
        policy._modified = (modified == null || modified.length() == 0) ? null : new Date(DateUtil.parseDateTime(modified));

        //ensure that if there is a property called 'assignments', that it is indeed a list
        if(map.containsKey("assignments"))
        {
            if(!(map.get("assignments") instanceof JSONArray))
                throw new RuntimeException("The assignements property does not contain a list!");
            JSONArray assignments = (JSONArray)map.get("assignments");
            for(Object element : assignments.toMapList())
            {
                if(!(element instanceof Map))
                    throw new RuntimeException("An element within the assignments property was not a map!");
                Map assignmentProps = (Map)element;

                //assignment map must have userId and role props
                if(!assignmentProps.containsKey("userId") || !assignmentProps.containsKey("role"))
                    throw new RuntimeException("A map within the assignments list did not have a userId or role property!");

                //resolve the role and principal
                Role role = RoleManager.getRole((String) assignmentProps.get("role"));
                if(null == role)
                    throw new RuntimeException("The role '" + assignmentProps.get("role") + "' is not a valid role name");

                Integer userId = (Integer)assignmentProps.get("userId");
                if(null == userId)
                    throw new RuntimeException("Null user id passed in role assignment!");

                UserPrincipal principal = SecurityManager.getPrincipal(userId.intValue());
                if(null == principal)
                    continue; //silently ignore--this could happen if the principal was deleted in between the get and save

                policy.addRoleAssignment(principal, role);
            }
        }

        return policy;
    }

    @NotNull
    protected Set<Role> getContextualRoles(UserPrincipal principal)
    {
        Set<Role> roles = new HashSet<Role>();
        if(principal instanceof User)
        {
            User user = (User)principal;

            if(user.isAdministrator())
                roles.add(RoleManager.siteAdminRole);
            if(user.isDeveloper())
                roles.add(RoleManager.getRole(DeveloperRole.class));
        }

        return roles;
    }

    /**
     * This will normalize the policy by performing a few clean-up actions. For instance it will
     * remove all redundant NoPermissionsRole assignments.
     */
    public void normalize()
    {
        if (isEmpty())
            return;

        //remove all NoPermissionsRole assignments
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Iterator<RoleAssignment> iter = _assignments.iterator();
        while (iter.hasNext())
        {
            RoleAssignment ra = iter.next();
            if(noPermsRole.equals(ra.getRole()))
                iter.remove();
        }

        //if we are now empty, we need to add a no perms role assignment for guests to keep the Policy from
        //getting ignored. Otherwise, the SecurityManager will return the parent policy and potentially
        //grant users access who did not have access before
        if (isEmpty())
            addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), noPermsRole);
    }

}