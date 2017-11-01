/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.AssaySchedulePanel', {
    extend : 'Ext.panel.Panel',

    width: 750,

    border : false,

    bodyStyle : 'background-color: transparent;',

    disableEdit : true,

    dirty : false,

    returnURL : null,

    initComponent : function()
    {
        this.items = [this.getAssaysGrid()];

        this.callParent();

        this.queryAssayPlan();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getAssaysGrid : function()
    {
        if (!this.assaysGrid)
        {
            this.assaysGrid = Ext4.create('LABKEY.VaccineDesign.AssaysGrid', {
                disableEdit: this.disableEdit,
                visitNoun: this.visitNoun,
                useAlternateLookupFields: this.useAlternateLookupFields
            });

            this.assaysGrid.on('dirtychange', this.enableSaveButton, this);
            this.assaysGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.assaysGrid;
    },

    queryAssayPlan : function()
    {
        // query the StudyProperties table for the initial assay plan value
        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'StudyProperties',
            columns: 'AssayPlan',
            scope: this,
            success: function(data)
            {
                var text = (data.rows.length == 1) ? data.rows[0]["AssayPlan"] : '';

                this.add(this.getAssayPlanPanel(text));
                this.add(this.getButtonBar());
            }
        });

    },

    getAssayPlanPanel : function(initValue)
    {
        if (!this.assayPlanPanel)
        {
            this.assayPlanPanel = Ext4.create('Ext.form.Panel', {
                cls: 'study-vaccine-design',
                padding: '20px 0',
                border: false,
                items: [
                    Ext4.create('Ext.Component', {
                        html: '<div class="main-title">Assay Plan</div>'
                    }),
                    this.getAssayPlanTextArea(initValue)
                ]
            });
        }

        return this.assayPlanPanel;
    },

    getAssayPlanTextArea : function(initValue)
    {
        if (!this.assayPlanTextArea)
        {
            this.assayPlanTextArea = Ext4.create('Ext.form.field.TextArea', {
                name: 'assayPlan',
                readOnly: this.disableEdit,
                value: initValue,
                width: 500,
                height: 100
            });

            this.assayPlanTextArea.on('change', this.enableSaveButton, this, {buffer: 500});
        }

        return this.assayPlanTextArea;
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
                handler: this.saveAssaySchedule,
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

    saveAssaySchedule : function()
    {
        this.getEl().mask('Saving...');

        var assays = [], errorMsg = [];
        Ext4.each(this.getAssaysGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);

            // drop any empty treatment rows that were just added
            var hasData = LABKEY.VaccineDesign.Utils.modelHasData(recData, LABKEY.VaccineDesign.Assay.getFields());
            if (hasData)
            {
                var sampleQuantity = Number(recData['SampleQuantity']);
                if (isNaN(sampleQuantity) || sampleQuantity < 0)
                    errorMsg.push('Assay sample quantity value must be a positive number: ' + recData['SampleQuantity'] + '.');
                else
                    assays.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
            return;
        }

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-design', 'updateAssaySchedule.api'),
            method  : 'POST',
            jsonData: {
                assays: assays,
                assayPlan: this.getAssayPlanTextArea().getValue()
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

Ext4.define('LABKEY.VaccineDesign.AssaysGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataViewAddVisit',

    cls : 'study-vaccine-design vaccine-design-assays',

    mainTitle : 'Assay Schedule',

    width : 620,

    studyDesignQueryNames : ['StudyDesignAssays', 'StudyDesignLabs', 'StudyDesignSampleTypes', 'StudyDesignUnits', 'Location', 'DataSets'],

    visitNoun : 'Visit',

    useAlternateLookupFields : false,

    //Override
    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                storeId : 'AssaysGridStore',
                pageSize : 100000, // need to explicitly set otherwise it defaults to 25
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('query', 'selectRows.api', null, {
                        'schemaName' : 'study',
                        'query.queryName' : 'assayspecimen'
                    }),
                    reader: {
                        type: 'json',
                        root: 'rows'
                    }
                },
                sorters: [{ property: 'AssayName', direction: 'ASC' }],
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: this.getVisitStore
                }
            });
        }

        return this.store;
    },

    getVisitStore : function()
    {
        if (!this.visitStore)
        {
            this.visitStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Visit',
                pageSize : 100000, // need to explicitly set otherwise it defaults to 25
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('query', 'selectRows.api', null, {
                        'schemaName' : 'study',
                        'query.queryName' : 'visit'
                    }),
                    reader: {
                        type: 'json',
                        root: 'rows'
                    }
                },
                sorters: [{ property: 'DisplayOrder', direction: 'ASC' }, { property: 'SequenceNumMin', direction: 'ASC' }],
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: this.getAssaySpecimenVisitStore
                }
            });
        }

        return this.visitStore;
    },

    getAssaySpecimenVisitStore : function()
    {
        if (!this.assaySpecimenVisitStore)
        {
            this.assaySpecimenVisitStore = Ext4.create('Ext4.data.Store', {
                storeId : 'AssaySpecimenVisitStore',
                model : 'LABKEY.VaccineDesign.AssaySpecimenVisit',
                pageSize : 100000, // need to explicitly set otherwise it defaults to 25
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('query', 'selectRows.api', null, {
                        'schemaName' : 'study',
                        'query.queryName' : 'AssaySpecimenVisit'
                    }),
                    reader: {
                        type: 'json',
                        root: 'rows'
                    }
                },
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: function (store, records)
                    {
                        var includedVisits = [];

                        // stash the visit mapping information attached to each record in the assay store
                        Ext4.each(records, function(record)
                        {
                            var assayRecord = this.getStore().findRecord('RowId', record.get('AssaySpecimenId'));
                            if (assayRecord != null)
                            {
                                var visitMap = assayRecord.get('VisitMap') || [];
                                visitMap.push(Ext4.clone(record.data));
                                assayRecord.set('VisitMap', visitMap);

                                includedVisits.push(record.get('VisitId'));
                            }
                        }, this);

                        var includedVisits = Ext4.Array.unique(includedVisits);
                        Ext4.each(this.getVisitStore().getRange(), function(visit)
                        {
                            var included = includedVisits.indexOf(visit.get('RowId')) > -1;
                            visit.set('Included', included);
                        }, this);

                        this.add(this.getDataView());
                        this.fireEvent('loadcomplete', this);
                    }
                }
            });
        }

        return this.assaySpecimenVisitStore;
    },

    //Override
    loadDataViewStore : function()
    {
        // just call getStore here to initial the load, we will add the DataView
        // and fire the loadcomplete event after all of the stores for this page are done loading
        this.getStore();
    },

    columnHasData : function(dataIndex)
    {
        var recordsDataArr = Ext4.Array.pluck(this.getStore().getRange(), 'data'),
            colDataArr = Ext4.Array.pluck(recordsDataArr, dataIndex);

        for (var i = 0; i < colDataArr.length; i++)
        {
            if ((Ext4.isNumber(colDataArr[i]) && colDataArr[i] > 0) || (Ext4.isString(colDataArr[i]) && colDataArr[i] != ''))
                return true;
        }

        return false;
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var width = 0; // add to the running width as we go through which columns to show in the config
            var columnConfigs = [];

            var assayNameEditorConfig = LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('AssayName', 185, 'StudyDesignAssays');
            assayNameEditorConfig.editable = true; // Rho use-case

            columnConfigs.push({
                label: 'Assay Name',
                width: 200,
                dataIndex: 'AssayName',
                required: true,
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: assayNameEditorConfig
            });
            width += 200;

            columnConfigs.push({
                label: 'Dataset',
                width: 200,
                dataIndex: 'DataSet',
                queryName: 'DataSets',
                editorType: 'LABKEY.ext4.ComboBox',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('DataSet', 185, 'DataSets', undefined, 'Label', 'DataSetId')
            });
            width += 200;

            var hidden = this.disableEdit && !this.columnHasData('Description');
            columnConfigs.push({
                label: 'Description',
                width: 200,
                hidden: hidden,
                dataIndex: 'Description',
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Description', 185)
            });
            if (!hidden) {
                width += 200;
            }

            if (this.useAlternateLookupFields)
            {
                hidden = this.disableEdit && !this.columnHasData('Source');
                columnConfigs.push({
                    label: 'Source',
                    width: 60,
                    hidden: hidden,
                    dataIndex: 'Source',
                    editorType: 'Ext.form.field.Text',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Source', 45)
                });
                if (!hidden) {
                    width += 60;
                }

                hidden = this.disableEdit && !this.columnHasData('LocationId');
                columnConfigs.push({
                    label: 'Location',
                    width: 140,
                    hidden: hidden,
                    dataIndex: 'LocationId',
                    queryName: 'Location',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('LocationId', 125, 'Location', undefined, 'Label', 'RowId')
                });
                if (!hidden) {
                    width += 140;
                }

                hidden = this.disableEdit && !this.columnHasData('TubeType');
                columnConfigs.push({
                    label: 'TubeType',
                    width: 200,
                    hidden: hidden,
                    dataIndex: 'TubeType',
                    editorType: 'Ext.form.field.Text',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('TubeType', 185)
                });
                if (!hidden) {
                    width += 200;
                }
            }
            else
            {
                hidden = this.disableEdit && !this.columnHasData('Lab');
                columnConfigs.push({
                    label: 'Lab',
                    width: 140,
                    hidden: hidden,
                    dataIndex: 'Lab',
                    queryName: 'StudyDesignLabs',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('Lab', 125, 'StudyDesignLabs')
                });
                if (!hidden) {
                    width += 140;
                }

                hidden = this.disableEdit && !this.columnHasData('SampleType');
                columnConfigs.push({
                    label: 'Sample Type',
                    width: 140,
                    hidden: hidden,
                    dataIndex: 'SampleType',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('SampleType', 125, 'StudyDesignSampleTypes', undefined, 'Name')
                });
                if (!hidden) {
                    width += 140;
                }

                hidden = this.disableEdit && !this.columnHasData('SampleQuantity');
                columnConfigs.push({
                    label: 'Sample Quantity',
                    width: 140,
                    hidden: hidden,
                    dataIndex: 'SampleQuantity',
                    editorType: 'Ext.form.field.Number',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignNumberConfig('SampleQuantity', 125, 2)
                });
                if (!hidden) {
                    width += 140;
                }

                hidden = this.disableEdit && !this.columnHasData('SampleUnits');
                columnConfigs.push({
                    label: 'Sample Units',
                    width: 140,
                    hidden: hidden,
                    dataIndex: 'SampleUnits',
                    queryName: 'StudyDesignUnits',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('SampleUnits', 125, 'StudyDesignUnits')
                });
                if (!hidden) {
                    width += 140;
                }
            }

            var visitConfigs = this.getVisitColumnConfigs();

            // update the width based on the number of visit columns
            width += (Math.max(2, visitConfigs.length) * 75);
            this.setWidth(width);

            // update the outer panel width if necessary
            var outerPanel = this.up('panel');
            if (outerPanel != null)
                outerPanel.setWidth(Math.max(width, 750));

            this.columnConfigs = columnConfigs.concat(visitConfigs);
        }

        return this.columnConfigs;
    },

    getVisitColumnConfigs : function()
    {
        var visitConfigs = [];

        Ext4.each(this.getVisitStore().getRange(), function(visit)
        {
            if (visit.get('Included'))
            {
                visitConfigs.push({
                    label: visit.get('Label') || visit.get('SequenceNumMin'),
                    width: 75,
                    dataIndex: 'VisitMap',
                    dataIndexArrFilterProp: 'VisitId',
                    dataIndexArrFilterValue: visit.get('RowId'),
                    editorType: 'Ext.form.field.Checkbox',
                    editorConfig: {
                        hideFieldLabel: true,
                        name: 'VisitMap'
                    }
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
    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        var value = this.callParent([column, record, dataIndex, outerDataIndex, subgridIndex]);

        if (dataIndex == 'VisitMap' && Ext4.isArray(value))
        {
            var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(value, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);
            return matchingIndex > -1;
        }
        else if ((dataIndex == 'SampleQuantity' || dataIndex == 'LocationId') || dataIndex == 'DataSet' && value == 0)
        {
            return null;
        }

        return value;
    },

    //Override
    updateStoreRecordValue : function(record, column, newValue, field)
    {
        // special case for editing the value of one of the pivot visit columns
        if (column.dataIndex == 'VisitMap')
        {
            var visitMapArr = record.get(column.dataIndex);
            if (!Ext4.isArray(visitMapArr))
            {
                visitMapArr = [];
                record.set(column.dataIndex, visitMapArr);
            }

            var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(visitMapArr, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);

            if (newValue)
                visitMapArr.push({VisitId: column.dataIndexArrFilterValue});
            else
                Ext4.Array.splice(visitMapArr, matchingIndex, 1);

            this.fireEvent('celledited', this, 'VisitMap', visitMapArr);
        }
        else
        {
            this.callParent([record, column, newValue]);
        }
    },

    //Override
    getNewModelInstance : function()
    {
        var newAssay = LABKEY.VaccineDesign.Assay.create();
        newAssay.set('VisitMap', []);
        return newAssay;
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected assay configuration? '
            + 'Note: this will also delete all related visit mapping information.';
    }
});
