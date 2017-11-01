/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2017 LabKey Corporation
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
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=security">LabKey Security and Accounts</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Security = new function()
{
    /*-- public methods --*/
    /** @scope LABKEY.Security */
    return {

        /**
         * A map of the various permission bits supported in the LabKey Server.
         * You can use these values with the hasPermission() method to test if
         * a user or group has a particular permission. The values in this map
         * are as follows:
         * <ul>
         * <li>read</li>
         * <li>insert</li>
         * <li>update</li>
         * <li>del</li>
         * <li>readOwn</li>
         * <li>updateOwn</li>
         * <li>deleteOwn</li>
         * <li>all</li>
         * </ul>
         * For example, to refer to the update permission, the syntax would be:<br/>
         * <pre><code>LABKEY.Security.permissions.update</code></pre>
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
         * A map of commonly used effective permissions supported in the LabKey Server.
         * You can use these values with the hasEffectivePermission() method to test if
         * a user or group has a particular permission. The values in this map
         * are as follows:
         * <ul>
         * <li>read</li>
         * <li>insert</li>
         * <li>update</li>
         * <li>del</li>
         * <li>readOwn</li>
         * </ul>
         * For example, to refer to the update permission, the syntax would be:<br/>
         * <pre><code>LABKEY.Security.effectivePermissions.update</code></pre>
         */
        effectivePermissions : {
            insert: "org.labkey.api.security.permissions.InsertPermission",
            read: "org.labkey.api.security.permissions.ReadPermission",
            admin: "org.labkey.api.security.permissions.AdminPermission",
            del: "org.labkey.api.security.permissions.DeletePermission",
            readOwn: "org.labkey.api.security.permissions.ReadSomePermission",
            update: "org.labkey.api.security.permissions.UpdatePermission"
        },

        /**
         * A map of the various permission roles exposed in the user interface.
         * The members are as follows:
         * <ul>
         * <li>admin</li>
         * <li>editor</li>
         * <li>author</li>
         * <li>reader</li>
         * <li>restrictedReader</li>
         * <li>noPerms</li>
         * </ul>
         * For example, to refer to the author role, the syntax would be:<br/>
         * <pre><code>LABKEY.Security.roles.author</code></pre>
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
         * at initial startup and are constant across installations. The values in
         * this map are as follows:
         * <ul>
         * <li>administrators</li>
         * <li>users</li>
         * <li>guests</li>
         * <li>developers</li>
         * </ul>
         * For example, to refer to the administrators group, the syntax would be:<br/>
         * <pre><code>LABKEY.Security.systemGroups.administrators</code></pre>
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
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>groupPermsInfo:</b> an object containing properties about the container and group permissions.
         * This object will have the following shape:
         *  <ul>
         *  <li>container
         *      <ul>
         *          <li>id: the container id</li>
         *          <li>name: the container name</li>
         *          <li>path: the container path</li>
         *          <li>isInheritingPerms: true if the container is inheriting permissions from its parent</li>
         *          <li>groups: an array of group objects, each of which will have the following properties:
         *              <ul>
         *                  <li>id: the group id</li>
         *                  <li>name: the group's name</li>
         *                  <li>type: the group's type ('g' for group, 'r' for role, 'm' for module-specific)</li>
         *                  <li>roleLabel: (DEPRECATED) a description of the group's permission role. This will correspond
         *                      to the visible labels shown on the permissions page (e.g., 'Admin (all permissions)'.</li>
         *                  <li>role: (DEPRECATED) the group's role value (e.g., 'ADMIN'). Use this property for programmatic checks.</li>
         *                  <li>permissions: (DEPRECATED) The group's effective permissions as a bit mask.
         *                          Use this with the hasPermission() method to test for specific permissions.</li>
         *                  <li>roles: An array of role unique names that this group is playing in the container. This replaces the
         *                              existing roleLabel, role and permissions properties. Groups may now play multiple roles in a container
         *                              and each role grants the user a set of permissions. Use the getRoles() method to retrieve information
         *                              about the roles, including which permissions are granted by each role.
         *                  </li>
         *                  <li>effectivePermissions: An array of effective permission unique names the group has.</li>
         *              </ul>
         *          </li>
         *          <li>children: if includeSubfolders was true, this will contain an array of objects, each of
         *              which will have the same shape as the parent container object.</li>
         *      </ul>
         *  </li>
         * </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {boolean} [config.includeSubfolders] Set to true to recurse down the subfolders (defaults to false)
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getGroupPermissions : function(config)
        {
            var params = {};
            if (config.includeSubfolders != undefined)
                params.includeSubfolders = config.includeSubfolders;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getGroupPerms", config.containerPath),
                method : 'GET',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Returns true if the permission passed in 'perm' is on in the permissions
         * set passed as 'perms'. This is a local function and does not make a call to the server.
         * @param {int} perms The permission set, typically retrieved for a given user or group.
         * @param {int} perm A specific permission bit to check for.
         */
        hasPermission : function(perms, perm)
        {
            return perms & perm;
        },

        /**
         * Returns true if the permission passed in 'desiredPermission' is in the permissions
         * array passed as 'effectivePermissions'. This is a local function and does not make a call to the server.
         * @param {Array} effectivePermissions The permission set, typically retrieved for a given user or group.
         * @param {String} desiredPermission A specific permission bit to check for.
         * @returns {boolean}
         */
        hasEffectivePermission : function(effectivePermissions, desiredPermission)
        {
            for (var i = 0; i < effectivePermissions.length; i++)
            {
                if (effectivePermissions[i] == desiredPermission)
                {
                    return true;
                }
            }
            return false;
        },

        /**
         * Returns the name of the security role represented by the permissions passed as 'perms'.
         * The return value will be the name of a property in the LABKEY.Security.roles map.
         * This is a local function, and does not make a call to the server.
         * @param {int} perms The permissions set
         * @deprecated Do not use this anymore. Use the roles array in the various responses and the
         * getRoles() method to obtain extra information about each role.
         */
        getRole : function(perms)
        {
            for (var role in LABKEY.Security.roles)
            {
                if (LABKEY.Security.roles.hasOwnProperty(role))
                {
                    if (perms == LABKEY.Security.roles[role])
                    {
                        return role;
                    }
                }
            }
        },

        /**
         * Returns information about a user's permissions within a container. If you don't specify a user id, this
         * will return information about the current user.
         * @param config A configuration object containing the following properties
         * @param {int} config.userId The id of the user. Omit to get the current user's information
         * @param {string} config.userEmail The email address (user name) of the user (specify only userId or userEmail, not both)
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>userPermsInfo:</b> an object containing properties about the user's permissions.
         * This object will have the following shape:
         *  <ul>
         *  <li>container: information about the container and the groups the user belongs to in that container
         *      <ul>
         *          <li>id: the container id</li>
         *          <li>name: the container name</li>
         *          <li>path: the container path</li>
         *          <li>roleLabel: (DEPRECATED) a description of the user's permission role in this container. This will correspond
         *               to the visible labels shown on the permissions page (e.g., 'Admin (all permissions)'.</li>
         *          <li>role: (DEPRECATED) the user's role value (e.g., 'ADMIN'). Use this property for programmatic checks.</li>
         *          <li>permissions: (DEPRECATED) The user's effective permissions in this container as a bit mask.
         *               Use this with the hasPermission() method to test for specific permissions.</li>
         *          <li>roles: An array of role unique names that this user is playing in the container. This replaces the
         *               existing roleLabel, role and permissions properties. Users may now play multiple roles in a container
         *               and each role grants the user a set of permissions. Use the getRoles() method to retrieve information
         *               about the roles, including which permissions are granted by each role.
         *          </li>
         *          <li>effectivePermissions: An array of effective permission unique names the user has.</li>
         *          <li>groups: an array of group objects to which the user belongs, each of which will have the following properties:
         *              <ul>
         *                  <li>id: the group id</li>
         *                  <li>name: the group's name</li>
         *                  <li>roleLabel: (DEPRECATED) a description of the group's permission role. This will correspond
         *                      to the visible labels shown on the permissions page (e.g., 'Admin (all permissions)'.</li>
         *                  <li>role: (DEPRECATED) the group's role value (e.g., 'ADMIN'). Use this property for programmatic checks.</li>
         *                  <li>permissions: (DEPRECATED) The group's effective permissions as a bit mask.
         *                          Use this with the hasPermission() method to test for specific permissions.</li>
         *                  <li>roles: An array of role unique names that this group is playing in the container. This replaces the
         *                              existing roleLabel, role and permissions properties. Groups may now play multiple roles in a container
         *                              and each role grants the user a set of permissions. Use the getRoles() method to retrieve information
         *                              about the roles, including which permissions are granted by each role.
         *                  </li>
         *                  <li>effectivePermissions: An array of effective permission unique names the group has.</li>
         *              </ul>
         *          </li>
         *          <li>children: if includeSubfolders was true, this will contain an array of objects, each of
         *              which will have the same shape as the parent container object.</li>
         *      </ul>
         *  </li>
         *  <li>user: information about the requested user
         *      <ul>
         *          <li>userId: the user's id</li>
         *          <li>displayName: the user's display name</li>
         *      </ul>
         *  </li>
         * </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {boolean} [config.includeSubfolders] Set to true to recurse down the subfolders (defaults to false)
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
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

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getUserPerms", config.containerPath),
                method : 'GET',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Returns a list of users given selection criteria. This may be called by any logged-in user.
         * @param config A configuration object containing the following properties
         * @param {int} [config.groupId] The id of a project group for which you want the members.
         * @param {string} [config.group] The name of a project group for which you want the members (specify groupId or group, not both).
         * @param {string} [config.name] The first part of the user name, useful for user name completion. If specified,
         * only users whose email address or display name starts with the value supplied will be returned.
         * @param {boolean} [config.allMembers] This value is used to fetch all members in subgroups.
         * @param {boolean} [config.active] This value is used to filter members based on activity (defaults to false).
         * @param {Mixed} [config.permissions] A permissions string or an Array of permissions strings.
         * If not present, no permission filtering occurs. If multiple permissions, all permissions are required.
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>usersInfo:</b> an object with the following shape:
         *  <ul>
         *      <li>users: an array of user objects in the following form:
         *          <ul>
         *              <li>userId: the user's id</li>
         *              <li>displayName: the user's display name</li>
         *              <li>email: the user's email address</li>
         *          </ul>
         *      </li>
         *      <li>container: the path of the requested container</li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
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

            if(undefined != config.allMembers)
                params.allMembers = config.allMembers;

            if (undefined != config.active)
                params.active = config.active;

            if (undefined != config.permissions)
            {
                if (!LABKEY.Utils.isArray(config.permissions))
                {
                    config.permissions = [ config.permissions ];
                }
                params.permissions = config.permissions;
            }

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("user", "getUsers.api", config.containerPath),
                method : 'GET',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Creates a new container, which may be a project, folder, or workbook.
         * @param config A configuration object with the following properties
         * @param {String} [config.name] Required for projects or folders. The name of the container.
         * @param {String} [config.title] The title of the container, used primarily for workbooks.
         * @param {String} [config.description] The description of the container, used primarily for workbooks.
         * @param {boolean} [config.isWorkbook] Whether this a workbook should be created. Defaults to false.
         * @param {String} [config.folderType] The name of the folder type to be applied.
         * @param {function} [config.success] A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>containersInfo:</b> an object with the following properties:
         *  <ul>
         *      <li>id: the id of the requested container</li>
         *      <li>name: the name of the requested container</li>
         *      <li>path: the path of the requested container</li>
         *      <li>sortOrder: the relative sort order of the requested container</li>
         *      <li>description: an optional description for the container (may be null or missing)</li>
         *      <li>title: an optional non-unique title for the container (may be null or missing)</li>
         *      <li>isWorkbook: true if this container is a workbook. Workbooks do not appear in the left-hand project tree.</li>
         *      <li>effectivePermissions: An array of effective permission unique names the group has.</li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container in which to create a new container. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        createContainer : function(config)
        {
            var params = {};
            params.name = config.name;
            params.title = config.title;
            params.description = config.description;
            params.isWorkbook = config.isWorkbook;
            params.folderType = config.folderType;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("core", "createContainer", config.containerPath),
                method : 'POST',
                jsonData : params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },
        
        /**
         * Deletes an existing container, which may be a project, folder, or workbook.
         * @param config A configuration object with the following properties
         * @param {function} [config.success] A reference to a function to call with the API results. This
         * function will be passed the following parameter:
         * <ul>
         * <li><b>object:</b> Empty JavaScript object</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] The container which should be deleted. If not specified,
         * the current container path will be deleted.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        deleteContainer : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("core", "deleteContainer", config.containerPath),
                method : 'POST',
                jsonData : {},
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Moves an existing container, which may be a folder or workbook to be the subfolder of another folder and/or project.
         * @param config A configuration object with the following properties
         * @param {string} config.containerPath The current container path of the container that is going to be moved. Additionally, the container
         * entity id is also valid.
         * @param {string} config.destinationParent The container path of destination parent. Additionally, the destination parent entity id
         * is also valid.
         * @param {boolean} [config.addAlias] Add alias of current container path to container that is being moved (defaults to True).
         * @param {function} [config.success] A reference to a function to call with the API results. This function will
         * be passed the following parameters:
         * <ul>
         * <li><b>object:</b> Empty JavaScript object</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         */
        moveContainer : function(config) {

            var params = {};
            params.container = config.container || config.containerPath;
            params.parent = config.destinationParent || config.parent || config.parentPath;
            params.addAlias = true;

            if (!params.container) {
                console.error("'containerPath' must be specified for LABKEY.Security.moveContainer invocation.");
                return;
            }

            if (!params.parent) {
                console.error("'parent' must be specified for LABKEY.Security.moveContainer invocation.");
                return;
            }
            
            if (config.addAlias === false) {
                params.addAlias = false;
            }
            
            return LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL("core", "moveContainer", params.container),
                method : 'POST',
                jsonData : params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Retrieves the full set of folder types that are available on the server.
         * @param config A configuration object with the following properties
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameter:
         * <ul>
         * <li><b>folderTypes:</b> Map from folder type name to folder type object, which consists of the following properties:
         *  <ul>
         *      <li><b>name:</b> the cross-version stable name of the folder type</li>
         *      <li><b>description:</b> a short description of the folder type</li>
         *      <li><b>label:</b> the name that's shown to the user for this folder type</li>
         *      <li><b>defaultModule:</b> name of the module that provides the home screen for this folder type</li>
         *      <li><b>activeModules:</b> an array of module names that are automatically active for this folder type</li>
         *      <li><b>workbookType:</b> boolean that indicates if this is specifically intended to use as a workbook type
         *      <li><b>requiredWebParts:</b> an array of web parts that are part of this folder type and cannot be removed
         *          <ul>
         *              <li><b>name:</b> the name of the web part</li>
         *              <li><b>properties:</b> a map of properties that are automatically set</li>
         *          </ul>
         *      </li>
         *      <li><b>preferredWebParts:</b> an array of web parts that are part of this folder type but may be removed. Same structure as requiredWebParts</li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getFolderTypes : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("core", "getFolderTypes", config.containerPath),
                method : 'POST',
                jsonData : {},
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },


        /**
         * Retrieves the full set of modules that are installed on the server.
         * @param config A configuration object with the following properties
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameter:
         * <ul>
         * <li><b>folderType:</b> the folderType, based on the container used when calling this API</li>
         * <li><b>modules:</b> Array of all modules present on this site, each of which consists of the following properties:
         *  <ul>
         *      <li><b>name:</b> the name of the module</li>
         *      <li><b>required:</b> whether this module is required in the folder type specified above</li>
         *      <li><b>tabName:</b> name of the tab associated with this module</li>
         *      <li><b>active:</b> whether this module should be active for this container</li>
         *      <li><b>enabled:</b> whether this module should be enabled by default for this container</li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getModules : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("admin", "getModules", config.containerPath),
                method : 'POST',
                jsonData : {},
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },


        /**
         * Returns information about the specified container, including the user's current permissions within
         * that container. If the includeSubfolders config option is set to true, it will also return information
         * about all descendants the user is allowed to see.
         * @param config A configuration object with the following properties
         * @param {Mixed} [config.container] A container id or full-path String or an Array of container id/full-path Strings.  If not present, the current container is used.
         * @param {boolean} [config.includeSubfolders] If set to true, the entire branch of containers will be returned.
         * If false, only the immediate children of the starting container will be returned (defaults to false).
         * @param {boolean} [config.includeEffectivePermissions] If set to false, the effective permissions for this container resource
         * will not be included. (defaults to true)
         * @param {int} [config.depth] May be used to control the depth of recursion if includeSubfolders is set to true.
         * @param {Array} [config.moduleProperties] The names (Strings) of modules whose Module Property values should be included for each container.
         * Use "*" to get the value of all Module Properties for all modules.
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>containersInfo:</b>
         * If <code>config.container</code> is an Array, an object with property
         * <code>containers</code> and value Array of 'container info' is returned.
         * If <code>config.container</code> is a String, a object of 'container info' is returned.
         * <br>
         * The 'container info' properties are as follows:
         *  <ul>
         *      <li>id: the id of the requested container</li>
         *      <li>name: the name of the requested container</li>
         *      <li>path: the path of the requested container</li>
         *      <li>sortOrder: the relative sort order of the requested container</li>
         *      <li>activeModules: an assay of the names (strings) of active modules in the container</li>
         *      <li>folderType: the name (string) of the folder type, matched with getFolderTypes()</li>
         *      <li>description: an optional description for the container (may be null or missing)</li>
         *      <li>title: an optional non-unique title for the container (may be null or missing)</li>
         *      <li>isWorkbook: true if this container is a workbook. Workbooks do not appear in the left-hand project tree.</li>
         *      <li>isContainerTab: true if this container is a Container Tab. Container Tabs do not appear in the left-hand project tree.</li>
         *      <li>userPermissions: (DEPRECATED) the permissions the current user has in the requested container.
         *          Use this value with the hasPermission() method to test for specific permissions.</li>
         *      <li>effectivePermissions: An array of effective permission unique names the group has.</li>
         *      <li>children: if the includeSubfolders parameter was true, this will contain
         *          an array of child container objects with the same shape as the parent object.</li>
         *      <li>moduleProperties: if requested in the config object, an array of module properties for each included module:
         *          <ul>
         *              <li>name: the name of the Module Property.</li>
         *              <li>moduleName: the name of the module specifying this property.</li>
         *              <li>effectiveValue: the value of the property, including a value potentially inherited from parent containers.</li>
         *              <li>value: the value of the property as set for this specific container</li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getContainers : function(config)
        {
            var params = {};
            config = config || {};
            if (undefined != config.container)
            {
                if (LABKEY.Utils.isArray(config.container))
                {
                    params.multipleContainers = true;
                }
                else
                {
                    config.container = [ config.container ];
                }
                params.container = config.container;
            }
            if (undefined != config.includeSubfolders)
                params.includeSubfolders = config.includeSubfolders;
            if (undefined != config.depth)
                params.depth = config.depth;
            if (undefined != config.moduleProperties)
                params.moduleProperties = config.moduleProperties;
            if (undefined != config.includeEffectivePermissions)
                params.includeEffectivePermissions = config.includeEffectivePermissions;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("project", "getContainers", config.containerPath),
                method : 'GET',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Exposes limited information about the current user. This property returns a JavaScript object
         * with the following properties:
         * <ul>
         * <li>id: the user's unique id number</li>
         * <li>displayName: the user's display name</li>
         * <li>email: the user's email address</li>
         * <li>canInsert: set to true if this user can insert data in the current folder</li>
         * <li>canUpdate: set to true if this user can update data in the current folder</li>
         * <li>canUpdateOwn: set to true if this user can update data this user created in the current folder</li>
         * <li>canDelete: set to true if this user can delete data in the current folder</li>
         * <li>canDeleteOwn: set to true if this user can delete data this user created in the current folder</li>
         * <li>isAdmin: set to true if this user has admin permissions in the current folder</li>
         * <li>isGuest: set to true if this user is the guest (anonymous) user</li>
         * <li>isSystemAdmin: set to true if this user is a system administrator</li>
         * <li>isDeveloper: set to true if this user is a developer</li>
         * <li>isSignedIn: set to true if this user is signed in</li>
         * </ul>
         */
        currentUser : LABKEY.user,

        /**
         * Exposes limited information about the current container. This property returns a JavaScript object
         * with the following properties:
         * <ul>
         * <li>id: the container's unique id (entityid)</li>
         * <li>name: the name of the container</li>
         * <li>path: the path of the current container</li>
         * <li>type: the type of container, either project, folder or workbook</li>
         * </ul>
         */
        currentContainer : LABKEY.container,

        /**
         * Returns the set of groups the current user belongs to in the current container or specified containerPath.
         * This may be called by any user.
         * @param {object} config A configuration object with the following properties:
         * @param {function} config.success A reference to a function that will be called with the results.
         * This function will receive the follwing parameters:
         * <ul>
         * <li>results: an object with the following properties:
         *  <ul>
         *      <li>groups: An array of group information objects, each of which has the following properties:
         *          <ul>
         *              <li>id: The unique id of the group.</li>
         *              <li>name: The name of the group.</li>
         *              <li>isProjectGroup: true if this group is defined at the project level.</li>
         *              <li>isSystemGroup: true if this group is defined at the system level.</li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getGroupsForCurrentUser : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getGroupsForCurrentUser", config.containerPath),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Ensures that the current user is logged in.
         * @param {object} config A configuration object with the following properties:
         * @param {boolean} [config.useSiteLoginPage] Set to true to redirect the browser to the normal site login page.
         * After the user logs in, the browser will be redirected back to the current page, and the current user information
         * will be available via {@link LABKEY.Security.currentUser}. If omitted or set to false, this function
         * will attempt to login via an AJAX request, which will cause the browser to display the basic authentication
         * dialog. After the user logs in successfully, the config.success function will be called.
         * @param {function} config.success A reference to a function that will be called after a successful login.
         * It is passed the following parameters:
         * <ul>
         *  <li>results: an object with the following properties:
         *      <ul>
         *          <li>currentUser: a reference to the current user. See {@link LABKEY.Security.currentUser} for more details.</li>
         *      </ul>
         *  </li>
         * </ul>
         * Note that if the current user is already logged in, the successCallback function will be called immediately,
         * passing the current user information from {@link LABKEY.Security.currentUser}.
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {boolean} [config.force] Set to true to force a login even if the user is already logged in.
         * This is useful for keeping a session alive during a long-lived page. To do so, call this function
         * with config.force set to true, and config.useSiteLoginPage to false (or omit).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        ensureLogin : function(config)
        {
            if (LABKEY.Security.currentUser.isGuest || config.force)
            {
                if (config.useSiteLoginPage)
                {
                    window.location = LABKEY.ActionURL.buildURL("login", "login") + "?returnUrl=" + window.location;
                }
                else
                {
                    return LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("security", "ensureLogin"),
                        method: 'GET',
                        success: LABKEY.Utils.getCallbackWrapper(function(data, req){
                            if(data.currentUser)
                                LABKEY.Security.currentUser = data.currentUser;

                            if(LABKEY.Utils.getOnSuccess(config))
                                LABKEY.Utils.getOnSuccess(config).call(config.scope || this, data, req);

                        }, this),
                        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
                    });
                }
            }
            else
            {
                LABKEY.Utils.getOnSuccess(config).call(config.scope || this, {currentUser: LABKEY.Security.currentUser});
            }
        },

        /**
         * Returns the tree of securable resources from the current container downward
         * @param config A configuration object with the following properties:
         * @param {Boolean} config.includeSubfolders If set to true, the response will include subfolders
         * and their contained securable resources (defaults to false).
         * @param {Boolean} config.includeEffectivePermissions If set to true, the response will include the
         * list of effective permissions (unique names) the current user has to each resource (defaults to false).
         * These permissions are calcualted based on the current user's group memberships and role assignments, and
         * represent the actual permissions the user has to these resources at the time of the API call.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> an object with a property named "resources" which contains the root resource.
         * Each resource has the following properties:
         *  <ul>
         *      <li>id: The unique id of the resource (String, typically a GUID).</li>
         *      <li>name: The name of the resource suitable for showing to a user.</li>
         *      <li>description: The description of the reosurce.</li>
         *      <li>resourceClass: The fully-qualified Java class name of the resource.</li>
         *      <li>sourceModule: The name of the module in which the resource is defined and managed</li>
         *      <li>parentId: The parent resource's id (may be omitted if no parent)</li>
         *      <li>parentContainerPath: The parent resource's container path (may be omitted if no parent)</li>
         *      <li>children: An array of child resource objects.</li>
         *      <li>effectivePermissions: An array of permission unique names the current user has on the resource. This will be
         *          present only if the includeEffectivePermissions property was set to true on the config object.</li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getSecurableResources : function(config)
        {
            var params = {};
            if(undefined != config.includeSubfolders)
                params.includeSubfolders = config.includeSubfolders;
            if(undefined != config.includeEffectivePermissions)
                params.includeEffectivePermissions = config.includeEffectivePermissions;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getSecurableResources", config.containerPath),
                method: "GET",
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /*
         * EXPERIMENTAL! gets permissions for a set of tables within a schema.
         * Currently only study tables have individual permissions so only works on the study schema
         * @param config A configuration object with the following properties:
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {object} [config.schemaName] Name of the schema to retrieve information on.
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> an object with a property named "schemas" which contains a queries object.
         * The queries object property with the name of each table/queries. So
         * schemas.study.queries.Demographics would yield the following results
         *  <ul>
         *      <li>id: The unique id of the resource (String, typically a GUID).</li>
         *      <li>name: The name of the resource suitable for showing to a user.</li>
         *      <li>description: The description of the reosurce.</li>
         *      <li>resourceClass: The fully-qualified Java class name of the resource.</li>
         *      <li>sourceModule: The name of the module in which the resource is defined and managed</li>
         *      <li>parentId: The parent resource's id (may be omitted if no parent)</li>
         *      <li>parentContainerPath: The parent resource's container path (may be omitted if no parent)</li>
         *      <li>children: An array of child resource objects.</li>
         *      <li>effectivePermissions: An array of permission unique names the current user has on the resource. This will be
         *          present only if the includeEffectivePermissions property was set to true on the config object.</li>
         *      <li>permissionMap: An object with one property per effectivePermission allowed the user. This restates
         *          effectivePermissions in a slightly more convenient way
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getSchemaPermissions: function(config) {
            if (config.schemaName && config.schemaName != "study")
                throw "Method only works for the study schema";

            var successCallback = function(json, response) {

                //First lets make sure there is a study in here.
                var studyResource = null;
                for (var i = 0; i < json.resources.children.length; i++)
                {
                    var resource = json.resources.children[i];
                    if (resource.resourceClass == "org.labkey.study.model.StudyImpl"){
                        studyResource = resource;
                        break;
                    }
                }

                if (null == studyResource)
                {
                    config.failure.apply(config.scope || this, [{description:"No study found in container."}, response]);
                    return;
                }

                var result = {queries:{}}, dataset;

                for (i=0; i < studyResource.children.length; i++) {
                    dataset = studyResource.children[i];
                    result.queries[dataset.name] = dataset;
                    dataset.permissionMap = {};
                    for (var j=0; j < dataset.effectivePermissions.length; j++) {
                        dataset.permissionMap[dataset.effectivePermissions[j]] = true;
                    }
                }

                config.success.apply(config.scope || this, [{schemas:{study:result}}, response]);
            };

            var myConfig = {};
            if (config) {
                for (var c in config) {
                    if (config.hasOwnProperty(c)) {
                        myConfig[c] = config[c];
                    }
                }
            }
            myConfig.includeEffectivePermissions = true;
            myConfig.success = successCallback;
            return LABKEY.Security.getSecurableResources(myConfig);
        },

        /**
         * Returns the complete set of roles defined on the server, along with the permissions each role grants.
         * @param config A configuration object with the following properties:
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>roles:</b> An array of role objects, each of which has the following properties:
         *  <ul>
         *      <li>uniqueName: The unique name of the resource (String, typically a fully-qualified class name).</li>
         *      <li>name: The name of the role suitable for showing to a user.</li>
         *      <li>description: The description of the role.</li>
         *      <li>sourceModule: The name of the module in which the role is defined.</li>
         *      <li>permissions: An array of permissions the role grants. Each permission has the following properties:
         *          <ul>
         *              <li>uniqueName: The unique name of the permission (String, typically a fully-qualified class name).</li>
         *              <li>name: The name of the permission.</li>
         *              <li>description: A description of the permission.</li>
         *              <li>sourceModule: The module in which the permission is defined.</li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getRoles : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getRoles"),
                method: "GET",
                success: LABKEY.Utils.getCallbackWrapper(function(data, req){

                    //roles and perms are returned in two separate blocks for efficiency
                    var idx;
                    var permMap = {};
                    var perm;
                    for(idx = 0; idx < data.permissions.length; ++idx)
                    {
                        perm = data.permissions[idx];
                        permMap[perm.uniqueName] = perm;
                    }

                    var idxPerm;
                    var role;
                    for(idx = 0; idx < data.roles.length; ++idx)
                    {
                        role = data.roles[idx];
                        for(idxPerm = 0; idxPerm < role.permissions.length; ++idxPerm)
                        {
                            role.permissions[idxPerm] = permMap[role.permissions[idxPerm]];
                        }
                    }

                    LABKEY.Utils.getOnSuccess(config).call(config.scope || this, data.roles, req);

                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Retrieves the security policy for the requested resource id. Note that this will return the
         * policy in effect for this resource, which might be the policy from a parent resource if there
         * is no explicit policy set on the requested resource. Use the isInherited method on the returned
         * LABKEY.SecurityPolicy object to determine if the policy is inherited or not.
         * Note that the securable resource must be within the current container, or one of its descendants.
         * @param config A configuration object with the following properties
         * @param {String} config.resourceId The unique id of the securable resource.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>policy:</b> an instance of a LABKEY.SecurityPolicy object.</li>
         * <li><b>relevantRoles:</b> an array of role ids that are relevant for the given resource.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getPolicy : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "getPolicy", config.containerPath),
                method: "GET",
                params: { resourceId: config.resourceId },
                success: LABKEY.Utils.getCallbackWrapper(function(data, req){
                    data.policy.requestedResourceId = config.resourceId;
                    var policy = new LABKEY.SecurityPolicy(data.policy);
                    LABKEY.Utils.getOnSuccess(config).call(config.scope || this, policy, data.relevantRoles, req);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Deletes the security policy for the requested resource id. This will cause resource to inherit its
         * security policy from its parent resource.
         * @param config A configuration object with the following properties
         * @param {String} config.resourceId The unique id of the securable resource.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with one property called 'success' which will be set to true.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        deletePolicy : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "deletePolicy", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: { resourceId: config.resourceId },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Saves the supplied security policy. This object should be a LABKEY.SecurityPolicy object. This
         * method will completely overwrite the existing policy for the resource. If another user has changed
         * the policy in between the time it was selected and this method is called, the save will fail with
         * an optimistic concurrency exception. To force your policy over the other, call the setModified()
         * method on the policy passing null.
         * @param config A configuration object with the following properties
         * @param {String} config.policy The LABKEY.SecurityPolicy object
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with one property called 'success' which will be set to true.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        savePolicy : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "savePolicy", config.containerPath),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : config.policy.policy,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Creates a new group. The new group will be created at the project level when the current
         * container is a folder or project, or will be created at the system level if the current
         * container is the root.
         * @param config A configuration object with the following properties:
         * @param {String} config.groupName The name of the group to create
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with two properties: id and name (the new group id and name respectively)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        createGroup : function(config)
        {
            var params = {name: config.groupName};
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "createGroup", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Deletes a group.
         * @param config A configuration object with the following properties:
         * @param {int} config.groupId The id of the group to delete
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with a property named "deleted" that contains the deleted group id.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        deleteGroup : function(config)
        {
            var params = {id: config.groupId};
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "deleteGroup", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Renames a group.
         * @param config A configuration object with the following properties:
         * @param {int} config.groupId The id of the group to delete
         * @param {String} config.newName The new name for the group
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with the following properties: 'renamed'=the group id; 'oldName'=the old name; 'newName'=the new name.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        renameGroup : function(config) {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "renameGroup", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: { id: config.groupId, newName: config.newName },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });

        },

        /**
         * Adds a new member to an existing group.
         * @param config A configuration object with the following properties:
         * @param {int} config.groupId The id of the group to which you want to add the member.
         * @param {int|int[]} config.principalIds An integer id or array of ids of the users or groups you want to add as members.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with a property named "added" that contains the added principal id.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        addGroupMembers : function(config)
        {
            var params = {
                groupId: config.groupId,
                principalIds: (LABKEY.Utils.isArray(config.principalIds) ? config.principalIds : [config.principalIds])
            };
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "addGroupMember", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });

        },

        /**
         * Removes a member from an existing group.
         * @param config A configuration object with the following properties:
         * @param {int} config.groupId The id of the group from which you want to remove the member.
         * @param {int|int[]} config.principalIds An integer id or array of ids of the users or groups you want to remove.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with a property named "removed" that contains the removed principal id.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        removeGroupMembers : function(config)
        {
            var params = {
                groupId: config.groupId,
                principalIds: (LABKEY.Utils.isArray(config.principalIds) ? config.principalIds : [config.principalIds])
            };
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "removeGroupMember", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Creates a new user account
         * @param config A configuration object with the following properties:
         * @param {String} config.email The new user's email address.
         * @param {Boolean} config.sendEmail Set to false to stop the server from sending a welcome email to the user.
         * @param {Function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>data:</b> a simple object with three properties: userId, email, and message.</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        createNewUser : function(config)
        {
            var params = {
                email: config.email,
                sendEmail: config.sendEmail
            };
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("security", "createNewUser", config.containerPath),
                method: "POST",
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Returns the name of the home container, which is automatically created when your server is set up.  It is usually 'home'
         * @returns {string} The name of the home container automatically created on this server.
         */
        getHomeContainer: function(){
            return LABKEY.homeContainer;
        },

        /**
         * Returns the name of the shared container, which is automatically created when your server is setup. It is usually 'Shared'
         * @returns {string} The name of the shared container automatically created on this server.
         */
        getSharedContainer: function(){
            return LABKEY.sharedContainer;
        }
    };
};
