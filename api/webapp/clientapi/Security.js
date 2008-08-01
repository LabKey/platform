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

/**
 * @namespace LabKey Security Reporting and Helper class.
 * This class provides several static methods and data members for
 * calling the security-related APIs, and interpreting the results.
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

            if(fn)
                fn.call(scope || this, json, response);
        }
    }

    /*-- public methods --*/
    /** @scope LABKEY.Security.prototype */
    return {

        /**
         * A map of the various permission bits supported in the LabKey Server.
         * You can use these values with the hasPermission() method to test if
         * a user or group has a particular permission.
         */
        permissions : {
            read: 1,
            insert: 2,
            update: 4,
            del: 8,
            readOwn: 16,
            updateOwn: 64,
            deleteOwn: 128,
            admin: 32768,
            all: 65535
        },

        /**
         * A map of the various permission roles exposed in the user interface.
         */
        roles : {
            admin: 65535,
            editor: 15,
            author: 195,
            reader: 1,
            restrictedReader: 16,
            submitter: 2,
            noPerms: 0 
        },

        /**
         * A map of the special system group ids. These ids are assigned by the system
         * at initial startup and are constant accorss installations.
         */
        systemGroups : {
            administrators: -1,
            users: -2,
            guests: -3,
            developers: -4
        },

        /**
         * Get the effective permissions for all groups within the container, optionally
         * recursing down the container hierarchy.
         * @param config A configuration object with the following properties:
         * @param {function} config.successsCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>groupPermsInfo:</b> an object containing properties about the group permissions</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.errorCallback] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {boolean} [config.includeSubfolders] Set to true to recurse down the subfolders (defaults to false)
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
         */
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

        /**
         * Returns true if the permission passed in 'perm' is on in the permissions
         * set passed as 'perms'.
         * @param {integer} perms The permission set, typically retrieved for a given user or group.
         * @param {integer} perm A specific permission bit to check for.
         */
        hasPermission : function(perms, perm)
        {
            return perms & perm;
        },

        /**
         * Returns the name of the security role represented by the permissions passed as 'perms'.
         * The return value will be the name of a property in the LABKEY.Security.roles map.
         * @param perms The permissions set
         */
        getRole : function(perms)
        {
            for(var role in LABKEY.Security.roles)
            {
                if(perms == LABKEY.Security.roles[role])
                    return role;
            }
        },

        /**
         * Returns information about a specific user's permissions within a container
         * @param config A configuration object containing the following properties
         * @param {integer} config.userId The id of the user.
         * @param {string} config.userEmail The email address (user name) of the user (specify only userId or userEmail, not both) 
         * @param {function} config.successsCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>userPermsInfo:</b> an object containing properties about the user's permissions</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.errorCallback] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {boolean} [config.includeSubfolders] Set to true to recurse down the subfolders (defaults to false)
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
         */
        getUserPermissions : function(config)
        {
            var params = {};

            if(config.userId != undefined)
                params.userId = config.userId;
            else if(config.userEmail != undefined)
                params.userEmail = config.userEmail;

            if(config.includeSubfolders != undefined)
                params.includeSubfolders = config.includeSubfolders;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getUserPerms", config.containerPath),
                method : 'GET',
                params: params,
                success: getCallbackWrapper(config.successCallback, config.scope),
                failure: getCallbackWrapper(config.errorCallback, config.scope)
            });
        },

        /**
         * Returns a list of users given selection criteria.
         * @param config A configuration object containing the following properties
         * @param {integer} [config.groupId] The id of a project group for which you want the members.
         * @param {string} [config.group] The name of a project group for which you want the members (specify groupId or group, not both).
         * @param {string} [config.name] The first part of the user name, useful for user name completion. If specified,
         * only users whose email address or display name starts with the value supplied will be returned.
         * @param {function} config.successsCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>usersInfo:</b> an object a property called 'users', which is an array of user information.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.errorCallback] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
         */
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