/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var curvyCornersNoAutoScan = true;
LABKEY.requiresScript('curvycorners.src.js');


var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;
var S = LABKEY.Security;


var SecurityCacheStore = Ext.extend(LABKEY.ext.Store,{
    constructor : function(config)
    {
        SecurityCacheStore.superclass.constructor.call(this,config);
        this.addEvents("ready");
        this.remoteSort = false;
        
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
        if (this.ready)
        {
            fn.call(scope);
        }
        else
        {
            this.on("ready",fn,scope);
        }
    },

    onMetaChange : function(meta, rtype, o)
    {
        SecurityCacheStore.superclass.onMetaChange.apply(this, arguments);
    },

    removeById : function(id)
    {
        var record = this.getById(id);
        if (record)
            this.remove(record);
    },
    
    ready : false
});



var UserInfoPopup = Ext.extend(Ext.Window,{
    constructor : function(config)
    {
        //this.panel = new Ext.Panel({html:'hey'});
        config = Ext.apply({},config,{
            closeable : true,
            closeAction : 'close',
            constrain : true,
            minWidth:200,
            width:200,
            height:200,
            autoScroll:true,
            minHeight:200
        });
        UserInfoPopup.superclass.constructor.call(this, config);
    },

    onRender : function(ct, where)
    {
        UserInfoPopup.superclass.onRender.call(this, ct, where);
        var cache = this.cache;
        var userId = this.userId;
        var ct = this.body;
        var user = this.cache.getPrincipal(userId);
        var groups = this.cache.getGroupsFor(userId);
        var html = ["<b>" + user.Name + "</b><br>"];
        for (var g=0 ; g<groups.length ; g++)
        {
            var group = groups[g];
            html.push(group.Name+"<br>");
        }
        var m = $dom.markup(html);
        ct.update(m, false);
    }
});


/*
 * SecurityCache consolidates a lot of the background data requests into one place, with one
 * onReady() event
 *
 * Ext.data.Store is overkill, but it has handy events rigged already
 */
var SecurityCache = Ext.extend(Ext.util.Observable,{

    groupAdministrators : -1,
    groupUsers : -2,
    groupGuests : -3,
    groupDevelopers : -4,


    principalsStore : null,

    membershipStore : null,

    containersReady : false,
    ContainerRecord : Ext.data.Record.create(['id','name','path','sortOrder']),
    containersStore : null,

    rolesReady : false,
    roles : null,
    roleMap : {},

    resourcesReady : false,
    resources : null,
    resourceMap : {},
    
    /*
     * fires "ready" when all the initially requested objects are loaded, subsequent loads
     * are handled with regular callbacks
     */
    constructor : function(config)
    {
        SecurityCache.superclass.constructor.call(this,config);
        this.addEvents(['ready']);
        var container = config.project || LABKEY.container.id;


        this.principalsStore = new SecurityCacheStore(
        {
            id:'UserId',
            schemaName:'core',
            queryName:'Principals'
        });
        this.principalsStore.on("loadexception", this._onLoadException, this);
        this.principalsStore.on("metachange", function(store,meta)
        {
            meta.fields.push({name:'sortOrder',type:'string',mvEnabled:false});
            var rtype = Ext.data.Record.create(meta.fields,'UserId');
            store.fields = rtype.prototype.fields;
            store.recordType = store.reader.recordType = rtype;
        }, this);

        this.membershipStore = new SecurityCacheStore(
        {
            schemaName:'core',
            queryName:'Members'
        });
        this.membershipStore.on("loadexception", this._onLoadException, this);

        this.containersStore = new Ext.data.Store({id:'id'}, this.ContainerRecord);

        S.getContainers({containerPath:container, includeSubfolders:true, successCallback:this._loadContainersResponse, errorCallback:this._errorCallback, scope:this});
        S.getRoles({containerPath:container, successCallback:this._loadRolesResponse, errorCallback:this._errorCallback, scope:this});
        this.principalsStore.load();
        this.principalsStore.onReady(this.Principals_onReady, this);
        S.getSecurableResources({successCallback:this._loadResourcesResponse, errorCallback:this._errorCallback, scope:this});

        // not required for onReady
        this.membershipStore.load();
    },

    _onLoadException : function(proxy, result, response, e)
    {
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

    getGroupsFor : function(id)
    {
        var record = this.principalsStore.getById(id);
        if (!record)
            return [];
        var user = record.data;
        if (!user.groups)
            this.cacheGroups(user);
        return user.groups;
    },

    mapGroupToMembers : null,
    mapPrincipalToGroups : null,

    cacheGroups : function(user)
    {
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var ids = this.mapPrincipalToGroups[user.UserId] || [];
        var groups = [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var id = ids[i];
            var record = this.principalsStore.getById(id);
            if (!record)
                continue;
            var group = record.data;
            groups.push(group);
            if (!group.groups)
                this.cacheGroups(group);
        }
        user.groups = groups;
    },

    _computeMembershipMaps : function()
    {
        var groups = {};
        var members = {};
        
        this.membershipStore.each(function(item){
            var uid = item.data.UserId;
            var gid = item.data.GroupId;
            groups[uid] ? groups[uid].push(gid) : groups[uid]=[gid];
            members[gid] ? members[gid].push(uid) : members[gid]=[uid];
        });

        this.mapGroupToMembers = members;
        this.mapPrincipalToGroups = groups;
    },

    getResource : function(id)
    {
        return this.resourceMap[id];
    },

    ready : false,
    
    checkReady : function()
    {
        if (this.principalsStore.ready &&
            this.containersReady &&
            this.rolesReady &&
            this.resourcesReady)
        {
            this.ready = true;
            this.fireEvent("ready");
        }
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
//        this.principalsStore.removeById(this.groupDevelopers);
//        this.principalsStore.removeById(this.groupAdministrators);
        // add a sortOrder field to each principal
        this.principalsStore.data.each(function(item){
            var data = item.data;
            var major = data.Type == 'u' ? '3' : data.Container ? '2' : '1';
            var minor = data.Name.toLowerCase();
            data.sortOrder = major + "." + minor;
        });
        this.principalsStore.sort('sortOrder');
        this.checkReady();
    },

    _errorCallback: function(e,r)
    {
        console.error(e + " " + r);
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
            this.roleMap[this.roles[r].uniqueName] = this.roles[r];
        this.rolesReady = true;
        this.checkReady();
    },

    _loadContainersResponse :function(r)
    {
        var map = this._mapContainers(null, [r], {});
        var records = [];
        for (var id in map)
            records.push(new this.ContainerRecord(map[id],id))
        this.containersStore.add(records);
        this.containersReady = true;
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



var CloseButton = Ext.extend(Ext.Button,{

    template : new Ext.Template(
                    '<table border="0" cellpadding="0" cellspacing="0" class="x-btn-wrap" style="display:inline-table;"><tbody><tr>',
                    '<td class="x-btn-left"><i>&#160;</i></td><td class="x-btn-center"><em unselectable="on"><button class="x-btn-text" type="{1}">{0}</button></em></td><td class="x-btn-center"><i class="pclose">&#160;</i></td><td class="x-btn-right"><i>&#160;</i></td>',
                    "</tr></tbody></table>"),

    initComponent : function()
    {
        CloseButton.superclass.initComponent.call(this);
        this.addEvents(['close']);
    },

    onRender : function(ct, position)
    {
        CloseButton.superclass.onRender.call(this, ct, position);
        // find the close element
        var close = this.el.child('I[class=pclose]');
        if (close)
        {
            close.on("click",this.onClose,this);
            if (this.tooltip)
            {
                if (typeof this.closeTooltip == 'object')
                {
                    Ext.QuickTips.register(Ext.apply({target: close}, this.closeTooltip));
                }
                else
                {
                    close.dom[this.tooltipType] = this.closeTooltip;
                }
            }
        }
    },

    stoppedEvent : null,

    onClose : function(event)
    {
        // can't seem to actually stop mousedown events, but we can disable the button
        //        event.stopEvent();
        this.stoppedEvent = event;
        this.fireEvent("close", this, event);
    },

    onClick : function(event)
    {
        if (!this.stoppedEvent || event.type != 'click')
            CloseButton.superclass.onClick.call(this, event);
        this.stoppedEvent = null;
    }
});


var ButtonGroup = function()
{
    this.buttons = [];
};

Ext.apply(ButtonGroup.prototype, {

    buttons : null,

    add : function(btn)
    {
        this.buttons.push(btn);
        btn.on("mouseover", this.over, this);
        btn.on("mouseout", this.out, this);
    },

    over : function()
    {
        for (var i=0 ; i<this.buttons.length ; i++)
        {
            var btn = this.buttons[i];
            btn.el.addClass("x-btn-over");
        }
    },

    out : function()
    {
        for (var i=0 ; i<this.buttons.length ; i++)
        {
            var btn = this.buttons[i];
            btn.el.removeClass("x-btn-over");
        }
    }
});


var PrincipalComboBox = Ext.extend(Ext.form.ComboBox,{

    excludedPrincipals : null,

    constructor : function(config)
    {
        var i;
        
        var a = config.excludedPrincipals || [];
        a.push(-4);//Developers
        a.push(-1);//Site Admin
        delete config.excludedPrincipals;
        this.excludedPrincipals = {};
        for (i=0 ; i<a.length ; i++)
            this.excludedPrincipals[a[i]] = true;
        
        config = Ext.apply({}, config, {
            store : config.cache.principalsStore,
            mode : 'local',
            minListWidth : 200,
            triggerAction : 'all',
            forceSelection : true,
            typeAhead : true,
            displayField : 'Name',
            emptyText : config.groupsOnly ? 'Add group..,' : 'Add user or group...'
        });
        PrincipalComboBox.superclass.constructor.call(this, config);
    },

    tpl : new Ext.XTemplate('<tpl for="."><div class="x-combo-list-item {[this.extraClass(values.Type)]}">{Name}</div></tpl>',
    {
        extraClass : function(type,container)
        {
            var c = 'pGroup';
            if (type == 'u')
                c = 'pUser';
            else if (type == 's')
                c = 'pSite';
            return c;
        }
    }),

    bindStore : function(store, initial)
    {
        // UNDONE: ComboBox does not lend itself to filtering, but this is expensive!
        // UNDONE: would be nice to share a filtered DataView like object across the PrincipalComboBoxes
        // CONSIDER only store UserId and lookup record in XTemplate
        if (store)
        {
            var ss = new Ext.data.SimpleStore({id:0, fields:[{name:'UserId'},{name:'Name'},{name:'Type'}]});
            var data = [];
            var records = store.getRange();
            for (i=0 ; i<records.length ; i++)
            {
                var d = records[i].data;
                if (this.excludedPrincipals[d.UserId])
                    continue;
                if (this.groupsOnly && d.Type!='g')
                    continue;
                data.push([d.UserId,d.Name,d.Type=='u'?'u':d.Container?'g':'s']);
            }
            ss.loadData(data);
            store = ss;
        }
        PrincipalComboBox.superclass.bindStore.call(this,store,initial);
    },

    initComponent : function()
    {
        PrincipalComboBox.superclass.initComponent.call(this);
    }
});


var PolicyEditor = Ext.extend(Ext.Panel, {

    constructor : function(config)
    {
        PolicyEditor.superclass.constructor.call(this,config);
        this.cache = config.securityCache;
        this.roleTemplate.compile();
        if (this.resourceId)
            this.setResource(this.resourceId);
    },

    initComponent : function()
    {
    },

    onRender : function(ct, position)
    {
        PolicyEditor.superclass.onRender.call(this, ct, position);
        this._redraw();
    },

    // config
    resourceId : null,
    saveButton : true,      // overloaded

    // components, internal
    inheritedCheckbox : null,
    table : null,

    // internal, private
    inheritedOriginally : false,
    resource : null,
    policy : null,
    roles : null,
    inheritedPolicy : null,
    buttonGroups : {},
    

    doLayout : function()
    {
    },


    isDirty : function()
    {
        if (this.inheritedCheckbox && this.inheritedCheckbox.getValue() != this.inheritedOriginally)
            return true;
        return this.policy && this.policy.isDirty();
    },


    setResource : function(id)
    {
        this.cache.onReady(function(){
            this.resource = this.cache.getResource(id);
            S.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
        },this);
    },


    setInheritedPolicy : function(policy)
    {
        this.inheritedPolicy = policy;
        if (this.inheritedCheckbox && this.inheritedCheckbox.getValue())
            this._redraw();
        if (this.inheritedCheckbox)
            this.inheritedCheckbox.enable();
    },
    

    setPolicy : function(policy, roles)
    {
        this.inheritedOriginally = policy.isInherited();
        if (this.inheritedCheckbox)
            this.inheritedCheckbox.setValue(this.inheritedOriginally);

        if (policy.isInherited())
        {
            this.inheritedPolicy = policy;
            this.policy = policy.copy(this.resource.id);
            if (this.inheritedCheckbox)
                this.inheritedCheckbox.enable();
        }
        else
        {
            this.policy = policy;
            // we'd still like to get the inherited policy
            S.getPolicy({resourceId:this.resource.parentId, containerPath:this.resource.parentId, successCallback:this.setInheritedPolicy, scope:this});
        }
        this.roles = [];
        for (var r=0 ; r<roles.length ; r++)
        {
            var role = this.cache.getRole(roles[r]);
            if (role)
                this.roles.push(role);
        }
        this._redraw();
    },


    getPolicy : function()
    {
        if (this.inheritedCheckbox.getValue())
            return null;
        var policy = this.policy.copy();
        if (policy.isEmpty())
            policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
        return policy;
    },


    // documented as a container method, but it's not there
    removeAll : function()
    {
        if (this.items)
            Ext.destroy.apply(Ext, this.items.items);
        this.items = null;
    },

    removeAllButtons : function()
    {
        for (var g in this.buttonGroups)
        {
            var bg = this.buttonGroups[g];
            for (var b=0 ; b<bg.buttons.length ; b++)
            {
                var btn = bg.buttons[b];
                this.remove(btn);
            }
        }
        this.buttonGroups = {};
    },

    _eachItem : function(fn)
    {
        this.items.each(function(item){
            if (item == this.saveButton)
                return;
            if (item == this.inheritedCheckbox)
                return;
            item[fn]();
        },this);
    },

    disable : function()
    {
        this._eachItem('disable');
    },

    enable : function()
    {
        this._eachItem('enable');
    },

    roleTemplate : new Ext.Template(
            '<tr class="permissionsTR">'+
            '<td width=300 valign=top class="roleTD"><div><h3 class="rn">{name}</h3><div class="rd">{description}</div></div></td>'+
            '<td valign=top width=100% id="{uniqueName}" class="groupsTD"><span id="$br${uniqueName}">&nbsp;<img height=20 width=1 src="' + Ext.BLANK_IMAGE_URL + '"><br></span></td>'+
            '</tr>\n'),



    _redraw : function()
    {
        if (!this.rendered)
            return;
        if (!this.roles)
        {
            this.body.update("<i>Loading...</i>");
            return;
        }
        
        var r, role, ct;

        this.removeAllButtons();

        // CONSIDER: use FormPanel for outer layout
        if (!this.table)
        {
            var html = [];

            var label = "Inherit permissions from " + (this.parentName || 'parent') + "<br>";
            html.push('<table><tr><td id=checkboxTD></td><td>&nbsp;' + label + '</td></tr></table>');

            html.push(['<table cellspacing=0 style="border-collapse:collapse;"><tr><th><h3>Roles<br><img src="' +Ext.BLANK_IMAGE_URL + '" width=300 height=1></h3></th><th><h3>Groups</h3></th></tr>']);
            var spacerRow = ''; // '<tr class="spacerTR"><td><img src="' + Ext.BLANK_IMAGE_URL + '"></td></tr>';
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                if (r > 0) html.push(spacerRow);
                html.push(this.roleTemplate.applyTemplate(role));
            }
            html.push("</table>");
            var m = $dom.markup(html);
            this.body.update("",false);
            this.table = this.body.insertHtml('beforeend', m, true);

            this.inheritedCheckbox = new Ext.form.Checkbox({id:'inheritedCheckbox', style:{display:'inline'}, disabled:(!this.inheritedPolicy), checked:this.inheritedOriginally});
            this.inheritedCheckbox.render('checkboxTD');
//            this.inheritedCheckbox.on("change", this.Inherited_onChange, this);
            this.inheritedCheckbox.on("check", this.Inherited_onChange, this);
            this.add(this.inheritedCheckbox);

            if (this.saveButton) // check if caller want's to omit the button
            {
                this.saveButton = new Ext.Button({text:'Save', handler:this.save, scope:this});
                this.saveButton.render(this.body);
                this.add(this.saveButton);
            }
            
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                ct = Ext.fly(role.uniqueName);
                var c = new PrincipalComboBox({cache:this.cache, id:('$add$'+role.uniqueName), roleId:role.uniqueName, excludedPrincipals:role.excludedPrincipals});
                c.on("select", this.Combo_onSelect, this);
                c.render(ct);
                this.add(c);
            }
        }

        // render security policy
        var policy = this.inheritedCheckbox.getValue() ? this.inheritedPolicy : this.policy;
        if (policy)
        {
            // render the security policy buttons
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                var groups = policy.getAssignedPrincipals(role.uniqueName);
                for (var g=0 ; g<groups.length ; g++)
                {
                    var group = this.cache.getPrincipal(groups[g]);
                    if (!group) continue;
                    this.addButton(group,role,false);
                }
            }
        }
        if (this.inheritedCheckbox.getValue())
            this.disable();
        else
            this.enable();
    },


    // expects button to have roleId and groupId attribute
    Button_onClose : function(btn,event)
    {
        this.removeRoleAssignment(btn.groupId, btn.roleId);
    },


    Button_onClick : function(btn,event)
    {
        var id = btn.groupId;
        var w = new UserInfoPopup({userId:id, cache:this.cache});
        w.show();
    },


    // expects combo to have roleId attribute
    Combo_onSelect : function(combo,record,index)
    {
        if (record)
            this.addRoleAssignment(record.data, combo.roleId);

        combo.selectText();
        //combo.reset();
        //Ext.getBody().el.focus.defer(100);
        // reset(), and clearValue() seem to leave combo in bad state
        // however, calling selectText() allows you to start typing a new value right away
    },


    Inherited_onChange : function(checkbox)
    {
        var inh = this.inheritedCheckbox.getValue();
        if (inh && !this.inheritedPolicy)
        {
            // UNDONE: use blank if we don't know the inherited policy
            this.inheritedPolicy = this.policy.copy();
            this.inheritedPolicy.clearRoleAssignments();
        }
        if (!inh && !this.policy)
        {
            this.policy = this.inheritedPolicy.copy();
        }
        this._redraw();
    },

    
    addButton : function(group, role, animate)
    {
        if (typeof group != 'object')
            group = this.cache.getPrincipal(group);
        var groupName = group.Name;
        var groupId = group.UserId;
        var roleId = role;
        if (typeof role == 'object')
            roleId = role.uniqueName;

        var style = 'pGroup';
        if (group.Type == 'u')
            style = 'pUser';
        else if (!group.Container)
            style = 'pSite';

        var btnId = roleId+'$'+groupId;
        var btnEl = Ext.fly(btnId);

        var br = Ext.get('$br$' + roleId);  // why doesn't Ext.fly() work?
        if (typeof animate == 'boolean' && animate)
        {
            var body = Ext.getBody();
            var combo = this.getComponent('$add$' + roleId);
            var span = body.insertHtml("beforeend",'<span style:"position:absolute;">' + $h(groupName) + '<span>', true);
            span.setXY(combo.el.getXY());
            var xy = btnEl ? btnEl.getXY() : br.getXY();
            span.shift({x:xy[0], y:xy[1], callback:function(){
                span.remove();
                this.addButton(group, role, false);
            }, scope:this});
            return;
        }
        
        if (btnEl)
        {
            btnEl.frame();
            return;
        }

        // really add the button
        var ct = Ext.fly(roleId);
        b = new CloseButton({text:groupName, id:btnId, groupId:groupId, roleId:roleId, closeTooltip:'Remove ' + groupName + ' from role'});
        b.addClass(style);
        b.on("close", this.Button_onClose, this);
        b.on("click", this.Button_onClick, this);
        b.render(ct, br);
        br.insertHtml("beforebegin"," ");

        if (typeof animate == 'string')
            b.el[animate]();

        this.add(b);
        if (!this.buttonGroups[groupId])
            this.buttonGroups[groupId] = new ButtonGroup();
        this.buttonGroups[groupId].add(b);
    },

    highlightGroup : function(groupId)
    {
        var btns = this.getButtonsForGroup(groupId);
        for (var i ; i<btns.length ; i++)
            btns[i].el.frame();
    },
    
    getButtonsForGroup : function(groupId)
    {
        var btns = [];
        this.items.each(function(item){
            if (item.buttonSelector && item.groupId == groupId) btns.push(item)
        });
        return btns;
    },

    removeButton : function(groupId, roleId, animate)
    {
        var button = this.getComponent(roleId+ '$' + groupId);
        if (!button)
            return;
        if (animate)
        {
            var combo = this.getComponent('$add$' + roleId);
            var xy = combo.el.getXY();
            var fx = {callback:this.removeButton.createDelegate(this,[groupId,roleId,false]), x:xy[0], y:xy[1], opacity:0};
            if (typeof animate == 'string')
                button.el[animate](fx);
            else
                button.el.shift(fx);
            return;
        }
        var button = this.getComponent(roleId+ '$' + groupId);
        if (button)
        {
            this.remove(button);
        }
    },
    
    addRoleAssignment : function(group, role)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId = role.uniqueName;
        this.policy.addRoleAssignment(groupId, roleId);

        this.addButton(group,role,true);
    },

    removeRoleAssignment : function(group, role)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId= role.uniqueName;
        this.policy.removeRoleAssignment(groupId,roleId);
        this.removeButton(groupId, roleId, true);
    },


    /* save probably belongs in a wrapper form */

    save : function()
    {
        var policy = this.getPolicy();
        this.disable();
        if (!policy)
            S.deletePolicy({resourceId:this.resource.id, successCallback:this.saveSuccess, errorCallback:this.saveFail, scope:this});
        else
            S.savePolicy({policy:policy, successCallback:this.saveSuccess, errorCallback:this.saveFail, scope:this});
    },

    saveSuccess : function()
    {
        // reload policy
        S.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
        // feedback
        var mb = Ext.MessageBox.show({title : 'Save', msg:'<div align=center><h3 style="color:green;">save successful</h3></div>', width:150, animEl:this.saveButton});
        var w = mb.getDialog();
        var save = w.el.getStyles();
        w.el.pause(1);
        w.el.fadeOut({callback:function(){mb.hide(); w.el.addStyles(save);}, scope:mb});
        this.enable();
    },

    saveFail : function(json, response, options)
    {
        var optimisticFail = false;
        if (-1 != response.responseText.indexOf('OptimisticConflictException'))
            optimisticFail = true;
        if (-1 != json.exception.indexOf('has been altered by someone'))
            optimisticFail = true;

        // reload policy
        if (optimisticFail)
            S.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});

        Ext.MessageBox.alert("Error", (json.exception || response.statusText || 'save failed'));
        this.enable();
    }
});
