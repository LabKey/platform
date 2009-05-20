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
    
    ready : false
});


/*
 * SecurityCache consolidates a lot of the background data requests into one place, with one
 * onReady() event
 *
 * Ext.data.Store is overkill, but it has handy events rigged already
 */
var SecurityCache = Ext.extend(Ext.util.Observable,{

    principalsStore : new SecurityCacheStore(
    {
        id:'UserId',
        schemaName:'core',
        queryName:'Principals'
    }),

    membershipStore : new SecurityCacheStore(
    {
        schemaName:'core',
        queryName:'Members'
    }),

    containersReady : false,
    ContainerRecord : Ext.data.Record.create(['id','name','path','sortOrder']),
    containersStore : new Ext.data.Store({id:'id'}, this.ContainerRecord),


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
        S.getContainers({containerPath:container, includeSubfolders:true, successCallback:this._loadContainersResponse, errorCallback:this._errorCallback, scope:this});
        S.getRoles({containerPath:container, successCallback:this._loadRolesResponse, errorCallback:this._errorCallback, scope:this});
        this.principalsStore.load();
        this.principalsStore.onReady(this.checkReady, this);
        S.getSecurableResources({successCallback:this._loadResourcesResponse, errorCallback:this._errorCallback, scope:this});

        // not required for onReady
        this.membershipStore.load();
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

})


var PrincipalComboBox = Ext.extend(Ext.form.ComboBox,{

    constructor : function(config)
    {
        var config = Ext.apply({}, config, {
            store : config.cache.principalsStore,
            mode : 'local',
            triggerAction : 'all',
            forceSelection : true,
            typeAhead : true,
            displayField : 'Name',
            emptyText : config.groupsOnly ? 'Add group..,' : 'Add user or group...'
        });
        PrincipalComboBox.superclass.constructor.call(this, config);
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
    },

    resource : null,

    table : null,

    doLayout : function()
    {
    },

    setResource : function(id)
    {
        this.resource = this.cache.getResource(id);
        S.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
    },


    setPolicy : function(policy, roles)
    {
        if (this.resource.id != policy.getResourceId() || policy.isInherited())
        {
            this.inheritedPolicy = policy;
            if (!this.policy)
            {
                this.policy = policy.copy(this.resource.id);
            }
        }
        else
        {
            this.policy = policy;
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


    getResizeEl : function()
    {
        return this.table || this.el;
    },


    // documented as a container method, but it's not there
    removeAll : function()
    {
        if (this.items)
            Ext.destroy.apply(Ext, this.items.items);
        this.items = null;
    },


    roleTemplate : new Ext.Template('<tr><td>&nbsp;</td></tr><tr><td width=300 valign=top><div><h3 class="rn">{name}</h3><div class="rd">{description}</div></div></td><td valign=top width=100% id="{uniqueName}" style=""><span id="$br${uniqueName}">&nbsp;<img height=20 width=1 src="' + Ext.BLANK_IMAGE_URL + '"><br></span></td></tr>'),

    _redraw : function()
    {
        //this.body.applyStyles({display:'block'});
        this.removeAll();
        this.buttonGroups = {};

        var r, role;
        var b;
        var policy = this.policy || this.inheritedPolicy;

        var html = ['<table cellspacing=4 cellpadding=4><tr><th><h3>Roles<br><img src="' +Ext.BLANK_IMAGE_URL + '" width=300 height=1></h3></th><th><h3>Groups</h3></th></tr>'];
        for (r=0 ; r<this.roles.length ; r++)
        {
            role = this.roles[r];
            html.push(this.roleTemplate.applyTemplate(role));
        }
        html.push("</table>");
        this.body.update("");
        var m = $dom.markup(html);

        this.body.update("",false);
        this.table = this.body.insertHtml('beforeend', m, true);

        for (r=0 ; r<this.roles.length ; r++)
        {
            role = this.roles[r];
            var ct = Ext.fly(role.uniqueName);
            var groups = policy.getAssignedPrincipals(role.uniqueName);
            for (var g=0 ; g<groups.length ; g++)
            {
                var group = this.cache.getPrincipal(groups[g]);
                if (!group) continue;
                this.addButton(group,role,false);
            }
            var c = new PrincipalComboBox({cache:this.cache, id:('$add$'+role.uniqueName), roleId:role.uniqueName});
            c.on("select", this.Combo_onSelect, this);
            c.render(ct);
            this.add(c);
        }

        this.saveButton = new Ext.Button({text:'Save', handler:this.save, scope:this});
        this.saveButton.render(this.el);
        this.add(this.saveButton);
        this.body.applyStyles({display:'block'});
    },

    // expects button to have roleId and groupId attribute
    Button_onClose : function(btn,event)
    {
        this.removeRoleAssignment(btn.groupId, btn.roleId);
    },

    Button_onClick : function(btn,event)
    {
        alert('click ' + btn.groupId);
    },

    // expects combo to have roleId attribute
    Combo_onSelect : function(combo,record,index)
    {
        if (record)
            this.addRoleAssignment(record.data, combo.roleId);
    },


    buttonGroups : {},
    
    addButton : function(group, role, animate)
    {
        if (typeof group != 'object')
            group = this.cache.getPrincipal(group);
        var groupName = group.Name;
        var groupId = group.UserId;
        var roleId = role;
        if (typeof role == 'object')
            roleId = role.uniqueName;

        var btnId = roleId+'$'+groupId;
        var btnEl = Ext.fly(btnId);

        var br = Ext.get('$br$' + roleId);  // why doesn't Ext.fly() work?
        if (true == animate)
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
        b.on("close", this.Button_onClose, this);
        b.on("click", this.Button_onClick, this);
        b.render(ct, br);
//        b.el.hover(this.highlightGroup.createDelegate(this,[groupId]), Ext.emptyFn, this);
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

        var combo = this.getComponent('$add$' + roleId);
        combo.selectText();
        // reset(), and clearValue() seem to leave combo in bad state
        // however, calling selectText() allows you to start typing a new value right away
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

    save : function()
    {
        if (!this.policy.isDirty())
            return;
        if (this.policy.isEmpty())
            this.policy.addRoleAssignment(this.policy.guestsPrincipal, this.policy.noPermissionsRole);
        S.savePolicy({policy:this.policy, successCallback:function(){this.afterSave();}, failureCallback:function(){alert('fail'); this.afterSave();}, scope:this});
    },

    afterSave : function()
    {
        // reload policy
        var w = new Ext.Window({closeAction:'close', html:'<h3 style="color:green;">saved</h3>', border:false, closable:false});
        w.show();
        w.el.pause(1);
        w.el.fadeOut({callback:w.close, scope:w});
        S.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy , scope:this});
    },

    _corners : {tl:{radius:6}, tr:{radius:6}, bl:{radius:6}, br:{radius:6}, antiAlias:true}
});




/*


// use a Store just for events
var ContainerRecord = Ext.data.Record.create(['id','name','path','sortOrder']);
var containers = new Ext.data.Store({id:'id'}, ContainerRecord);

var getContainer = function(id)
{
    var record = containers.getById(id);
    if (record)
        return record.data;
    S.getContainers({containerPath:id, successCallback:function(info){
        var r = new ContainerRecord(info, info.id);
        containers.add([r]);
    }});
    return {id:id};
};



var containerRenderer =  function(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value)
        return "&nbsp;";
    var c = getContainer(value);
    return $h(c.name || c.id);
};


var groupRenderer = function(value, metadata, record, rowIndex, colIndex, store)
{
    if (!record.data.Container && record.data.Type=='g')
        value = '/' + value;
    if (record.data.Type == 'g')
        return '<b>' + $h(value) + '</b>';
    if (record.data.Type == 'r')
        return '<i>' + $h(value) + '</i>';
    return $h(value);
};


var principalsGrid = new Ext.grid.GridPanel(
{
    store: principalsStore,
    border:false,
    columns: [
        {header:'UserId', sortable: true},
        {header:'Name', renderer:groupRenderer, sortable: true},
        {header:'Type', sortable: true},
        {header:'Container', renderer:containerRenderer, sortable: true}]
});


var groupLookupRenderer = function(value, metadata, record, rowIndex, colIndex, store)
{
    var principal = principalsStore.getById(value);
    if (!principal)
        return value;
    return $h(principal.data.Name);
};


var membersGrid = new Ext.grid.GridPanel(
{
    store: membershipStore,
    border:false,
    columns: [
        {header:'UserId', sortable: true, renderer:groupLookupRenderer},
        {header:'GroupId', sortable: true, renderer:groupLookupRenderer}
    ]
});


//window.alert(LABKEY.container.id);

S.getSecurableResources({successCallback:function(r){
//    window.alert(r.resources.name + " " + r.resources.id);
}});

S.getPolicy({resourceId:LABKEY.container.id, successCallback:function(policy,roles){
    //    window.alert(policy.getAssignedRoles(1001) + " - " + roles);
}});

        
*/