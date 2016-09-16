/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.StudyProductsPanel', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    width: 1200,

    disableEdit : true,

    returnURL : null,

    initComponent : function()
    {
        this.items = [
            this.getImmunogensGrid(),
            this.getAdjuvantGrid(),
            this.getButtonBar()
        ];

        this.callParent();
    },

    getImmunogensGrid : function()
    {
        if (!this.immunogenGrid)
        {
            this.immunogenGrid = Ext4.create('LABKEY.VaccineDesign.ImmunogensGrid', {
                disableEdit: this.disableEdit
            });

            this.immunogenGrid.on('dirtychange', function() { this.getSaveButton().enable(); }, this);
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

            this.adjuvantGrid.on('dirtychange', function() { this.getSaveButton().enable(); }, this);
        }

        return this.adjuvantGrid;
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

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                margin: this.disableEdit ? 0 : '0 0 0 10px',
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

            // drop and empty antigen rows that were just added
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
                this.onFailure(resp.exception);
            }
        });
    },

    goToReturnURL : function()
    {
        window.location = this.returnURL;
    },

    onFailure : function(text)
    {
        Ext4.Msg.show({
            cls: 'data-window',
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });

        this.getEl().unmask();
    }
});

Ext4.define('LABKEY.VaccineDesign.StudyProductsGrid', {

    extend : 'LABKEY.VaccineDesign.BaseDataView',

    filterRole : null,

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
        return 'Are you sure you want to delete the selected study product?<br/><br/>'
            + 'Note: if this study product is being used by any treatment definitions, '
            + 'those associations will also be deleted upon save.';
    }
});

Ext4.define('LABKEY.VaccineDesign.ImmunogensGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',

    width: 1200,

    mainTitle : 'Immunogens',

    filterRole : 'Immunogen',

    studyDesignQueryNames : ['StudyDesignImmunogenTypes', 'StudyDesignGenes', 'StudyDesignSubTypes'],

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 190)
            }, {
                label: 'Type',
                width: 200,
                dataIndex: 'Type',
                queryName: 'StudyDesignImmunogenTypes',
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Type', 190, 'StudyDesignImmunogenTypes')
            }, {
                label: 'HIV Antigens',
                width: 800,
                dataIndex: 'Antigens',
                subgridConfig: {
                    columns: [{
                        label: 'Gene',
                        width: 165,
                        dataIndex: 'Gene',
                        queryName: 'StudyDesignGenes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Gene', 155, 'StudyDesignGenes')
                    },{
                        label: 'Subtype',
                        width: 165,
                        dataIndex: 'SubType',
                        queryName: 'StudyDesignSubTypes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('SubType', 155, 'StudyDesignSubTypes')
                    },{
                        label: 'GenBank Id',
                        width: 215,
                        dataIndex: 'GenBankId',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('GenBankId', 205)
                    },{
                        label: 'Sequence',
                        width: 235,
                        dataIndex: 'Sequence',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Sequence', 225)
                    }]
                }
            }];
        }

        return this.columnConfigs;
    }
});

Ext4.define('LABKEY.VaccineDesign.AdjuvantsGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',

    width : 400,

    mainTitle : 'Adjuvants',

    filterRole : 'Adjuvant',

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 400,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 363)
            }];
        }

        return this.columnConfigs;
    }
});