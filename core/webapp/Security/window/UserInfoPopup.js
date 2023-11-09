/*
 * Copyright (c) 2012-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Security.window.UserInfoPopup', {

    extend: 'Ext.window.Window',

    focusPrincipalComboBox : false,

    initComponent : function()
    {
        const config = this.initialConfig;
        this.userId = config.userId;
        if (!config.user && config.userId)
            this.user = this.cache.getPrincipal(config.userId);

        Ext4.applyIf(this, {
            title       : this.htmlEncode(this.user.Name) + ' Information',
            autoScroll  : true,
            closeable   : true,
            closeAction : 'close',
            constrain   : true,
            minWidth    : 200,
            width       : 600,
            height      : 450,
            minHeight   : 200,
            bodyStyle   : 'padding-left: 10px',
            buttons: [{
                text    : 'Done',
                handler : this.close,
                scope   : this
            }]
        });

        this.callParent(arguments);

        this.updateItems();
    },

    htmlEncode : function(value) {
        return Ext4.String.htmlEncode(value);
    },

    updateItems : function()
    {
        const toAdd = [];
        const isGroup = this.user.Type === 'g' || this.user.Type === 'r';
        let hdrHtml = "<span style='font-size: 14px; font-weight: bold;'>" + (isGroup?'Group ':'User ') + this.htmlEncode(this.user.Name) + "</span>";
        const container = (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId);

        // links
        if (isGroup)
        {

            if (this.user.UserId === Security.util.SecurityCache.groupUsers || this.user.UserId === Security.util.SecurityCache.groupGuests)
                this.canEdit = false;

            if (!LABKEY.user.isSystemAdmin && this.user.UserId === Security.util.SecurityCache.groupAdministrators)
                this.canEdit = false;

            if (this.canEdit)
            {
                const userContainer = this.user.Container === this.cache.projectId ? container : this.user.Container;

                hdrHtml += LABKEY.Utils.textLink({
                    text : 'manage group',
                    href : LABKEY.ActionURL.buildURL('security', 'group', userContainer || '/', {id: this.user.UserId}),
                    style: 'float: right'
                });
            }
            hdrHtml += LABKEY.Utils.textLink({
                text : 'permissions',
                href : LABKEY.ActionURL.buildURL('security', 'groupPermission', container, {id:this.user.UserId}),
                style: 'float: right',
                target : '_blank'
            });
        }
        else
        {
            hdrHtml += LABKEY.Utils.textLink({
                text : 'permissions',
                href : LABKEY.ActionURL.buildURL('user', 'userAccess', container, {userId:this.user.UserId}),
                style: 'float: right',
                target : '_blank'
            });
        }

        // Add header
        toAdd.push({html:hdrHtml, border: false, frame: false, padding: '5 0 0 0'});

        let i, user,
            id = Ext4.id(),
            html = '',
            groups = this.cache.getGroupsFor(this.userId),
            users = this.cache.getMembersOf(this.userId);

        // render a principals drop down
        if (isGroup && this.canEdit)
        {
            user = users.sort(function(a,b){
                //sort by type (site groups, project groups, then users) and name
                const A = a.Type + (a.Container == null ? "1" : "2") + a.Name.toUpperCase();
                const B = b.Type + (b.Container == null ? "1" : "2") + b.Name.toUpperCase();
                return A > B ? 1 : A < B ? -1 : 0;
            });

            toAdd.push({
                xtype  : 'labkey-principalcombo',
                itemId : 'addPrincipalComboBox',
                width  : 350,
                cache  : this.cache,
                forceSelection : false,
                emptyTextPrefix : 'Add',
                listeners: {
                    select     : this.Combo_onSelect,
                    specialkey : this.Combo_onKeyPress,
                    scope      : this
                    //TODO
                    //render: principalWrapper
                }
            });
        }

        if (groups.length)
        {
            html = '<p class="userinfoHdr">Member of</p><ul style="list-style-type: none;">';

            for (var g=0; g < groups.length; g++)
            {
                html += '<li>' + this.htmlEncode(groups[g].Name) + '</li>';
                // UNDONE: also render inherited groups (indented)?
            }

            html += '</ul>';

            toAdd.push({html: html, border: false, frame: false});
            html = '';
        }

        if (this.policy)
        {
            let ids      = this.cache.getEffectiveGroups(this.userId),
                roles    = this.policy.getEffectiveRolesForIds(ids),
                allRoles = this.cache.roles,
                role, r;

            if (allRoles.length > 0)
            {
                html = '<p class="userinfoHdr">Effective Roles</p><ul style="list-style-type: none;">';

                for (r=0; r < allRoles.length; r++)
                {
                    role = allRoles[r];
                    if (roles[role.uniqueName])
                        html += '<li>' + this.htmlEncode((role && role.name) ? role.name : role.uniqueName) + '</li>';
                }

                html += '</ul>';
                toAdd.push({html: html, border: false, frame: false});
                html = '';
            }
        }

        if (isGroup)
        {
            if (this.userId === Security.util.SecurityCache.groupUsers)
            {
                toAdd.push({html: '<p>Site Users represents all signed-in users.</p>', border: false, frame: false});
            }
            else {
                toAdd.push({html: '<p class="userinfoHdr">Members</p>', border: false, frame: false});
                if (this.canEdit) {
                    if (users.length == 0 && this.userId > 0) {
                        toAdd.push(Ext4.create('Ext.Button', {
                            text: "Delete Empty Group",
                            handler: Ext4.bind(this.DeleteGroup_onClick, this)
                        }));
                    }
                }

                let items = [];
                const canRemove = this.canEdit && (this.userId !== Security.util.SecurityCache.groupAdministrators || users.length > 1);
                for (i = 0; i < users.length; i++) {
                    user = users[i];

                    const isMemberGroup = user.Type === 'g' || user.Type === 'r';
                    html = '';
                    if (isMemberGroup) {
                        const urlGroup = LABKEY.ActionURL.buildURL('security', 'group', (user.Container ? (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId) : '/'), {id: user.UserId});
                        html += '<a style="font-size: 95%; font-weight: bold;" href="' + urlGroup + '">' + (user.Container ? "" : "Site:&nbsp;") + this.htmlEncode(user.Name) + '</a>';
                    }
                    else {
                        html += this.htmlEncode(user.Name);
                        // issue 17704, add display name for users
                        if (user.Name !== user.DisplayName && user.DisplayName && user.DisplayName.length > 0) {
                            html += " (" + this.htmlEncode(user.DisplayName) + ")"
                        }
                    }
                    items.push({html: html, border: false, frame: false});
                    html = '';

                    if (canRemove) {
                        const removeWrapper = '$remove$' + id + user.UserId;
                        items.push(Ext4.create('Ext.Button', {
                            text: "remove",
                            handler: Ext4.bind(this.RemoveMember_onClick, this, [user.UserId])
                        }));
                    }
                    else
                        items.push({html: '&nbsp;', border: false, frame: false});

                    let urlWindow;
                    if (isMemberGroup) {
                        urlWindow = LABKEY.ActionURL.buildURL('security', 'groupPermission', (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId), {id: user.UserId});
                    }
                    else {
                        urlWindow = LABKEY.ActionURL.buildURL('user', 'userAccess', (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId), {userId: user.UserId});
                    }
                    const markupOpenWindow = this.getOpenWindowMarkup('permissions', urlWindow);
                    items.push(markupOpenWindow);
                }

                items.push({html: '&nbsp;', width: "240", border: false, frame: false});
                items.push({html: '&nbsp;', width: "60", border: false, frame: false});
                items.push({html: '&nbsp;', width: "100", border: false, frame: false});

                const usersTable = Ext4.create('Ext.panel.Panel', {
                    width: 'auto',
                    height: 'auto',
                    minWidth: 200,
                    minHeight: 200,
                    border: false, frame: false,
                    layout: {
                        type: 'table',
                        columns: 3
                    },
                    defaults: {bodyStyle: 'padding:5px;'},
                    items: items
                });
                toAdd.push(usersTable);
            }
        }

        this.removeAll();
        this.add(toAdd);

        // issue 14310
        if (this.rendered && this.focusPrincipalComboBox)
        {
            this.down('#addPrincipalComboBox').focus(10);
            this.focusPrincipalComboBox = false;
        }
    },

    getOpenWindowMarkup : function(text, href) {
        const handler = function(){ window.open(href + '&_print=1','_blank','location=1,scrollbars=1,resizable=1,width=500,height=500'); };
        return Ext4.create('Ext.Button', {
            text : "view permissions...",
            border: false, frame: false,
            handler: handler,
            scope: this
        });
    },

    DeleteGroup_onClick : function()
    {
        const groupid = this.user.UserId;
        this.cache.deleteGroup(groupid, this.close, this);
    },

    RemoveMember_onClick : function(userid)
    {
        const groupid = this.user.UserId;
        this.cache.removeMembership(groupid, userid, this.updateItems, this);
    },

    Combo_onSelect : function(combo, records)
    {
        if (records && records.length)
        {
            const groupid = this.user.UserId;
            const userid = records[0].data.UserId;
            this.focusPrincipalComboBox = true;
            this.cache.addMembership(groupid, userid, this.updateItems, this);
        }
        combo.selectText();
    },

    Combo_onKeyPress : function(combo, e)
    {
        if (e.ENTER !== e.getKey())
            return;
        let email = combo.getValue();

        if (!email)
            return;

        email = email.trim();

        // the selected combo value is an existing group or user
        if (combo.getStore().find('Name', email) > -1)
            return;

        const config = {
            name : email,
            success : function(info) {
                if(info && info.users && info.users.length === 0){
                    Ext4.Msg.show({
                        title   : 'Create New User',
                        msg     : 'User was not found. Would you like to create the user for \'' + this.htmlEncode(email) + '\'?',
                        buttons : Ext4.MessageBox.YESNO,
                        width: 450,
                        fn : function(btn){
                            if (btn === 'yes'){
                                this.cache.createNewUser(email, true, function(user)
                                {
                                    const groupid = this.user.UserId;
                                    const userid  = user.UserId;
                                    this.cache.addMembership(groupid, userid, this.updateItems, this);
                                }, this);
                                combo.selectText();
                                combo.clearValue();
                            }
                        },
                        scope: this
                    });

                }
            },
            failure : function(errorinfo, response) {
                LABKEY.Utils.displayAjaxErrorResponse(errorinfo.exception, response);
            },
            scope : this
        };
        LABKEY.Security.getUsers(config)
    }
});
