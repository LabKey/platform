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
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
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

        RoleAssignment assignment = new RoleAssignment(_resource, principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        addAssignment(assignment);
    }

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
    public List<Role> getAssignedRoles(UserPrincipal principal)
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
    public Set<Role> getEffectiveRoles(UserPrincipal principal)
    {
        return getRoles(GroupManager.getAllGroupsForPrincipal(principal));
    }

    /**
     * Clears assigned roles for the user principal
     * @param principal The principal
     */
    public void clearAssignedRoles(UserPrincipal principal)
    {
        List<RoleAssignment> toRemove = new ArrayList<RoleAssignment>();
        for(RoleAssignment assignment : _assignments)
        {
            if(assignment.getUserId() == principal.getUserId())
                toRemove.add(assignment);
        }
        _assignments.removeAll(toRemove);
    }

    public Set<Class<? extends Permission>> getPermissions(UserPrincipal principal)
    {
        return getPermissions(principal, null);
    }

    public Set<Class<? extends Permission>> getPermissions(UserPrincipal principal, Set<Role> contextualRoles)
    {
        return getPermissions(GroupManager.getAllGroupsForPrincipal(principal), contextualRoles);
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

    public boolean hasPermission(UserPrincipal principal, Class<? extends Permission> permission)
    {
        return hasPermission(principal, permission, null);
    }

    public boolean hasPermission(UserPrincipal principal, Class<? extends Permission> permission, Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).contains(permission);
    }

    public boolean hasPermissions(UserPrincipal principal, Class<? extends Permission>... permissions)
    {
        Set<Class<? extends Permission>> permsSet = new HashSet<Class<? extends Permission>>();
        permsSet.addAll(Arrays.asList(permissions));
        return hasPermissions(principal, permsSet);
    }

    public boolean hasPermissions(UserPrincipal principal, Set<Class<? extends Permission>> permissions)
    {
        return hasPermissions(principal, permissions, null);
    }

    public boolean hasPermissions(UserPrincipal principal, Set<Class<? extends Permission>> permissions, Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).containsAll(permissions);
    }

    protected Set<Class<? extends Permission>> getPermissions(int[] principals)
    {
        return getPermissions(principals, null);
    }

    protected Set<Class<? extends Permission>> getPermissions(int[] principals, Set<Role> contextualRoles)
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
                ++principalsIdx;
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

    protected Set<Role> getRoles(int[] principals)
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
                ++principalsIdx;
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

    public Date getModified()
    {
        return _modified;
    }

    public SecurityPolicyBean getBean()
    {
        return new SecurityPolicyBean(_resource, _modified);
    }

    /**
     * Serializes this policy into a map suitable for returning via an API action
     * @return The serialized policy
     */
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
        policy._modified = (Date)map.get("modified");

        //ensure that if there is a property called 'assignments', that it is indeed a list
        if(map.containsKey("assignments"))
        {
            if(!(map.get("assignments") instanceof List))
                throw new RuntimeException("The assignements property does not contain a list!");
            List assignments = (List)map.get("assignments");
            for(Object element : assignments)
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
                
                UserPrincipal principal = SecurityManager.getGroup(userId.intValue());
                if(null == principal)
                    principal = UserManager.getUser(userId.intValue());
                if(null == principal)
                    throw new RuntimeException("Could not find a group or user with the id " + userId.intValue());

                policy.addRoleAssignment(principal, role);
            }
        }

        return policy;
    }

}