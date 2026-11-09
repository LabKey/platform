/*
 * Copyright (c) 2016-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.import.OptionsPanel', {
    extend: 'Ext.form.Panel',

    cls: 'import-options-panel',

    border: false,
    bodyStyle: 'background-color: transparent;',
    baseHeight: null,

    formId: null,
    isProjectAdmin: false,
    canCreateSharedDatasets: false,
    isCreateSharedDatasets: false,
    isValidateQueries: true,
    isFailForUndefinedVisits: false,
    showFailForUndefinedVisits: true,
    isCloudRoot: false,

    initComponent: function()
    {
        this.items = [
            this.getMainFormView(),
            this.getSubmitButton()
        ];
        this.callParent();
    },

    getMainFormView : function()
    {
        if (!this.mainFormView)
        {
            this.mainFormView = Ext4.create('Ext.view.View', {
                store: this.getOptionsStore(),
                itemSelector: 'input',
                tpl: new Ext4.XTemplate(
                        '<tpl for=".">',
                        '<tpl if="hidden !== true">',
                        '<table cellpadding=0>',
                        ' <tpl if="header != null">',
                        '  <tr><td class="labkey-announcement-title" align=left><span>{header}</span></td></tr>',
                        '  <tr><td class="labkey-title-area-line"></td></tr>',
                        ' </tpl>',
                        ' <tr><td>{description}</td></tr>',
                        ' <tr><td class="import-options-form-cell">',
                        '  <label><input type="checkbox" id="{name}" name="{name}" value="true" {initChecked}>{label}</label>',
                        ' </td></tr>',
                        ' <tr><td class="import-options-form-cell">',
                        '  <div id="{name}-optionsForm"></div>',
                        ' </td></tr>',
                        '</table>',
                        '</tpl>',
                        '</tpl>'
                )
            });

            this.mainFormView.on('viewready', function()
            {
                // attach any optionsForm panels for the sections
                Ext4.each(this.getOptionsStore().getRange(), function(record)
                {
                    if (!record.get('hidden') && record.get('optionsForm') != null)
                        record.get('optionsForm').call(this, record.get('name') + '-optionsForm');
                }, this);
            }, this);

            this.mainFormView.on('itemclick', function(view, record)
            {
                var optionsForm = record.get('optionsForm') != null ? record.get('optionsForm').call(this) : null;
                if (optionsForm != null)
                {
                    var checked = Ext4.get(record.get('name')).dom.checked;
                    optionsForm.toggleState(checked);
                }

                this.updatePanelHeight();
            }, this);
        }

        return this.mainFormView;
    },

    getOptionsStore : function()
    {
        if (!this.optionsStore)
        {
            var data = [{
                    header: 'Validate Queries',
                    description: 'By default, queries will be validated upon import of a folder archive and any failure '
                            + 'to validate will cause the import job to raise an error. To suppress this validation step, uncheck '
                            + 'the box below.',
                    name: 'validateQueries',
                    initChecked: this.isValidateQueries ? "checked": "",
                    isChecked: this.isValidateQueries,
                    label: 'Validate all queries after import',
                    optionsForm: null
            }];

            if (this.showFailForUndefinedVisits) {
                data.splice(1, 0, {
                    header: 'Study Import Options',
                    description: 'By default, new visit rows will be created in the study during import for any dataset or specimen rows which have a new, undefined visit. '
                            + 'If, instead, you would like for the import of the folder archive to fail when it encounters a visit that is not already defined in the study '
                            + 'or as part of the incoming visit map, check the box below.',
                    name: 'failForUndefinedVisits',
                    initChecked: this.isFailForUndefinedVisits ? "checked": "",
                    isChecked: this.isFailForUndefinedVisits,
                    label: 'Fail import for undefined visits',
                    optionsForm: null
                });
            }

            if (this.canCreateSharedDatasets) {
                data.splice(0, 0, {
                    header: 'Shared Datasets',
                    description: 'By default, datasets will be created in this container. For Dataspace projects, shared datasets are '
                            + 'created at the project level so that they can be used by each of the study folders in the project.',
                    name: 'createSharedDatasets',
                    initChecked: this.isCreateSharedDatasets ? "checked": "",
                    isChecked: this.isCreateSharedDatasets,
                    label: 'Create shared datasets',
                    optionsForm: null
                });
            }

            this.optionsStore = Ext4.create('Ext.data.Store', {
                fields: ['header', 'description', 'name', 'initChecked', 'isChecked', 'label', 'hidden', 'optionsForm'],
                data: data
            });
        }

        return this.optionsStore;
    },

    updatePanelHeight : function()
    {
        if (this.baseHeight == null)
            this.baseHeight = this.getHeight();

        this.setHeight(this.baseHeight + this.getSubmitButton().getHeight());
    },

    getSubmitButton : function()
    {
        if (!this.submitButton)
        {
            this.submitButton = Ext4.create('Ext.button.Button', {
                text: 'Start Import',
                cls: 'import-options-form-btn',
                scope: this,
                handler: function()
                {
                    // call beforeSubmit for each optionsForm.
                    // if a section wants to display a confirmation message, gather those and ask if the user wants to proceed
                    var confirmMsgs = [];
                    Ext4.each(this.getOptionsStore().getRange(), function(record)
                    {
                        var optionsForm = record.get('optionsForm') != null ? record.get('optionsForm').call(this) : null;
                        if (optionsForm != null)
                        {
                            var msg = optionsForm.beforeSubmit();
                            if (msg)
                                confirmMsgs.push(msg);
                        }
                    }, this);

                    if (confirmMsgs.length > 0)
                    {
                        confirmMsgs.push("<br/>Would you like to proceed?");
                        Ext4.Msg.confirm("Confirmation", confirmMsgs.join('<br/>'), function(btnId)
                        {
                            if (btnId == 'yes')
                                document.getElementById('pipelineImportForm').submit();
                        });
                    }
                    else
                    {
                        document.getElementById('pipelineImportForm').submit();
                    }
                }
            })
        }

        return this.submitButton;
    }
});

Ext4.define('LABKEY.import.FolderTreeStore', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'containerPath', type: 'string'},
        {name: 'text', type: 'string'},
        {name: 'expanded', type: 'boolean'},
        {name: 'isProject', type: 'boolean'},
        {name: 'leaf', type: 'boolean'},
        {name: 'checked', type: 'boolean', defaultValue: false},
        {name: 'id'}
    ]
});