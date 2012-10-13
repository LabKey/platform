Ext4.define('Security.panel.GroupPicker', {

    extend: 'Ext.panel.Panel',

    alias: 'widget.labkey-grouppicker',
//
//    requires : [
//        'Security.store.SecurityCache'
//    ],

    projectId : null,
    view : null,
//    cls : 'x-combo-list',
//    selectedClass : 'x-combo-selected',

    selectedGroup : null,

    initComponent : function()
    {
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            }
        });

        this.callParent(arguments);

        this.addEvents('select');

        this.cache.onReady(function(){

            this.cache.principalsStore.on("datachanged", this.onDataChanged, this);
            this.cache.principalsStore.on("add",         this.onDataChanged, this);
            this.cache.principalsStore.on("remove",      this.onDataChanged, this);
            this.cache.principalsStore.on("update",      this.onDataUpdated, this);

            this.cache.membershipStore.on("datachanged", this.onDataChanged, this);
            this.cache.membershipStore.on("add",         this.onDataChanged, this);
            this.cache.membershipStore.on("remove",      this.onDataChanged, this);
            this.cache.membershipStore.on("update",      this.onDataUpdated, this);

            this.updateStore();

        }, this);

        this.add(this.getGrid());
    },

    extraClass : function(values)
    {
        var c = 'pGroup';
        if (values.Type == 'u')
            c = 'pUser';
        else if (values.Type == 's')
            c = 'pSite';
        return c;
    },

    getGrid : function() {

        if (this.grid)
            return this.grid;

        this.grid = Ext4.create('Ext.grid.Panel', {
            store          : this.store,
            deferredRender : true,
            emptyText      : this.emptyText||'No groups defined',
            itemId : 'view',
            reserveScrollOffset : true,
            forceFit : true,
            columns: [{
                header: 'Group',
                width: 220,
                dataIndex: 'name',
                tpl:'<div class="{extraClass}" style="cursor:pointer;">{name}</div>'
            },{
                header: '<span ext:qtip="Direct group memberships for the group">Member Groups</span>',
                width: 120,
                dataIndex: 'countGroups',
                align:'right'
            },{
                header: '<span ext:qtip="Direct user memberships for the group">Member Users</span>',
                width: 115,
                dataIndex: 'countUsers',
                align:'right'
            },
            {
                header: '<span ext:qtip="Recursive count of unique users in the group and all member groups">Total Users</span>',
                width: 100,
                dataIndex: 'countTotalUsers',
                align:'right'
            }],
            listeners: {
                select: this.onViewClick,
                scope: this
            },
            scope : this
        });

        return this.grid;
    },

    onDataUpdated : function(s, record, type)
    {
        this.onDataChanged();
    },

    onDataChanged : function()
    {
        this.updateStore();

        if (this.selectedGroup)
        {
            var index = this.store.indexOfId(this.selectedGroup.UserId);
            if (index >= 0)
                this.down('#view').select(index);
        }
    },

    updateStore : function()
    {
        var principalsStore = Security.store.SecurityCache.filterPrincipalsStore(this.cache.principalsStore, (this.projectId ? 'project' : 'site'), this.projectId, {});
        var data = [];
        principalsStore.each(function(r){
            var name = r.data.Name;
            var countAllMembers = this.cache.getMembersOf(r.data.UserId).length;
            var countMemberGroups = this.cache.getMemberGroups(r.data.UserId).length;
            var countTotalUsers = this.cache.getEffectiveUsers(r.data.UserId).length;
            var extraCls = this.extraClass(r.data);
            data.push([name,(countAllMembers-countMemberGroups),countMemberGroups,countTotalUsers,r.data.Type,extraCls,r.data.UserId]);
        }, this);

        if(!this.store)
            this.store = new Ext4.data.ArrayStore({data:data, fields:['name','countUsers','countGroups','countTotalUsers','type','extraClass','id']});
        else {
            this.store.removeAll();
            this.store.add(data)
        }
    },

    onViewClick : function(model,record)
    {
        var group = this.cache.getPrincipal(record.data.id);
        if (group)
        {
            this.selectedGroup = group;
            this.fireEvent('select', this, group);
        }
    },

    onRender : function(ct,position)
    {
        this.callParent(arguments);
        this.cache.onReady(this.onDataChanged,this);
        this.cls = 'groupPicker';
    }
});