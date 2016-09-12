/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.VaccineDesign.StudyProductsGrid', {

    extend : 'LABKEY.VaccineDesign.BaseDataView',

    filterRole : null,

    //Override - see LABKEY.VaccineDesign.BaseDataView
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

    //Override - see LABKEY.VaccineDesign.BaseDataView
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Product.create({Role: this.filterRole});
    }
});

Ext4.define('LABKEY.VaccineDesign.ImmunogensGrid', {
    extend : 'LABKEY.VaccineDesign.StudyProductsGrid',
    width: 1100,
    mainTitle : 'Immunogens',
    filterRole : 'Immunogen',
    hiddenColumns : ["RowId", "Role"],

    //Override - see LABKEY.VaccineDesign.BaseDataView
    getColumnConfig : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('Label', true, 190)
            }, {
                label: 'Type',
                width: 200,
                dataIndex: 'Type',
                queryName: 'StudyDesignImmunogenTypes',
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('Type', false, 190, 'StudyDesignImmunogenTypes')
            }, {
                label: 'HIV Antigens',
                width: 700,
                dataIndex: 'Antigens',
                subgridConfig: {
                    columns: [{
                        label: 'Gene',
                        width: 140,
                        dataIndex: 'Gene',
                        queryName: 'StudyDesignGenes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('Gene', false, 130, 'StudyDesignGenes')
                    },{
                        label: 'Subtype',
                        width: 140,
                        dataIndex: 'SubType',
                        queryName: 'StudyDesignSubTypes',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('SubType', false, 130, 'StudyDesignSubTypes')
                    },{
                        label: 'GenBank Id',
                        width: 190,
                        dataIndex: 'GenBankId',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('GenBankId', false, 180)
                    },{
                        label: 'Sequence',
                        width: 210,
                        dataIndex: 'Sequence',
                        editorType: 'Ext.form.field.Text',
                        editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('Sequence', false, 200)
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
    hiddenColumns : ["RowId", "Role", "Type", "Antigens"],

    //Override - see LABKEY.VaccineDesign.BaseDataView
    getColumnConfig : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 400,
                dataIndex: 'Label',
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignFieldEditorConfig('Label', true, 363)
            }];
        }

        return this.columnConfigs;
    }
});