/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.2
 * @license Copyright (c) 2009 LabKey Corporation
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



LABKEY.SecurityPolicy = Ext.extend(Ext.util.Observable, {

    noPermissionsRole: "org.labkey.api.security.roles.NoPermissionsRole",
    
    constructor : function(config)
    {
        LABKEY.SecurityPolicy.superclass.constructor.apply(this, arguments);

        this.policy = config;

        this.addEvents({
            "changed": true
        });

    },

    getResourceId : function()
    {
        return this.policy.resourceId;
    },

    isInherited : function()
    {
        return this.policy.requestedResourceId != this.policy.resourceId;
    },

    getAssignedRoles : function(principalId)
    {
        var idx, assgn;
        var roles = [];
        for(idx = 0; idx < this.policy.assignments.length; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if(assgn.userId == principalId)
                roles.push(assgn.role);
        }
        return roles;
    },

    addRoleAssignment : function(principalId, role)
    {
        this.policy.assignments.push({
            userId: principalId,
            role: role
        });
    },

    removeRoleAssignment : function(principalId, role)
    {
        var idx, assgn;
        for(idx = 0; idx < this.policy.assignments.length; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if(assgn.userId == principalId && assgn.role == role)
                break;
        }
        if(idx < this.policy.assignments.length)
            this.policy.assignments.splice(idx, 1);
    },

    clearRoleAssignments : function(principalId)
    {
        if(undefined === principalId)
        {
            this.policy.assignments = [];
            return;
        }

        var idx, assgn;
        for(idx = 0; idx < this.policy.assignments.length; ++idx)
        {
            assgn = this.policy.assignments[idx];
            if(assgn.userId == principalId)
                this.policy.assignments.splice(idx, 1);
        }
    },

    getEffectiveRoles : function(principalId, membershipsTable)
    {
        var ids = this.getGroupsForPrincipal(principalId, membershipsTable);
        ids.push(principalId);

        var idxAssgn, assgn, idxIds;
        var roles = [];
        for(idxAssgn = 0; idxAssgn < this.policy.assignments.length; ++idxAssgn)
        {
            assgn = this.policy.assignments[idxAssgn];
            for(idxIds = 0; idxIds < ids.length; ++idxIds)
            {
                if(ids[idxIds] == assgn.userId)
                    roles.push(assgn.role);
            }
        }
        return roles;
    },

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
    }
});
