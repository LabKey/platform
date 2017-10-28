/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.TreatmentSchedulePanelBase', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    disableEdit : true,

    dirty : false,

    returnURL : null,

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
                handler: this.saveTreatmentSchedule,
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

    getTreatments: function()
    {
        return [];
    },

    saveTreatmentSchedule : function()
    {
        this.getEl().mask('Saving...');

        var treatments = this.getTreatments(), cohorts = [], errorMsg = [];

        if (!Ext4.isArray(treatments)) // treatments invalid
            return;

        Ext4.each(this.getTreatmentScheduleGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);

            // drop any empty cohort rows that were just added
            var hasData = recData['Label'] != '' || recData['SubjectCount'] != '' || recData['VisitMap'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
            {
                var countVal = Number(recData['SubjectCount']);
                if (isNaN(countVal) || countVal < 0)
                    errorMsg.push('Cohort ' + this.subjectNoun.toLowerCase() + ' count values must be a positive integer: ' + recData['SubjectCount'] + '.');
                else
                    cohorts.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
            return;
        }

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-design', 'updateTreatmentSchedule.api'),
            method  : 'POST',
            jsonData: {
                treatments: treatments,
                cohorts: cohorts
            },
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
        LABKEY.Utils.signalWebDriverTest("treatmentScheduleDirty", dirty);
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

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleGridBase', {
    extend : 'LABKEY.VaccineDesign.BaseDataViewAddVisit',

    cls : 'study-vaccine-design vaccine-design-cohorts',

    mainTitle : 'Treatment Schedule',

    width: 350,

    subjectNoun : 'Subject',

    visitNoun : 'Visit',

    //studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Cohort',
                sorters: [{ property: 'RowId', direction: 'ASC' }]
            });

            this.queryStudyTreatmentSchedule();
        }

        return this.store;
    },

    queryStudyTreatmentSchedule : function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study-design', 'getStudyTreatmentSchedule', null, {splitByRole: true}),
            method: 'GET',
            scope: this,
            success: function (response)
            {
                var o = Ext4.decode(response.responseText);
                if (o.success)
                {
                    this.getVisitStore(o['visits']);
                    this.getStore().loadData(o['cohorts']);
                    this.onStudyTreatmentScheduleStoreLoad();
                }
            }
        });
    },

    getVisitStore : function(data)
    {
        if (!this.visitStore)
        {
            this.visitStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Visit',
                data : data,
                sorters : [{property: 'DisplayOrder', direction: 'ASC'},{property: 'SequenceNumMin', direction: 'ASC'}]
            });
        }

        return this.visitStore;
    },

    getTreatmentsStore : function()
    {
        if (!this.treatmentsStore)
        {
            var me = this;
            this.treatmentsStore = Ext4.create('Ext.data.Store', {
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
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        me.getStore().fireEvent('load', me.getStore());
                    }
                }
            });
        }

        return this.treatmentsStore;
    },

    getVisitColumnConfigs : function()
    {
        var visitConfigs = [];

        Ext4.each(this.getVisitStore().getRange(), function(visit)
        {
            if (visit.get('Included'))
            {
                visitConfigs.push({
                    label: visit.get('Label') || (this.visitNoun + visit.get('RowId')),
                    width: 150,
                    dataIndex: 'VisitMap',
                    dataIndexArrFilterProp: 'VisitId',
                    dataIndexArrFilterValue: visit.get('RowId'),
                    dataIndexArrValue: 'TreatmentId',
                    lookupStoreId: 'TreatmentsGridStore',
                    editorType: this.getTreatmentFieldEditorType(),
                    editorConfig: this.getTreatmentFieldConfig,
                    isTreatmentLookup: this.isFieldTreatmentLookup()
                });
            }
        }, this);

        if (visitConfigs.length == 0 && !this.disableEdit)
        {
            visitConfigs.push({
                label: 'No ' + this.visitNoun + 's Defined',
                displayValue: '',
                width: 160
            });
        }

        return visitConfigs;
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var columnConfigs = [{
                label: 'Group / Cohort',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            },{
                label: this.subjectNoun + ' Count',
                width: 130,
                dataIndex: 'SubjectCount',
                editorType: 'Ext.form.field.Number',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignNumberConfig('SubjectCount', 115)
            }];

            var visitConfigs = this.getVisitColumnConfigs();

            // update the width based on the number of visit columns
            var width = 400 + (Math.max(2, visitConfigs.length) * 150);
            this.setWidth(width);

            // update the outer panel width if necessary
            var outerPanel = this.up('panel');
            if (outerPanel != null)
                outerPanel.setWidth(Math.max(width, 1400));

            this.columnConfigs = columnConfigs.concat(visitConfigs);
        }

        return this.columnConfigs;
    },

    //Override
    getNewModelInstance : function()
    {
        var newCohort = LABKEY.VaccineDesign.Cohort.create();
        newCohort.set('VisitMap', []);
        return newCohort;
    },

    //Override
    removeOuterRecord : function(title, record) {
        if (!record.get('CanDelete')) {
            Ext4.Msg.show({
                title: 'Unable to Remove Cohort',
                msg: 'The selected cohort can not be removed because it is in-use in the study.<br/><b>'
                    + Ext4.String.htmlEncode(record.get('Label')) + '</b>',
                buttons: Ext4.Msg.OK,
                icon: Ext4.Msg.INFO
            });
        }
        else {
            this.callParent([title, record]);
        }
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected group / cohort and its associated treatment / visit mapping records?';
    },

    //Override
    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        var value = this.callParent([column, record, dataIndex, outerDataIndex, subgridIndex]);

        if (Ext4.isArray(value))
        {
            var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(value, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);
            if (matchingIndex > -1)
                return value[matchingIndex][column.dataIndexArrValue];
            else
                return null;
        }

        return value;
    },

    getTreatmentCellDisplayValue : function(val, lookupStore)
    {
        var displayVal = val;
        if (Ext4.isDefined(lookupStore) && val != null && val != '')
        {
            var store = Ext4.getStore(lookupStore);
            if (store != null)
            {
                var record = store.findRecord('RowId', val);
                if (record != null)
                    displayVal = record.get('Label');
            }
        }
        return displayVal;
    },

    //Override
    updateStoreRecordValue : function(record, column, newValue, field)
    {
        var value = this.getTreatmentValue(column, newValue, field);
        // special case for editing the value of one of the pivot visit columns
        if (column.dataIndex == 'VisitMap')
        {
            var visitMapArr = record.get(column.dataIndex),
                    matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(visitMapArr, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);

            if (matchingIndex > -1)
            {
                if (value != null)
                    visitMapArr[matchingIndex][column.dataIndexArrValue] = value;
                else
                    Ext4.Array.splice(visitMapArr, matchingIndex, 1);
            }
            else if (value != null)
            {
                visitMapArr.push({
                    CohortId: record.get('RowId'),
                    VisitId: column.dataIndexArrFilterValue,
                    TreatmentId: value
                });
            }

            this.fireEvent('celledited', this, 'VisitMap', visitMapArr);
        }
        else
        {
            this.callParent([record, column, value]);
        }
    },

    getTreatmentValue : function(column, newValue, field) {
        return newValue;
    }
});