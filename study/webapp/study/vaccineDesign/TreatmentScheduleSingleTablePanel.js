Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleSingleTablePanel', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    width: 1400,

    disableEdit : true,

    dirty : false,

    returnURL : null,

    initComponent : function()
    {
        this.items = [this.getTreatmentScheduleGrid(), this.getButtonBar()];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getTreatmentScheduleGrid : function()
    {
        if (!this.treatmentScheduleGrid)
        {
            this.treatmentScheduleGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentScheduleSingleTableGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit,
                subjectNoun: this.subjectNoun,
                visitNoun: this.visitNoun,
                productRoles: this.productRoles
            });

            this.treatmentScheduleGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentScheduleGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.treatmentScheduleGrid;
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
                margin: this.disableEdit ? 0 : '0 0 0 10px',
                text: this.disableEdit ? 'Done' : 'Cancel',
                handler: this.goToReturnURL,
                scope: this
            });
        }

        return this.cancelButton;
    },

    saveTreatmentSchedule : function()
    {
        this.getEl().mask('Saving...');

        var treatments = [], cohorts = [], errorMsg = [];

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

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleSingleTableGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataViewAddVisit',

    cls : 'study-vaccine-design vaccine-design-cohorts',

    mainTitle : 'Treatment Schedule',

    width: 350,

    subjectNoun : 'Subject',

    visitNoun : 'Visit',

    studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

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
                    this.getTreatmentsStore();
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
                data : data
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
                    editorType: 'Ext.form.field.Text',
                    editorConfig: this.getTreatmentFieldConfig,
                    isTreatmentLookup: true
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

    getTreatmentFieldConfig : function()
    {
        var me = this;
        return {
            hideFieldLabel: true,
            name: 'VisitMap',
            width: 135,
            editable : false,
            disabled: true,
            cls: 'treatment-input-cell',
            listeners: {
                render: function(cmp) {
                    if (cmp.value)
                        cmp.getEl().dom.title = cmp.value; //tooltip

                    cmp.getEl().on('click', function(){
                        var win;
                        var popupConfig = {
                            productRoles: me.productRoles,
                            autoScroll  : true,
                            buttonAlign : 'right',
                            modal: true,
                            width: 100 + me.productRoles.length * 170,
                            height: 300,
                            border: false,
                            closable: false,
                            title: 'Treatment',
                            draggable: false,
                            buttons: [{
                                text: 'Cancel',
                                onClick : function () {
                                    win.close();
                                }
                            },{
                                text: 'Okay',
                                cls: 'commentSubmit',
                                onClick : function () {
                                    var isFormDirty = win.getForm().getForm().isDirty();
                                    if (!isFormDirty) {
                                        win.close();
                                        return;
                                    }

                                    var treatment = win.getTreatmentFormValues();
                                    if (treatment && treatment.Products.length == 0) {
                                        win.close();
                                        cmp.treatmentId = null;
                                        cmp.setValue(null);
                                        return;
                                    }
                                    var treatments = [treatment];
                                    LABKEY.Ajax.request({
                                        url     : LABKEY.ActionURL.buildURL('study-design', 'updateTreatments.api'),
                                        method  : 'POST',
                                        jsonData: {
                                            treatments: treatments
                                        },
                                        scope: this,
                                        success: function(response)
                                        {
                                            var resp = Ext4.decode(response.responseText);
                                            if (resp.success) {
                                                win.close();
                                                cmp.treatmentId = resp.treatmentId;
                                                cmp.setValue(treatments[0].Label);
                                            }
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

                                }
                            }]

                        };
                        if (cmp.treatmentId) {
                            Ext4.Ajax.request({
                                url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true, treatmentId: cmp.treatmentId}),
                                method : 'POST',
                                success: LABKEY.Utils.getCallbackWrapper(function(response){
                                    popupConfig.treatmentDetails = response.treatments.length > 0 ? response.treatments[0] : null;
                                    win = new LABKEY.VaccineDesign.TreatmentDialog(popupConfig);
                                    win.show();

                                }, me)
                            });
                        }
                        else {
                            win = new LABKEY.VaccineDesign.TreatmentDialog(popupConfig);
                            win.show();
                        }
                    });
                }
            }
        };
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
        var value = newValue;
        if (column.lookupStoreId && field && field.treatmentId)
            value = field.treatmentId;
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
    }
});