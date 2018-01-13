/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
 * SecurityCache consolidates a lot of the background data requests into one place, with one
 * onReady() event
 *
 * Ext.data.Store is overkill, but it has handy events rigged already
 */
Ext4.define('Security.util.SecurityCache', {

    mixins: {
        observable: 'Ext.util.Observable'
    },

    statics : {
        groupAdministrators : -1,
        groupUsers : -2,
        groupGuests : -3,
        groupDevelopers : -4,

        global : null,
        getGlobalCache : function() {
            return Security.util.SecurityCache.global;
        }
    },

//    requires : [
//        'Security.store.SecurityCache'
//    ],

    rootContainer : null,
    rootId : null,
    projectId : null,
    folderId : null,

    principalsStore : null,

    membershipStore : null,

    containersReady : false,
    projectsReady : false,

    containersStore : null,

    rolesReady : false,
    roles : null,
    roleMap : {},

    resourcesReady : false,
    resources : null,
    resourceMap : {},

    /*
     * config just takes a project path/id, folder path/id, and root container id
     *
     * fires "ready" when all the initially requested objects are loaded, subsequent loads
     * are handled with regular callbacks
     */
    constructor : function(config)
    {
        this.mixins.observable.constructor.call(this, config);

        this.addEvents('ready');

        this.rootId    = config.root;
        this.projectId = config.project || config.folder  || LABKEY.container.id;
        this.folderId  = config.folder  || config.project || LABKEY.container.id;

        this.getContainersStore();
        this.getMembershipStore();
        this.getPrincipalsStore();

        LABKEY.Security.getRoles({
            containerPath   : this.folderId,
            successCallback : this._loadRolesResponse,
            errorCallback   : this._errorCallback,
            scope : this
        });

        this.principalsStore.load();

        LABKEY.Security.getSecurableResources({
            includeSubfolders : false,
            successCallback   : this._loadResourcesResponse,
            errorCallback     : this._errorCallback,
            scope:this
        });

        this.membershipStore.load();

        if (config.global) {
            // Set the global cache to this securty cache instance
            Security.util.SecurityCache.global = this;
        }
    },

    getContainersStore : function() {
        if (this.containersStore)
            return this.containersStore;

        this.containersStore = Ext4.create('Ext.data.Store', {
            fields : ['id','name','path','sortOrder', {name:'project', type:'boolean'}]
        });

        return this.containersStore;
    },

    getMembershipStore : function() {

        if (this.membershipStore)
            return this.membershipStore;

        this.membershipStore = Ext4.create('Security.store.SecurityCache', {
            schemaName : 'core',
            queryName  : 'Members',
            columns    : '*',
            listeners  : {
                loadexception : this._onLoadException,
                scope : this
            },
            scope : this
        });

        this.membershipStore.onReady(this.checkReady, this);

        return this.membershipStore;
    },

    getPrincipalKey : function(record) {
        return record.data['UserId'];
    },

    getPrincipalsStore : function() {

        if (this.principalsStore)
            return this.principalsStore;

        this.principalsStore = Ext4.create('Security.store.SecurityCache', {
            schemaName : 'core',
            // issue 17704, add displayName for users
            sql:"SELECT p.*, u.DisplayName FROM Principals p LEFT JOIN Users u ON p.type='u' AND p.UserId=u.UserId",
            listeners  : {
                loadexception : this._onLoadException,
                metachange    : function(store, meta) {
                    meta.fields.push({name:'sortOrder',type:'string',mvEnabled:false});
                },
                scope : this
            },
            getById : function(id) {
                // 16380
                // The default Ext 4.1.0 version iterates across entire data array so to improve performance
                // we override to use Ext4.util.AbstractMixedCollection.getByKey and provide our own
                // getKey method.
                return this.data.getByKey(id);
            },
            scope: this
        });

        // 16380
        this.principalsStore.data.getKey = this.getPrincipalKey;

        this.principalsStore.onReady(this.Principals_onReady, this);

        return this.principalsStore;
    },


    _onLoadException : function(proxy, result, response, e)
    {
        if (e && e.message)
            console.log(e.message);
    },

    getRole : function(id)
    {
        return this.roleMap[id];
    },


    getPrincipal : function(id)
    {
        var record = this.principalsStore.getById(id);
        if (record)
            return record.data;
        return null;
    },

    // type = 'project', 'site', 'both'
    getGroups : function(type)
    {
        var groups = [];
        if (!type) type = 'both';
        this.principalsStore.each(function(record){
            var data = record.data;
            if (data.TYPE == 'u')
                return;
            if ('both' == type || 'site' == type && !data.Container || 'project' == type && data.Container)
                groups.push(data);
        });
        return groups;
    },


    // direct (and explicit) group membership for this principal
    getGroupsFor : function(principal)
    {
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var ids = this.mapPrincipalToGroups[principal] || [];
        var groups = [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var id = ids[i];
            var record = this.principalsStore.getById(id);
            if (!record)
                continue;
            var group = record.data;
            groups.push(group);
        }
        return groups;
    },

    // recursive membership, return array of ids
    getEffectiveGroups : function(principal)
    {
        if (principal === Security.util.SecurityCache.groupGuests || principal === 0) // guest
            return [0, Security.util.SecurityCache.groupGuests];
        var set = {};
        set[Security.util.SecurityCache.groupGuests] = true;
        set[Security.util.SecurityCache.groupUsers] = true;
        this._collectGroups([principal], set);
        var ret = [];
        for (var id  in set)
            ret.push(id);
        ret.sort();
        return ret;
    },


    // recursive group memberships
    _collectGroups : function(groups, set)
    {
        if (!groups)
            return;
        for (var g=0 ; g<groups.length ; g++)
        {
            var id = groups[g];
            if (set[id])
                continue;
            set[id] = true;
            this._collectGroups(this.mapPrincipalToGroups[id], set);
        }
    },


    // non-recursive, return objects
    getMembersOf : function(principal)
    {
        if (principal == Security.util.SecurityCache.groupUsers)
            return [];
        if (principal == Security.util.SecurityCache.groupGuests)
            return [{UserId:0, Name:'Guest', Type:'u'}];
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var users = [];
        var ids = this.mapGroupToMembers[principal] || [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var record = this.principalsStore.getById(ids[i]);
            if (record)
                users.push(record.data);
        }
        return users;
    },

    getMemberGroups : function(principal)
    {
        var members = this.getMembersOf(principal);
        var groups = [];
        for (var i = 0; i < members.length; i++)
        {
            if (members[i].Type != 'u')
                groups.push(members[i]);
        }
        return groups;
    },

    // recursive, returns objects
    getEffectiveUsers : function(principal, users, set)
    {
        users = users || [];
        set = set || {};
        if (principal == Security.util.SecurityCache.groupUsers)
            return users;
        if (principal == Security.util.SecurityCache.groupGuests)
            return [{UserId:0, Name:'Guest', Type:'u'}];
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var ids = this.mapGroupToMembers[principal] || [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var id = ids[i];
            var record = this.principalsStore.getById(id);
            if (!record)
                continue;
            var user = record.data;
            if (set[user.UserId])
                continue;
            set[user.UserId] = true;
            if (user.Type == 'u')
                users.push(user);
            else
                this.getEffectiveUsers(user.UserId, users, set);
        }
        return users;
    },

    _addPrincipal : function(p)
    {
        if (p.UserId && !p.id) {
            p.id = p.UserId;
        }
        var st = this.principalsStore;
        var record = st.model.create(p);  //TODO: is ID set right? ,p.UserId);
        this._applyPrincipalsSortOrder(record);
        st.add(record);
        st.sort();
        return record;
    },

    /**
     * Interprets an empty projectId or '/' as Site Groups (as opposed to Project Groups).
     */
    createGroup : function(projectId, name, callback, scope)
    {
        var me = this;
        var path = (projectId == '/' || projectId == '') ? '/' : projectId;
        LABKEY.Security.createGroup({
            containerPath : path,
            groupName     : name,
            success       : function(group) {
                var container = (path == '/' || path == '') ? null : path;
                var group     = {UserId:group.id, Name:group.name, Container:container, Type:'g'};
                me._addPrincipal(group);
                if (typeof callback == 'function')
                    callback.call(scope || this, group);
        }});
    },

    deleteGroup : function(groupid, callback, scope)
    {
        var group = this.getPrincipal(groupid);
        if (group && group.Type == 'g')
        {
            var me = this;
            LABKEY.Security.deleteGroup({groupId:groupid, containerPath:(group.Container||'/'),
                success: function()
                {
                    me.principalsStore.removeById(groupid);
                    callback.call(scope || this);
                },
                failure: function(error, response)
                {
                    var errorDisplay = error.exception;
                    // issue 13837 - hack to display the group name since the server only knows about the group ID for this API
                    if (errorDisplay.indexOf("Group id " + group.UserId) == 0)
                        errorDisplay = errorDisplay.replace("Group id " + group.UserId, "Group " + group.Name)

                    Ext4.Msg.alert("Error", errorDisplay);
                }
            });
        }
    },

    createNewUser : function(email, sendEmail, callback, scope)
    {
        var me = this;
        LABKEY.Security.createNewUser({email:email, sendEmail:sendEmail,
            successCallback : function(user,response)
            {
                // make user match the Principals query
                var jsonResponse = Ext4.JSON.decode(response.responseText);
                if(jsonResponse.message)
                {
                    Ext4.Msg.alert("Success", jsonResponse.message);
                }
                var data = {UserId:user.userId, Name:user.email, Type:'u', Container:null};
                var record = me._addPrincipal(data);
                if (callback)
                    callback.call(scope||this, data);
            },

            errorCallback: function(json,response)
            {
                LABKEY.Utils.displayAjaxErrorResponse(response, json.exception);
            }
        });
    },

    isMemberOf : function(userid, groupid)
    {
        if (!this.mapGroupToMembers)
            this._computeMembershipMaps();
        var ids = this.mapGroupToMembers[groupid] || [];
        for (var i=0 ; i<ids.length ; i++)
        {
            if (userid == ids[i])
                return true;
        }
        return false;
    },

    addMembership : function(groupid, userid, callback, scope)
    {
        //if already a member, just return
        if (this.isMemberOf(userid, groupid))
            return;

        var group = this.getPrincipal(groupid);
        if (group && (group.Type == 'g' || group.Type == 'r'))
        {
            var success = function()
            {
                this._addMembership(groupid,userid);
                callback.call(scope || this);
            };
            LABKEY.Security.addGroupMembers({groupId:groupid, principalIds:[userid], containerPath:(group.Container||'/'), successCallback:success, scope:this});
        }
    },

    removeMembership : function(groupid, userid, callback, scope)
    {
        var group = this.getPrincipal(groupid);
        if (group && (group.Type == 'g' || group.Type == 'r'))
        {
            var success = function()
            {
                this._removeMembership(groupid,userid);
                callback.call(scope || this);
            };
            // find the container for the group
            var config = {groupId:groupid, principalIds:[userid], containerPath:(group.Container||'/'), successCallback:success, scope:this};

            //if this is site or project admins group and user is removing themselves, display confirmation dialog.
            if (group.Name.toLowerCase().indexOf("administrators") > -1 && userid == LABKEY.user.id)
            {
                var msg = "If you delete your own user account from the Administrators group, you will no longer have administrative privileges. Are you sure that you want to continue?";
                Ext4.Msg.confirm("Confirm", msg, function (btnId) {
                    if (btnId == "yes")
                    {
                        LABKEY.Security.removeGroupMembers(config);
                    }
                }, this);
            }
            else
            {
                LABKEY.Security.removeGroupMembers(config);
            }
        }
    },

    ready : false,

    // NOT recursive
    mapGroupToMembers : null,
    mapPrincipalToGroups : null,

    _addMembership : function(groupid,userid)
    {
        if (this.mapGroupToMembers)
        {
            if (!this.mapGroupToMembers[groupid])
                this.mapGroupToMembers[groupid] = [userid];
            else if (-1 == this.mapGroupToMembers[groupid].indexOf(userid))
                this.mapGroupToMembers[groupid].push(userid);

            if (!this.mapPrincipalToGroups[userid])
                this.mapPrincipalToGroups[userid] = [groupid];
            else if (-1 == this.mapPrincipalToGroups[userid].indexOf(groupid))
                this.mapPrincipalToGroups[userid].push(groupid);
        }
        var r = this.principalsStore.getById(groupid);
        if (r)
            this.principalsStore.fireEvent('update', this.principalsStore, r, Ext4.data.Model.EDIT);
        r = this.principalsStore.getById(userid);
        if (r)
            this.principalsStore.fireEvent('update', this.principalsStore, r, Ext4.data.Model.EDIT);
    },

    _removeMembership : function(groupid,userid)
    {
        if (this.mapGroupToMembers)
        {
            if (this.mapGroupToMembers[groupid])
                Ext4.Array.remove(this.mapGroupToMembers[groupid],userid);
            if (this.mapPrincipalToGroups[userid])
                Ext4.Array.remove(this.mapPrincipalToGroups[userid],groupid);
        }
        var r = this.principalsStore.getById(groupid);
        if (r)
            this.principalsStore.fireEvent('update', this.principalsStore, r, Ext4.data.Model.EDIT);
        r = this.principalsStore.getById(userid);
        if (r)
            this.principalsStore.fireEvent('update', this.principalsStore, r, Ext4.data.Model.EDIT);
    },

    _computeMembershipMaps : function()
    {
        var groups  = {};
        var members = {};

        this.membershipStore.each(function(item){
            var uid = item.data.UserId;
            var gid = item.data.GroupId;
            groups[uid]  ? groups[uid].push(gid)  : groups[uid]=[gid];
            members[gid] ? members[gid].push(uid) : members[gid]=[uid];
        });

        this.mapGroupToMembers    = members;
        this.mapPrincipalToGroups = groups;
    },

    getResource : function(id)
    {
        return this.resourceMap[id];
    },

    checkReady : function()
    {
        if (this.principalsStore.ready
//          &&  this.containersReady &&
//          &&  this.projectsReady &&
            && this.rolesReady
            && this.membershipStore.ready
            && this.resourcesReady
           )
        {
            if (!this.ready)
            {
                this.ready = true;
                this.fireEvent('ready');
            }
            return true;
        }

        return false;
    },

    onReady : function(fn, scope)
    {
        if (this.ready)
            fn.call(scope);
        else
            this.on("ready", fn, scope);
    },

    Principals_onReady : function()
    {
        var admin = this.principalsStore.getById(Security.util.SecurityCache.groupAdministrators);
        if (admin)
            admin.data.Name = 'Site Administrators';
        var users = this.principalsStore.getById(Security.util.SecurityCache.groupUsers);
        if (users)
            users.data.Name = 'All Site Users';
        var guests = this.principalsStore.getById(Security.util.SecurityCache.groupGuests);
        if (guests)
        {
            if (LABKEY.experimental.disableGuestAccount)
                guests.data.Name = 'Guests (disabled)';
        }
        // add a sortOrder field to each principal
        this.principalsStore.data.each(this._applyPrincipalsSortOrder);
        this.principalsStore.sort('sortOrder');
        this.checkReady();
    },

    _applyPrincipalsSortOrder : function(item)
    {
        var data = item.data;
        var major = data.Type == 'u' ? '4' : data.Container ? '3' : data.UserId > 0 ? '2' : '1'; // Put system groups at the top
        var minor = (data.Name||'').toLowerCase();
        data.sortOrder = major + "." + minor;
    },

    _errorCallback: function(e,resp,req)
    {
        console.error(e.exception);
    },

    _loadResourcesResponse : function(r)
    {
        this.resources = r.resources;
        this._mapResources([r.resources],this.resourceMap);
        this.resourcesReady = true;
        this.checkReady();
    },

    _mapResources : function(list,map)
    {
        if (!list || list.length==0)
            return map;
        for (var i=0; i<list.length ; i++)
        {
            var r = list[i];
            map[r.id] = r;
            this._mapResources(r.children, map);
        }
        return map;
    },

    _loadRolesResponse : function(resp)
    {
        this.roles = resp;
        for (var r=0 ; r<this.roles.length ; r++)
        {
            var role = this.roles[r];
            this.roleMap[role.uniqueName] = role;
            if (!role.excludedPrincipals)
                role.excludedPrincipals = [];
            role.accept = function(id)
            {
                if (typeof id == 'object')
                    id = id.Userid;
                //return -1 == role.excludedPrincipals.indexOf(id);
                for (var i=0 ; i<this.excludedPrincipals.length ; i++)
                    if (id == this.excludedPrincipals[i])
                        return false;
                return true;
            };
        }
        this.rolesReady = true;
        this.checkReady();
    },

    _mapContainers : function(parent, list, map)
    {
        if (!list || list.length == 0)
            return map;
        for (var i=0; i<list.length ; i++)
        {
            var c = list[i];
            c.parent = parent;
            map[c.id] = c;
            this._mapContainers(c, c.children, map);
        }
        return map;
    }
});

/**
 * @private
 * Utility store for Security.util.SecurityCache
 */
Ext4.define('Security.store.SecurityCache', {

    extend: 'LABKEY.ext4.Store',

    statics : {
        /**
         * Static method to generate principal stores based on a given type. Takes into
         * account the set of excludedPrincipals so they are not included either.
         * @param store - The store to be filtered
         * @param type
         * @param container
         * @param excludedPrincipals
         */
        filterPrincipalsStore : function(store, type, container, excludedPrincipals) {

            if (!excludedPrincipals)
                excludedPrincipals = {};

            var data    = [],
                records = store.getRange(),
                i, d;

            for (i=0; i < records.length; i++) {
                d = records[i].data;
                if (excludedPrincipals[d.UserId])
                    continue;
                switch (type)
                {
                    case 'users'   : if (d.Type != 'u') continue; break;
                    case 'groups'  : if (d.Type != 'g' && d.Type != 'r') continue; break;
                    case 'project' : if (d.Type != 'g' || d.Container != container) continue; break;
                    case 'site'    : if ((d.Type != 'g' && d.Type != 'r') || d.Container) continue; break;
                }
                data.push(d);
            }

            store = Ext4.create('Ext.data.Store', {
                data   : data,
                reader : {
                    type : 'json',
                    id   : 'UserId'
                },
                model  : store.model
            });
            store.sort('sortOrder');

            return store;
        }
    },

    constructor : function(config) {

        this.callParent([config]);

        this.addEvents('ready');

        this.remoteSort = false;

        //NOTE: all props in this second argument to Ext.extend become prototype props,
        // so any instance properties (such as this ready flag), should be set via code
        // in the constructor
        this.ready = false;

        // consider the component 'ready' after first successful load
        this.on("load", function()
        {
            if (this.ready)
                return;
            this.ready = true;
            this.fireEvent("ready");
        }, this);
    },

    onReady : function(fn, scope)
    {
        this.ready ? fn.call(scope) : this.on('ready', fn, scope);
    },

    removeById : function(id)
    {
        var record = this.getById(id);
        if (record) {
            this.remove(record);
        }
    }
});
