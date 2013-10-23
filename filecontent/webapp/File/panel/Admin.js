/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Admin', {

    extend : 'Ext.tab.Panel',

    alias : ['widget.fileadmin'],

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
    },

    initComponent : function() {
        this.items = this.getItems();

        var submitButton = {
            text: 'submit',
            handler: this.onSubmit,
            scope: this
        };

        var cancelButton = {
            text: 'cancel',
            handler: this.onCancel,
            scope: this
        };

        this.resetDefaultsButton = Ext4.create('Ext.button.Button', {
            text: 'Reset To Default',
            handler: this.onResetDefaults,
            scope: this
        });


        this.buttons = [submitButton, cancelButton, this.resetDefaultsButton];
        this.callParent();
    },

    getItems: function(){
        return [
            this.getActionsPanel(),
            this.getFilePropertiesPanel(),
            this.getToolBarPanel(),
            this.getGeneralSettingsPanel()
        ];
    },

    getFilePropertiesPanel: function(){
        this.filePropertiesPanel = Ext4.create('File.panel.FileProperties', {
            border: false,
            padding: 10,
            fileConfig: this.pipelineFileProperties.fileConfig,
            additionalPropertiesType: this.additionalPropertiesType
        });

        this.filePropertiesPanel.on('editfileproperties', this.onEditFileProperties, this);

        this.filePropertiesPanel.on('activate', function(){
            this.resetDefaultsButton.hide();
        }, this);

        return this.filePropertiesPanel;
    },

    getToolBarPanel: function(){
        this.toolBarPanel = Ext4.create('File.panel.ToolbarPanel', {
            title : 'Toolbar and Grid Settings',
            tbarActions : this.pipelineFileProperties.tbarActions,
            gridConfigs : this.pipelineFileProperties.gridConfig,
            useCustomProps: this.pipelineFileProperties.fileConfig === 'useCustom',
            fileProperties : this.fileProperties
        });

        this.toolBarPanel.on('activate', function(){
            this.resetDefaultsButton.show();
        }, this);
        return this.toolBarPanel;
    },

    getActionsPanel : function() {
        if (!this.actionsPanel) {
            this.actionsPanel = Ext4.create('File.panel.Actions', {
                title : 'Actions',
                containerPath : this.containerPath,
                isPipelineRoot : this.isPipelineRoot,
                importDataEnabled: this.pipelineFileProperties.importDataEnabled
            });
        }
        return this.actionsPanel;
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

            // We default to opening the upload panel. If the user never set the property, then it will not be present
            // in the pipelineFileProperties.
            var checked = this.pipelineFileProperties.hasOwnProperty('expandFileUpload') ? this.pipelineFileProperties.expandFileUpload : true;

            this.showUploadCheckBox = Ext4.create('Ext.form.field.Checkbox', {
                boxLabel: 'Show the file upload panel by default.',
                width: '100%',
                margin: '0 0 0 10',
                checked: checked,
                name: 'showUpload'
            });

            this.generalSettingsPanel = Ext4.create('Ext.panel.Panel', {
                title: 'General Settings',
                border: false,
                padding: 10,
                items: [descriptionText, this.showUploadCheckBox]
            });
        }

        this.generalSettingsPanel.on('activate', function(){
            this.resetDefaultsButton.hide();
        }, this);

        return this.generalSettingsPanel;
    },

    onCancel: function(){
        this.fireEvent('close');
    },

    onSubmit: function(button, event, handler){
        var updateURL = LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath);
        var postData = {
            importDataEnabled : this.actionsPanel.getComponent('showImportCheckbox').getValue(), // terrible
            expandFileUpload: this.showUploadCheckBox.getValue(),
            fileConfig: this.filePropertiesPanel.getFileConfig(),
            actions: this.actionsPanel.getActionsForSubmission()
        };

        if(this.toolBarPanel.gridConfigsChanged())
        {
            postData.gridConfig = this.toolBarPanel.getGridConfigs();
        }
        if(this.toolBarPanel.tbarChanged())
        {
            postData.tbarActions = this.toolBarPanel.getTbarActions();
        }

        if(!handler){
            handler = function(){
                this.fireEvent('success', this.toolBarPanel.gridConfigsChanged());
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
    },

    onResetDefaults: function(){
        var tab = this.getActiveTab(),
            type,
            msg;
        if(tab.title === "Toolbar and Grid Settings"){
            type = 'tbar';
            msg = 'All grid and toolbar button customizations on this page will be deleted, continue?';
        } else if(tab.title === "Actions"){
            type = 'actions';
            msg = 'All action customizations on this page will be deleted, continue?';
        }

        var requestConfig = {
            url: LABKEY.ActionURL.buildURL('filecontent', 'resetFileOptions', null, {type:type}),
            method: 'POST',
            success: function(response){
                this.fireEvent('success', true);
                this.fireEvent('close');
            },
            failure: function(response){
                var json = Ext4.JSON.decode(response.responseText);
                console.log(json);
            },
            scope: this
        };

        Ext4.Msg.show({
            title: 'Confirm Reset',
            msg: msg,
            buttons: Ext4.Msg.YESNO,
            icon: Ext4.Msg.QUESTION,
            fn: function(choice){
                if(choice === 'yes'){
                    Ext4.Ajax.request(requestConfig);
                }
            }
        });
    }
});
