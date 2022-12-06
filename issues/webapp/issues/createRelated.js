/*
 * Copyright (c) 2016-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Issues.window.CreateRelatedIssue', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    closeAction: 'destroy',
    title: 'Create Related Issue',

    initComponent : function() {
        this.buttons = ['->',{
            text: 'Cancel',
            scope: this,
            handler: this.close
        }, {
            text: 'Create Issue',
            scope: this,
            handler: this.handleCreate
        }];

        this.items = [this.getPanel()];

        this.callParent();
        this.on('show', function() {
            this.createCombo.focus(false, 500);
        }, this)
    },

    getPanel : function() {

        var items = [];
        this.defaultRelatedFolder = this.params.defaultRelatedFolder;
        this.currentIssueDef = this.params.currentIssueDef;

        this.createCombo = Ext4.create('Ext.form.field.ComboBox', {
            store           : this.getStore(),
            valueField      : 'key',
            displayField    : 'displayName',
            fieldLabel      : 'Folder',
            triggerAction   : 'all',
            width           : 450,
            queryMode       : 'local',
            typeAhead       : true,
            msgTarget       : 'under',
            forceSelection  : false,
            validateOnChange: false,
            validateOnBlur  : false,
            allowBlank      : false,
            listeners : {
                scope: this,
                'change': function(cb, value) {
                    // translate the select target folder
                    var rec = cb.getStore().findRecord('key', value);
                    if (rec){
                        this.containerPath = rec.get('containerPath');
                        this.destIssueDefName = rec.get('issueDefName');
                    }
                }
            }
        });

        items.push({
            xtype: 'container',
            html: "<div>Select a folder from the list below and click the 'Create Issue' button</div>",
            margin: '0 0 15 0'
        });
        items.push(this.createCombo);

        // add the hidden form fields
        items.push({xtype : 'hidden', name : 'assignedTo', value: this.params.assignedTo});
        items.push({xtype : 'hidden', name : 'body', value: this.params.body});
        items.push({xtype : 'hidden', name : 'callbackURL', value: this.params.callbackURL});
        items.push({xtype : 'hidden', name : 'priority', value: this.params.priority});
        items.push({xtype : 'hidden', name : 'related', value: this.params.related});
        items.push({xtype : 'hidden', name : 'skipPost', value: this.params.skipPost});
        items.push({xtype : 'hidden', name : 'title', value: this.params.title});

        return {
            xtype   : 'form',
            padding : 10,
            border  : false,
            items   : items
        };
    },

    getStore: function(){
        // define data models
        if (!this.store) {
            if (!Ext4.ModelManager.isRegistered('Issues.model.Containers')) {
                Ext4.define('Issues.model.Containers', {
                    extend: 'Ext.data.Model',
                    fields: [
                        {name: 'key', type: 'string'},
                        {name: 'containerPath', type: 'string'},
                        {name: 'displayName', type: 'string'},
                        {name: 'issueDefName', type: 'string'}
                    ]
                });
            }

            this.store = Ext4.create('Ext.data.Store', {
                model: 'Issues.model.Containers',
                autoLoad: true,
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('issues', 'getRelatedFolder.api', LABKEY.container.path, {issueDefName : this.issueDefName}),
                    reader: {
                        type: 'json',
                        root: 'containers'
                    }
                },
                listeners: {
                    scope: this,
                    load: {
                        fn : function(cmp, records, success){
                            // if the configured related folder is available and accessible to the current user, select it
                            // by default, else just use this existing issue def
                            if (cmp.findRecord('key', this.defaultRelatedFolder)){
                                this.createCombo.setValue(this.defaultRelatedFolder);
                            }
                            else if (cmp.findRecord('key', this.currentIssueDef)){
                                this.createCombo.setValue(this.currentIssueDef);
                            }
                        }
                    }
                }
            });
        }
        return this.store;
    },

    handleCreate : function(){
        var formPanel = this.down('form');

        if (formPanel && formPanel.getForm() && formPanel.getForm().isValid()){
            var form = formPanel.getForm();
            form.submit({
                url : LABKEY.ActionURL.buildURL('issues', 'insert.view', this.containerPath, {issueDefName : this.destIssueDefName}),
                standardSubmit : true
            })
        }
    }
});
