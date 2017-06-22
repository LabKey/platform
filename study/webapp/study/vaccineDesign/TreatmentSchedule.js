/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.TreatmentSchedulePanel', {
    extend : 'LABKEY.VaccineDesign.TreatmentSchedulePanelBase',

    width: 1400,

    initComponent : function()
    {
        this.items = [this.getTreatmentsGrid()];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getTreatmentsGrid : function()
    {
        if (!this.treatmentsGrid)
        {
            this.treatmentsGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentsGrid', {
                disableEdit: this.disableEdit,
                productRoles: this.productRoles
            });

            this.treatmentsGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentsGrid.on('celledited', this.enableSaveButton, this);

            // Note: since we need the data from the treatment grid, don't add this.getTreatmentScheduleGrid() until the treatment grid store has loaded
            this.treatmentsGrid.on('loadcomplete', this.onTreatmentGridLoadComplete, this, {single: true});
        }

        return this.treatmentsGrid;
    },

    onTreatmentGridLoadComplete : function()
    {
        this.add(this.getTreatmentScheduleGrid());
        this.add(this.getButtonBar());

        // since a treatment label change needs to be reflected in the treatment schedule grid, force a refresh there
        this.getTreatmentsGrid().on('celledited', function(view, fieldName, value){
            if (fieldName == 'Label')
                this.getTreatmentScheduleGrid().refresh();
        }, this);

        // removing a treatment row needs to also remove any visit mappings for that treatment
        this.getTreatmentsGrid().on('beforerowdeleted', function(grid, record){
            this.getTreatmentScheduleGrid().removeTreatmentUsages(record.get('RowId'));
        }, this);
    },

    getTreatmentScheduleGrid : function()
    {
        if (!this.treatmentScheduleGrid)
        {
            this.treatmentScheduleGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit,
                subjectNoun: this.subjectNoun,
                visitNoun: this.visitNoun
            });

            this.treatmentScheduleGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentScheduleGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.treatmentScheduleGrid;
    },

    getTreatments: function()
    {
        var treatments = [], index = 0, errorMsg = [];

        Ext4.each(this.getTreatmentsGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);
            index++;

            // drop any empty immunogen or adjuvant or challenge rows that were just added
            recData['Products'] = [];
            Ext4.each(this.productRoles, function(role)
            {
                Ext4.each(recData[role], function(product)
                {
                    if (Ext4.isDefined(product['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(product))
                        recData['Products'].push(product);
                }, this);
            }, this);

            // drop any empty treatment rows that were just added
            var hasData = recData['Label'] != '' || recData['Description'] != '' || recData['Products'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
            {
                var treatmentLabel = recData['Label'] != '' ? '\'' + recData['Label'] + '\'' : index;

                // validation: treatment must have at least one immunogen or adjuvant, no duplicate immunogens/adjuvants for a treatment
                var treatmentProductIds = Ext4.Array.clean(Ext4.Array.pluck(recData['Products'], 'ProductId'));
                if (recData['Products'].length == 0)
                    errorMsg.push('Treatment ' + treatmentLabel + ' must have at least one immunogen, adjuvant or challenge defined.');
                else if (treatmentProductIds.length != Ext4.Array.unique(treatmentProductIds).length)
                    errorMsg.push('Treatment ' + treatmentLabel + ' contains a duplicate immunogen, adjuvant or challenge.');
                else
                    treatments.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
            return false;
        }
        return treatments;
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentsGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataView',

    cls : 'study-vaccine-design vaccine-design-treatments',

    mainTitle : 'Treatments',

    width: 1400,

    studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

    //Override
    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                storeId : 'TreatmentsGridStore',
                model : 'LABKEY.VaccineDesign.Treatment',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true}),
                    reader: {
                        type: 'json',
                        root: 'treatments'
                    }
                },
                sorters: [{ property: 'RowId', direction: 'ASC' }],
                autoLoad: true
            });
        }

        return this.store;
    },

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
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            },{
                label: 'Description',
                width: 200,
                dataIndex: 'Description',
                editorType: 'Ext.form.field.TextArea',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Description', 185, '95%')
            }];

            if (Ext4.isArray(this.productRoles)) {
                Ext4.each(this.productRoles, function(role){
                    var roleColumn = this.getProductRoleColumn(role);
                    this.columnConfigs.push(roleColumn);
                }, this);
            }
        }

        return this.columnConfigs;
    },

    getProductRoleColumn: function(roleName) {
        var column = {
            label: roleName + 's',
            width: 310,
            dataIndex: roleName,
            subgridConfig: {
                columns: [{
                    label: roleName,
                    width: 140,
                    dataIndex: 'ProductId',
                    required: true,
                    queryName: 'Product',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getProductEditor(roleName)
                },{
                    label: 'Dose and Route',
                    width: 140,
                    dataIndex: 'DoseAndRoute',
                    queryName: 'DoseAndRoute',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getDoseAndRouteEditorConfig()
                }]
            }
        };
        return column;
    },

    getProductEditor : function(roleName){

        var filter = LABKEY.Filter.create('Role', roleName),
            cfg = LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('ProductId', 125, 'Product', filter, 'Label', 'RowId');

        cfg.listeners = {
            scope: this,
            change : function(cmp, productId) {
                // clear out (if any) value for the dose and route field
                var record = this.getStore().getAt(cmp.storeIndex),
                    outerDataIndex = cmp.outerDataIndex,
                    subgridIndex = Number(cmp.subgridIndex),
                    selector = 'tr.data-row:nth(' + (this.getStore().indexOf(record)+1) + ') table.subgrid-' + outerDataIndex
                            + ' tr.subrow:nth(' + (subgridIndex+1) + ') td[data-index=DoseAndRoute] input';

                var inputField = this.getInputFieldFromSelector(selector);
                if (inputField != null)
                {
                    inputField.setValue('');
                    inputField.bindStore(this.getNewDoseAndRouteComboStore(productId));
                }
            }
        };
        return cfg;
    },

    getDoseAndRouteEditorConfig : function()
    {
        return {
            hideFieldLabel: true,
            name: 'DoseAndRoute',
            width: 125,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            displayField : 'Label',
            valueField : 'Label',
            store : null, // the store will be created and bound to this combo after render
            listeners : {
                scope: this,
                render : function(cmp) {
                    var record = this.getStore().getAt(cmp.storeIndex),
                        outerDataIndex = cmp.outerDataIndex,
                        subgridIndex = Number(cmp.subgridIndex),
                        productId = record.get(outerDataIndex)[subgridIndex]['ProductId'];

                    cmp.bindStore(this.getNewDoseAndRouteComboStore(productId));
                },
                change : function(cmp, newValue, oldValue) {
                    var record = this.getStore().getAt(cmp.storeIndex),
                        outerDataIndex = cmp.outerDataIndex,
                        subgridIndex = Number(cmp.subgridIndex),
                        subRecord = record.get(outerDataIndex)[subgridIndex];

                    // if the ProductDoseRoute is set, we need to update it
                    if (Ext4.isDefined(subRecord['ProductDoseRoute']) && Ext4.isDefined(subRecord['ProductId']))
                        subRecord['ProductDoseRoute'] = subRecord['ProductId'] + '-#-' + newValue;
                }
            }
        };
    },

    getNewDoseAndRouteComboStore : function(productId)
    {
        // need to create a new store each time since we need to add a [none] option and include any new treatment records
        var data = [];
        Ext4.each(Ext4.getStore('DoseAndRoute').getRange(), function(record)
        {
            if (record.get('ProductId') == null || record.get('ProductId') == productId)
                data.push(Ext4.clone(record.data));
        }, this);

        return Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'Label'],
            data: data
        });
    },

    //Override
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Treatment.create({
            RowId: Ext4.id() // need to generate an id so that the treatment schedule grid can use it
        });
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected treatment? '
            + 'Note: this will also delete any usages of this treatment record in the Treatment Schedule grid below.';
    },

    //Override
    updateSubgridRecordValue : function(record, outerDataIndex, subgridIndex, fieldName, newValue)
    {
        var preProductIds = [];
        Ext4.each(this.productRoles, function(role){
            var productRoleIds = Ext4.Array.pluck(record.get(role), 'ProductId');
            if (preProductIds.length == 0)
                preProductIds = productRoleIds;
            else
                preProductIds = preProductIds.concat(productRoleIds);
        });

        this.callParent([record, outerDataIndex, subgridIndex, fieldName, newValue]);

        // auto populate the treatment label if the user has not already entered a value
        if (fieldName == 'ProductId')
            this.populateTreatmentLabel(record, preProductIds);
    },

    //Override
    removeSubgridRecord : function(target, record)
    {
        var preProductIds = [];
        Ext4.each(this.productRoles, function(role){
            var productRoleIds = Ext4.Array.pluck(record.get(role), 'ProductId');
            if (preProductIds.length == 0)
                preProductIds = productRoleIds;
            else
                preProductIds = preProductIds.concat(productRoleIds);
        });
        this.callParent([target, record]);
        this.populateTreatmentLabel(record, preProductIds);
        this.refresh(true);
    },

    populateTreatmentLabel : function(record, preProductIds)
    {
        var currentLabel = record.get('Label');
        if (currentLabel == '' || currentLabel == this.getLabelFromProductIds(preProductIds))
        {
            var postProductIds = [];
            Ext4.each(this.productRoles, function(role){
                var productRoleIds = Ext4.Array.pluck(record.get(role), 'ProductId');
                if (postProductIds.length == 0)
                    postProductIds = productRoleIds;
                else
                    postProductIds = postProductIds.concat(productRoleIds);
            });

            var updatedTreatmentLabel = this.getLabelFromProductIds(postProductIds);

            // need to update the input field value, which will intern update the record and fire teh celledited event
            var inputField = this.getInputFieldFromSelector('tr.data-row:nth(' + (this.getStore().indexOf(record)+1) + ') td.cell-value input');
            if (inputField != null)
            {
                inputField.setValue(updatedTreatmentLabel);
                record.set('Label', updatedTreatmentLabel);
            }
        }
    },

    getInputFieldFromSelector : function(selector)
    {
        var inputFieldEl = Ext4.DomQuery.selectNode(selector, this.getEl().dom);
        if (inputFieldEl != null)
            return Ext4.ComponentManager.get(inputFieldEl.id.replace('-inputEl', ''));

        return null;
    },

    getLabelFromProductIds : function(productIdsArr)
    {
        var labelArr = [];

        if (Ext4.isArray(productIdsArr))
        {
            Ext4.each(productIdsArr, function(productId){
                if (productId != undefined || productId != null)
                    labelArr.push(LABKEY.VaccineDesign.Utils.getLabelFromStore('Product', productId));
            });
        }

        return labelArr.join(' | ');
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
    extend : 'LABKEY.VaccineDesign.TreatmentScheduleGridBase',

    //Override
    onStudyTreatmentScheduleStoreLoad : function()
    {
        this.getStore().fireEvent('load', this.getStore());
    },

    getTreatmentsStore : function()
    {
        if (!this.treatmentsStore)
        {
            this.treatmentsStore = Ext4.getStore('TreatmentsGridStore');
        }

        return this.treatmentsStore;
    },

    //Override
    getTreatmentFieldEditorType: function()
    {
        return 'LABKEY.ext4.ComboBox';
    },

    //Override
    isFieldTreatmentLookup: function()
    {
        return false;
    },

    getTreatmentFieldConfig : function()
    {
        return {
            hideFieldLabel: true,
            name: 'VisitMap',
            width: 135,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            displayField : 'Label',
            valueField : 'RowId',
            store : this.getNewTreatmentComboStore()
        };
    },

    getNewTreatmentComboStore : function()
    {
        // need to create a new store each time since we need to add a [none] option and include any new treatment records
        var data = [{RowId: null, Label: '[none]'}];
        Ext4.each(this.getTreatmentsStore().getRange(), function(record)
        {
            data.push(Ext4.clone(record.data));
        }, this);

        return Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'Label'],
            data: data
        });
    },

    removeTreatmentUsages : function(treatmentId)
    {
        this.getStore().suspendEvents();
        Ext4.each(this.getStore().getRange(), function(record)
        {
            var newVisitMapArr = Ext4.Array.filter(record.get('VisitMap'), function(item){ return item.TreatmentId != treatmentId; });
            record.set('VisitMap', newVisitMapArr);
        }, this);
        this.getStore().resumeEvents();

        this.refresh(true);
    }
});