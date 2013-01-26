/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Admin', {

    extend : 'Ext.tab.Panel',

    constructor : function(config) {

        Ext4.apply(config, {
            defaults: {
                xtype: 'panel',
                border: false,
                margin: '5 0 0 0'
            }
        });

        Ext4.applyIf(config, {

        });

        this.callParent([config]);

//        this.addEvents();
    },

    initComponent : function() {
        this.items = this.getItems();

        var submitButton = {
            xtype: 'button',
            text: 'submit',
            handler: this.onSubmit,
            scope: this
        };

        var cancelButton = {
            xtype: 'button',
            text: 'cancel',
            handler: this.onCancel,
            scope: this
        };


        this.buttons = [submitButton, cancelButton];
        this.callParent();
    },

    getItems: function(){
        return [
//            this.getActionsPanel(), TODO: Create the actions panel. Skipping for this sprint (13.1 Sprint 2)
            this.getFilePropertiesPanel(),
            this.getToolBarPanel(),
            this.getGeneralSettingsPanel()
        ];
    },

    getActionsPanel: function(){
        return {
            title: 'Actions',
            items: []
        };
    },

    getFilePropertiesPanel: function(){
        this.filePropertiesPanel = Ext4.create('File.panel.FileProperties', {
            border: false,
            padding: 10,
            fileConfig: this.pipelineFileProperties.fileConfig,
            additionalPropertiesType: this.additionalPropertiesType
        });

        this.filePropertiesPanel.on('editfileproperties', this.onEditFileProperties, this);

        return this.filePropertiesPanel;
    },

    getToolBarPanel: function(){
        console.log(this.pipelineFileProperties.tbarActions);
        return {
            title: 'Toolbar and Grid Settings',
            items: []
        };
    },

    getGeneralSettingsPanel: function(){
        if(!this.generalSettingsPanel){
            var descriptionText = {
                html: '<span class="labkey-strong">Configure General Settings</span>' +
                        '<br />' +
                        'Set the default File UI preferences for this folder.',
                border: false,
                height: 55,
                autoScroll:true
            };

            this.showUploadCheckBox = Ext4.create('Ext.form.field.Checkbox', {
                boxLabel: 'Show the file upload panel by default.',
                width: '100%',
                margin: '0 0 0 10',
                checked: this.pipelineFileProperties.expandFileUpload,
                name: 'showUpload'
            }); 

            this.generalSettingsPanel = Ext4.create('Ext.panel.Panel', {
                title: 'General Settings',
                border: false,
                padding: 10,
                items: [descriptionText, this.showUploadCheckBox]
            });
        }

        return this.generalSettingsPanel;
    },

    onCancel: function(){
        this.fireEvent('close');
    },

    onSubmit: function(button, event, handler){
        var updateURL = LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath);
        var postData = {
            expandFileUpload: this.showUploadCheckBox.getValue(),
            fileConfig: this.filePropertiesPanel.getFileConfig()/*,
            gridConfig: this.toolBarPanel.getGridConfig(),
            tbarActions: this.toolBarPanel.getTbarConfig(),
            actions: this.actionsPanel.getActionConfigs()*/
        };

        if(!handler){
            handler = function(){
                console.log("Save successful");
                this.fireEvent('success');
                this.fireEvent('close');
            }
        }

        Ext4.Ajax.request({
            url: updateURL,
            method: 'POST',
            scope: this,
            success: handler,
            failure: function(){
                console.log('Failure saving files webpart settings.');
                this.fireEvent('failure');
            },
            jsonData: postData
        });
    },

    onEditFileProperties: function(){
        // TODO: Save new settings and navigate to ecit properties page.
        var handler = function(){
            window.location = LABKEY.ActionURL.buildURL('fileContent', 'designer', this.containerPath, {'returnURL':window.location});
        };
        
        this.onSubmit(null, null, handler);
    }
});
