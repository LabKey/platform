/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.3
 * @license Copyright (c) 2008 LabKey Corporation
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

LABKEY.Security = new function()
{
    /*-- private methods --*/
    function getCallbackWrapper(fn, scope)
    {
        return function(response, options)
        {
            //ensure response is JSON before trying to decode
            var json = null;
            var contentType = response.getResponseHeader['Content-Type'];
            if(contentType && contentType.indexOf('application/json') >= 0)
                json = Ext.util.JSON.decode(response.responseText);

            fn.call(scope, json, response);
        }
    }

    /*-- public methods --*/
    return {

        permissions : {
            read: 0x00000001,
            insert: 0x00000002,
            update: 0x00000004,
            del: 0x00000008,
            readOwn: 0x00000010,
            updateOwn: 0x00000040,
            deleteOwn: 0x00000080,
            admin: 0x00008000,
            all: 0x0000ffff
        },

        roles : {
            admin: 0x0000ffff,
            editor: 0x0000000f,
            author: 0x000000c3,
            reader: 0x00000001,
            restrictedReader: 0x00000010,
            submitter: 0x00000002,
            noPerms: 0 
        },

        systemGroups : {
            administrators: -1,
            users: -2,
            guests: -3,
            developers: -4
        },

        getGroupPermissions : function(config)
        {
            var params = {};
            if(config.includeSubfolders != undefined)
                params.includeSubfolders = config.includeSubfolders;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getGroupPerms", config.containerPath),
                method : 'GET',
                params: params,
                success: getCallbackWrapper(config.successCallback, config.scope),
                failure: getCallbackWrapper(config.errorCallback, config.scope)
            });
        },

        hasPermission : function(perms, perm)
        {
            return perms & perm;
        },

        getRole : function(perms)
        {
            for(var role in LABKEY.Security.roles)
            {
                if(perms == LABKEY.Security.roles[role])
                    return role;
            }
        },

        getUsers : function(config)
        {
            var params = {};
            if(undefined != config.groupId)
                params.groupId = config.groupId;
            else if(undefined != config.group)
                params.group = config.group;

            if(undefined != config.name)
                params.name = config.name;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("user", "getUsers", config.containerPath),
                method : 'GET',
                params: params,
                success: getCallbackWrapper(config.successCallback, config.scope),
                failure: getCallbackWrapper(config.errorCallback, config.scope)
            });
        }

    }
}