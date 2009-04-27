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

import org.labkey.api.security.permissions.Permission;

import java.util.*;

/*
* User: Dave
* Date: Apr 22, 2009
* Time: 10:19:02 AM
*/

/**
 * A collection of role assignments. Somewhat like an ACL, but this tracks
 * which principal is assigned which role for a given resource.
 * You can use this to determine if a particular user has a particular
 * permission on a given resource.
 */
public abstract class SecurityPolicy
{
    SortedSet<RoleAssignment> _assignments = new TreeSet<RoleAssignment>();

    protected SecurityPolicy()
    {
    }

    protected SecurityPolicy(RoleAssignment[] assignments)
    {
        if(null != assignments)
            _assignments.addAll(Arrays.asList(assignments));
    }

    public SortedSet<RoleAssignment> getAssignments()
    {
        return Collections.unmodifiableSortedSet(_assignments);
    }

    public Set<Class<? extends Permission>> getPermissions(User user)
    {
        return getPermissions(user.getGroups());
    }

    public Set<Class<? extends Permission>> getPermissions(Group group)
    {
        return getPermissions(GroupManager.getAllGroupsForPrincipal(group));
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

    public boolean hasPermission(User user, Class<? extends Permission> permission)
    {
        return getPermissions(user).contains(permission);
    }

    public boolean hasPermission(Group group, Class<? extends Permission> permission)
    {
        return getPermissions(group).contains(permission);
    }

    public boolean hasPermissions(User user, Set<Class<? extends Permission>> permissions)
    {
        return getPermissions(user).containsAll(permissions);
    }

    public boolean hasPermissions(Group group, Set<Class<? extends Permission>> permissions)
    {
        return getPermissions(group).containsAll(permissions);
    }

    protected Set<Class<? extends Permission>> getPermissions(int[] principals)
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
        return perms;
    }

    protected void addAssignment(RoleAssignment assignment)
    {
        _assignments.add(assignment);
    }
}