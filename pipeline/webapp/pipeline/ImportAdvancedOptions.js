/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.import.OptionsPanel', {
    extend: 'Ext.form.Panel',

    border: false,
    bodyStyle: 'background-color: transparent;',

    advancedImportOptionId: null,
    importers: [],

    canCreateSharedDatasets: false,
    isCreateSharedDatasets: false,
    isValidateQueries: true,
    isAdvancedImportOptions: false,

    initComponent: function()
    {
        this.items = [
            this.getMainFormView(),
            this.getAdvancedImportForm(),
            this.getSubmitButton()
        ];

        this.callParent();
    },

    getMainFormView : function()
    {
        if (!this.mainFormView)
        {
            var data = [{
                header: 'Validate Queries',
                description: 'By default, queries will be validated upon import of a study/folder archive and any failure '
                    + 'to validate will cause the import job to raise an error. To suppress this validation step, uncheck '
                    + 'the option below before clicking \'Start Import\'.',
                name: 'validateQueries',
                initChecked: this.isValidateQueries ? "checked": "",
                isChecked: this.isValidateQueries,
                label: 'Validate all queries after import',
                optionsForm: null
            },{
                header: 'Advanced Import Options',
                description: 'By default, all settings from the import archive will be used. If you would like to select a subset of '
                    + 'those import options, you can use the advanced import options section to see the full list of folder '
                    + 'archive settings to be imported.',
                name: 'advancedImportOptions',
                initChecked: this.isAdvancedImportOptions ? "checked": "",
                isChecked: this.isAdvancedImportOptions,
                label: 'Use advanced import options',
                optionsForm: this.getAdvancedImportForm
            }];

            if (this.canCreateSharedDatasets)
            {
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

            var store = Ext4.create('Ext.data.Store', {
                fields: ['header', 'description', 'name', 'initChecked', 'isChecked', 'label', 'include', 'optionsForm'],
                data: data
            });

            this.mainFormView = Ext4.create('Ext.view.View', {
                store: store,
                itemSelector: 'input',
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<table cellpadding=0>',
                        ' <tr><td class="labkey-announcement-title" align=left><span>{header}</span></td></tr>',
                        ' <tr><td class="labkey-title-area-line"></td></tr>',
                        ' <tr><td>{description}</td></tr>',
                        ' <tr><td class="main-form-cell">',
                        '  <label><input type="checkbox" class="main-input" id="{name}" name="{name}" value="true" {initChecked}>{label}</label>',
                        ' </td></tr>',
                        '</table>',
                    '</tpl>'
                )
            });

            this.mainFormView.on('itemclick', function(view, record)
            {
                if (record.get('optionsForm'))
                {
                    var optionsForm = record.get('optionsForm').call(this);
                    if (optionsForm)
                    {
                        var checked = Ext4.get(record.get('name')).dom.checked;
                        optionsForm.setVisible(checked);

                        // set all folder import type checkboxes to match this checked state
                        Ext4.each(document.getElementsByName('dataTypes'), function(input)
                        {
                            input.checked = checked;
                        });
                    }
                }
            }, this);
        }

        return this.mainFormView;
    },

    getAdvancedImportForm : function()
    {
        if (!this.advancedImportOptionsForm)
        {
            var advancedImportItems = [this.getImportOptionsHeaderConfig('Folder')],
                additionalImportItems = [];

            Ext4.each(this.importers, function(importer)
            {
                var dataType = importer['dataType'],
                        children = importer['children'];

                if (!Ext4.isArray(children))
                {
                    advancedImportItems.push(this.getImportOptionInputConfig(dataType));
                }
                else
                {
                    additionalImportItems.push(this.getImportOptionsHeaderConfig(dataType));
                    additionalImportItems.push(this.getImportOptionInputConfig(dataType, null, true));
                    Ext4.each(children, function(child)
                    {
                        additionalImportItems.push(this.getImportOptionInputConfig(child, dataType));
                    }, this);
                }
            }, this);

            // change the form panel layout based on how many columns we have
            var width = 310,
                layout = 'anchor',
                items = advancedImportItems;
            if (additionalImportItems.length > 0)
            {
                width = width + 275;
                layout = 'column';
                items = [{
                    border: false,
                    bodyStyle: 'padding-right: 25px;',
                    items: advancedImportItems
                },{
                    border: false,
                    items: additionalImportItems
                }];
            }

            this.advancedImportOptionsForm = Ext4.create('Ext.form.Panel', {
                renderTo: this.advancedImportOptionId,
                hidden: !this.isAdvancedImportOptions,
                cls: 'import-option-panel',
                width: width,
                layout: layout,
                items: items
            });
        }

        return this.advancedImportOptionsForm;
    },

    getImportOptionsHeaderConfig : function(header)
    {
        return {
            xtype: 'box',
            cls: 'import-option-header',
            html: '<div class="import-option-title">' + header + ' objects to import:</div>'
                + '<div class="labkey-title-area-line"></div>'
        };
    },

    getImportOptionInputConfig : function(dataType, parent, hide)
    {
        var checked = hide || !this.isAdvancedImportOptions ? '' : ' checked',
            parentAttr = parent ? 'parentDataType="' + parent + '"' : '';

        return {
            xtype: 'box',
            cls: hide ? 'import-option-hide' : 'import-option-input',
            html: '<label><input type="checkbox" name="dataTypes" '
                + 'value="' + dataType + '" ' + parentAttr + checked + '>' + dataType + '</label>'
        }
    },

    getSubmitButton : function()
    {
        if (!this.submitButton)
        {
            this.submitButton = Ext4.create('Ext.button.Button', {
                text: 'Start Import',
                cls: 'main-form-btn',
                scope: this,
                handler: function()
                {
                    // check any hidden parent dataType checkboxes that should be checked (i.e. has at least one child checked)
                    var checkboxInputs = {};
                    Ext4.each(document.getElementsByName('dataTypes'), function(input)
                    {
                        checkboxInputs[input.value] = input;

                        var parentDataType = input.getAttribute('parentDataType');
                        if (parentDataType && input.checked)
                            checkboxInputs[parentDataType].checked = true;
                    });

                    document.getElementById('pipelineImportForm').submit();
                }
            })
        }

        return this.submitButton;
    }
});