/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
 * @class Security.panel.PolicyEditor
 * @param {SecurityCache} [config.cache] An allocated SecurityCache object
 * @cfg {string}  [config.resourceId] the id of the resource whose policy is being edited
 * @cfg {boolean} [config.isRootUserManager] Is the current user able to manage users for the root container
 * @cfg {boolean} [config.isProjectAdministrator] Does the current user have project administrator permissions
 * @cfg {boolean} [config.saveButton] show the save button, may be hidden if the container has its own button/toolbar
 * @cfg {boolean} [config.canInherit] defaults to true, show the inherit permissions option
 */
Ext4.define('Security.panel.PolicyEditor', {

    extend: 'Ext.panel.Panel',

    alias: 'widget.labkey-policyeditor',

    statics : {
        globalPolicy : null,
        getGlobalPolicy : function() {
            return Security.panel.PolicyEditor.globalPolicy;
        }
    },

    autoScroll: true,

    firstRender: true,

    initComponent: function()
    {
        this.callParent(arguments);

        if (this.resourceId)
            this.setResource(this.resourceId);
        this.cache.principalsStore.on('remove',this.Principals_onRemove,this);

        this.cache.onReady(function(){
            if (!this.roles)
                this.add({html: '<i>Loading...</i>', border: false});
        }, this);

        if (this.globalPolicy)
        {
            Security.panel.PolicyEditor.globalPolicy = this.getPolicy();
        }
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
        if (this.redrawRequested) {
            this.redrawRequested = false;
            this.on('afterrender', function() {
                this.firstRender = true;
                this._redraw();
            }, this, {single: true});
        }
    },

    // config
    resourceId : null,
    saveButton : true,      // overloaded
    isRootUserManager : false,
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
        if (this.inheritedOriginally)
        {
            this.inheritedPolicy = policy;
            this.policy = policy.copy(this.resource.id);
            this.policy.policy.modified = null; // UNDONE: make overwrite explicit in savePolicy
            if (this.canInherit)
            {
                this.getInheritCheckbox().enable();
                this.getInheritCheckbox().setValue(this.inheritedOriginally);
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

        // check after the policy has been set
        if (!this.inheritedOriginally && this.canInherit)
        {
            this.getInheritCheckbox().setValue(this.inheritedOriginally);
        }

        this.roles = [];
        for (var r=0 ; r<roles.length ; r++)
        {
            var role = this.cache.getRole(roles[r]);
            if (role)
                this.roles.push(role);
        }
        if (!this.isVisible()) {
            this.redrawRequested = true;
        }
        else {
            this._redraw();
        }
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
                change : this.onChangeInherited,
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
            this.removeAll(true);

            toAdd.push({
                xtype: 'box',
                autoEl: {
                    tag: 'a',
                    href: LABKEY.ActionURL.buildURL('security', 'folderAccess'),
                    html: 'view permissions report',
                    cls: 'labkey-text-link'
                }
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

        var me = this;
        for (r=0; r < this.roles.length; r++){
            role = this.roles[r];
            roleRows.push({
                layout: 'hbox',
                itemId: role.uniqueName.replace(/\./g, '_'),
                roleId: role.uniqueName,
                cls   : (r < this.roles.length-1) ? 'rolepanel' : 'rolepanel last',
                policyEditor : me,
                role : role,
                bodyStyle: 'padding: 5px; background-color: transparent;',
                border : false,
                defaults: {border: false},
                items: [{
                    html: '<div><h3 class="rn">' + role.name + '</h3><div class="rd">' + role.description + '</div></div>',
                    bodyStyle : 'background-color: transparent;',
                    cls: 'rn',
                    width: 300
                },{
                    xtype: 'panel',
                    flex : 1,
                    bodyStyle : 'background-color: transparent;',
                    items: [{
                        xtype  : 'panel',
                        autoScroll : true,
                        border : false,
                        itemId : 'buttonArea',
                        bodyStyle : 'background-color: transparent;'
                    },{
                        xtype  : 'labkey-principalcombo',
                        width  : 350,
                        cache  : this.cache,
                        itemId : ('$add$'+role.uniqueName),
                        roleId : role.uniqueName,
                        excludedPrincipals: [Security.util.SecurityCache.groupAdministrators].concat(role.excludedPrincipals),  // exclude SiteAdministrators who already has all permissions
                        listeners: {
                            select: this.onComboSelect,
                            scope: this
                        },
                        scope : this
                    }],
                    scope : this
                }],
                listeners: {
                    render: this.initializeRoleDropZone,
                    scope: this
                },
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
            LABKEY.Utils.signalWebDriverTest("policyRendered");
        }

        this.disableRoles(this.getInheritCheckboxValue());
    },

    initializeButtonDragZone : function(b) {

        // initialize button as drag zone
        b.dragZone = Ext4.create('Ext.dd.DragZone', b.getEl(), {

            ddGroup : 'secPerm',

            getDragData : function(e) {

                var sourceEl = Ext4.get(e.target).parent('.dragbutton');
                if (sourceEl && sourceEl.dom) {
                    var dom = document.createElement('div');
                    var child = document.createElement('div');
                    child.setAttribute('class', 'x4-btn-default-small');
                    dom.appendChild(child);
                    b.renderTpl.append(child, {
                        text : b.getText(),
                        id   : 'dragbtn',
                        baseCls : 'x4-btn',
                        btnCls : 'x4-btn-center'
                    });

                    return b.dragData = {
                        sourceEl : sourceEl.dom,
                        repairXY : Ext4.fly(sourceEl.dom).getXY(),
                        ddel : dom,
                        roleId : b.roleId,
                        groupId : b.groupId
                    };
                }
            },

            getRepairXY : function() {
                return this.dragData.repairXY;
            }
        });
    },

    initializeRoleDropZone : function(dropPanel) {

        dropPanel.dropZone = Ext4.create('Ext.dd.DropZone', dropPanel.el, {

            ddGroup : 'secPerm',

            padding: [10,10,10,10],

            getTargetFromEvent: function(e) {
                return e.getTarget('.x4-box-inner');
            },

            onNodeEnter : function(target, dd, e, data){
                Ext4.fly(target).addCls('rolehover');
            },

            onNodeOut : function(target, dd, e, data){
                Ext4.fly(target).removeCls('rolehover');
            },

            onNodeOver : function(target, dd, e, data){
                return Ext4.dd.DropZone.prototype.dropAllowed;
            },

            onNodeDrop : function(target, dd, e, data){
                if (data.roleId == dropPanel.roleId || !dropPanel.role.accept(data.groupId)) {
                    return false;
                }

                dropPanel.policyEditor.addRoleAssignment(data.groupId, dropPanel.role, dd.proxy.el);
                dropPanel.policyEditor.removeRoleAssignment(data.groupId, data.roleId);
                return true;
            }
        });
    },

    disableRoles : function(isDisabled)
    {
        // mask the roles
        var roles = this.down('#roles');
        if (roles) {
            roles.setDisabled(isDisabled);
        }
    },

    getInheritCheckboxValue : function()
    {
        return this.canInherit && this.getInheritCheckbox().getValue();
    },

    // expects button to have roleId and groupId attribute
    Button_onClose : function(btn)
    {
        btn.closing = true;
        if (!this.getInheritCheckboxValue())
            this.removeRoleAssignment(btn.groupId, btn.roleId);
    },

    Button_onClick : function(btn)
    {
        if (btn.closing)
        {
            btn.closing = false;
            return;
        }

        var id = btn.groupId;
        var principal = this.cache.getPrincipal(id);
        var canEdit = (!principal.Container && this.isRootUserManager) || (principal.Container && this.isProjectAdmin);

        Ext4.create('Security.window.UserInfoPopup', {
            userId : id,
            cache  : this.cache,
            policy : this.getPolicy(),
            modal  : true,
            canEdit: canEdit,
            autoShow: true
        });
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

    onChangeInherited : function(checkbox)
    {
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
        var btn;
        if (animEl) {
            btn = this._addButton(group, role, true);
            var animCopy = animEl.dom.cloneNode(true);
            animCopy.id = Ext4.id();
            var box = animEl.getBox();
            animCopy = Ext4.get(animCopy);
            animCopy.appendTo(Ext4.getBody());
            animCopy.setLocation(box.x, box.y);
            box = btn.getEl().getBox();
            animCopy.animate({
                duration : 500,
                to : {
                    x : box.x,
                    y : box.y
                },
                listeners : {
                    afteranimate : function() {
                        animCopy.hide();
                        btn.show();
                        Ext4.removeNode(animCopy.dom);
                    }
                }
            });
        }
        else {
            btn = this._addButton(group, role, false);
        }

        return btn;
    },

    _addButton : function(group, role, hideButton) {

        if (!Ext4.isObject(group))
            group = this.cache.getPrincipal(group);

        var ids = this.getGroupRole(group, role);
        var buttonArea = this.down('#roles');
        buttonArea = buttonArea.child('panel[itemId="' + ids.roleId.replace(/\./g, '_') + '"]');
        buttonArea = buttonArea.down('#buttonArea');
        var btnId = (ids.roleId + '$' + ids.groupId).replace(/\./g, "_");
        var button = buttonArea.down('button[itemId="'+btnId+'"]');

        //button already exists...
        if (button){
            button.getEl().frame();
            return button;
        }

        // really add the button
        var tooltip = (group.Type == 'u' ? 'User: ' : group.Container ? 'Group: ' : 'Site group: ') + group.Name;
        button = buttonArea.add({
            xtype : 'button',
            cls: 'dragbutton',
            handleMouseEvents: false,
            iconCls   : 'closeicon',
            iconAlign : 'right',
            margin  : '2 5 5 0',
            text    : group.Type == 'u' && group.DisplayName ? group.Name + ' (' + group.DisplayName + ')' : group.Name,
            itemId  : btnId,
            groupId : ids.groupId,
            roleId  : ids.roleId,
            tooltip : tooltip,
            hidden : hideButton || false,
            hideMode : hideButton ? 'visibility' : 'display',
            listeners : {
                afterrender : function(b) {
                    Ext4.DomQuery.select('span.closeicon', b.getEl().id)[0].onclick = Ext4.bind(this.Button_onClose, this, [b]);
                    this.initializeButtonDragZone(b);
                },
                click : this.Button_onClick,
                scope : this
            }
        });

        return button;
    },

    removeButton : function(groupId, roleId, animate)
    {
        var buttonArea = this.down('#roles');
        buttonArea = buttonArea.child('panel[itemId="' + roleId.replace(/\./g, '_') + '"]');
        buttonArea = buttonArea.down('#buttonArea');
        var safeRoleId = roleId.replace(/\./g, "_");
        var button = buttonArea.getComponent(safeRoleId+ '$' + groupId);
        if (!button) {
            return;
        }

        buttonArea.remove(button);
    },

    addRoleAssignment : function(group, role, animEl)
    {
        var ids = this.getGroupRole(group, role);
        this.policy.addRoleAssignment(ids.groupId, ids.roleId);

        var b = this.addButton(group,role,true,animEl);
        if (b && b.getEl()) {
            b.getEl().frame();
        }
    },

    removeRoleAssignment : function(group, role)
    {
        var ids = this.getGroupRole(group, role);
        this.policy.removeRoleAssignment(ids.groupId, ids.roleId);
        this.removeButton(ids.groupId, ids.roleId, true);
    },

    getGroupRole : function(group, role) {

        var groupId = group;
        if (Ext4.isObject(group)) {
            groupId = group.UserId;
        }
        var roleId = role;
        if (Ext4.isObject(role)) {
            roleId = role.uniqueName;
        }

        return { groupId: groupId, roleId: roleId };
    },

    /*
     * SAVE
     */

    save : function(overwrite, success, scope)
    {
        success = success || this.saveSuccess;
        scope = scope || this;

        var policy;
        if (!this.getInheritCheckboxValue())
        {
            policy = this.policy.copy();
            if (policy.isEmpty())
                policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
        }

        if (this.getEl())
        {
            this.getEl().mask();
        }

        if (!policy)
        {
            Security.util.Policy.deletePolicy({resourceId:this.resource.id, successCallback:success, errorCallback:this.saveFail, scope:scope});
        }
        else
        {
            this._removeInvalidRoles(policy, this.roles);
            if (policy.isEmpty())
                policy.addRoleAssignment(Security.util.SecurityCache.groupGuests, this.policy.noPermissionsRole);
            if (overwrite)
                policy.setModified(null);
            if (Ext4.isBoolean(this.policy.confirm))
                policy.policy.confirm = this.policy.confirm;
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
        if (this.getEl())
        {
            this.getEl().unmask();
        }
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
    }
});