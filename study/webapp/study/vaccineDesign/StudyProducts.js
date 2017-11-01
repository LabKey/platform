/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.StudyProductsPanel', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    minWidth: 1350,

    disableEdit : true,

    dirty : false,

    returnURL : null,

    initComponent : function()
    {
        this.items = [
            this.getImmunogensGrid(),
            this.getAdjuvantGrid(),
            this.getChallengesGrid(),
            this.getButtonBar()
        ];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getImmunogensGrid : function()
    {
        if (!this.immunogenGrid)
        {
            this.immunogenGrid = Ext4.create('LABKEY.VaccineDesign.ImmunogensGrid', {
                disableEdit: this.disableEdit
            });

            this.immunogenGrid.on('dirtychange', this.enableSaveButton, this);
            this.immunogenGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.immunogenGrid;
    },

    getAdjuvantGrid : function()
    {
        if (!this.adjuvantGrid)
        {
            this.adjuvantGrid = Ext4.create('LABKEY.VaccineDesign.AdjuvantsGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit
            });

            this.adjuvantGrid.on('dirtychange', this.enableSaveButton, this);
            this.adjuvantGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.adjuvantGrid;
    },

    getChallengesGrid : function()
    {
        if (!this.challengesGrid)
        {
            this.challengesGrid = Ext4.create('LABKEY.VaccineDesign.ChallengesGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit
            });

            this.challengesGrid.on('dirtychange', this.enableSaveButton, this);
            this.challengesGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.challengesGrid;
    },

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                dock: 'bottom',
                ui: 'footer',
                padding: 0,
                style : 'background-color: transparent;',
                defaults: {width: 75},
                items: [this.getSaveButton(), this.getCancelButton()]
            });
        }

        return this.buttonBar;
    },

    getSaveButton : function()
    {
        if (!this.saveButton)
        {
            this.saveButton = Ext4.create('Ext.button.Button', {
                text: 'Save',
                disabled: true,
                hidden: this.disableEdit,
                handler: this.saveStudyProducts,
                scope: this
            });
        }

        return this.saveButton;
    },

    enableSaveButton : function()
    {
        this.setDirty(true);
        this.getSaveButton().enable();
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                text: this.disableEdit ? 'Done' : 'Cancel',
                handler: this.goToReturnURL,
                scope: this
            });
        }

        return this.cancelButton;
    },

    saveStudyProducts : function()
    {
        var studyProducts = [];

        this.getEl().mask('Saving...');

        Ext4.each(this.getImmunogensGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);

            // drop any empty antigen rows that were just added
            var antigenArr = [];
            Ext4.each(recData['Antigens'], function(antigen)
            {
                if (Ext4.isDefined(antigen['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(antigen))
                    antigenArr.push(antigen);
            }, this);
            recData['Antigens'] = antigenArr;

            // drop any empty rows that were just added
            var hasData = recData['Label'] != '' || recData['Type'] != '' || recData['Antigens'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
                studyProducts.push(recData);
        }, this);

        Ext4.each(this.getAdjuvantGrid().getStore().getRange(), function(record)
        {
            // drop any empty rows that were just added
            if (Ext4.isDefined(record.get('RowId')) || record.get('Label') != '')
                studyProducts.push(Ext4.clone(record.data));
        }, this);

        Ext4.each(this.getChallengesGrid().getStore().getRange(), function(record)
        {
            var hasData = record['Label'] != '' || record['Type'] != '';
            if (Ext4.isDefined(record.get('RowId')) || hasData)
                studyProducts.push(Ext4.clone(record.data));
        }, this);

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-design', 'updateStudyProducts.api'),
            method  : 'POST',
            jsonData: { products: studyProducts },
            scope: this,
            success: function(response)
            {
                var resp = Ext4.decode(response.responseText);
                if (resp.success)
                    this.goToReturnURL();
                else
                    this.onFailure();
            },
            failure: function(response)
            {
                var resp = Ext4.decode(response.responseText);
                if (resp.errors)
                    this.onFailure(Ext4.Array.pluck(resp.errors, 'message').join('<br/>'));
                else
                    this.onFailure(resp.exception);
            }
        });
    },

    goToReturnURL : function()
    {
        this.setDirty(false);
        window.location = this.returnURL;
    },

    onFailure : function(text)
    {
        Ext4.Msg.show({
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });

        this.getEl().unmask();
    },

    setDirty : function(dirty)
    {
        this.dirty = dirty;
        LABKEY.Utils.signalWebDriverTest("studyProductsDirty", dirty);
    },

    isDirty : function()
    {
        return this.dirty;
    },

    beforeUnload : function()
    {
        if (!this.disableEdit && this.isDirty())
            return 'Please save your changes.';
    }
});

Ext4.define('LABKEY.VaccineDesign.StudyProductsGrid', {

    extend : 'LABKEY.VaccineDesign.BaseDataView',

    filterRole : null,

    showDoseRoute : true,

    //Override
    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Product',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("study-design", "getStudyProducts", null, {role: this.filterRole}),
                    reader: {
                        type: 'json',
                        root: 'products'
                    }
                },
                sorters: [{ property: 'RowId', direction: 'ASC' }],
                autoLoad: true
            });
        }

        return this.store;
    },

    //Override
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Product.create({Role: this.filterRole});
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected study product? '
            + 'Note: if this study product is being used by any treatment definitions, '
            + 'those associations will also be deleted upon save.';
    }
});

Ext4.define('LABKEY.VaccineDesign.ImmunogensGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',

    cls : 'study-vaccine-design vaccine-design-immunogens',

    mainTitle : 'Immunogens',

    filterRole : 'Immunogen',

    studyDesignQueryNames : ['StudyDesignImmunogenTypes', 'StudyDesignGenes', 'StudyDesignSubTypes', 'StudyDesignRoutes'],

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var width = 0; // add to the running width as we go through which columns to show in the config

            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            }, {
                label: 'Type',
                width: 200,
                dataIndex: 'Type',
                queryName: 'StudyDesignImmunogenTypes',
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Type', 185, 'StudyDesignImmunogenTypes')
            },{
                label: 'HIV Antigens',
                width: 600,
                dataIndex: 'Antigens',
                subgridConfig: {
                    columns: [{
                        label: 'Gene',
                        width: 140,
                        dataIndex: 'Gene',
                        queryName: 'StudyDesignGenes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Gene', 125, 'StudyDesignGenes')
                    },{
                        label: 'Subtype',
                        width: 140,
                        dataIndex: 'SubType',
                        queryName: 'StudyDesignSubTypes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('SubType', 125, 'StudyDesignSubTypes')
                    },{
                        label: 'GenBank Id',
                        width: 150,
                        dataIndex: 'GenBankId',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('GenBankId', 135)
                    },{
                        label: 'Sequence',
                        width: 150,
                        dataIndex: 'Sequence',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Sequence', 135)
                    }]
                }
            }];
            width += 1000;

            if (this.showDoseRoute)
            {
                this.columnConfigs.push({
                    label: 'Doses and Routes',
                    width: 315,
                    dataIndex: 'DoseAndRoute',
                    subgridConfig: {
                        columns: [{
                            label: 'Dose',
                            width: 140,
                            dataIndex: 'Dose',
                            editorType: 'Ext.form.field.Text',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Dose', 125)
                        },{
                            label: 'Route',
                            width: 140,
                            dataIndex: 'Route',
                            queryName: 'StudyDesignRoutes',
                            editorType: 'LABKEY.ext4.ComboBox',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Route', 125, 'StudyDesignRoutes')
                        }]
                    }
                });
                width += 315;
            }

            this.setWidth(width);
        }

        return this.columnConfigs;
    }
});

Ext4.define('LABKEY.VaccineDesign.AdjuvantsGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',

    cls : 'study-vaccine-design vaccine-design-adjuvants',

    mainTitle : 'Adjuvants',

    filterRole : 'Adjuvant',

    studyDesignQueryNames : ['StudyDesignRoutes'],

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var width = 0; // add to the running width as we go through which columns to show in the config

            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            }];
            width += 200;

            if (this.showDoseRoute)
            {
                this.columnConfigs.push({
                    label: 'Doses and Routes',
                    width: 330,
                    dataIndex: 'DoseAndRoute',
                    subgridConfig: {
                        columns: [{
                            label: 'Dose',
                            width: 140,
                            dataIndex: 'Dose',
                            editorType: 'Ext.form.field.Text',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Dose', 125)
                        }, {
                            label: 'Route',
                            width: 140,
                            dataIndex: 'Route',
                            queryName: 'StudyDesignRoutes',
                            editorType: 'LABKEY.ext4.ComboBox',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Route', 125, 'StudyDesignRoutes')
                        }]
                    }
                });
                width += 330;
            }

            this.setWidth(width);
        }

        return this.columnConfigs;
    }
});

Ext4.define('LABKEY.VaccineDesign.ChallengesGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',

    cls : 'study-vaccine-design vaccine-design-challenges',

    mainTitle : 'Challenges',

    filterRole : 'Challenge',

    studyDesignQueryNames : ['StudyDesignChallengeTypes', 'StudyDesignRoutes'],

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var width = 0; // add to the running width as we go through which columns to show in the config

            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            }, {
                label: 'Type',
                width: 200,
                dataIndex: 'Type',
                queryName: 'StudyDesignChallengeTypes',
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Type', 185, 'StudyDesignChallengeTypes')
            }];
            width += 400;

            if (this.showDoseRoute)
            {
                this.columnConfigs.push({
                    label: 'Doses and Routes',
                    width: 330,
                    dataIndex: 'DoseAndRoute',
                    subgridConfig: {
                        columns: [{
                            label: 'Dose',
                            width: 140,
                            dataIndex: 'Dose',
                            editorType: 'Ext.form.field.Text',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Dose', 125)
                        }, {
                            label: 'Route',
                            width: 140,
                            dataIndex: 'Route',
                            queryName: 'StudyDesignRoutes',
                            editorType: 'LABKEY.ext4.ComboBox',
                            editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Route', 125, 'StudyDesignRoutes')
                        }]
                    }
                });
                width += 330;
            }

            this.setWidth(width);
        }

        return this.columnConfigs;
    }
});