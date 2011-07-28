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
            'filterCleared',
            'measureRemoved'
        );

        // add any y-axis measures from the origMeasures object (for saved chart)
        if(typeof config.origMeasures == "object"){
            for(var i = 0; i < config.origMeasures.length; i++){
                if(config.origMeasures[i].axis.name == 'y-axis'){
                    config.measures.push({
                        id: i,
                        name: config.origMeasures[i].measure.name,
                        queryName: config.origMeasures[i].measure.queryName,
                        origLabel: config.origMeasures[i].measure.label,
                        label: config.origMeasures[i].measure.label + " from " + config.origMeasures[i].measure.queryName,
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
            hideHeaders: true,
            multiSelect: false,
            singleSelect: true,
            store: new Ext.data.JsonStore({
                root: 'measures',
                idProperty: 'id',
                fields: [
                    {name: 'id', type: 'integer'},
                    {name: 'label', type: 'string'},
                    {name: 'name', type: 'string'},
                    {name: 'queryName', type: 'string'}
                ]
            }),
            columns: [{
                width: 1,
                dataIndex: 'label'
            }],
            listeners: {
                scope: this,
                'afterrender': function(listView){
                    // if no record selected, select the first
                    if(listView.getSelectedIndexes().length == 0){
                        listView.select(0);
                    }
                },
                'selectionchange': function(listView, selections){
                    // set the UI components for the measures series information
                    if(listView.getSelectedIndexes().length > 0){
                        var md = this.measures[this.getSelectedMeasureIndex()];
                        this.measureDimensionComboBox.bindStore(md.dimensionStore);
                        this.toggleDimensionComponents(md.dimension.name, md.measure.aggregate);
                    }
                }
            }
        });
        columnOneItems.push({
            xtype: 'panel',
            border: true,
            width: 400,
            items: [this.measuresListsView]
        });

        // add any original measures (from saved chart) to the measure listview
        if(this.measures.length > 0){
            this.measuresListsView.getStore().loadData(this);
            this.measuresListsView.select(this.measuresListsView.getStore().getCount()-1, false, true);
        }

        // add a button for the user to add a measure to the chart
        this.addMeasureButton = new Ext.Button({
            text: 'Add Measure',
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

       // add a button for the user to remove the selected measure
        this.removeMeasureButton = new Ext.Button({
            text: 'Remove Measure',
            disabled: this.measures.length <= 1,
            handler: this.removeSelectedMeasure,
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
                    if(checked && this.getSelectedMeasureIndex() != -1) {
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
                    if(checked && this.getSelectedMeasureIndex() != -1) {
                        // enable the dimension and aggregate combo box
                        this.measureDimensionComboBox.enable();
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();

                        // if saved chart, then set dimension value based on the saved value
                        if(this.measures[this.getSelectedMeasureIndex()].dimension.name){
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
                    if(this.getSelectedMeasureIndex() != -1){
                        this.measures[this.getSelectedMeasureIndex()].dimension = {
                            label: record.data.label,
                            name: record.data.name,
                            queryName: record.data.queryName,
                            schemaName: record.data.schemaName,
                            type: record.data.type
                        };

                        // if the combo value is being changed, remove the selector panel from the previous value
                        if(this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel){
                            this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel.destroy();
                            delete this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel;
                        }

                        this.measureDimensionSelected(this.getSelectedMeasureIndex(), true);
                    }
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
                    if(this.getSelectedMeasureIndex() != -1){
                        this.setDimensionAggregate(cmp.getValue());
                        this.fireEvent('chartDefinitionChanged', true);
                    }
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
        columnTwoItems.push(this.dataFilterWarning);
        columnTwoItems.push(this.dataFilterRemoveButton);

        this.items = [{
            border: false,
            layout: 'column',
            items: [{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems,
                buttonAlign: 'left',
                buttons: [
                    this.addMeasureButton,
                    this.removeMeasureButton
                ]
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
            title: 'Add Measure...',
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
                measuresStoreData: this.measuresStoreData,
                listeners: {
                    scope: this,
                    'measureChanged': function (axisId, data) {
                        // store the selected measure for later use
                        this.changeMeasureSelection = data;

                        Ext.getCmp('measure-selection-button').setDisabled(false);
                    },
                    'measuresStoreLoaded': function (data) {
                        // store the measure store JSON object for later use
                        this.measuresStoreData = data;
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
        // add the measure to this
        this.measures.push({
            id: this.getNextMeasureId(),
            name: newMeasure.name,
            queryName: newMeasure.queryName,
            origLabel: newMeasure.label,
            label: newMeasure.label + " from " + newMeasure.queryName,
            measure: Ext.apply({}, newMeasure), 
            dimension: {}
        });

        // reload the measure listview store and select the new measure (last index)
        if(this.measures.length > 0){
            this.measuresListsView.getStore().loadData(this);
            this.measuresListsView.select(this.measuresListsView.getStore().getCount()-1, false, true);

            this.removeMeasureButton.enable();
        }

        // return the index of the added measure
        return this.measures.length -1;
    },

    removeSelectedMeasure: function(){
        if(this.measuresListsView.getSelectionCount() == 1){
            var index = this.measuresListsView.getSelectedIndexes()[0];

            // remove the dimension selector panel, if necessary
            if(this.measures[index].dimensionSelectorPanel){
                this.measures[index].dimensionSelectorPanel.destroy();
            }

            // remove the measure from this object and reload the measure listview store
            this.measures.splice(index, 1);
            this.measuresListsView.getStore().loadData(this);

            // select the previous measure, if there is one
            if(this.measures.length > 0){
                this.measuresListsView.select(index > 0 ? index-1 : 0);
            }
            else{
                // if there are no other measure to select/remove, disable the remove button
                this.removeMeasureButton.disable();
            }

            // fire the measureRemoved event to update label/title/etc. and redraw
            this.fireEvent('measureRemoved');
        }
    },

    newDimensionStore: function(measure, dimension) {
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
                url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, measure)
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

                    this.toggleDimensionComponents(dimension.name, measure.aggregate);

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
                }
            }
        })
    },

    toggleDimensionComponents: function(dimensionName, measureAggregate)
    {
        // enable/disable the dimension components depending if there is a dimension set
        if(dimensionName){
            this.measureDimensionComboBox.enable();
            this.measureDimensionComboBox.setValue(dimensionName);

            this.dimensionAggregateLabel.enable();
            this.dimensionAggregateComboBox.enable();
            this.dimensionAggregateComboBox.setValue(measureAggregate);

            this.setPerDimensionRadioWithoutEvents();
        }
        else{
            this.measureDimensionComboBox.disable();
            this.measureDimensionComboBox.setValue("");

            this.dimensionAggregateLabel.disable();
            this.dimensionAggregateComboBox.disable();
            this.dimensionAggregateComboBox.setValue("");

            this.setPerSubjectRadioWithoutEvents();
        }

        // set the dimension radio as enabled/disabled
        if(this.measureDimensionComboBox.getStore().getCount() == 0){
            this.seriesPerDimensionRadio.disable();
        }
        else{
            this.seriesPerDimensionRadio.enable();
        }
    },

    measureDimensionSelected: function(index, reloadChartData) {
        var measure = this.measures[index].measure;
        var dimension = this.measures[index].dimension;

        // get the dimension values for the selected dimension/grouping
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, dimension),
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
                    dimension.values = new Array();
                    var selectedRecords = selModel.getSelections();
                    for(var i = 0; i < selectedRecords.length; i++) {
                        dimension.values.push(selectedRecords[i].get('value'));
                    }

                    // sort the selected dimension array
                    dimension.values.sort();

                    this.fireEvent('chartDefinitionChanged', true);
                }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

                this.measures[index].dimensionSelectorPanel = new Ext.Panel({
                    title: dimension.label,
                    autoScroll: true,
                    items: [
                        this.defaultDisplayField,
                        new Ext.grid.GridPanel({
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
                                {header: dimension.label, dataIndex:'value'}
                            ],
                            selModel: sm,
                            header: false,
                            listeners: {
                                scope: this,
                                'viewready': function(grid) {
                                    // if this is not a saved chart with pre-selected values, initially select the first 5 values
                                    var selectDefault = false;
                                    if(!dimension.values){
                                        selectDefault = true;
                                        dimension.values = [];
                                        for(var i = 0; i < (grid.getStore().getCount() < 5 ? grid.getStore().getCount() : 5); i++) {
                                            dimension.values.push(grid.getStore().getAt(i).data.value);
                                        }
                                    }

                                    // check selected dimension values in grid panel (but suspend events during selection)
                                    var dimSelModel = grid.getSelectionModel();
                                    var dimStore = grid.getStore();
                                    dimSelModel.suspendEvents(false);
                                    for(var i = 0; i < dimension.values.length; i++){
                                        var index = dimStore.find('value', dimension.values[i]);
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
                this.measures[index].dimensionSelectorPanel.on('activate', function(pnl){
                   pnl.doLayout();
                }, this);

                Ext.getCmp('series-selector-tabpanel').add(this.measures[index].dimensionSelectorPanel);
                Ext.getCmp('series-selector-tabpanel').activate(this.measures[index].dimensionSelectorPanel.getId());
                Ext.getCmp('series-selector-tabpanel').doLayout();
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    getMeasuresAndDimensions: function(){
        return this.measures;
    },

    getSelectedMeasureIndex: function(){
        var index = -1;
        if(this.measuresListsView.getSelectionCount() == 1){
            var rec = this.measuresListsView.getSelectedRecords()[0];
            for(var i = 0; i < this.measures.length; i++){
                if (this.measures[i].id == rec.get("id"))
                {
                    index = i;
                    break;
                }
            }
        }
        return index;
    },

    // method called on render of the chart panel when a saved chart is being viewed to set the dimension stores for all of the measrues
    initializeDimensionStores: function(){
        for(var i = 0; i < this.measures.length; i++){
            if(!this.measures[i].dimensionStore){
                this.setDimensionStore(i);
            }
        }
    },

    setDimensionStore: function(index){
        if(this.measures[index]){
            var measure = this.measures[index].measure;
            var dimension = this.measures[index].dimension;

            // if we are not setting the store with a selected dimension, remove the dimension object from this
            if(!dimension.name){
                this.setPerSubjectRadioWithoutEvents();
            }
            else{
                this.setPerDimensionRadioWithoutEvents();
            }

            // initialize the dimension store and bind it to the combobox
            this.fireEvent('measureMetadataRequestPending');
            this.measures[index].dimensionStore = this.newDimensionStore(measure, dimension);
            this.measureDimensionComboBox.bindStore(this.measures[index].dimensionStore);

            // if this is a saved chart with a dimension selected, show dimension selector tab
            if(dimension.name){
                this.measureDimensionSelected(index, false);
            }
        }
    },

    setPerSubjectRadioWithoutEvents: function(){
        this.seriesPerSubjectRadio.suspendEvents(false);
        this.seriesPerSubjectRadio.setValue(true);
        this.seriesPerDimensionRadio.setValue(false);
        this.seriesPerSubjectRadio.resumeEvents();
    },

    setPerDimensionRadioWithoutEvents: function(){
        this.seriesPerDimensionRadio.suspendEvents(false);
        this.seriesPerDimensionRadio.setValue(true);
        this.seriesPerSubjectRadio.setValue(false);
        this.seriesPerDimensionRadio.resumeEvents();
    },

    removeDimension: function(){
        // remove any dimension selection/values that were added to the yaxis measure
        this.measures[this.getSelectedMeasureIndex()].dimension = {};

        // disable and clear the dimension combobox
        this.measureDimensionComboBox.disable();
        this.measureDimensionComboBox.setValue("");

        // disable and clear the dimension aggregate combobox
        this.dimensionAggregateLabel.disable();
        this.dimensionAggregateComboBox.disable();
        this.setDimensionAggregate("");

        // if there was a different dimension selection, remove that list view from the series selector
        this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel.destroy();
        delete this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel;
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
    },

    getNumMeasures: function(){
        return this.measures.length;
    },

    getNextMeasureId: function(){
        var id = 0;
        if (this.measures.length > 0)
        {
            id = this.measures[this.measures.length -1].id + 1;
        }
        return id;
    },

    setMeasuresStoreData: function(data){
        this.measuresStoreData = data;
    },

    getDefaultLabel: function(){
        var label = "";
        Ext.each(this.measures, function(m){
            if (label.indexOf(m.origLabel) == -1)
                label += (label.length > 0 ? ", " : "") + m.origLabel;
        });
        return label;
    },

    getDefaultTitle: function(){
        var title = "";
        Ext.each(this.measures, function(m){
            if (title.indexOf(m.queryName) == -1)
                title += (title.length > 0 ? ", " : "") + m.queryName;
        });
        return title;
    }
});
