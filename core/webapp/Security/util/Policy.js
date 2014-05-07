/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Security.util.Policy', {

    mixins: {
        observable: 'Ext.util.Observable'
    },

    noPermissionsRole : 'org.labkey.api.security.roles.NoPermissionsRole',

    statics : {

        deletePolicy : LABKEY.Security.deletePolicy,

        // mimics LABKEY.Security.getPolicy
        getPolicy : function(config)
        {
            var params = {resourceId: config.resourceId};

            return Ext4.Ajax.request({
                url    : LABKEY.ActionURL.buildURL('security', 'getPolicy', config.containerPath),
                method : 'GET',
                params : params,
                success: LABKEY.Utils.getCallbackWrapper(function(data, req){
                    data.policy.requestedResourceId = config.resourceId;
                    var policy = Ext4.create('Security.util.Policy', data.policy);
                    LABKEY.Utils.getOnSuccess(config).call(config.scope || this, policy, data.relevantRoles, req);
                }, this)
            });
        },

        savePolicy : LABKEY.Security.savePolicy
    },

    constructor : function(config)
    {
        this.mixins.observable.constructor.call(this, config);

        this.addEvents('change');

        this.policy = config;
        this._dirty = false;
    },

    addRoleAssignment : function(principalId, role)
    {
        this.removeRoleAssignment(principalId, this.noPermissionsRole);

        var i, assgn;
        for (i=0; i < this.policy.assignments.length; i++)
        {
            assgn = this.policy.assignments[i];
            if (assgn.userId == principalId && assgn.role == role)
                return;
        }
        this.policy.assignments.push({
            userId: principalId,
            role: role
        });
        this.fireEvent('change');
        this._dirty = true;
    },

    clearRoleAssignments : function(principalId)
    {
        if (undefined === principalId)
        {
            this.policy.assignments = [];
            this.fireEvent('change');
            this._dirty = true;
            return;
        }

        var i, assgn;
        for (i = this.policy.assignments.length-1; i >= 0; --i)
        {
            assgn = this.policy.assignments[i];
            if (assgn.userId == principalId)
                this.policy.assignments.splice(i, 1);
        }
    },

    copy : function(resourceid)
    {
        var config = Ext4.apply(this.policy);
        if (resourceid)
            config.requestedResourceId = config.resourceId = resourceid;
        config.assignments = Ext4.Array.clone(config.assignments);
        return Ext4.create('Security.util.Policy', this.policy);
    },

    getAssignedPrincipals : function(role)
    {
        var i, assgn, principals = [];
        for (i=0; i < this.policy.assignments.length; i++)
        {
            assgn = this.policy.assignments[i];
            if (assgn.role == role)
                principals.push(assgn.userId);
        }
        return principals;
    },

    /**
     * Returns the array of roles to which the given principal is directly assigned.
     * @param principalId
     */
    getAssignedRoles : function(principalId)
    {
        var i, assgn, roles = [];
        for (i=0; i < this.policy.assignments.length; i++)
        {
            assgn = this.policy.assignments[i];
            if(assgn.userId == principalId)
                roles.push(assgn.role);
        }
        return roles;
    },

    getEffectiveRoles : function(principalId, membershipsTable)
    {
        var ids = this.getGroupsForPrincipal(principalId, membershipsTable);
        ids.push(principalId);
        return this.getEffectiveRolesForIds(ids);
    },

    getEffectiveRolesForIds : function(ids)
    {
        var i, j, assgn, set = {};
        for (i = 0; i < this.policy.assignments.length; ++i)
        {
            assgn = this.policy.assignments[i];
            for (j = 0; j < ids.length; ++j)
            {
                if(ids[j] == assgn.userId)
                    set[assgn.role]=true;
            }
        }
        return set;
    },

    getGroupsForPrincipal : function(principalId, membershipsTable)
    {
        //recurses to determine all relevant groups for a given principal id
        var rows = membershipsTable.rows || membershipsTable,
            i, row,
            groups = [];

        for(i=0; i < rows.length; ++i)
        {
            row = rows[i];
            if(row.UserId == principalId)
                groups = groups.concat(row.GroupId, this.getGroupsForPrincipal(row.GroupId, membershipsTable));
        }
        return groups;
    },

    getResourceId : function()
    {
        return this.policy.resourceId;
    },

    isDirty : function()
    {
        return this._dirty;
    },

    /**
     * Returns true if this policy is empty (i.e., has no role assignments).
     */
    isEmpty : function()
    {
        return this.policy.assignments.length == 0;
    },

    /**
     * Returns true if this policy was inherited from an ancestor resource (see getResourceId())
     */
    isInherited : function()
    {
        return this.policy.requestedResourceId != this.policy.resourceId;
    },

    removeRoleAssignment : function(principalId, role)
    {
        var i, assgn;
        for (i=0; i < this.policy.assignments.length; i++)
        {
            assgn = this.policy.assignments[i];
            if (assgn.userId == principalId && assgn.role == role)
                break;
        }

        if (i < this.policy.assignments.length)
        {
            this.policy.assignments.splice(i, 1);
            this.fireEvent('change');
            this._dirty = true;
        }
    },

    setModified : function(modified)
    {
        this.policy.modified = modified;
        this.fireEvent('change');
        this._dirty = true;
    }
});
