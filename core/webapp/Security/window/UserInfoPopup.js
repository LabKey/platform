/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Security.window.UserInfoPopup', {

    extend: 'Ext.window.Window',

    focusPrincipalComboBox : false,

    initComponent : function()
    {
        var config = this.initialConfig;
        this.userId = config.userId;
        if (!config.user && config.userId)
            this.user = this.cache.getPrincipal(config.userId);

        Ext4.applyIf(this, {
            title       : this.user.Name + ' Information',
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

    updateItems : function()
    {
        var toAdd = [],
            isGroup = this.user.Type == 'g' || this.user.Type == 'r',
            hdrHtml = "<span style='font-size: 14px; font-weight: bold;'>" + (isGroup?'Group ':'User ') + this.user.Name + "</span>",
            container = (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId);

        // links
        if (isGroup)
        {

            if (this.user.UserId == Security.util.SecurityCache.groupUsers || this.user.UserId == Security.util.SecurityCache.groupGuests)
                this.canEdit = false;

            if (!LABKEY.user.isSystemAdmin && (this.user.UserId == Security.util.SecurityCache.groupAdministrators
                    || this.user.UserId == Security.util.SecurityCache.groupDevelopers))
                this.canEdit = false;

            if (this.canEdit)
            {
                var userContainer = this.user.Container == this.cache.projectId ? container : this.user.Container;

                hdrHtml += LABKEY.Utils.textLink({
                    text : 'manage group',
                    href : LABKEY.ActionURL.buildURL('security', 'group', userContainer || '/', {id: this.user.UserId}),
                    style: 'float: right'
                });
            }
            if (this.user.UserId != Security.util.SecurityCache.groupDevelopers)
            {
                hdrHtml += LABKEY.Utils.textLink({
                    text : 'permissions',
                    href : LABKEY.ActionURL.buildURL('security', 'groupPermission', container, {id:this.user.UserId}),
                    style: 'float: right',
                    target : '_blank'
                });
            }
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

        var i, user,
            id = Ext4.id(),
            html = '',
            groups = this.cache.getGroupsFor(this.userId),
            principalWrapper,
            removeWrappers = [],
            deleteGroup,
            users = this.cache.getMembersOf(this.userId);

        // render a principals drop down
        if (isGroup && this.canEdit)
        {
            user = users.sort(function(a,b){
                //sort by type (site groups, project groups, then users) and name
                var A = a.Type + (a.Container == null ? "1" : "2") + a.Name.toUpperCase(), B = b.Type + (b.Container == null ? "1" : "2") + b.Name.toUpperCase();
                return A > B ? 1 : A < B ? -1 : 0;
            });

            toAdd.push({
                xtype  : 'labkey-principalcombo',
                itemId : 'addPrincipalComboBox',
                width  : 350,
                cache  : this.cache,
                forceSelection : false,
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
                html += '<li>' + groups[g].Name + '</li>';
                // UNDONE: also render inherited groups (indented)?
            }

            html += '</ul>';

            toAdd.push({html: html, border: false, frame: false});
            html = '';
        }

        if (this.policy)
        {
            var ids      = this.cache.getEffectiveGroups(this.userId),
                roles    = this.policy.getEffectiveRolesForIds(ids),
                allRoles = this.cache.roles,
                role, r;

            if (allRoles.length > 0)
            {
                html += '<p class="userinfoHdr">Effective Roles</p><ul style="list-style-type: none;">';

                for (r=0; r < allRoles.length; r++)
                {
                    role = allRoles[r];
                    if (roles[role.uniqueName])
                        html += '<li>' + ((role && role.name) ? role.name : role.uniqueName) + '</li>';
                }

                html += '</ul>';
            }
        }

        if (isGroup)
        {
            if (this.userId == Security.util.SecurityCache.groupUsers)
            {
                html += '<p>Site Users represents all signed-in users.</p>';
            }
            else
            {
                html += '<p class="userinfoHdr">Members</p><table class="userinfo">';
                if (this.canEdit)
                {
                    principalWrapper = '$p$' + id;
                    html += '<tr><td colspan="3" id="' + principalWrapper + '"></td></tr>';
                    if (users.length == 0 && this.userId > 0)
                    {
                        deleteGroup = '$delete$' + id;
                        html += '<tr><td colspan=3><a id="' + deleteGroup +'" class="labkey-button" href="#"><span>Delete Empty Group</span></a></td></tr>';
                    }
                }

                var canRemove = this.canEdit && (this.userId != Security.util.SecurityCache.groupAdministrators || users.length > 1);
                for (i=0; i < users.length; i++)
                {
                    user = users[i];
                    var isMemberGroup = user.Type == 'g' || user.Type == 'r';
                    html += '<tr><td>';
                    if (isMemberGroup)
                    {
                        var url = LABKEY.ActionURL.buildURL('security', 'group',(user.Container ? (this.cache.projectPath ? this.cache.projectPath : this.cache.projectId) : '/'),{id:user.UserId});
                        html += '<a style="font-size: 95%; font-weight: bold;" href="' + url + '">' + (user.Container ? "" : "Site:&nbsp;") + Ext4.String.htmlEncode(user.Name) + '</a>';
                    }
                    else
                    {
                        html += Ext4.String.htmlEncode(user.Name);
                        // issue 17704, add display name for users
                        if (user.Name != user.DisplayName && user.DisplayName && user.DisplayName.length > 0)
                        {
                            html += " (" + Ext4.String.htmlEncode(user.DisplayName) + ")"
                        }
                    }
                    html += '</td>';

                    if (canRemove)
                    {
                        var removeWrapper = '$remove$' + id + user.UserId;
                        html += '<td style="padding: 2px"><a class="labkey-button" href="#" id="' + removeWrapper + '"><span>remove</span></a></td>';
                        removeWrappers.push([removeWrapper, user.UserId]);
                    }
                    html += '<td>';

                    var url;
                    if (isMemberGroup)
                    {
                        url = LABKEY.ActionURL.buildURL('security','groupPermission',(this.cache.projectPath ? this.cache.projectPath : this.cache.projectId),{id:user.UserId});
                    }
                    else
                    {
                        url = LABKEY.ActionURL.buildURL('user','userAccess',(this.cache.projectPath ? this.cache.projectPath : this.cache.projectId),{userId:user.UserId});
                    }

                    html += this.getOpenWindowMarkup('permissions', url) + '</td></tr>';
                }
                html += '</table>';
            }
        }

        toAdd.push({
            html: html,
            border: false, frame: false,
            listeners : {
                afterlayout : function(p) {
                    var el;
                    for (var r=0; r < removeWrappers.length; r++)
                    {
                        el = Ext4.fly(removeWrappers[r][0]);
                        if (el)
                        {
                            // listeners for removing group members
                            el.dom.onclick = Ext4.bind(this.RemoveMember_onClick, this, [removeWrappers[r][1]]);
                        }
                    }

                    // listener for deleting group
                    if (deleteGroup)
                    {
                        el = Ext4.fly(deleteGroup);
                        if (el)
                        {
                            el.dom.onclick = Ext4.bind(this.DeleteGroup_onClick, this);
                        }
                    }
                },
                scope : this
            },
            scope : this
        });

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

        return LABKEY.Utils.textLink({
            text : text,
            href : '#',
            onClick : "(function(){ window.open('" + href + "&_print=1','_blank','location=1,scrollbars=1,resizable=1,width=500,height=500'); })();"
        });
    },

    DeleteGroup_onClick : function()
    {
        var groupid = this.user.UserId;
        this.cache.deleteGroup(groupid, this.close, this);
    },

    RemoveMember_onClick : function(userid)
    {
        var groupid = this.user.UserId;
        this.cache.removeMembership(groupid, userid, this.updateItems, this);
    },

    Combo_onSelect : function(combo, records, index)
    {
        if (records && records.length)
        {
            var groupid = this.user.UserId;
            var userid = records[0].data.UserId;
            this.focusPrincipalComboBox = true;
            this.cache.addMembership(groupid, userid, this.updateItems, this);
        }
        combo.selectText();
    },

    Combo_onKeyPress : function(combo, e)
    {
        if (e.ENTER != e.getKey())
            return;
        var email = combo.getValue();

        if (!email)
            return;

        email = email.trim();

        // the selected combo value is an existing group or user
        if (combo.getStore().find('Name', email) > -1)
            return;

        var config = {
            name : email,
            success : function(info, response) {
                if(info && info.users && info.users.length == 0){
                    Ext4.Msg.show({
                        title   : 'Create New User',
                        msg     : 'User was not found. Would you like to create the user for \'' + Ext4.String.htmlEncode(email) + '\'?',
                        buttons : Ext4.MessageBox.YESNO,
                        fn : function(btn, text){
                            if(btn == 'yes'){
                                this.cache.createNewUser(email, true, function(user)
                                {
                                    var groupid = this.user.UserId;
                                    var userid  = user.UserId;
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
        LABKEY.Security.getUsers(config);
    }
});
