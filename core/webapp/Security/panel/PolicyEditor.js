/*
 * @class Security.panel.PolicyEditor
 * @param {SecurityCache} [config.cache] An allocated SecurityCache object
 * @cfg {string}  [config.resourceId] the id of the resource whose policy is being edited
 * @cfg {boolean} [config.isSiteAdmin] Is the current user a site administrator
 * @cfg {boolean} [config.isProjectAdministrator] Does the current user have project administrator permissions
 * @cfg {boolean} [config.saveButton] show the save button, may be hidden if the container has its own button/toolbar
 * @cfg {boolean} [config.canInherit] defaults to true, show the inherit permissions option
 */
Ext4.define('Security.panel.PolicyEditor', {

    extend: 'Ext.panel.Panel',

    alias: 'widget.labkey-policyeditor',

    initComponent: function()
    {
        Ext4.apply(this, {
            autoScroll: true
        });

        this.firstRender = true;

        this.callParent(arguments);

//        this.on('afterlayout', function(){
//            if (!this.cache.ready) {
//                this.getEl().mask('Loading Memberships...');
//                this.cache.on('ready', function() {
//                    this.getEl().unmask();
//                }, this, {single: true});
//            }
//        }, this, {single: true});

        if (this.resourceId)
            this.setResource(this.resourceId);
        this.cache.principalsStore.on('remove',this.Principals_onRemove,this);

        this.cache.onReady(function(){
            if (!this.roles)
                this.add({html: '<i>Loading...</i>', border: false});
        }, this);
    },

    Principals_onRemove : function(store,record,index)
    {
        if (this.policy)
        {
            var id = record.id;
            this.policy.clearRoleAssignments(id);
            this._redraw();
        }
    },

    onRender : function(ct, position)
    {
        this.callParent(arguments);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);
    },

    // config
    resourceId : null,
    saveButton : true,      // overloaded
    isSiteAdmin : false,
    isProjectAdmin : false,
    canInherit : true,
    doneURL : LABKEY.ActionURL.buildURL('project', 'begin', LABKEY.ActionURL.getContainer()),

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


    isDirty : function()
    {
        if (this.getInheritCheckboxValue() != this.inheritedOriginally)
            return true;
        return this.policy && this.policy.isDirty();
    },


    setResource : function(id)
    {
        this.cache.onReady(function(){
            this.resource = this.cache.getResource(id);
            Security.util.Policy.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
        },this);
    },


    setInheritedPolicy : function(policy)
    {
        this.inheritedPolicy = policy;
        if (this.getInheritCheckboxValue())
            this._redraw();
        if (this.canInherit)
        {
            this.getInheritCheckbox().enable();
        }
    },


    setPolicy : function(policy, roles)
    {
        this.inheritedOriginally = policy.isInherited();
        if (this.canInherit)
        {
            this.getInheritCheckbox().setValue(this.inheritedOriginally);
        }

        if (policy.isInherited())
        {
            this.inheritedPolicy = policy;
            this.policy = policy.copy(this.resource.id);
            this.policy.policy.modified = null; // UNDONE: make overwrite explicit in savePolicy
            if (this.canInherit)
            {
                this.getInheritCheckbox().enable();
            }
        }
        else
        {
            this.policy = policy;
            // we'd still like to get the inherited policy
            if (this.resource.parentId && this.resource.parentId != this.cache.rootId)
                Security.util.Policy.getPolicy({resourceId:this.resource.parentId, containerPath:this.resource.parentId, successCallback:this.setInheritedPolicy,
                    errorCallback: function(errorInfo, response){
                        if (response.status != 401)
                            Ext4.Msg.alert("Error", "Error getting parent policy: " + errorInfo.exception);
                    }, scope:this});
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
        return (this.getInheritCheckboxValue() ? this.inheritedPolicy : this.policy);
    },

    _eachItem : function(fn)
    {
        this.items.each(function(item){
            if (item.itemId == 'saveButton')
                return;
            if (item.itemId == 'inheritedCheckbox')
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

    getInheritCheckbox : function() {
        if (this.ibox)
            return this.ibox;

        this.ibox = Ext4.create('Ext.form.field.Checkbox', {
            id       : 'inheritedCheckbox',
            itemId   : 'inheritedCheckbox',
            name     : 'inheritedCheckbox',
            boxLabel : 'Inherit permissions from parent',
            style    : 'margin: 3px 0 0 5px;',
            disabled : !this.inheritedPolicy,
            checked  : this.inheritedOriginally,
            listeners: {
                change : this.Inherited_onChange,
                scope  : this
            }
        });

        return this.ibox;
    },

    _redraw : function()
    {
        if (!this.rendered || !this.roles)
            return;

        var toAdd = [];

        if (this.firstRender) {

            this.firstRender = false;
            this.removeAll();

            toAdd.push({
                layout: 'hbox',
                itemId: 'savebar',
                hidden: this.saveButton === false,
                border: false,
                defaults: {
                    style: {margin:'5px'}
                },
                items: [{
                    xtype : 'button',
                    text  : 'Save and Finish',
                    handler: function(){
                        this.save(false, this.cancel);
                    },
                    scope : this
                },{
                    xtype : 'button',
                    text  : 'Save',
                    handler: function(){
                        this.save(false, function() {
                            this.saveSuccess();
                        }, this);
                    },
                    scope : this
                },{
                    xtype   : 'button',
                    text    : 'Cancel',
                    handler : this.cancel,
                    scope   : this
                }]
            });
            toAdd.push({
                xtype   : 'labkey-linkbutton',
                text    : 'view permissions report',
                href    : LABKEY.ActionURL.buildURL('security', 'folderAccess'),
                style   : 'margin-left:5px;',
                linkCls : 'labkey-text-link'
            });

            if (this.canInherit)
            {
                toAdd.push(this.getInheritCheckbox());
            }
        }

        var r, role;

        var roleRows = [{
            layout   : 'hbox',
            defaults : {border: false},
            itemId   : 'header',
            items: [{
                html: '<h3>Roles</h3>',
                width: 300
            },{
                html: '<h3>Groups</h3>'
            }]
        }];

        for (r=0; r < this.roles.length; r++){
            role = this.roles[r];
            roleRows.push({
                layout: 'hbox',
                itemId: role.uniqueName.replace(/\./g, '_'),
                roleId: role.uniqueName,
                border: true,
                cls   : 'rolepanel',
                bodyStyle: 'padding: 5px',
                defaults: {border: false},
                items: [{
                    html: '<div><h3 class="rn">' + role.name + '</h3><div class="rd">' + role.description + '</div></div>',
                    //TODO
                    cls: 'rn',
                    width: 300
                },{
                    xtype: 'panel',
                    flex : 1,
                    items: [{
                        xtype  : 'panel',
                        border : false,
                        itemId : 'buttonArea'
                    },{
                        xtype  : 'labkey-principalcombo',
                        cache  : this.cache,
                        itemId : ('$add$'+role.uniqueName),
                        roleId : role.uniqueName,
                        excludedPrincipals: [-1].concat(role.excludedPrincipals),  // exclude SiteAdministrators who already has all permissions
                        listeners: {
                            select: this.onComboSelect,
                            scope: this
                        },
                        scope : this
                    }],
//                    listeners: {
//                        render: function(panel) {
//                            this.addDD(panel.getEl(), role);
//                        },
//                        scope: this
//                    },
                    scope : this
                }],
                scope : this
            });
        }

        toAdd.push({
            xtype      : 'container',
            itemId     : 'roles',
            style      : 'padding: 5px;',
            autoScroll : true,
            border     : false,
            defaults   : {
                border: false
            },
            items: roleRows
        });

        if (this.down('#roles'))
            this.remove(this.down('#roles'));
        this.add(toAdd);

        // DropSource (whole editor)
//        Ext4.create('Security.dd.ButtonsDragDropZone', {
//            container: this
//        });

        // render security policy
        var policy = this.getPolicy();
        if (policy)
        {
            // render the security policy buttons
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                var groupIds = policy.getAssignedPrincipals(role.uniqueName);

                //resolve groupids into group objects
                var groups = [], group, i;

                for (i=0; i < groupIds.length; i++)
                {
                    group = this.cache.getPrincipal(groupIds[i]);
                    if (!group) continue;
                    groups.push(group);
                }

                //sort groups
                groups.sort(function(g1, g2){
                    return g1.Name.localeCompare(g2.Name); //CONSIDER: should this be sorted only by name, or by type then name?
                });

                //add button for each group
                for (i=0; i<groups.length ; i++)
                {
                    this.addButton(groups[i],role,false);
                }
            }

            // make it easier for Selenium to recognize when permissions is loaded
            if (!Ext4.get('policyRendered'))
                Ext4.DomHelper.insertHtml('beforeend', document.body, '<input type=hidden id="policyRendered" value="1">');
        }

        this.disableRoles(this.getInheritCheckboxValue());
    },

    disableRoles : function(isDisabled)
    {
        // mask the roles
        var roles = this.down('#roles');
        roles.setDisabled(isDisabled);

        // hide the buttons
        var buttonArea = this.down('#savebar');
        buttonArea.setVisible(!isDisabled);
    },

    addDD: function(el, role)
    {
        // DropTarget
        new Ext4.dd.DropTarget(el,
        {
            editor : this,
            role : role,
            ddGroup  : 'ButtonsDD',
            notifyEnter : function(dd, e, data)
            {
                this.el.stopFx();
                if (data.roleId == this.role.uniqueName || !this.role.accept(data.groupId))
                {
                    dd.proxy.setStatus(this.dropNotAllowed);
                    return this.dropNotAllowed;
                }
                // DOESN'T WORK RELIABLY... this.el.highlight("ffff9c",{duration:1});
                dd.proxy.setStatus(this.dropAllowed);
                return this.dropAllowed;
            },
            notifyOut : function(dd, e, data)
            {
                this.el.stopFx();
            },
            notifyDrop  : function(ddSource, e, data)
            {
                this.el.stopFx();
                if (data.roleId == this.role.uniqueName || !this.role.accept(data.groupId))
                {
                    // add for fail animation
                    this.editor.addRoleAssignment(data.groupId, data.roleId, ddSource.proxy.el);
                    return false;
                }
                else
                {
                    this.editor.addRoleAssignment(data.groupId, this.role, ddSource.proxy.el);
                    if (!e.shiftKey)
                        this.editor.removeRoleAssignment(data.groupId, data.roleId);
                    return true;
                }
            }
        });

    },

    getInheritCheckboxValue : function()
    {
        return this.canInherit && this.getInheritCheckbox().getValue();
    },

    // expects button to have roleId and groupId attribute
    Button_onClose : function(btn,event)
    {
        btn.closing = true;
        if (!this.getInheritCheckboxValue())
            this.removeRoleAssignment(btn.groupId, btn.roleId);
    },

    Button_onClick : function(btn,event)
    {
        if (btn.closing)
        {
            btn.closing = false;
            return;
        }

        var id = btn.groupId;
        var policy = this.getPolicy();
        // is this a user or a group
        var principal = this.cache.getPrincipal(id);
        // can edit?
        var canEdit = !principal.Container && this.isSiteAdmin || principal.Container && this.isProjectAdmin;
        var w = Ext4.create('Security.window.UserInfoPopup', {
            userId : id,
            cache  : this.cache,
            policy : policy,
            modal  : true,
            canEdit: canEdit
        });
        w.show();
    },

    // expects combo to have roleId attribute
    onComboSelect : function(combo, records, index)
    {
        if (records && records.length)
            this.addRoleAssignment(records[0].data, combo.roleId);

        combo.selectText();
        combo.reset();
        Ext4.getBody().focus(100);
        // reset(), and clearValue() seem to leave combo in bad state
        // however, calling selectText() allows you to start typing a new value right away
    },

    Inherited_onChange : function(checkbox)
    {
        Ext4.removeNode(document.getElementById('policyRendered')); // to aid selenium automation

        var inh = this.getInheritCheckboxValue();
        if (inh && !this.inheritedPolicy)
        {
            // UNDONE: use blank if we don't know the inherited policy
            this.inheritedPolicy = this.policy.copy();
            this.inheritedPolicy.clearRoleAssignments();
        }
        if (!inh && !this.policy)
        {
            var copy = this.inheritedPolicy.copy();
            this._removeInvalidRoles(copy, this.roles);
            this.policy = copy;
        }
        this.disableRoles(this.getInheritCheckboxValue());
    },


    addButton : function(group, role, animate, animEl)
    {
        if (typeof group != 'object')
            group = this.cache.getPrincipal(group);
        var groupName = group.Name;
        var groupId = group.UserId;
        var roleId = role;
        var roleName = '';
        if (typeof role == 'object')
        {
            roleId = role.uniqueName;
            roleName = role.name;
        }
//        roleId = roleId.replace(/\./g, '_');

        var buttonArea = this.down('#roles');
        buttonArea = buttonArea.child('panel[itemId="'+roleId.replace(/\./g, '_')+'"]');
        buttonArea = buttonArea.down('#buttonArea');
        var btnId = (roleId+'$'+groupId).replace(/\./g, "_");
        var button = buttonArea.down('labkey-closebutton[itemId="'+btnId+'"]');

        //TODO: animate
//        if (typeof animate == 'boolean' && animate)  //!Ext4.isSafari
//        {
//            var startAtEl = animEl || this.getComponent('$add$' + roleId).el;
//            var endAtEl = btnEl || br;

//            var body = Ext4.getBody();
//            var span = body.insertHtml("beforeend",'<span style:"position:absolute;">' + Ext4.util.Format.htmlEncode(groupName) + '<span>', true);
//            span.setXY(startAtEl.getXY());
//            var xy = endAtEl.getXY();
//            span.shift({x:xy[0], y:xy[1], callback:function(){
//                span.remove();
//                this.addButton(group, role, false);
//            }, scope:this});
//            return;
//        }

        //button already exists...
        if (button){
            button.getEl().frame();
            return;
        }

        // really add the button
        var tooltip = (group.Type == 'u' ? 'User: ' : group.Container ? 'Group: ' : 'Site group: ') + group.Name;
        button = buttonArea.add({
            xtype : 'labkey-closebutton',
            iconCls   : 'closeicon',
            iconAlign : 'right',
            margin  : '2 5 5 0',
            text    : groupName,
            itemId  : btnId,
            groupId : groupId,
            roleId  : roleId,
            tooltip : tooltip,
            listeners : {
                afterrender : function(b) {
                    Ext4.DomQuery.select('span.closeicon', b.getEl().id)[0].onclick = Ext4.bind(this.Button_onClose, this, [b]);
                },
                click : this.Button_onClick,
                scope : this
            }
        });
//
//        if (typeof animate == 'string')
//            b.el[animate]();

//        if (!this.buttonGroups[groupId])
//            this.buttonGroups[groupId] = new ButtonGroup();
//        this.buttonGroups[groupId].add(button);
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
        var buttonArea = this.down('#roles');
        buttonArea = buttonArea.child('panel[itemId="'+roleId.replace(/\./g, '_')+'"]');
        buttonArea = buttonArea.down('#buttonArea');
        var safeRoleId = roleId.replace(/\./g, "_");
        var button = buttonArea.getComponent(safeRoleId+ '$' + groupId);
        if (!button) {
            return;
        }
//        if (animate)
//        {
//            var combo = this.getComponent('$add$' + roleId);
//            var xy = combo.el.getXY();
//            var fx = {callback:this.removeButton.createDelegate(this,[groupId,roleId,false]), x:xy[0], y:xy[1], opacity:0};
//            if (typeof animate == 'string')
//                button.el[animate](fx);
//            else
//                button.el.shift(fx);
//            return;
//        }

        buttonArea.remove(button);
    },

    addRoleAssignment : function(group, role, animEl)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId = role.uniqueName;
        this.policy.addRoleAssignment(groupId, roleId);

        this.addButton(group,role,true,animEl);
    },

    removeRoleAssignment : function(group, role)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId= role.uniqueName;
        xx = this;
        this.policy.removeRoleAssignment(groupId,roleId);
        this.removeButton(groupId, roleId, true);
    },



    /*
     * SAVE
     */

    save : function(overwrite, success, scope)
    {
        Ext4.removeNode(document.getElementById('policyRendered')); // to aid selenium automation

        success = success || this.saveSuccess;
        scope = scope || this;

        var policy;
        if (!this.getInheritCheckboxValue())
        {
            policy = this.policy.copy();
            if (policy.isEmpty())
                policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
        }

        this.getEl().mask();
        if (!policy)
        {
            Security.util.Policy.deletePolicy({resourceId:this.resource.id, successCallback:success, errorCallback:this.saveFail, scope:scope});
        }
        else
        {
            this._removeInvalidRoles(policy, this.roles);
            if (policy.isEmpty())
                policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
            if (overwrite)
                policy.setModified(null);
            Security.util.Policy.savePolicy({policy:policy, successCallback:success, errorCallback:this.saveFail, scope:scope});
        }
    },


    _removeInvalidRoles : function(policy, roles)
    {
        var i, validUniqueRoles = {};
        for (i=0 ; i<roles.length; i++)
            validUniqueRoles[roles[i].uniqueName] = true;

        var a = [], from = policy.policy.assignments;
        for (i=0 ; i < from.length; i++)
        {
            if (validUniqueRoles[from[i].role])
                a.push(from[i]);
            else
                policy._dirty = true;
        }
        policy.policy.assignments = a;
    },


    saveSuccess : function()
    {
        this.getEl().unmask();
        // reload policy
        Security.util.Policy.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
        // feedback
        var mb = Ext4.MessageBox.show({
            title  : 'Save',
            msg:'<div align=center><span style="color:green; font-weight:bold; font-size:133%;">save successful</span></div>',
            width  : 150
        });

        Ext4.defer(mb.hide, 1000, mb);
    },

    saveFail : function(json, response, options)
    {
        var optimisticFail = false;
        if (-1 != response.responseText.indexOf('OptimisticConflictException'))
            optimisticFail = true;
        if (-1 != json.exception.indexOf('has been altered by someone'))
            optimisticFail = true;

        if (optimisticFail)
        {
            // UNDONE: prompt for overwrite
            Ext4.MessageBox.alert("Error", (json.exception || response.statusText || 'save failed'));
            Security.util.Policy.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
            return;
        }

        Ext4.MessageBox.alert("Error", (json.exception || response.statusText || 'save failed'));
        this.enable();
    },

    cancel : function()
    {
        LABKEY.setSubmit(true);
        window.location = this.doneURL;
    }
});

var ButtonGroup = function()
{
    this.buttons = [];
};

Ext4.apply(ButtonGroup.prototype, {

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

Ext4.define('Security.dd.ButtonsDragDropZone', {
    extend: 'Ext4.dd.DragZone',
    ddGroup : "ButtonsDD",

    constructor : function(config)
    {
        Ext.apply(this, config);

        this.node = this.container.el.dom;
        //this.view = grid.getView();
        this.callParent([this.node, config]);
        this.scroll = false;
        this.ddel = document.createElement('div');
    },

    getDragData : function(e)
    {
        // is target a button in my container?
        var btnEl = Ext4.fly(e.getTarget()).findParentNode('span.principalButton');
        if (!btnEl || !btnEl.id)
            return false;
        var btn = this.container.getComponent(btnEl.id);
        if (!btn)
            return false;
        if (!('groupId' in btn) || !btn.roleId)
            return false;
        return btn;
    },

    onInitDrag : function(e)
    {
        var data = this.dragData;
        this.ddel.innerHTML = data.text;
        this.proxy.update(this.ddel);
        this.proxy.setStatus(this.proxy.dropAllowed);
    },

    afterRepair : function()
    {
        this.dragging = false;
    },

    getRepairXY : function(e, data)
    {
        return false;
    },

    onEndDrag : function(data, e)
    {
    },

    onValidDrop : function(dd, e, id)
    {
        this.hideProxy();
    },

    beforeInvalidDrop : function(e, id)
    {
    }
});

Ext4.define('Security.button.Close', {

    extend : 'Ext.button.Button',

    alias : 'widget.labkey-closebutton',

    renderTpl: [
        '<em id="{id}-btnWrap">',
            '<div id="{id}-btnEl" type="{type}" class="{btnCls}" role="button">',
                '<span id="{id}-btnInnerEl" class="{baseCls}-inner" style="{innerSpanStyle}">',
                    '{text}',
                '</span>',
                '<span id="{id}-btnIconEl" style="position: absolute; background-repeat: no-repeat; top: 2px; right: 2px;" class="{baseCls}-icon {iconCls}"<tpl if="iconUrl"> style="background-image:url({iconUrl})"</tpl>></span>',
            '</div>',
        '</em>'
    ]
});