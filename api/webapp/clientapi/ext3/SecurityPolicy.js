/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */


/**
 * @class Represents a security policy for a particular securable resource on the server. In general, you
 * should obtain an instance of this class from the LABKEY.Security.getPolicy() method. You may use the methods
 * of this class to alter the policy and save it back to the server using the LABKEY.Security.savePolicy() method.
 * <p>
 * The following definitions should be helpful in understanding the methods of this class:
 * <ul>
 * <li><b>Principal:</b> A user principal, which can be either a user or a group. Users and groups are both
 * user principals, and in a security policy, a user principal is assigned to a given role.</li>
 * <li><b>Role:</b> A role grants a specific set of permissions. For example, the 'Reader' role grants the read permission.
 * Roles are identified by unique names (usually a fully-qualified Java class name). A full set of roles is obtainable
 * from the LABKEY.Security.getRoles() method.</li>
 * <li><b>Direct vs Effective Assignment:</b> In a policy, principals are assigned to one or more roles. However, because a
 * principal might be a group, the users that belong to that group are effectively in whatever role the group is
 * assigned to. In this situation, the user is 'effectively' assigned to the role, while the group is 'directly'
 * assigned to the role. Asking for a user's effective roles will return all roles the user is directly assigned to
 * plus all roles the groups the user belongs to are assigned to.</li>
 * </ul>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=security">LabKey Security and Accounts</a></li>
 *              </ul>
 *           </p>
 * @example
&lt;script type="text/javascript"&gt;
    LABKEY.Security.getPolicy({
     resourceId: ....
     successCallback: onGetPolicy
 });

 function onGetPolicy(policy, relevantRoles)
 {
     //policy is an instance of this class
     //relevantRoles is an array of role unique names that are relevant to the resource
 }
&lt;/script&gt;
 */
LABKEY.SecurityPolicy = Ext.extend(Ext.util.Observable, {

    guestsPrincipal:-3,
    noPermissionsRole: "org.labkey.api.security.roles.NoPermissionsRole",
    
    constructor : function(config)
    {
        LABKEY.SecurityPolicy.superclass.constructor.apply(this, arguments);

        this.policy = config;
        this._dirty = false;

        /**
         * @memberOf LABKEY.SecurityPolicy#
         * @name change
         * @event
         * @description Fired after the policy has been changed in some way.
         */
        this.addEvents({
            "change": true
        });

    },

    /**
     * Returns the resource ID this policy applies to. Note that this may not be same ID that was requested.
     * If the requested resource inherits its permissions from an ancestor resource, this method will return
     * the ID of the nearest resource that has an policy associated with it.
     * @name getResourceId
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @returns The resource ID for this policy.
     */
    getResourceId : function()
    {
        return this.policy.resourceId;
    },

    /**
     * Returns true if this policy is empty (i.e., has no role assignments).
     * @name isEmpty
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @returns true if this policy is empty, false otherwise.
     */
    isEmpty : function()
    {
        return this.policy.assignments.length == 0;
    },

    /**
     * Returns true if this policy was inherited from an ancestor resource (see getResourceId())
     * @name isInherited
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @returns true if this policy was inherited, false otherwise.
     */
    isInherited : function()
    {
        return this.policy.requestedResourceId != this.policy.resourceId;
    },

    /**
     * Returns the array of roles to which the given principal is directly assigned.
     * @name getAssignedRoles
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The ID of the principal.
     * @returns An array of role unique names.
     */
    getAssignedRoles : function(principalId)
    {
        var idx, assgn;
        var roles = [];
        for (idx = 0; idx < this.policy.assignments.length; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if(assgn.userId == principalId)
                roles.push(assgn.role);
        }
        return roles;
    },

    /**
     * Returns an array of principal IDs that are directly assigned to a given role.
     * @name getAssignedPrincipals
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param role The unique name of the role
     * @returns An array of principal IDs
     */
    getAssignedPrincipals : function(role)
    {
        var idx, len, assgn;
        var principals = [];
        for (idx = 0, len=this.policy.assignments.length ; idx < len ; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if (assgn.role == role)
                principals.push(assgn.userId);
        }
        return principals;
    },

    /**
     * Adds a direct role assignment to the policy.
     * @name addRoleAssignment
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The principal ID
     * @param role The role unique name
     */
    addRoleAssignment : function(principalId, role)
    {
        this.removeRoleAssignment(principalId, this.noPermissionsRole);

        var idx, len, assgn;
        for (idx = 0, len = this.policy.assignments.length; idx < len ; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if (assgn.userId == principalId && assgn.role == role)
                return;
        }
        this.policy.assignments.push({
            userId: principalId,
            role: role
        });
        this.fireEvent("change");
        this._dirty = true;
    },

    /**
     * Removes a direct role assignment from the policy.
     * @name removeRoleAssignment
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The principal ID
     * @param role The role unique name
     */
    removeRoleAssignment : function(principalId, role)
    {
        var idx, assgn;
        for (idx = 0; idx < this.policy.assignments.length; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if (assgn.userId == principalId && assgn.role == role)
                break;
        }
        if(idx < this.policy.assignments.length)
        {
            this.policy.assignments.splice(idx, 1);
            this.fireEvent("change");
            this._dirty = true;
        }
    },

    /**
     * Removes all direct role assignments for the given principal
     * @name clearRoleAssignments
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The principal ID
     */
    clearRoleAssignments : function(principalId)
    {
        if (undefined === principalId)
        {
            this.policy.assignments = [];
            this.fireEvent("change");
            this._dirty = true;
            return;
        }

        var idx, assgn, len = this.policy.assignments.length;
        for (idx = len-1 ; idx >= 0 ; --idx)
        {
            assgn = this.policy.assignments[idx];
            if (assgn.userId == principalId)
                this.policy.assignments.splice(idx, 1);
        }
        if (len != this.policy.assignments.length)
        {
            this.fireEvent("change");
            this._dirty = true;
        }
    },

    /**
     * Returns all the roles the principal is effectively assigned to in this policy. See the definitions
     * in the class description for the distinction between effective and direct assignment.
     * @name getEffectiveRoles
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The principal ID
     * @param membershipsTable The group memberships table. This is required to determine the groups
     * the principal belongs to. You can obtain this table by requesting the 'Members' table from the 'Core'
     * schema using LABKEY.Query.selectRows().
     * @returns An array of roles the principal is effectively playing.
     */
    getEffectiveRoles : function(principalId, membershipsTable)
    {
        var ids = this.getGroupsForPrincipal(principalId, membershipsTable);
        ids.push(principalId);
        return this.getEffectiveRolesForIds(ids);
    },

    /**
     * Returns an object containing a property per role the given principals are effectively playing.
     * The name of each property is the role unique name, and the value of each property is simply 'true'.
     * Thus, the returned object is essentially a Set.
     * @name getEffectiveRolesForIds
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param ids An array of principal IDs
     * @returns An object with a property per unique role name the users are effectively playing.
     */
    getEffectiveRolesForIds : function(ids)
    {
        var idxAssgn, assgn, idxIds;
        var set = {};
        for (idxAssgn = 0; idxAssgn < this.policy.assignments.length; ++idxAssgn)
        {
            assgn = this.policy.assignments[idxAssgn];
            for (idxIds = 0; idxIds < ids.length; ++idxIds)
            {
                if(ids[idxIds] == assgn.userId)
                    set[assgn.role]=true;
            }
        }
        return set;
//        var roles = [];
//        for (var role in set)
//            roles.push(role);
//        return roles;
    },


    /**
     * Returns all groups this principal belongs to. This function allows for the possibility
     * that groups may contain other groups.
     * @name getGroupsForPrincipal
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param principalId The principal
     * @param membershipsTable The group memberships table. This is required to determine the groups
     * the principal belongs to. You can obtain this table by requesting the 'Members' table from the 'Core'
     * schema using LABKEY.Query.selectRows().
     * @returns An array of group IDs this user belongs to.
     */
    getGroupsForPrincipal : function(principalId, membershipsTable)
    {
        //recurses to determine all relevant groups for a given principal id
        var rows = membershipsTable.rows || membershipsTable;
        var idx, row;
        var groups = [];

        for(idx = 0; idx < rows.length; ++idx)
        {
            row = rows[idx];
            if(row.UserId == principalId)
                groups = groups.concat(row.GroupId, this.getGroupsForPrincipal(row.GroupId, membershipsTable));
        }
        return groups;
    },

    /**
     * Sets the modified property to a new value. The modified property is used during save to determine if the policy has
     * been modified since it was selected. You may pass null to this method to disable this optimistic concurrency
     * check and force the policy to save, even if another user modified it since it was selected.
     * @name setModified
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param modified New modified value, or null to override optimistic concurrency check.
     */
    setModified : function(modified)
    {
        this.policy.modified = modified;
        this.fireEvent("change");
        this._dirty = true;
    },

    /**
     * Returns true if this policy has been modified.
     * @name isDirty
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @returns true if modified, false otherwise.
     */
    isDirty : function()
    {
        return this._dirty;
    },

    /**
     * Creates a new copy of this policy, optionally resetting the resource ID.
     * @name copy
     * @function
     * @memberOf LABKEY.SecurityPolicy#
     * @param resourceid A different resource ID to use. This is typically used when you
     * want to create a new policy for a resource using the policy from another resource as a template.
     * @returns A new instance of this class which is a deep copy of the current instance.
     */
    copy : function(resourceid)
    {
        var config = Ext.apply(this.policy);
        if (resourceid)
            config.requestedResourceId = config.resourceId = resourceid;
        config.assignments = this.copyArray(config.assignments);
        return new LABKEY.SecurityPolicy(this.policy);
    },

    /* private shallow copy*/
    copyArray : function(a)
    {
    var copy = [];
    for (var i=0 ; i<a.length ; i++)
        copy.push(a[i]);
    return copy;
    }
});
