/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorMeasurePanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.applyIf(config, {
            title: 'Measure',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding:5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

        this.addEvents(
            'measureSelected',
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        LABKEY.vis.ChartEditorMeasurePanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the measure editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        columnOneItems.push({
            id: 'measure-label',
            xtype: 'label',
            fieldLabel: 'Measure',
            text: this.measure.label + ' from ' + this.measure.queryName
        });

        columnOneItems.push({
            xtype: 'button',
            text: 'Change',
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

        this.seriesPerSubjectRadio = new Ext.form.Radio({
            fieldLabel: 'Divide data into Series',
            name: 'measure_series',
            inputValue: 'per_subject',
            boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular,
            height: 1,
            checked: true,
            listeners: {
                scope: this,
                'check': function(field, checked) {
                    if(checked) {
                        this.removeDimension();
                        this.fireEvent('chartDefinitionChanged', true);
                    }
                }
            }
        });
        columnTwoItems.push(this.seriesPerSubjectRadio);

        this.seriesPerDimensionRadio = new Ext.form.Radio({
            name: 'measure_series',
            inputValue: 'per_subject_and_dimension',
            boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular + ' and ',
            disabled: true,
            width: 185,
            height: 1,
            listeners: {
                scope: this,
                'check': function(field, checked){
                    // when this radio option is selected, enable the dimension combo box
                    if(checked) {
                        // by default select the first item and then give the input focus
                        this.measureDimensionComboBox.enable();

                        // if saved chart, then set dimension value based on the saved value
                        if(this.dimension){
                            this.measureDimensionComboBox.setValue(this.dimension.name);
                        }
                        else{
                            var selIndex = 0;
                            var selRecord = this.measureDimensionComboBox.getStore().getAt(selIndex);
                            this.measureDimensionComboBox.setValue(selRecord.get("name"));
                            this.measureDimensionComboBox.fireEvent('select', this.measureDimensionComboBox, selRecord, selIndex);
                        }
                    }
                }
            }
        });

        this.measureDimensionComboBox = new Ext.form.ComboBox({
            id: 'measure-dimension-combo',
            emptyText: '<Select Grouping Field>',
            //editable: false,
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.Store({}),
            valueField: 'name',
            displayField: 'label',
            disabled: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.dimension = record.data;
                    this.measureDimensionSelected(true);
                }
            }
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            //id: 'measure-series-per-subject-dimension',
            defaults: {flex: 1},
            items: [
                this.seriesPerDimensionRadio,
                this.measureDimensionComboBox
            ]
        });

        this.items = [{
            layout: 'column',
            items: [{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems
            },{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnTwoItems
            }]
        }];

        this.on('activate', function(){
           this.doLayout();
        }, this);

        LABKEY.vis.ChartEditorMeasurePanel.superclass.initComponent.call(this);
    },

    showMeasureSelectionWindow: function() {
        delete this.changeMeasureSelection;
        var win = new Ext.Window({
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'hide',
            items: new LABKEY.vis.MeasuresPanel({
                axis: [{
                    multiSelect: false,
                    name: "y-axis",
                    label: "Choose a data measure for the y-axis"
                }],
                listeners: {
                    scope: this,
                    'measureChanged': function (axisId, data) {
                        // store the selected measure for later use
                        this.changeMeasureSelection = data;

                        Ext.getCmp('measure-selection-button').setDisabled(false);
                    }
                }
            }),
            buttons: [{
                id: 'measure-selection-button',
                text:'Select',
                disabled:true,
                handler: function(){
                    if(this.changeMeasureSelection) {
                        this.measure = this.changeMeasureSelection;

                        // fire the measureSelected event so other panels can update as well
                        this.fireEvent('measureSelected', this.changeMeasureSelection, true);

                        win.hide();
                    }
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function(){
                    delete this.changeMeasureSelection;
                    win.hide();
                },
                scope: this
            }]
        });
        win.show(this);
    },

    newDimensionStore: function() {
        return new Ext.data.Store({
            autoLoad: true,
            reader: new Ext.data.JsonReader({
                    root:'dimensions',
                    idProperty:'id'
                },
                ['id', 'name', 'label', 'description', 'isUserDefined', 'queryName', 'schemaName', 'type']
            ),
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, this.measure)
            }),
            sortInfo: {
                field: 'label',
                direction: 'ASC'
            },
            listeners: {
                scope: this,
                'load': function(store, records, options) {
                    // loop through the records to remove Subject as a dimension option
                    for(var i = 0; i < records.length; i++) {
                        if(records[i].data.name == this.viewInfo.subjectColumn) {
                            store.remove(records[i]);
                            break;
                        }
                    }

                    // set dimension radio and combo options (enabled/disabled, etc.)
                    this.setDimensionOptions();

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
                }
            }
        })
    },

    measureDimensionSelected: function(reloadChartData) {
        // if there was a different dimension selection, remove that list view from the series selector
        Ext.getCmp('series-selector-tabpanel').remove('dimension-series-selector-panel', true);
        Ext.getCmp('series-selector-tabpanel').doLayout();

        // get the dimension values for the selected dimension/grouping
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, this.dimension),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                // decode the JSON responseText
                var dimensionValues = Ext.util.JSON.decode(response.responseText);

                // todo: get this to select the first 5 values after the gridPanel is sorted
                // if this is not a saved chart with pre-selected values, initially select the first 5 values
                if(!this.dimension.values){
                    this.dimension.values = new Array();
                    //var gridStore = Ext.getCmp('dimension-list-view').getStore();
                    for(var i = 0; i < (dimensionValues.values.length < 5 ? dimensionValues.values.length : 5); i++) {
                        this.dimension.values.push(dimensionValues.values[i].value);
                    }
                }

                // put the dimension values into a list view for the user to enable/disable series
                var sm = new  Ext.grid.CheckboxSelectionModel({
                    listeners: {
                        scope: this,
                        'selectionChange': function(selModel){
                            // add the selected dimension values to the chartInfo
                            this.dimension.values = new Array();
                            var selectedRecords = selModel.getSelections();
                            for(var i = 0; i < selectedRecords.length; i++) {
                                this.dimension.values.push(selectedRecords[i].get('value'));
                            }

                            this.fireEvent('chartDefinitionChanged', true);
                        }
                    }
                });
                var newSeriesSelectorPanel = new Ext.Panel({
                    id: 'dimension-series-selector-panel',
                    title: this.dimension.label,
                    autoScroll: true,
                    items: [
                        new Ext.grid.GridPanel({
                            id: 'dimension-list-view',
                            autoHeight: true,
                            enableHdMenu: false,
                            store: new Ext.data.JsonStore({
                                root: 'values',
                                fields: ['value'],
                                data: dimensionValues,
                                sortInfo: {
                                    field: 'value',
                                    direction: 'ASC'
                                }
                            }),
                            viewConfig: {forceFit: true},
                            border: false,
                            frame: false,
                            columns: [
                                sm,
                                {header: this.dimension.label, dataIndex:'value'}
                            ],
                            selModel: sm,
                            header: false,
                            listeners: {
                                scope: this,
                                'viewready': function(grid) {
                                    // check selected dimension values in grid panel (but suspend events during selection)
                                    var dimSelModel = grid.getSelectionModel();
                                    var dimStore = grid.getStore();
                                    dimSelModel.suspendEvents(false);
                                    for(var i = 0; i < this.dimension.values.length; i++){
                                        var index = dimStore.find('value', this.dimension.values[i]);
                                        dimSelModel.selectRow(index, true);
                                    }
                                    dimSelModel.resumeEvents();
                                }
                            }
                         })
                    ]
                });
                Ext.getCmp('series-selector-tabpanel').add(newSeriesSelectorPanel);
                Ext.getCmp('series-selector-tabpanel').activate('dimension-series-selector-panel');
                Ext.getCmp('series-selector-tabpanel').doLayout();

                if(reloadChartData){
                    this.fireEvent('chartDefinitionChanged', true);
                }
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    getMeasure: function(){
        return this.measure;
    },

    getDimension: function(){
        return this.dimension;
    },

    setMeasureLabel: function(newLabel){
        Ext.getCmp('measure-label').setText(newLabel);
    },

    setDimensionStore: function(dimension){
        // if we are not setting the store with a selected dimension, remove the dimension object from this
        if(!dimension){
            this.removeDimension();
            this.seriesPerSubjectRadio.suspendEvents(false);
            this.seriesPerSubjectRadio.setValue(true);
            this.seriesPerDimensionRadio.setValue(false);
            this.seriesPerSubjectRadio.resumeEvents();
        }
        else{
            this.seriesPerDimensionRadio.suspendEvents(false);
            this.seriesPerDimensionRadio.setValue(true);
            this.seriesPerSubjectRadio.setValue(false);
            this.seriesPerDimensionRadio.resumeEvents();
        }

        // re-initialize the dimension store and bind it to the combobox
        this.fireEvent('measureMetadataRequestPending');
        var newDStore = this.newDimensionStore();
        this.measureDimensionComboBox.bindStore(newDStore);

        // if this is a saved chart with a dimension selected, show dimension selector tab
        if(dimension){
            this.measureDimensionSelected(false);
        }
    },

    setDimensionOptions: function(){
        // enable/disable the dimension combo box depending if there is a dimension set
        if(this.dimension){
            this.measureDimensionComboBox.enable();
            this.measureDimensionComboBox.setValue(this.dimension.name);
        }
        else{
            this.measureDimensionComboBox.disable();
        }


        // set the dimension radio as enabled/disabled
        if(this.measureDimensionComboBox.getStore().getCount() == 0){
            this.seriesPerDimensionRadio.setDisabled(true);
        }
        else{
            this.seriesPerDimensionRadio.setDisabled(false);
        }
    },

    removeDimension: function(){
        // remove any dimension selection/values that were added to the yaxis measure
        this.dimension = null;

        // disable and clear the dimension combobox
        this.measureDimensionComboBox.disable();
        this.measureDimensionComboBox.setValue("");

        // if there was a different dimension selection, remove that list view from the series selector
        Ext.getCmp('series-selector-tabpanel').remove('dimension-series-selector-panel', true);
        Ext.getCmp('series-selector-tabpanel').doLayout();
    }
});
