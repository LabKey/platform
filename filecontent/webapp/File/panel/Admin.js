/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Admin', {

    extend : 'Ext.tab.Panel',

    alias : ['widget.fileadmin'],

    constructor : function(config) {

        if (!config.pipelineFileProperties) {
            console.error('pipelineFileProperties required for', this.$className);
            return;
        }

        Ext4.apply(config, {
            defaults: {
                xtype: 'panel',
                border: false,
                margin: '5 0 0 0'
            }
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.items = [
            this.getActionsPanel(),
            this.getFilePropertiesPanel(),
            this.getToolBarPanel(),
            this.getGeneralSettingsPanel()
        ];

        this.resetDefaultsButton = Ext4.create('Ext.button.Button', {
            text: 'Reset To Default',
            handler: this.onResetDefaults,
            scope: this
        });

        this.buttons = [{
            text: 'submit',
            handler: this.onSubmit,
            scope: this
        },{
            text: 'cancel',
            handler: this.onCancel,
            scope: this
        }, this.resetDefaultsButton];

        this.callParent();
    },

    getFilePropertiesPanel : function() {
        if (!this.filePropertiesPanel) {
            this.filePropertiesPanel = Ext4.create('File.panel.FileProperties', {
                border: false,
                padding: 10,
                fileConfig: this.pipelineFileProperties.fileConfig,
                additionalPropertiesType: this.additionalPropertiesType,
                listeners : {
                    activate: function() { this.resetDefaultsButton.hide(); },
                    editfileproperties: this.onEditFileProperties,
                    scope: this
                },
                scope: this
            });
        }

        return this.filePropertiesPanel;
    },

    getToolBarPanel : function() {
        if (!this.toolBarPanel) {
            this.toolBarPanel = Ext4.create('File.panel.Toolbar', {
                title: 'Toolbar and Grid Settings',
                tbarActions: this.pipelineFileProperties.tbarActions,
                gridConfigs: this.pipelineFileProperties.gridConfig,
                useCustomProps: this.pipelineFileProperties.fileConfig === 'useCustom',
                fileProperties: this.fileProperties,
                listeners: {
                    activate: function() { this.resetDefaultsButton.show(); },
                    scope: this
                },
                scope: this
            });
        }
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

    getGeneralSettingsPanel : function() {
        if (!this.generalSettingsPanel) {

            //
            // We default to not showing the upload panel. If the user never set the property,
            // then it will not be present in the pipelineFileProperties.
            //
            var pfp = this.pipelineFileProperties;
            var checked = pfp.hasOwnProperty('expandFileUpload') ? pfp.expandFileUpload : false;

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
                items: [{
                    html: '<span class="labkey-strong">Configure General Settings</span>' +
                            '<p>Set the default File UI preferences for this folder.</p>',
                    border: false
                }, this.showUploadCheckBox],
                listeners: {
                    activate: function() { this.resetDefaultsButton.show(); },
                    scope: this
                },
                scope: this
            });
        }

        return this.generalSettingsPanel;
    },

    onCancel : function() {
        this.fireEvent('close');
    },

    onSubmit : function(button, event, handler) {
        var updateURL = LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath);
        var postData = {
            importDataEnabled : this.getActionsPanel().isImportDataEnabled(),
            expandFileUpload: this.showUploadCheckBox.getValue(),
            fileConfig: this.filePropertiesPanel.getFileConfig(),
            actions: this.getActionsPanel().getActionsForSubmission()
        };

        var toolbar = this.getToolBarPanel();

        if (toolbar.gridConfigsChanged()) {
            postData.gridConfig = toolbar.getGridConfigs();
        }

        if (toolbar.tbarChanged()) {
            postData.tbarActions = toolbar.getTbarActions();
        }

        if (!handler) {
            handler = function() {
                this.fireEvent('success', this.getToolBarPanel().gridConfigsChanged());
                this.fireEvent('close');
            };
        }

        Ext4.Ajax.request({
            url: updateURL,
            method: 'POST',
            scope: this,
            success: handler,
            failure: function() {
                console.log('Failure saving files webpart settings.');
                this.fireEvent('failure');
            },
            jsonData: postData
        });
    },

    onEditFileProperties : function() {
        this.onSubmit(null, null, function() {
            window.location = LABKEY.ActionURL.buildURL('fileContent', 'designer', this.containerPath, {
                'returnURL': window.location
            });
        });
    },

    onResetDefaults : function() {
        var tab = this.getActiveTab(),
            type,
            msg;
        if (tab.title === "Toolbar and Grid Settings") {
            type = 'tbar';
            msg = 'All grid and toolbar button customizations on this page will be deleted, continue?';
        }
        else if (tab.title === "Actions") {
            type = 'actions';
            msg = 'All action customizations on this page will be deleted, continue?';
        }
        else if (tab.title === "General Settings") {
            type = 'general';
            msg = 'All general settings on this page will be reset, continue?';
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
                console.log('Failed to request filecontent/resetFileOptions');
                console.log(json);
            },
            scope: this
        };

        Ext4.Msg.show({
            title: 'Confirm Reset',
            msg: msg,
            cls: 'data-window',
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
