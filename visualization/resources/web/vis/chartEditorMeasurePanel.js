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
            labelWidth: 0,
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

        // add labels indicating the selected measure and which query it is from
        columnOneItems.push({
            xtype: 'label',
            html: 'Measure:<BR/>'
        });
        columnOneItems.push({
            id: 'measure-label',
            xtype: 'label',
            text: this.measure.label + ' from ' + this.measure.queryName
        });

        // add a button for the user to change which measure is selected for the chart
        columnOneItems.push({
            xtype: 'button',
            text: 'Change',
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

        // add a label and radio buttons for allowing user to divide data into series (subject and dimension options)
        columnTwoItems.push({
            xtype: 'label',
            html: 'Divide data into Series:<BR/>'
        });
        this.seriesPerSubjectRadio = new Ext.form.Radio({
            name: 'measure_series',
            inputValue: 'per_subject',
            hideLabel: true,
            boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular,
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
                        // enable the dimension and aggregate combo box
                        this.measureDimensionComboBox.enable();
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();

                        // if saved chart, then set dimension value based on the saved value
                        if(this.dimension){
                            this.measureDimensionComboBox.setValue(this.dimension.name);
                        }
                        // otherwise select the first item and then give the input focus
                        else{
                            var selIndex = 0;
                            var selRecord = this.measureDimensionComboBox.getStore().getAt(selIndex);
                            this.measureDimensionComboBox.setValue(selRecord.get("name"));
                            this.measureDimensionComboBox.fireEvent('select', this.measureDimensionComboBox, selRecord, selIndex);
                        }

                        // enable and set the dimension aggregate combo box
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();
                        this.setDimensionAggregate(LABKEY.Visualization.Aggregate.AVG);
                    }
                }
            }
        });

        this.measureDimensionComboBox = new Ext.form.ComboBox({
            emptyText: '<Select Grouping Field>',
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
            hideLabel: true,
            items: [
                this.seriesPerDimensionRadio,
                this.measureDimensionComboBox
            ]
        });

        // get the list of aggregate options from LABKEY.Visualization.Aggregate
        var aggregates = new Array();
        for(var item in LABKEY.Visualization.Aggregate){
            aggregates.push([LABKEY.Visualization.Aggregate[item]]);
        };

        // initialize the aggregate combobox
        this.dimensionAggregateComboBox = new Ext.form.ComboBox({
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.ArrayStore({
                fields: ['name'],
                data: aggregates,
                sortInfo: {
                    field: 'name',
                    direction: 'ASC'
                }
            }),
            valueField: 'name',
            displayField: 'name',
            disabled: true,
            width: 75,
            style: {
                marginLeft: '20px'
            },
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.setDimensionAggregate(cmp.getValue());
                    this.fireEvent('chartDefinitionChanged', true);
                }
            }
        });

        // the aggregate combo label has to be a separate component so that it can also be disabled/enabled
        this.dimensionAggregateLabel = new Ext.form.Label({
            text: 'Display Duplicate Values as: ',
            style: {
                marginLeft: '20px'
            },
            disabled: true
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            hideLabel: true,
            items: [
                this.dimensionAggregateLabel,
                this.dimensionAggregateComboBox
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
                var sm = new  Ext.grid.CheckboxSelectionModel({});
                sm.on('selectionchange', function(selModel){
                    // add the selected dimension values to the chartInfo
                    this.dimension.values = new Array();
                    var selectedRecords = selModel.getSelections();
                    for(var i = 0; i < selectedRecords.length; i++) {
                        this.dimension.values.push(selectedRecords[i].get('value'));
                    }

                    this.fireEvent('chartDefinitionChanged', true);
                }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

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
                newSeriesSelectorPanel.on('activate', function(){
                   newSeriesSelectorPanel.doLayout();
                }, this);

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

            this.dimensionAggregateLabel.enable();
            this.dimensionAggregateComboBox.enable();
            this.setDimensionAggregate(this.measure.aggregate);
        }
        else{
            this.measureDimensionComboBox.disable();
            this.dimensionAggregateLabel.disable();
            this.dimensionAggregateComboBox.disable();
        }


        // set the dimension radio as enabled/disabled
        if(this.measureDimensionComboBox.getStore().getCount() == 0){
            this.seriesPerDimensionRadio.disable();
        }
        else{
            this.seriesPerDimensionRadio.enable();
        }
    },

    removeDimension: function(){
        // remove any dimension selection/values that were added to the yaxis measure
        this.dimension = null;

        // disable and clear the dimension combobox
        this.measureDimensionComboBox.disable();
        this.measureDimensionComboBox.setValue("");

        // disable and clear the dimension aggregate combobox
        this.dimensionAggregateLabel.disable();
        this.dimensionAggregateComboBox.disable();
        this.setDimensionAggregate("");

        // if there was a different dimension selection, remove that list view from the series selector
        Ext.getCmp('series-selector-tabpanel').remove('dimension-series-selector-panel', true);
        Ext.getCmp('series-selector-tabpanel').doLayout();
    },

    setDimensionAggregate: function(newAggregate){
        this.dimensionAggregateComboBox.setValue(newAggregate);
        if(newAggregate != ""){
            this.measure.aggregate = newAggregate;
        }
        else{
            delete this.measure.aggregate;
        }
    }
});
