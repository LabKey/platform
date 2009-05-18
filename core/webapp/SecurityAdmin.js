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

    resourcesReady : true,
    resources : null,
    
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
        this.membershipStore.load();
        this.membershipStore.onReady(this.checkReady, this);
        this.principalsStore.load();
        this.principalsStore.onReady(this.checkReady, this);
        S.getSecurableResources({successCallBack:this._loadResourcesResponse, errorCallback:this._errorCallback, scope:this});
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

    ready : false,
    
    checkReady : function()
    {
        if (this.principalsStore.ready &&
            this.membershipStore.ready &&
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
        this.resources = r;
        this.resourcesReady = true;
        this.checkReady();
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
                    '<table border="0" cellpadding="0" cellspacing="0" class="x-btn-wrap" style="display:inline;"><tbody><tr>',
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
            close.on("click",this.onClose,this);                                              
    },

    stoppedEvent : null,
    
    onClose : function(event,button)
    {
        // can't seem to actually stop mousedown events, but we can disable the button
        //        event.stopEvent();
        this.stoppedEvent = event;
        this.fireEvent("close",event,button);
    },

    onClick : function(event)
    {
        if (!this.stoppedEvent)
            CloseButton.superclass.onClick.call(this,event);
        this.stoppedEvent = null;
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
    
    setResource : function(id)
    {
        this.resource = this.cache.resources;
        S.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
    },

    setPolicy : function(policy, roles)
    {
        this.policy = policy;
        this.roles = [];
        for (var r=0 ; r<roles.length ; r++)
        {
            var role = this.cache.getRole(roles[r]);
            if (role)
                this.roles.push(role);
        }
        this._update();
    },


    getResizeEl : function()
    {
        return this.table || this.el;
    },
    

    roleTemplate : new Ext.Template('<tr><td>&nbsp;</td></tr><tr><td width=300 valign=top><div><h3 class="rn">{name}</h3><div class="rd">{description}</div></div></td><td valign=top width=100% id="{uniqueName}" style=""></td></tr>'),

    _update : function()
    {
        if (this.resource)
            this.setTitle(this.resource.name);
        var r, role;
        var b;

        var html = ['<table cellspacing=4 cellpadding=4><tr><th><h3>Roles<br><img src="' +Ext.BLANK_IMAGE_URL + '" width=300 height=1></h3></th><th><h3>Groups</h3></th></tr>'];
        for (r=0 ; r<this.roles.length ; r++)
        {
            role = this.roles[r];
            html.push(this.roleTemplate.applyTemplate(role));
        }
        html.push("</table>");
        this.body.update("");
        var m = $dom.markup(html);
        this.table = this.body.insertHtml('beforeend', m, true);

        for (r=0 ; r<this.roles.length ; r++)
        {
            var ct = Ext.fly(role.uniqueName);
            role = this.roles[r];
            var groups = this.policy.getAssignedPrincipals(role.uniqueName);
            for (var g=0 ; g<groups.length ; g++)
            {
                var group = this.cache.getPrincipal(groups[g]);
                if (!group) continue;
                b = new CloseButton({text:group.Name});
                b.on("close",window.alert.createDelegate(window,['close ' + group.Name]));
                b.on("click",window.alert.createDelegate(window,['click ' + group.Name]));
                b.render(ct);
            }
            b = new Ext.Button({text:'Add Group'});
            b.on("click",window.alert.createDelegate(window,['add to ' + role.uniqueName]));
            b.render(ct);
        }

        //curvyCorners({tl:{radius:6}, tr:{radius:6}, bl:{radius:6}, br:{radius:6}, antiAlias:true}, '.curvy');
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