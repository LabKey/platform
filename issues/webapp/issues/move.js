/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Issues.window.MoveIssue', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    width: 500,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Move Issue', // TODO: use entity noun here

    statics: {
        create : function(issueIds, issueDefName) {
            Ext4.onReady(function() {
                Ext4.create('Issues.window.MoveIssue', {issueIds: issueIds, issueDefName : issueDefName}).show();
            });
        }
    },

    initComponent : function() {
        this.buttons = ['->', {
            text: 'Move',
            scope: this,
            handler: this.handleMoveIssue
        },{
            text: 'Cancel',
            scope: this,
            handler: this.close
        }];

        this.items = [this.getPanel()];

        this.callParent();
        this.on('show', function() {
            this.moveCombo.focus(false, 500);
        }, this)
    },

    getPanel : function() {

        this.moveCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getUserStore(),
            name: 'moveIssueCombo',
            valueField: 'containerId',
            displayField: 'containerPath',
            fieldLabel: 'Target folder',
            triggerAction: 'all',
            labelWidth: 65,
            queryMode: 'local',
            typeAhead: true,
            msgTarget: 'under',
            forceSelection: false,
            validateOnChange: false,
            validateOnBlur: false,
            allowBlank: true,
            validator: function(val) {
                var index = this.getStore().findExact(this.displayField, val);
                return (index != -1) ? true : 'Invalid selection';
            },
            tpl: Ext4.create('Ext.XTemplate',
                    '<tpl for=".">',
                    '<div class="x4-boundlist-item">{containerPath:htmlEncode}</div>',
                    '</tpl>'
            )
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [{
                xtype: 'container',
                html: "<div>Select a folder from the list below and click the 'Move' button</div>",
                margin: '0 0 15 0'
            }, this.moveCombo]
        };
    },

    getUserStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('Issues.model.Containers')) {
            Ext4.define('Issues.model.Containers', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'containerId', type: 'string'},
                    {name: 'containerPath', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'Issues.model.Containers',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('issues', 'getMoveDestination', LABKEY.container.path, {issueDefName : this.issueDefName}),
                reader: {
                    type: 'json',
                    root: 'containers'
                }
            }
        });
    },

    handleMoveIssue: function(){
        if (!this.moveCombo.validate())
            return;

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('issues', 'move.api'),
            method: 'POST',
            params: {
                issueIds: this.issueIds,
                containerId: this.moveCombo.getValue(),
                returnUrl: window.location
            },
            scope: this,
            success: function(response) {
                window.location.reload();
            },
            failure: function(response){
                var jsonResp = LABKEY.Utils.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    Ext4.Msg.alert('Error', errorHTML);
                }
            }
        });
    }
});
