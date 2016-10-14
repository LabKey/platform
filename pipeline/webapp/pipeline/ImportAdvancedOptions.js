/*
 * Copyright (c) 2016 LabKey Corporation
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
    isSpecificImportOptions: false,
    isApplyToMultipleFolders: false,

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
                description: 'By default, queries will be validated upon import of a study/folder archive and any failure '
                    + 'to validate will cause the import job to raise an error. To suppress this validation step, uncheck '
                    + 'the box below.',
                name: 'validateQueries',
                initChecked: this.isValidateQueries ? "checked": "",
                isChecked: this.isValidateQueries,
                label: 'Validate all queries after import',
                optionsForm: null
            },{
                header: 'Advanced Import Options',
                description: 'By default, all objects and settings from the import archive will be used. If you would '
                    + 'like to select a subset of those import objects, check the box below to see the full list of '
                    + 'folder archive objects to be imported.',
                name: 'specificImportOptions',
                initChecked: this.isSpecificImportOptions ? "checked": "",
                isChecked: this.isSpecificImportOptions,
                label: 'Select specific objects to import',
                optionsForm: this.getSpecificImportOptionsForm
            },{
                header: null,
                description: 'By default, the imported archive is only applied to the current folder. If you would like to '
                    + 'apply this imported archive to multiple folders, check the box below to see additional folders for this project. '
                    + 'The import archive will be applied to all selected folders.',
                name: 'applyToMultipleFolders',
                hidden: !this.isProjectAdmin,
                initChecked: this.isApplyToMultipleFolders ? "checked": "",
                isChecked: this.isApplyToMultipleFolders,
                label: 'Apply to multiple folders',
                optionsForm: this.getApplyToMultipleFoldersForm
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

            this.optionsStore = Ext4.create('Ext.data.Store', {
                fields: ['header', 'description', 'name', 'initChecked', 'isChecked', 'label', 'hidden', 'optionsForm'],
                data: data
            });
        }

        return this.optionsStore;
    },

    getSpecificImportOptionsForm : function(renderTo)
    {
        if (renderTo)
        {
            this.specificImportOptionsForm = Ext4.create('LABKEY.import.SpecificImportOptions', {
                importers: this.importers,
                hidden: !this.isSpecificImportOptions
            });

            this.specificImportOptionsForm.on('render', this.updatePanelHeight, this);
            this.specificImportOptionsForm.render(renderTo);
        }

        return this.specificImportOptionsForm;
    },

    getApplyToMultipleFoldersForm : function(renderTo)
    {
        if (renderTo)
        {
            this.applyToMultipleFoldersForm = Ext4.create('LABKEY.import.ApplyToMultipleFolders', {
                formId: this.formId,
                hidden: !this.isApplyToMultipleFolders
            });

            this.applyToMultipleFoldersForm.on('render', this.updatePanelHeight, this);
            this.applyToMultipleFoldersForm.render(renderTo);
        }

        return this.applyToMultipleFoldersForm;
    },

    updatePanelHeight : function()
    {
        if (this.baseHeight == null)
            this.baseHeight = this.getHeight();

        var specificImportOptionsHeight = 0;
        if (Ext4.isDefined(this.getSpecificImportOptionsForm()) && this.getSpecificImportOptionsForm().isVisible())
            specificImportOptionsHeight = this.getSpecificImportOptionsForm().getHeight();

        var applyMultipleFoldersHeight = 0;
        if (Ext4.isDefined(this.getApplyToMultipleFoldersForm()) && this.getApplyToMultipleFoldersForm().isVisible())
            applyMultipleFoldersHeight = this.getApplyToMultipleFoldersForm().getHeight();

        this.setHeight(this.baseHeight + specificImportOptionsHeight + applyMultipleFoldersHeight);
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
                    // call beforeSubmit for each optionsForm
                    Ext4.each(this.getOptionsStore().getRange(), function(record)
                    {
                        var optionsForm = record.get('optionsForm') != null ? record.get('optionsForm').call(this) : null;
                        if (optionsForm != null)
                            optionsForm.beforeSubmit();
                    }, this);

                    document.getElementById('pipelineImportForm').submit();
                }
            })
        }

        return this.submitButton;
    }
});

Ext4.define('LABKEY.import.SpecificImportOptions', {
    extend: 'Ext.form.Panel',

    cls: 'advanced-options-panel',
    layout: 'anchor',
    width: 310,

    importers: [],

    initComponent: function()
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
        var items = advancedImportItems;
        if (additionalImportItems.length > 0)
        {
            this.width = this.width + 275;
            this.layout = 'column';

            items = [{
                border: false,
                bodyStyle: 'padding-right: 25px;',
                items: advancedImportItems
            },{
                border: false,
                items: additionalImportItems
            }];
        }

        this.items = items;

        this.callParent();
    },

    toggleState : function(checked)
    {
        this.setVisible(checked);

        // set all folder import type checkboxes to match this checked state
        Ext4.each(this.getAllVisibleInputBoxes(), function(box)
        {
            this.getInputFromBox(box).checked = checked;
        }, this);
    },

    getAllVisibleInputBoxes : function()
    {
        return this.query('box[cls=advanced-options-input]');
    },

    getInputFromBox : function(box)
    {
        return Ext4.dom.Query.selectNode('input', box.getEl().dom);
    },

    getImportOptionsHeaderConfig : function(header)
    {
        return {
            xtype: 'box',
            cls: 'advanced-options-header',
            html: '<div class="advanced-options-title">' + header + ' objects to import:</div>'
                + '<div class="labkey-title-area-line"></div>'
        };
    },

    getImportOptionInputConfig : function(dataType, parent, hide)
    {
        var checked = hide || this.hidden ? '' : ' checked',
            parentAttr = parent ? 'parentDataType="' + parent + '"' : '';

        return {
            xtype: 'box',
            cls: hide ? 'advanced-options-hide' : 'advanced-options-input',
            html: '<label><input type="checkbox" name="dataTypes" '
                + 'value="' + dataType + '" ' + parentAttr + checked + '>' + dataType + '</label>'
        }
    },

    beforeSubmit : function()
    {
        // check any hidden parent dataType checkboxes that should be checked (i.e. has at least one child checked)
        Ext4.each(this.query('box[cls=advanced-options-hide]'), function(hiddenBox)
        {
            var hiddenInput = this.getInputFromBox(hiddenBox);
            Ext4.each(this.getAllVisibleInputBoxes(), function(box)
            {
                var visibleInput = this.getInputFromBox(box),
                    parentDataType = visibleInput.getAttribute('parentDataType');

                if (parentDataType == hiddenInput.value && visibleInput.checked)
                {
                    hiddenInput.checked = true;
                    return false; // break;
                }
            }, this);
        }, this);
    }
});

Ext4.define('LABKEY.import.ApplyToMultipleFolders', {
    extend: 'Ext.tree.Panel',

    cls: 'apply-multiple-panel',
    width: 670,
    height: 300,
    autoScroll: true,
    rootVisible: true,

    formId: null,
    projectRoot: null,
    store: null,

    initComponent: function()
    {
        this.dockedItems = [this.getBottomDockedPanel()];

        this.callParent();

        this.on('select', this.onRecordSelect, this);

        // load the tree root information the first time the section is shown
        if (!this.hidden)
            this.getProjectRootNode();
    },

    getBottomDockedPanel : function()
    {
        if (!this.bottomDockedPanel)
        {
            this.bottomDockedPanel = Ext4.create('Ext.panel.Panel', {
                dock: 'bottom',
                border: false,
                cls: 'multiple-folder-footer',
                html: 'Note: any subfolders from the imported archive will not be applied when multiple folders are selected.'
            });
        }

        return this.bottomDockedPanel;
    },

    getProjectFolderTreeStore : function()
    {
        if (!this.treeStore)
        {
            this.treeStore = Ext4.create('Ext.data.TreeStore', {
                model: 'LABKEY.import.FolderTreeStore',
                root: this.projectRoot,
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api', null, {
                        requiredPermission: 'org.labkey.api.security.permissions.AdminPermission',
                        useTitles: true,
                        annotateLeaf: true
                    })
                }
            });
        }

        return this.treeStore
    },

    getProjectRootNode : function()
    {
        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('core', 'getContainerTreeRootInfo.api'),
            scope: this,
            success: function(response)
            {
                var resp = Ext4.decode(response.responseText),
                    projectInfo = resp.project ? resp.project : resp.current;

                this.currentRowId = resp.current.id;

                this.projectRoot = {
                    id: projectInfo.id,
                    expanded: true,
                    containerPath: projectInfo.path,
                    text: projectInfo.title,
                    isProject: true
                };

                // by default, select the current container node in the tree
                this.getProjectFolderTreeStore().on('load', function(store, node){
                    var record = store.getNodeById(resp.current.id);
                    if (record)
                        this.setChecked(record);
                }, this, {single: true});

                this.bindStore(this.getProjectFolderTreeStore());
            }
        });
    },

    onRecordSelect : function(tree, record)
    {
        this.setChecked(record);
    },

    setChecked : function(record)
    {
        record.set('checked', true);

        if (!record.isRoot() && !record.isLeaf())
            record.expand();
    },

    toggleState : function(checked)
    {
        // load the tree root information the first time the section is shown
        if (checked && this.projectRoot == null)
            this.getProjectRootNode();

        this.setVisible(checked);

        // on state hide, remove all checked records
        if (!checked && this.projectRoot != null)
        {
            Ext4.each(this.getView().getChecked(), function(record)
            {
                record.set('checked', false);
            }, this);

            // reselect the current container node
            this.getSelectionModel().deselectAll();
            var currentNode = this.getProjectFolderTreeStore().getNodeById(this.currentRowId);
            currentNode.set('checked', true);

            // expand the tree to the parent of the selected currentNode
            if (currentNode.parentNode)
                this.expandPath(currentNode.parentNode.getPath());
        }
    },

    beforeSubmit : function()
    {
        // add hidden form elements for each of the selected folders
        if (!this.hidden && this.formId != null)
        {
            var form = Ext4.get(this.formId);

            Ext4.each(this.getView().getChecked(), function(record)
            {
                form.createChild({
                    tag: 'input',
                    type: 'hidden',
                    name: 'folderRowIds',
                    value: record.get('id')
                });
            }, this);
        }
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