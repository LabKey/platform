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
            this._redraw();
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
        var cb = this.down('#inheritedCheckbox');
        if (cb && cb.getValue() != this.inheritedOriginally)
            return true;
        return this.policy && this.policy.isDirty();
    },


    setResource : function(id)
    {
        this.cache.onReady(function(){
            this.resource = this.cache.getResource(id);
            LABKEY.Security.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
        },this);
    },


    setInheritedPolicy : function(policy)
    {
        this.inheritedPolicy = policy;
        if (this.getInheritCheckboxValue())
            this._redraw();
        if (this.down('#inheritedCheckbox'))
            this.down('#inheritedCheckbox').enable();
    },


    setPolicy : function(policy, roles)
    {
        this.inheritedOriginally = policy.isInherited();
        if (this.down('#inheritedCheckbox'))
            this.down('#inheritedCheckbox').setValue(this.inheritedOriginally);

        if (policy.isInherited())
        {
            this.inheritedPolicy = policy;
            this.policy = policy.copy(this.resource.id);
            this.policy.policy.modified = null; // UNDONE: make overwrite explicit in savePolicy
            if (this.down('#inheritedCheckbox'))
                this.down('#inheritedCheckbox').enable();
        }
        else
        {
            this.policy = policy;
            // we'd still like to get the inherited policy
            if (this.resource.parentId && this.resource.parentId != this.cache.rootId)
                LABKEY.Security.getPolicy({resourceId:this.resource.parentId, containerPath:this.resource.parentId, successCallback:this.setInheritedPolicy,
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
        if (this.getInheritCheckboxValue())
            return null;
        var policy = this.policy.copy();
        if (policy.isEmpty())
            policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
        return policy;
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

    _redraw : function()
    {
        if (!this.rendered)
            return;
        if (!this.roles)
        {
            this.add({html: "<i>Loading...</i>"});
            return;
        }

        var r, role, ct;

        var toAdd = [{
            layout: 'hbox',
            hidden: this.saveButton === false,
            border: false,
            defaults: {
                style: {margin:'5px'}
            },
            items: [{
                xtype: 'button',
                text: 'Save and Finish',
                handler: function(){
                    this.save(false, function(){
                        LABKEY.setSubmit(true);
                        window.location = this.doneURL;
                    })
                },
                scope: this
            },{
                xtype: 'button',
                text: 'Save',
                handler: this.SaveButton_onClick,
                scope: this
            },{
                xtype: 'button',
                text: 'Cancel',
                handler: this.cancel,
                scope: this
            }]
        },{
            xtype: 'labkey-linkbutton',
            href: LABKEY.ActionURL.buildURL('security', 'folderAccess', LABKEY.ActionURL.getContainer()),
            style: 'margin-left:5px;',
            linkCls: 'labkey-text-link',
            text: 'view permissions report'
        }];

        if (this.canInherit)
        {
            toAdd.push({
                xtype: 'checkbox',
                itemId: 'inheritedCheckbox',
                boxLabel: "Inherit permissions from " + (this.parentName || 'parent'),
                listeners: {
                    check: this.Inherited_onChange,
                    scope: this
                },
                style: {display:'inline', 'margin-left':5},
                disabled: !this.inheritedPolicy,
                checked: this.inheritedOriginally
            });
        }

        var roleRows = [{
            layout: 'hbox',
            defaults: {border: false},
            itemId: 'header',
            items: [{
                html: '<h3>Roles</h3>',
                width: 300
            },{
                html: '<h3>Groups</h3>'
            }]
        }];

        for (r=0 ; r<this.roles.length ; r++){
            role = this.roles[r];
            roleRows.push({
                layout: 'hbox',
                itemId: role.uniqueName.replace(/\./g, '_'),
                roleId: role.uniqueName,
                border: true,
                bodyStyle: 'padding: 5px',
                defaults: {border: false},
                items: [{
                    html: '<div><h3 class="rn">' + role.name + '</h3><div class="rd">' + role.description + '</div></div>',
                    //TODO
                    cls: 'rn',
                    width: 300
                },{
                    xtype: 'container',
                    items: [{
                        xtype  : 'container',
                        layout : 'hbox',
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
                    listeners: {
                        render: function(panel) {
                            this.addDD(panel.getEl(), role);
                        },
                        scope: this
                    },
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

        Ext4.suspendLayouts();
        this.removeAll();
        this.add(toAdd);
        Ext4.resumeLayouts(true);

        // DropSource (whole editor)
        Ext4.create('Security.dd.ButtonsDragDropZone', {
            container: this
        });

        // render security policy
        var policy = this.getInheritCheckboxValue() ? this.inheritedPolicy : this.policy;
        if (policy)
        {
            // render the security policy buttons
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                var groupIds = policy.getAssignedPrincipals(role.uniqueName);

                //resolve groupids into group objects
                var groups = [];
                var group;
                var idx;
                for (idx=0; idx < groupIds.length; idx++)
                {
                    group = this.cache.getPrincipal(groupIds[idx]);
                    if (!group) continue;
                    groups.push(group);
                }

                //sort groups
                groups.sort(function(g1, g2){
                    return g1.Name.localeCompare(g2.Name); //CONSIDER: should this be sorted only by name, or by type then name?
                });

                //add button for each group
                for (idx=0 ; idx<groups.length ; idx++)
                {
                    this.addButton(groups[idx],role,false);
                }
            }
//            // make selenium testing easiers
//            if (!Ext4.get('policyRendered'))
//                Ext4.DomHelper.insertHtml('beforeend', document.body, '<input type=hidden id="policyRendered" value="1">');
        }
        if (this.getInheritCheckboxValue())
            this.disable();
        else
            this.enable();
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
                console.log('drop ' + (e.shiftKey?'SHIFT ':'') + data.text + ' ' + data.groupId + ' ' + data.roleId);
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
        return this.canInherit && this.down('#inheritedCheckbox').getValue();
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
        var policy = this.getInheritCheckboxValue() ? this.inheritedPolicy : this.policy;
        // is this a user or a group
        var principal = this.cache.getPrincipal(id);
        // can edit?
        var canEdit = !principal.Container && this.isSiteAdmin || principal.Container && this.isProjectAdmin;
        var w = Ext4.create('Security.window.UserInfoPopup', {userId:id, cache:this.cache, policy:policy, modal:true, canEdit:canEdit});
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
//        this._redraw();
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
            xtype : 'button',
//            xtype   : 'labkey-closebutton',
//            cls     : 'principalButton',
            iconCls   : 'closeicon',
            iconAlign : 'right',
            margin  : '5 5 5 0',
            text    : groupName,
            itemId  : btnId,
            groupId : groupId,
            roleId  : roleId,
            tooltip : tooltip,
            closing : false,
            closeTooltip: 'Remove ' + groupName + ' from' + (roleName ? (' ' + roleName) : '') + ' role',
            listeners : {
                afterrender : function(b) {
                    Ext4.DomQuery.select('span.closeicon', b.getEl().id)[0].onclick = Ext4.bind(this.Button_onClose, this, [b]);
                    zz = b;
                },
                scope : this
            }
        });

        button.on("close", this.Button_onClose, this);
        button.on("click", this.Button_onClick, this);
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
            console.error('unable to find button');
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

    SaveButton_onClick : function(e)
    {
        this.save(false);
    },

    save : function(overwrite, success, scope)
    {
        Ext4.removeNode(document.getElementById('policyRendered')); // to aid selenium automation

        success = success || this.saveSuccess;
        scope = scope || this;

        var policy = this.getPolicy();
        this.disable();
        if (!policy)
        {
            LABKEY.Security.deletePolicy({resourceId:this.resource.id, successCallback:success, errorCallback:this.saveFail, scope:scope});
        }
        else
        {
            this._removeInvalidRoles(policy, this.roles);
            if (policy.isEmpty())
                policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
            if (overwrite)
                policy.setModified(null);
            LABKEY.Security.savePolicy({policy:policy, successCallback:success, errorCallback:this.saveFail, scope:scope});
        }
    },


    _removeInvalidRoles : function(policy, roles)
    {
        var i;
        var validUniqueRoles = {};
        for (i=0 ; i<roles.length; i++)
            validUniqueRoles[roles[i].uniqueName] = true;
        var a = [], from = policy.policy.assignments;
        for (i=0 ; i<from.length ; i++)
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
        // reload policy
        LABKEY.Security.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
        // feedback
        var mb = Ext4.MessageBox.show({title : 'Save', msg:'<div align=center><span style="color:green; font-weight:bold; font-size:133%;">save successful</span></div>', width:150, animEl:this.saveButton});
        var save = mb.getEl().getStyles();
        mb.getEl().pause(1);
        mb.getEl().fadeOut({callback:function(){mb.hide(); mb.getEl().applyStyles(save);}, scope:mb});
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
            LABKEY.Security.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
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

//function _link(text,href)
//{
//    return LABKEY.Utils.textLink({
//        href : href || '#',
//        text : text
//    });
//}
//function $open(href)
//{
//    window.open(href+'&_print=1','_blank','location=1,scrollbars=1,resizable=1,width=500,height=500');
//}
//function _open(text,href)
//{
//    return LABKEY.Utils.textLink({
//        href : '#',
//        onClick : "$open('" + href + "')",
//        text : text
//    });
//}

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

    extend: 'Ext.button.Button',

    alias: 'widget.labkey-closebutton',

//    tpl [
//        '<span id="{4}" class="{3}"><table cellpadding="0" cellspacing="0" class="x-btn x-btn-noicon" width="auto" style="float:left; margin-right:5px;"><tbody>',
//        '<tr><td class="x-btn-tl"><i>&nbsp;</i></td><td class="x-btn-tc" colspan="2"></td><td class="x-btn-tr"><i>&nbsp;</i></td></tr>',
//        '<tr><td class="x-btn-ml"><i>&nbsp;</i></td><td class="x-btn-mc"><em unselectable="on"><button class="x-btn-text" type="{1}">{0}</button></em><td class="x-btn-mc"><i class="pclose">&#160;</i></td><td class="x-btn-mr"><i>&nbsp;</i></td></tr>',
//        '<tr><td class="x-btn-bl"><i>&nbsp;</i></td><td class="x-btn-bc" colspan="2"></td><td class="x-btn-br"><i>&nbsp;</i></td></tr>',
//        "</tbody></table><span>"
//    ]
//    // add &nbsp;
//    templateIE : new Ext4.Template(
//            '<span id="{4}" class="{3}"><table cellpadding="0" cellspacing="0" class="x-btn x-btn-noicon" width="auto" style="float:left; margin-right:5px;"><tbody>',
//            '<tr><td class="x-btn-tl"><i>&nbsp;</i></td><td class="x-btn-tc" colspan="2"></td><td class="x-btn-tr"><i>&nbsp;</i></td></tr>',
//            '<tr><td class="x-btn-ml"><i>&nbsp;</i></td><td class="x-btn-mc"><em unselectable="on"><button class="x-btn-text" type="{1}">{0}</button></em><td class="x-btn-mc"><i class="pclose">&#160;</i></td><td class="x-btn-mr"><i>&nbsp;</i></td></tr>',
//            '<tr><td class="x-btn-bl"><i>&nbsp;</i></td><td class="x-btn-bc" colspan="2"></td><td class="x-btn-br"><i>&nbsp;</i></td></tr>',
//            "</tbody></table>&nbsp;<span>"),

//    renderTpl: [
//        '<em id="{id}-btnWrap"<tpl if="splitCls"> class="{splitCls}"</tpl>>',
//        '<button id="{id}-btnEl" type="{type}" class="{btnCls}" hidefocus="true"',
//            // the autocomplete="off" is required to prevent Firefox from remembering
//            // the button's disabled state between page reloads.
//            '<tpl if="tabIndex"> tabIndex="{tabIndex}"</tpl>',
//            '<tpl if="disabled"> disabled="disabled"</tpl>',
//            ' role="button" autocomplete="off">',
//            '<span id="{id}-btnInnerEl" class="{baseCls}-inner">',
//                '{text}',
//            '</span>',
//            '<i id="{id}-btnIconEl" class="{baseCls}-icon {iconCls}"></i>',
//        '</button>',
//        '</em>'
//    ],
//
//    childEls: [
//        'btnEl', 'btnWrap', 'btnInnerEl', 'btnIconEl', 'closeEl'
//    ],

    stoppedEvent : null,

    initComponent : function()
    {
        Ext4.apply(this, {
            closable: true,
            closeText: "Close",
            iconCls: 'pClose'
        });

        this.callParent();
        this.addEvents('close');
    },

    getTemplateArgs: function() {
        var me = this,
            result = me.callParent();

        result.closable = true;
        result.closeText = me.closeText;

        return result;
    },

    onRender : function(ct, position)
    {
        this.callParent(arguments);
        // find the close element
//        var close = this.el.child('I[class=pclose]');
//        if (close)
//        {
//            close.on("click",this.onClose,this);
//            if (this.tooltip)
//            {
//                if (typeof this.closeTooltip == 'object')
//                {
//                    Ext4.QuickTips.register(Ext4.apply({target: close}, this.closeTooltip));
//                }
//                else
//                {
//                    close.dom[this.tooltipType] = this.closeTooltip;
//                }
//            }
//        }
    },

    onClose : function(event)
    {
        if (this.disabled)
            return;
        // can't seem to actually stop mousedown events, but we can disable the button
        //        event.stopEvent();
        this.stoppedEvent = event;
        this.fireEvent('close', this, event);
    },

    onClick : function(event)
    {
        if (!this.stoppedEvent || event.type != 'click')
            this.callParent(arguments);
        this.stoppedEvent = null;
    }
});