/*
 * Copyright (c) 2011-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
    @param (String) [action] After a workbook is created, it will nagivate to this page
    @param (String) [controller] After a workbook is created, it will navigate to a URL using this controller
    @param (Object) [urlParams] (optional) After a workbook is created, it will navigate to a URL with these params
    @param {boolean} [canAddToExistingExperiment] If false, the radios to choose an existing experiment are hidden
    @param (String) [workbookFolderType] (optional) The folderType used for newly created workbooks.  If not provided, the default workbook will be used.
 */

Ext4.define('LABKEY.ext.ImportWizard', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-importwizard',
    initComponent: function(){
        Ext4.apply(this, {
            activeTab: 0,
            border: false,
            width: 620,
            frame: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'radiogroup',
                style: 'padding:5px;',
                hidden: this.canAddToExistingExperiment===false,
                columns: 2,
                width: 500,
                itemId: 'inputType',
                items: [{
                    boxLabel: 'Create New Workbook',
                    inputValue: 'new',
                    width: 250,
                    name: 'inputType',
                    checked: true
                },{
                    boxLabel: 'Add To Existing Workbook',
                    inputValue: 'existing',
                    width: 250,
                    name: 'inputType'
                }],
                listeners: {
                    change: {fn: function(btn, val){
                        var panel = btn.up('form');
                        if(val.inputType=='new')
                            panel.renderWorkbookForm.call(panel);
                        else
                            panel.renderExistingWorkbookForm.call(panel);
                    }, delay: 20}
                }

            },{
                itemId: 'renderArea',
                bodyStyle: 'padding: 5px;'
            }]
        });

        Ext4.applyIf(this, {
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.formSubmit
            },{
                text: 'Cancel',
                target: '_self',
                href: LABKEY.ActionURL.buildURL('project', 'home')
            }]
        });

        this.callParent(arguments);

        if((LABKEY.container && LABKEY.container.type == 'workbook')){
            this.doLoad(LABKEY.ActionURL.getContainer());
        }
    },

    formSubmit: function(btn){
        var panel = btn.up('form') || btn.up('window');
        var type = panel.down('#inputType');
        if(type.getValue().inputType=='new'){
            // this should really be enforced upstream.
            // it is possible some actions would be available to users w/ read access only, so we dont completely hide this dialog
            if(!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to create new workbooks.  Please choose an existing one.');
                return;
            }

            Ext4.Msg.wait('Creating workbook');

            LABKEY.Security.createContainer({
                isWorkbook: true,
                title: panel.down('#titleField').getValue(),
                description: panel.down('#descriptionField').getValue(),
                folderType: panel.workbookFolderType,
                success: function(data){
                    Ext4.Msg.hide();
                    this.doLoad(data.path);
                },
                scope: this,
                failure: function(error){
                    Ext4.Msg.hide();
                    if(error.exception)
                        alert(error.exception);
                    LABKEY.Utils.onError(error);
                }

            })
        }
        else {
            var combo = panel.down('#workbookName');
            var rowid = combo.getValue();
            if(!rowid){
                alert('Must pick a workbook');
                return;
            }

            var rec = combo.store.getAt(combo.store.find('RowId', rowid));
            if(!rec){
                alert('Must pick a workbook');
                return;
            }

            this.doLoad('/__r' + rec.get('RowId'));
        }
    },

    doLoad: function(containerPath){
        var controller = this.controller;
        var action = this.action;
        var urlParams = this.urlParams || {};

        window.location = LABKEY.ActionURL.buildURL(controller, action, containerPath, urlParams);
    },

    renderWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'form',
            itemId: 'workbookForm',
            border: false,
            defaults: {
                border: false,
                width: 600,
                labelAlign: 'top'
            },
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Title',
                name: 'title',
                itemId: 'titleField',
                value: LABKEY.Security.currentUser.displayName + ' ' + (Ext4.Date.format(new Date(),'Y-m-d'))
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'description',
                itemId: 'descriptionField',
                height: 200
            }]
        });
    },
    renderExistingWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'labkey-combo',
            labelAlign: 'top',
            displayField: 'Title',
            valueField: 'RowId',
            itemId: 'workbookName',
            fieldLabel: 'Choose Workbook',
            typeAhead: true,
            minChars: 0,
            listConfig: {
                itemTpl: Ext4.create('Ext.XTemplate', '<tpl for=".">{Title} ({Name})</tpl>').compile()
            },
            width: 400,
            queryMode: 'local',
            store: Ext4.create('LABKEY.ext4.data.Store', {
                schemaName: 'core',
                queryName: 'workbooks',
                columns: 'RowId,Title,CreatedBy,Name',
                sort: '-rowid',
                autoLoad: true
            })
        },{
            xtype: 'checkbox',
            fieldLabel: 'My Workbooks Only',
            labelWidth: 150,
            listeners: {
                change: function(btn, val){
                    var panel = btn.up('form');
                    var combo = panel.down('#workbookName');
                    combo.reset();

                    if(val)
                        combo.store.filter('CreatedBy', LABKEY.Security.currentUser.id);
                    else
                        combo.store.clearFilter();
                }
            }
        });
    },
    listeners: {
        beforerender: function(win){
            win.renderWorkbookForm();
        }
    }
});


Ext4.define('LABKEY.ext.ImportWizardWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-importwizardwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'destroy',
            title: this.title || 'Import Data',
            modal: true,
            items: [{
                xtype: 'labkey-importwizard',
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                frame: false,
                itemId: 'theForm',
                title: null,
                action: this.action,
                urlParams: this.urlParams,
                controller: this.controller,
                canAddToExistingExperiment: this.canAddToExistingExperiment,
                buttons: []
            }],
            listeners: {
                scope: this,
                delay: 100,
                show: function(win){
                    win.down('#titleField').focus(20);

                    new Ext4.util.KeyNav(win.getEl(), {
                        "enter" : function(e){
                            console.log('e')
                            var form = this.down('#theForm');
                            form.formSubmit.call(form, form);
                        },
                        scope : this
                    });

                }
            },
            buttons: [{
                text: 'Submit'
                ,width: 50
                ,handler: function(btn){
                    var form = this.down('#theForm');
                    form.formSubmit.call(form, btn);
                    this.close();
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.close();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');

        if((LABKEY.container && LABKEY.container.type == 'workbook')){
            this.on('beforeshow', function(){return false});
            var form = this.down('#theForm');
            form.doLoad.call(form, LABKEY.ActionURL.getContainer());
        }
    }
});