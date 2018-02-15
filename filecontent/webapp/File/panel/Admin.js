/*
 * Copyright (c) 2013-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Admin', {

    extend : 'Ext.tab.Panel',

    alias : ['widget.fileadmin'],

    isPipelineRoot: false,

    constructor : function(config) {

        if (!config.fileProperties) {
            console.error('fileProperties required for', this.$className);
            return;
        }
        if (!config.pipelineFileProperties) {
            console.error('pipelineFileProperties required for', this.$className);
            return;
        }

        this.callParent([config]);

        this.addEvents('success', 'failure', 'cancel');
    },

    initComponent : function() {
        this.items = [
            this.getActionsPanel(),
            this.getFilePropertiesPanel(),
            this.getToolBarPanel(),
            this.getGeneralSettingsPanel()
        ];

        this.buttons = [{
            text: 'submit',
            handler: function() { this.onSubmit(); },
            scope: this
        },{
            text: 'cancel',
            handler: this.onCancel,
            scope: this
        },{
            text: 'Reset to Default',
            handler: this.onResetDefaults,
            listeners: {
                afterrender: function(btn) {
                    this.on('tabchange', function(panel, active) {
                        if (active && active.itemId === 'filePropertiesPanel') {
                            btn.hide();
                        }
                        else {
                            btn.show();
                        }
                    });
                },
                scope: this
            },
            scope: this
        }];

        this.callParent();
    },

    getFilePropertiesPanel : function() {
        if (!this.filePropertiesPanel) {
            this.filePropertiesPanel = Ext4.create('File.panel.FileProperties', {
                itemId: 'filePropertiesPanel',
                border: false,
                padding: 10,
                fileConfig: this.pipelineFileProperties.fileConfig,
                additionalPropertiesType: this.additionalPropertiesType,
                listeners : {
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
                itemId: 'toolBarPanel',
                optionsType: 'tbar',
                optionsMsg: 'All grid and toolbar button customizations on this page will be deleted, continue?',
                tbarActions: this.pipelineFileProperties.tbarActions,
                gridConfigs: this.pipelineFileProperties.gridConfig,
                useCustomProps: this.pipelineFileProperties.fileConfig === 'useCustom',
                fileProperties: this.fileProperties,
                disableFileUpload: this.disableFileUpload,
                scope: this
            });
        }
        return this.toolBarPanel;
    },

    getActionsPanel : function() {
        if (!this.actionsPanel) {
            this.actionsPanel = Ext4.create('File.panel.Actions', {
                itemId: 'actionsPanel',
                title : 'Actions',
                optionsType: 'actions',
                optionsMsg: 'All action customizations on this page will be deleted, continue?',
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
            this.showUploadCheckBox = Ext4.create('Ext.form.field.Checkbox', {
                boxLabel: 'Show the file upload panel by default.',
                width: '100%',
                margin: '0 0 0 10',
                checked: this.pipelineFileProperties.expandFileUpload === true,
                name: 'showUpload'
            });

            this.generalSettingsPanel = Ext4.create('Ext.panel.Panel', {
                itemId: 'generalSettingsPanel',
                title: 'General Settings',
                optionsType: 'general',
                optionsMsg: 'All general settings on this page will be reset, continue?',
                border: false,
                padding: 10,
                items: [{
                    xtype: 'box',
                    tpl: new Ext4.XTemplate(
                        '<span class="labkey-strong">Configure General Settings</span>',
                        '<p>Set the default File UI preferences for this folder.</p>'
                    ),
                    data: {},
                    border: false
                }, this.showUploadCheckBox]
            });
        }

        return this.generalSettingsPanel;
    },

    onCancel : function() {
        this.fireEvent('cancel', this);
    },

    onSubmit : function(onSuccess, scope) {

        var postData = {
            importDataEnabled : this.getActionsPanel().isImportDataEnabled(),
            expandFileUpload: this.showUploadCheckBox.getValue(),
            fileConfig: this.filePropertiesPanel.getFileConfig(),
            actions: this.getActionsPanel().getActionsForSubmission()
        };

        var toolbar = this.getToolBarPanel(),
            gridChanged = toolbar.isGridColumnsChanged(),
            toolbarChanged = toolbar.isToolbarChanged();

        if (gridChanged) {
            postData.gridConfig = toolbar.getGridConfigs();
        }

        if (toolbarChanged) {
            postData.tbarActions = toolbar.getActions();
        }

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath),
            method: 'POST',
            jsonData: postData,
            success: function(response) {
                this.fireEvent('success', this, {
                    gridChanged: true, // 32612: File config changes may have modified the set of default columns
                    toolbarChanged: toolbarChanged,
                    expandUploadChanged: postData.expandFileUpload !== this.pipelineFileProperties.expandFileUpload
                });

                if (Ext4.isFunction(onSuccess)) {
                    onSuccess.call(scope || this, response);
                }
            },
            failure: function() {
                console.log('Failure saving files webpart settings.');
                this.fireEvent('failure', this);
            },
            scope: this
        });
    },

    onEditFileProperties : function() {
        this.onSubmit(function() {
            window.location = LABKEY.ActionURL.buildURL('fileContent', 'designer', this.containerPath, {
                returnURL: window.location
            });
        });
    },

    onResetDefaults : function() {
        var tab = this.getActiveTab();

        var requestConfig = {
            url: LABKEY.ActionURL.buildURL('filecontent', 'resetFileOptions', null, {type: tab.optionsType}),
            method: 'POST',
            success: function(response) {

                // just say all the things changed
                this.fireEvent('success', this, {
                    gridChanged: true,
                    toolbarChanged: true,
                    uploadChanged: true
                });
            },
            failure: function(response) {
                Ext4.Msg.alert('Failed to reset file options.');
                console.log(Ext4.decode(response.responseText));
            },
            scope: this
        };

        Ext4.Msg.show({
            title: 'Confirm Reset',
            msg: tab.optionsMsg,
            cls: 'data-window',
            buttons: Ext4.Msg.YESNO,
            icon: Ext4.Msg.QUESTION,
            fn: function(choice) {
                if (choice === 'yes') {
                    Ext4.Ajax.request(requestConfig);
                }
            }
        });
    }
});
