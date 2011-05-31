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
            measures: [],
            title: 'Measures',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding:5px',
            border: false,
            labelWidth: 0,
            items: []
        });

        this.addEvents(
            'measureSelected',
            'dimensionSelected',
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete',
            'filterCleared'
        );

        // add any y-axis measures from the origMeasures object (for saved chart)
        if(typeof config.origMeasures == "object"){
            for(var i = 0; i < config.origMeasures.length; i++){
                if(config.origMeasures[i].axis.name == 'y-axis'){
                    config.measures.push({
                        measure: Ext.apply({}, config.origMeasures[i].measure),
                        dimension: Ext.apply({}, config.origMeasures[i].dimension)
                    });
                }
            }
        }

        LABKEY.vis.ChartEditorMeasurePanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the measure editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        // add labels indicating the selected measure and which query it is from
        this.measuresListsView = new Ext.list.ListView({
            width: 400,
            height: 95,
            //style: {border: 'solid grey 1px'},
            hideHeaders: true,
            multiSelect: false,
            singleSelect: true,
            store: new Ext.data.JsonStore({
                fields: [
                    {name: 'id', type: 'int'},
                    {name: 'label', type: 'string'},
                    {name: 'name', type: 'string'},
                    {name: 'queryName', type: 'string'}
                ]
            }),
            columns: [{
                width: 1,
                dataIndex: 'label'
            }]
        });
        columnOneItems.push(this.measuresListsView);

        // add any original measures (from saved chart) to the measure listview
        for(var i = 0; i < this.measures.length; i++){
            this.addMeasureToListView(this.measures[i].measure);
        }

        // add a button for the user to change which measure is selected for the chart
        columnOneItems.push({
            xtype: 'button',
            text: 'Change',
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

        this.dataFilterUrl = this.filterUrl;
        this.dataFilterWarning = new Ext.form.Label({
            // No text by default
        });
        this.dataFilterRemoveButton = new Ext.Button({
            hidden: true,
            text: 'Remove',
            listeners: {
                scope: this,
                'click' : function()
                {
                    this.removeFilterWarning();
                }
            }
        });
        columnOneItems.push(this.dataFilterWarning);
        columnOneItems.push(this.dataFilterRemoveButton);

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
                        this.fireEvent('dimensionSelected', false);
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
                        if(this.measures[this.getSelectedMeasureIndex()].dimension){
                            this.measureDimensionComboBox.setValue(this.measures[this.getSelectedMeasureIndex()].dimension.name);
                        }
                        // otherwise try to select the first item and then give the input focus
                        else{
                            var selIndex = 0;
                            var selRecord = this.measureDimensionComboBox.getStore().getAt(selIndex);
                            if(selRecord){
                                this.measureDimensionComboBox.setValue(selRecord.get("name"));
                                this.measureDimensionComboBox.fireEvent('select', this.measureDimensionComboBox, selRecord, selIndex);
                            }
                        }

                        // enable and set the dimension aggregate combo box
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();
                        this.setDimensionAggregate(LABKEY.Visualization.Aggregate.AVG);

                        this.fireEvent('dimensionSelected', true);
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
                    this.measures[this.getSelectedMeasureIndex()].dimension = {
                        label: record.data.label,
                        name: record.data.name,
                        queryName: record.data.queryName,
                        schemaName: record.data.schemaName,
                        type: record.data.type
                    };
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
            border: false,
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

    setFilterWarningText: function(text)
    {
        var warning = "<b>This chart data is filtered:</b> " + LABKEY.Utils.encodeHtml(text);
        this.dataFilterWarning.setText(warning, false);
        this.dataFilterRemoveButton.show();
    },

    removeFilterWarning: function()
    {
        this.dataFilterUrl = undefined;
        this.dataFilterWarning.setText('');
        this.dataFilterRemoveButton.hide();
        this.fireEvent('filterCleared');
    },

    getDataFilterUrl: function()
    {
        return this.dataFilterUrl;
    },

    showMeasureSelectionWindow: function() {
        delete this.changeMeasureSelection;
        var win = new Ext.Window({
            cls: 'extContainer',
            title: 'Change Measure...',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'hide',
            items: new LABKEY.vis.MeasuresPanel({
                axis: [{
                    multiSelect: false,
                    name: "y-axis",
                    label: "Choose a data measure"
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

    addMeasure: function(newMeasure){
        // clear the measure from this and the listview
        this.measures = [];
        this.measuresListsView.getStore().removeAll();

        // add the measure to this and the listview
        this.measures.push({measure: newMeasure});
        this.addMeasureToListView(newMeasure);
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
                url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, this.measures[this.getSelectedMeasureIndex()].measure)
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
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, this.measures[this.getSelectedMeasureIndex()].dimension),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                // decode the JSON responseText
                var dimensionValues = Ext.util.JSON.decode(response.responseText);

                this.defaultDisplayField = new Ext.form.DisplayField({
                    hideLabel: true,
                    hidden: true,
                    value: 'Selecting 5 values by default',
                    style: 'font-size:75%;color:red;'
                });

                // put the dimension values into a list view for the user to enable/disable series
                var sm = new  Ext.grid.CheckboxSelectionModel({});
                sm.on('selectionchange', function(selModel){
                    // add the selected dimension values to the chartInfo
                    this.measures[this.getSelectedMeasureIndex()].dimension.values = new Array();
                    var selectedRecords = selModel.getSelections();
                    for(var i = 0; i < selectedRecords.length; i++) {
                        this.measures[this.getSelectedMeasureIndex()].dimension.values.push(selectedRecords[i].get('value'));
                    }

                    // sort the selected dimension array
                    this.measures[this.getSelectedMeasureIndex()].dimension.values.sort();

                    this.fireEvent('chartDefinitionChanged', true);
                }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

                var newSeriesSelectorPanel = new Ext.Panel({
                    id: 'dimension-series-selector-panel',
                    title: this.measures[this.getSelectedMeasureIndex()].dimension.label,
                    autoScroll: true,
                    items: [
                        this.defaultDisplayField,
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
                                {header: this.measures[this.getSelectedMeasureIndex()].dimension.label, dataIndex:'value'}
                            ],
                            selModel: sm,
                            header: false,
                            listeners: {
                                scope: this,
                                'viewready': function(grid) {
                                    // if this is not a saved chart with pre-selected values, initially select the first 5 values
                                    var selectDefault = false;
                                    if(!this.measures[this.getSelectedMeasureIndex()].dimension.values){
                                        selectDefault = true;
                                        this.measures[this.getSelectedMeasureIndex()].dimension.values = [];
                                        for(var i = 0; i < (grid.getStore().getCount() < 5 ? grid.getStore().getCount() : 5); i++) {
                                            this.measures[this.getSelectedMeasureIndex()].dimension.values.push(grid.getStore().getAt(i).data.value);
                                        }
                                    }

                                    // check selected dimension values in grid panel (but suspend events during selection)
                                    var dimSelModel = grid.getSelectionModel();
                                    var dimStore = grid.getStore();
                                    dimSelModel.suspendEvents(false);
                                    for(var i = 0; i < this.measures[this.getSelectedMeasureIndex()].dimension.values.length; i++){
                                        var index = dimStore.find('value', this.measures[this.getSelectedMeasureIndex()].dimension.values[i]);
                                        dimSelModel.selectRow(index, true);
                                    }
                                    dimSelModel.resumeEvents();

                                    // show the selecting default text if necessary
                                    if(grid.getStore().getCount() > 5 && selectDefault){
                                        // show the display for 3 seconds before hiding it again
                                        var refThis = this;
                                        refThis.defaultDisplayField.show();
                                        refThis.doLayout();
                                        setTimeout(function(){
                                            refThis.defaultDisplayField.hide();
                                            refThis.doLayout();
                                        },5000);
                                    }

                                    if(reloadChartData){
                                        this.fireEvent('chartDefinitionChanged', true);
                                    }
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
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    getMeasure: function(){
        // todo: for multiple measures, will need to return an array of measures
        return this.measures[this.getSelectedMeasureIndex()].measure;
    },

    getDimension: function(){
        // todo: for multiple measures, this should be returned with the getMeasure call
        return this.measures[this.getSelectedMeasureIndex()].dimension;
    },

    addMeasureToListView: function(measure){
        // add the measure label to the listview
        var listStore = this.measuresListsView.getStore();
        var recIdx = listStore.getCount();
        var rec = new listStore.recordType({
            id: recIdx,
            label: measure.label + " from " + measure.queryName,
            name: measure.name,
            queryName: measure.queryName
        }, recIdx);
        listStore.insert(recIdx, rec);

        // select the new record from the listview (suppress events)
        this.measuresListsView.select(rec, false, true);
    },

    getSelectedMeasureIndex: function(){
        if(this.measuresListsView.getSelectionCount() == 1){
            var rec = this.measuresListsView.getSelectedRecords()[0];
            var index = 0;
            for(var i = 0; i < this.measures.length; i++){
                if(this.measures[i].measure.name == rec.get("name") && this.measures[i].measure.queryName == rec.get("queryName")){
                    index = i;
                    break;
                }
            }
            return index;
        }
        else
            return 0; // todo: what should actually be returned if no measure is selected?
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
        if(this.measures[this.getSelectedMeasureIndex()].dimension){
            this.measureDimensionComboBox.enable();
            this.measureDimensionComboBox.setValue(this.measures[this.getSelectedMeasureIndex()].dimension.name);

            this.dimensionAggregateLabel.enable();
            this.dimensionAggregateComboBox.enable();
            this.setDimensionAggregate(this.measures[this.getSelectedMeasureIndex()].measure.aggregate);
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
        this.measures[this.getSelectedMeasureIndex()].dimension = null;

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
            this.measures[this.getSelectedMeasureIndex()].measure.aggregate = newAggregate;
        }
        else{
            delete this.measures[this.getSelectedMeasureIndex()].measure.aggregate;
        }
    }
});
