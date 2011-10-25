//experimental

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
                columns: 2,
                width: 500,
                itemId: 'inputType',
                items: [{
                    boxLabel: 'Create New Experiment',
                    inputValue: 'new',
                    width: 250,
                    name: 'inputType',
                    checked: true
                },{
                    boxLabel: 'Add To Existing Experiment',
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
    },

    formSubmit: function(btn){
        var panel = btn.up('form') || btn.up('window');
        var type = panel.down('#inputType');

        if(type.getValue().inputType=='new'){
            LABKEY.Security.createContainer({
                isWorkbook: true,
                title: panel.down('#titleField').getValue(),
                description: panel.down('#descriptionField').getValue(),
                folderType: 'Expt Workbook',
                success: function(data){
                   this.doLoad(data.path);
                },
                scope: this,
                failure: LABKEY.Utils.onError
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
            this.doLoad('/' + LABKEY.ActionURL.getContainerName() + '/' + rec.data.Name)
        }
    },

    doLoad: function(containerPath){
        var controller = this.controller;
        var action = this.action;
        var params = this.urlParams;

        window.location = LABKEY.ActionURL.buildURL(controller, action, containerPath, params);
    },

    renderWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'form',
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
                value: LABKEY.Security.currentUser.displayName + ' ' + (new Date().format('Y-m-d'))
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'description',
                itemId: 'descriptionField',
                height: 200
            }]
        });
        target.doLayout();
    },
    renderExistingWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'labkey-combo',
            labelAlign: 'top',
            displayField: 'title',
            valueField: 'RowId',
            itemId: 'workbookName',
            fieldLabel: 'Choose Workbook',
            width: 400,
            queryMode: 'local',
            store: Ext4.create('LABKEY.ext4.Store', {
                schemaName: 'core',
                queryName: 'workbooks',
                columns: '*',
                autoLoad: true
            })
        },{
            xtype: 'checkbox',
            fieldLabel: 'My Workbooks Only',
            listeners: {
                change: function(btn, val){
                    var panel = btn.up('form');
                    var combo = panel.down('#workbookName');

                    if(val)
                        combo.store.filter('CreatedBy', LABKEY.Security.currentUser.id);
                    else
                        combo.store.clearFilter();
                }
            }
        });
        target.doLayout();
    },
    listeners: {
        render: function(){
            this.renderWorkbookForm();
        }
    }
});


Ext4.define('LABKEY.ext.ImportWizardWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-importwizardwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
//            frame: true,
            title: 'Import Data',
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
                buttons: []
            }],
            buttons: [{
                text: 'Submit'
                ,width: 50
                ,handler: function(btn){
                    var form = this.down('#theForm');
                    form.formSubmit.call(form, btn);
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.hide();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');
    }
});