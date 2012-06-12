/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.MeasureOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        Ext4.applyIf(config, {
            measures: [],
            labelWidth: 0
        });

        // shared from the parent component
        this.filtersParentPanel = config.filtersParentPanel;

        // add any y-axis measures from the origMeasures object (for saved chart)
        if (typeof config.origMeasures == "object")
        {
            for (var i = 0; i < config.origMeasures.length; i++)
            {
                // backwards compatible, charts saved before addition of right axis will default to left
                Ext4.applyIf(config.origMeasures[i].measure, {yAxis: "left"});

                config.measures.push({
                    id: i,
                    name: config.origMeasures[i].measure.name,
                    queryName: config.origMeasures[i].measure.queryName,
                    origLabel: config.origMeasures[i].measure.label,
                    label: config.origMeasures[i].measure.label + " from " + config.origMeasures[i].measure.queryName,
                    measure: Ext4.apply({}, config.origMeasures[i].measure),
                    dimension: Ext4.apply({}, config.origMeasures[i].dimension),
                    dateCol: config.origMeasures[i].dateOptions ? Ext4.apply({}, config.origMeasures[i].dateOptions.dateCol) : undefined
                });
            }
        }

        Ext4.define('Measure', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'id', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'name', type: 'string'},
                {name: 'queryName', type: 'string'}
            ]
        });

        Ext4.define('DimensionValue', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'id'},
                {name:'name'},
                {name:'label'},
                {name:'longlabel'},
                {name:'description'},
                {name:'isUserDefined'},
                {name:'queryName'},
                {name:'schemaName'},
                {name:'type'}
            ]
        });

        Ext4.define('SimpleValue', {
            extend: 'Ext.data.Model',
            fields: ['value']
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete',
            'filterCleared'
        );
    },

    initComponent : function() {
        // track if the panel has changed in a way that would require a chart/data refresh
        this.hasChanges = false;
        this.requireDataRefresh = false;

        // the measure editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        // add labels indicating the selected measure and which query it is from
        this.measuresListsView = Ext4.create('Ext.grid.Panel', {
            width: 400,
            height: 95,
            hideHeaders: true,
            multiSelect: false,
            singleSelect: true,
            store: Ext4.create('Ext.data.Store', {
                model: 'Measure',
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json',
                        root:'measures',
                        idProperty:'id'
                    }
                },
                data: this,
                listeners: {
                    scope: this,
                    'load': function(store, records) {
                        if (this.measuresListsView && this.measuresListsView.rendered && records.length > 0)
                            this.measuresListsView.getSelectionModel().select(records.length - 1, false, true);
                    }
                }
            }),
            columns: [{
                flex: 1,
                dataIndex: 'label'
            }],
            listeners: {
                scope: this,
                'afterrender': function(listView){
                    // select the last measure in the list
                    if (listView.getStore().getCount() > 0)
                        listView.getSelectionModel().select(listView.getStore().getCount()-1, false, false);
                },
                'selectionchange': function(listView, selections){
                    // set the UI components for the measures series information
                    if (selections.length > 0)
                    {
                        var md = this.measures[this.getSelectedMeasureIndex()];

                        // set the values for the measure dimension elements
                        this.measureDimensionComboBox.bindStore(md.dimensionStore);
                        this.toggleDimensionOptions(md.dimension.name, md.measure.aggregate, md.measure.yAxis);

                        // set the value of the measure date combo box
                        this.measureDateCombo.bindStore(md.dateColStore);
                        this.measureDateCombo.setValue(this.measures[this.getSelectedMeasureIndex()].dateCol.name);

                        // set the value of the yAxisValue comboBox.
                        this.yAxisSide.setValue(this.measures[this.getSelectedMeasureIndex()].measure.yAxis);
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

        // add a button for the user to add a measure to the chart
        this.addMeasureButton = Ext4.create('Ext.button.Button', {
            text: 'Add Measure',
            width: 105,
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

       // add a button for the user to remove the selected measure
        this.removeMeasureButton = Ext4.create('Ext.button.Button', {
            text: 'Remove Measure',
            width: 130,
            disabled: this.measures.length == 0,
            handler: this.removeSelectedMeasure,
            scope: this
        });

        // combobox for choosing axis on left/right
        this.yAxisSide = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['value', 'label'],
                data: [['left', 'Left'], ['right', 'Right']]
            }),
            fieldLabel: 'Draw y-axis on',
            forceSelection: 'true',
            valueField: 'value',
            displayField: 'label',
            value: 'left',
            editable: false,
            listeners: {
                scope: this,
                'select': function(combo){
                    // When the user selects left or right we want to save their choice to the measure.
                    this.measures[this.getSelectedMeasureIndex()].measure.yAxis = combo.getValue();
                    this.hasChanges = true;
                }
            }
        });
        columnTwoItems.push(this.yAxisSide);

        //Measure date combo box.
        this.measureDateCombo = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.Store', {
                fields: [],
                data: []
            }),
            valueField: 'name',
            displayField: 'label',
            forceSelection: true,
            fieldLabel: 'Measure date',
            editable: false,
            listeners: {
                scope: this,
                'select': function(cmp, records) {
                    if (records.length > 0)
                        this.measures[this.getSelectedMeasureIndex()].dateCol = records[0].data;

                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                }
            }
        });
        columnTwoItems.push(this.measureDateCombo);

        // add a label and radio buttons for allowing user to divide data into series (subject and dimension options)
        columnTwoItems.push({
            xtype: 'label',
            html: 'Divide data into Series:<BR/>'
        });
        this.seriesPerSubjectRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'measure_series',
            inputValue: 'per_subject',
            hideLabel: true,
            boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular,
            width: 150,
            checked: true,
            listeners: {
                scope: this,
                'change': function(field, checked) {
                    if (checked && this.getSelectedMeasureIndex() != -1)
                    {
                        this.removeDimension();
                        this.hasChanges = true;
                        this.requireDataRefresh = true;
                    }
                }
            }
        });
        columnTwoItems.push(this.seriesPerSubjectRadio);

        this.seriesPerDimensionRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'measure_series',
            inputValue: 'per_subject_and_dimension',
            boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular + ' and ',
            disabled: true,
            width: 185,
            flex: 1,
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // when this radio option is selected, enable the dimension combo box
                    if (checked && this.getSelectedMeasureIndex() != -1)
                    {
                        // enable the dimension and aggregate combo box
                        this.measureDimensionComboBox.enable();
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();

                        // if saved chart, then set dimension value based on the saved value
                        if (this.measures[this.getSelectedMeasureIndex()].dimension.name)
                        {
                            this.measureDimensionComboBox.setValue(this.measures[this.getSelectedMeasureIndex()].dimension.name);
                        }
                        // otherwise try to select the first item and then give the input focus
                        else{
                            var selIndex = 0;
                            var selRecord = this.measureDimensionComboBox.getStore().getAt(selIndex);
                            if (selRecord)
                            {
                                this.measureDimensionComboBox.setValue(selRecord.get("name"));
                                this.measureDimensionComboBox.fireEvent('select', this.measureDimensionComboBox, [selRecord]);
                            }
                        }

                        // enable and set the dimension aggregate combo box
                        this.dimensionAggregateLabel.enable();
                        this.dimensionAggregateComboBox.enable();
                        this.setDimensionAggregate(LABKEY.Visualization.Aggregate.AVG);
                    }
                }
            }
        });

        this.measureDimensionComboBox = Ext4.create('Ext.form.field.ComboBox', {
            emptyText: '<Select Grouping Field>',
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.Store', {
                fields: [],
                data: []
            }),
            valueField: 'name',
            displayField: 'label',
            disabled: true,
            editable: false,
            flex: 1,
            listeners: {
                scope: this,
                'select': function(cmp, records) {
                    if (this.getSelectedMeasureIndex() != -1 && records.length > 0)
                    {
                        var record = records[0];

                        this.measures[this.getSelectedMeasureIndex()].dimension = {
                            label: record.data.label,
                            name: record.data.name,
                            queryName: record.data.queryName,
                            schemaName: record.data.schemaName,
                            type: record.data.type
                        };

                        // if the combo value is being changed, remove the selector panel from the previous value
                        if (this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel)
                        {
                            this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel.destroy();
                            delete this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel;
                        }

                        this.measureDimensionSelected(this.getSelectedMeasureIndex(), true);
                    }
                }
            }
        });

        columnTwoItems.push({
            xtype: 'fieldcontainer',
            layout: 'hbox',
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
        }

        // initialize the aggregate combobox
        this.dimensionAggregateComboBox = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['name'],
                data: aggregates,
                sorters: {property: 'name', direction: 'ASC'}
            }),
            valueField: 'name',
            displayField: 'name',
            disabled: true,
            editable: false,
            boxWidth: 50,
            flex: 1,
            style: {
                marginLeft: '20px'
            },
            listeners: {
                scope: this,
                'select': function(cmp) {
                    if (this.getSelectedMeasureIndex() != -1)
                    {
                        this.setDimensionAggregate(cmp.getValue());
                        this.hasChanges = true;
                        this.requireDataRefresh = true;
                    }
                }
            }
        });

        // the aggregate combo label has to be a separate component so that it can also be disabled/enabled
        this.dimensionAggregateLabel = Ext4.create('Ext.form.Label', {
            flex: 1,
            width: 75,
            text: 'Display Duplicate Values as: ',
            style: {
                marginLeft: '20px'
            },
            disabled: true
        });

        columnTwoItems.push({
            xtype: 'fieldcontainer',
            layout: 'hbox',
            hideLabel: true,
            items: [
                this.dimensionAggregateLabel,
                this.dimensionAggregateComboBox
            ]
        });

        this.dataFilterUrl = this.filterUrl;
        this.dataFilterQuery = this.filterQuery;
        this.dataFilterWarning = Ext4.create('Ext.form.Label', {
            // No text by default
        });
        this.dataFilterRemoveButton = Ext4.create('Ext.button.Button', {
            hidden: true,
            width: 115,
            text: 'Remove Filter',
            listeners: {
                scope: this,
                'click' : function()
                {
                    this.removeFilterWarning();
                }
            }
        });
        columnOneItems.push(this.dataFilterWarning);

        this.items = [{
            xtype: 'panel',
            border: false,
            layout: 'column',
            items: [{
                columnWidth: .5,
                xtype: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems,
                buttonAlign: 'left',
                buttons: [
                    this.addMeasureButton,
                    this.removeMeasureButton,
                    this.dataFilterRemoveButton
                ]
            },{
                columnWidth: .5,
                xtype: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnTwoItems
            }]
        }];

        this.buttons = [
            {
                text: 'Apply',
                handler: function(){
                    this.fireEvent('closeOptionsWindow');
                    this.checkForChangesAndFireEvents();
                },
                scope: this
            }
        ];

        this.callParent();
    },

    setFilterWarningText: function(text)
    {
        var tipText;
        var tip;
        text = LABKEY.Utils.encodeHtml(text);
        if(text.length > 25) {
            tipText = text;
            Ext4.tip.QuickTipManager.register({
                target: this.dataFilterWarning.getId(),
                text: tipText
            });
            text = text.substr(0, 24) + "...";
        }
        var warning = "<b>This chart data is filtered:</b> " + text;
        this.dataFilterWarning.setText(warning, false);
        this.dataFilterRemoveButton.show();
    },

    removeFilterWarning: function()
    {
        this.dataFilterUrl = undefined;
        this.dataFilterQuery = undefined;
        this.dataFilterWarning.setText('');
        this.dataFilterRemoveButton.hide();

        this.hasChanges = true;
        this.requireDataRefresh = true;
    },

    setYAxisSide: function(measureIndex){
        this.yAxisSide.setValue(this.measures[measureIndex].measure.yAxis);
    },

    setMeasureDateStore: function(measure, measureIndex, toFireEvent){
        if (toFireEvent)
            this.fireEvent('measureMetadataRequestPending');        

        // add a store for measureDateCombo to a measure.
        this.measures[measureIndex].dateColStore = this.newMeasureDateStore(measure, measureIndex, toFireEvent);
        this.measureDateCombo.bindStore(this.measures[measureIndex].dateColStore);
    },

    newMeasureDateStore: function(measure, measureIndex, toFireEvent) {
        return Ext4.create('Ext.data.Store', {
            model: 'DimensionValue',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL('visualization', 'getMeasures', LABKEY.ActionURL.getContainer(), {
                    filters: [LABKEY.Visualization.Filter.create({schemaName: measure.schemaName, queryName: measure.queryName})],
                    dateMeasures: true
                }),
                reader: {
                    type: 'json',
                    root: 'measures',
                    idProperty:'id'
                }
            },
            autoLoad: true,
            sorters: {property: 'label', direction: 'ASC'},
            listeners: {
                scope: this,
                'load': function(store, records, options){
                    // since the ParticipantVisit/VisitDate will almost always be the date the users wants for multiple measures,
                    // always make sure that it is added to the store
                    var visitDateStr = this.viewInfo.subjectNounSingular + "Visit/VisitDate";
                    if (store.find('name', visitDateStr) == -1)
                    {
                        var newDateRecordData = {
                            schemaName: measure.schemaName,
                            queryName: measure.queryName,
                            name: visitDateStr,
                            label: "Visit Date",
                            type: "TIMESTAMP"
                        };
                        store.add(newDateRecordData);
                    }

                    // if this is a saved report, we will have a measure date to select
                    var index = 0;
                    if (this.measures[measureIndex].dateCol)
                    {
                        index = store.find('name', this.measures[measureIndex].dateCol.name);
                    }
                    // otherwise, try a few hard-coded options
                    else if (store.find('name', visitDateStr) > -1)
                    {
                        index = store.find('name', visitDateStr);
                    }

                    if (store.getAt(index))
                    {
                        this.measureDateCombo.setValue(store.getAt(index).get('name'));
                        this.measures[measureIndex].dateCol = Ext4.apply({}, store.getAt(index).data);
                    }

                    // this is one of the requests being tracked, see if the rest are done
                    if (toFireEvent)
                        this.fireEvent('measureMetadataRequestComplete');

                    // if this is the last loader for the given measure, reload teh measure list store data
                    this.loaderCount--;
                    if (this.loaderCount == 0)
                    {
                        // reload the measure listview store and select the new measure (last index)
                        this.measuresListsView.getStore().loadRawData(this);
                        this.removeMeasureButton.enable();
                    }
                }
            }
        });
    },

    showMeasureSelectionWindow: function() {
        delete this.changeMeasureSelection;
        this.measureSelectionBtnId = Ext4.id();

        var win = Ext4.create('LABKEY.ext4.MeasuresDialog', {
            allColumns: false,
            multiSelect : false,
            closeAction:'hide',
            listeners: {
                scope: this,
                'beforeMeasuresStoreLoad': function (mp, data) {
                    // store the measure store JSON object for later use
                    this.measuresStoreData = data;
                },
                'measuresSelected': function (records, userSelected){
                    this.addMeasure(records[0].data, false);
                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                    win.hide();
                }
            }
        });
        win.show(this);
    },

    addMeasure: function(newMeasure, initialMeasure){
        // add the measure to this
        newMeasure.yAxis = newMeasure.yAxis || "left";
        this.measures.push({
            id: this.getNextMeasureId(),
            name: newMeasure.name,
            queryName: newMeasure.queryName,
            origLabel: newMeasure.label,
            label: newMeasure.label + " from " + newMeasure.queryName,
            measure: Ext4.apply({}, newMeasure),
            dimension: {}
        });

        var measureIndex = this.measures.length - 1;
        this.loaderCount = 2; // keep track of the properties loading for the measure (dimension store and date store)
        this.setDimensionStore(measureIndex, initialMeasure);
        this.setMeasureDateStore(newMeasure, measureIndex, initialMeasure);
        this.setYAxisSide(measureIndex);
    },

    removeSelectedMeasure: function(){
        var index = this.getSelectedMeasureIndex();
        if (index != -1)
        {
            // remove the dimension selector panel, if necessary
            if (this.measures[index].dimensionSelectorPanel){
                this.measures[index].dimensionSelectorPanel.destroy();
            }

            // remove the measure from this object and reload the measure listview store
            this.measures.splice(index, 1);
            this.measuresListsView.getStore().loadRawData(this);

            // select the previous measure, if there is one
            if (this.measures.length > 0){
                this.measuresListsView.getSelectionModel().select(index > 0 ? index-1 : 0);
            }
            else{
                // if there are no other measure to select/remove, disable the remove button
                this.removeMeasureButton.disable();
            }

            this.hasChanges = true;
            this.requireDataRefresh = true;
        }
    },

    newDimensionStore: function(measure, dimension, toFireEvent) {
        return Ext4.create('Ext.data.Store', {
            model: 'DimensionValue',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, measure),
                reader: {
                    type: 'json',
                    root: 'dimensions',
                    idProperty:'id'
                }
            },
            autoLoad: true,
            sorters: {property: 'label', direction: 'ASC'},
            listeners: {
                scope: this,
                'load': function(store, records, options) {
                    // loop through the records to remove Subject as a dimension option
                    for(var i = 0; i < records.length; i++) {
                        if (records[i].data.name == this.viewInfo.subjectColumn)
                        {
                            store.remove(records[i]);
                            break;
                        }
                    }

                    this.toggleDimensionOptions(dimension.name, measure.aggregate);

                    // this is one of the requests being tracked, see if the rest are done
                    if (toFireEvent)
                        this.fireEvent('measureMetadataRequestComplete');

                    // if this is the last loader for the given measure, reload teh measure list store data
                    this.loaderCount--;
                    if (this.loaderCount == 0)
                    {
                        // reload the measure listview store and select the new measure (last index)
                        this.measuresListsView.getStore().loadRawData(this);
                        this.removeMeasureButton.enable();
                    }
                }
            }
        })
    },

    toggleDimensionOptions: function(dimensionName, measureAggregate)
    {
        // enable/disable the dimension components depending if there is a dimension set
        if (dimensionName)
        {
            this.measureDimensionComboBox.enable();
            this.measureDimensionComboBox.setValue(dimensionName);

            this.dimensionAggregateLabel.enable();
            this.dimensionAggregateComboBox.enable();
            this.dimensionAggregateComboBox.setValue(measureAggregate);

            this.setPerDimensionRadioWithoutEvents();
        }
        else
        {
            this.measureDimensionComboBox.disable();
            this.measureDimensionComboBox.setValue("");

            this.dimensionAggregateLabel.disable();
            this.dimensionAggregateComboBox.disable();
            this.dimensionAggregateComboBox.setValue("");

            this.setPerSubjectRadioWithoutEvents();
        }

        // set the dimension radio as enabled/disabled
        if (this.measureDimensionComboBox.getStore().getTotalCount() == 0)
            this.seriesPerDimensionRadio.disable();
        else
            this.seriesPerDimensionRadio.enable();
    },

    measureDimensionSelected: function(index, reloadChartData) {
        var measure = this.measures[index].measure;
        var dimension = this.measures[index].dimension;

        // get the dimension values for the selected dimension/grouping
        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, dimension),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                // decode the JSON responseText
                var dimensionValues = Ext4.decode(response.responseText);

                // sort the dimension values in ascending order
                function compareValues(a, b) {
                    if (a.value < b.value) {return -1}
                    if (a.value > b.value) {return 1}
                    return 0;
                }
                dimensionValues.values.sort(compareValues);

                this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
                    hideLabel: true,
                    hidden: true,
                    value: 'Selecting 5 values by default',
                    style: 'font-size:75%;color:red;'
                });

                // put the dimension values into a list view for the user to enable/disable series
                var sm = Ext4.create('Ext.selection.CheckboxModel', {});
                sm.on('selectionchange', function(selModel){
                    // add the selected dimension values to the chartInfo
                    dimension.values = new Array();
                    var selectedRecords = selModel.getSelection();
                    for(var i = 0; i < selectedRecords.length; i++) {
                        dimension.values.push(selectedRecords[i].get('value'));
                    }

                    // sort the selected dimension array
                    dimension.values.sort();

                    this.fireEvent('chartDefinitionChanged', true);
                }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

                var ttRenderer = function(value, p, record) {
                    var msg = Ext4.util.Format.htmlEncode(value);
                    p.tdAttr = 'data-qtip="' + msg + '"';
                    return msg;
                };

                this.measures[index].dimensionSelectorPanel = Ext4.create('Ext.panel.Panel', {
                    title: dimension.label,
                    border: false,
                    cls: 'report-filter-panel',
                    autoScroll: true,
                    items: [
                        this.defaultDisplayField,
                        Ext4.create('Ext.grid.GridPanel', {
                            autoHeight: true,
                            enableHdMenu: false,
                            store: Ext4.create('Ext.data.Store', {
                                model: 'SimpleValue',
                                proxy: {
                                    type: 'memory',
                                    reader: {
                                        type: 'json',
                                        root:'values',
                                        idProperty:'value'
                                    }
                                },
                                data: dimensionValues
                            }),
                            viewConfig: {forceFit: true},
                            sortableColumns: false,
                            border: false,
                            frame: false,
                            columns: [{    
                                text: 'All',
                                dataIndex:'value',
                                menuDisabled: true,
                                resizable: false,
                                renderer: ttRenderer,
                                flex: 1
                            }],
                            selModel: sm,
                            header: false,
                            listeners: {
                                scope: this,
                                'viewready': function(grid) {
                                    // if this is not a saved chart with pre-selected values, initially select the first 5 values
                                    var selectDefault = false;
                                    if (!dimension.values)
                                    {
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
                                        dimSelModel.select(dimStore.find('value', dimension.values[i]), true);
                                    }
                                    dimSelModel.resumeEvents();

                                    // show the selecting default text if necessary
                                    if (grid.getStore().getCount() > 5 && selectDefault)
                                    {
                                        // show the display for 5 seconds before hiding it again
                                        var refThis = this;
                                        refThis.defaultDisplayField.show();
                                        setTimeout(function(){
                                            refThis.defaultDisplayField.hide();
                                        },5000);
                                    }

                                    if (reloadChartData){
                                        this.hasChanges = true;
                                        this.requireDataRefresh = true;
                                    }
                                }
                            }
                         })
                    ]
                });

                this.filtersParentPanel.add(this.measures[index].dimensionSelectorPanel);
                this.measures[index].dimensionSelectorPanel.expand();
            },
            failure: function(info, response, options) {
                LABKEY.Utils.displayAjaxErrorResponse(response, options);
            },
            scope: this
        });
    },

    getSelectedMeasureIndex: function(){
        var index = -1;
        if (this.measuresListsView.getSelectionModel().getCount() == 1)
        {
            var rec = this.measuresListsView.getSelectionModel().getLastSelected();
            for (var i = 0; i < this.measures.length; i++)
            {
                if (this.measures[i].id == rec.get("id"))
                {
                    index = i;
                    break;
                }
            }
        }
        return index;
    },

    // method called on render of this panel when a saved chart is being viewed to set the dimension stores for all of the measrues
    initializeDimensionStores: function(){
        for(var i = 0; i < this.measures.length; i++){
            if (!this.measures[i].dimensionStore)
                this.setDimensionStore(i, false);

            if(!this.measures[i].dateColStore)
                this.setMeasureDateStore(this.measures[i].measure, i, false);
        }
    },

    setDimensionStore: function(index, toFireEvent){
        if (this.measures[index])
        {
            var measure = this.measures[index].measure;
            var dimension = this.measures[index].dimension;

            // if we are not setting the store with a selected dimension, remove the dimension object from this
            if (!dimension.name)
                this.setPerSubjectRadioWithoutEvents();
            else
                this.setPerDimensionRadioWithoutEvents();

            // initialize the dimension store and bind it to the combobox
            if (toFireEvent)
                this.fireEvent('measureMetadataRequestPending');
            this.measures[index].dimensionStore = this.newDimensionStore(measure, dimension, toFireEvent);
            this.measureDimensionComboBox.bindStore(this.measures[index].dimensionStore);

            // if this is a saved chart with a dimension selected, show dimension selector tab
            if (dimension.name)
                this.measureDimensionSelected(index, false);
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
        if (this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel)
        {
            this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel.destroy();
            delete this.measures[this.getSelectedMeasureIndex()].dimensionSelectorPanel;
        }
    },

    setDimensionAggregate: function(newAggregate){
        this.dimensionAggregateComboBox.setValue(newAggregate);
        if (newAggregate != ""){
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

    getDefaultLabel: function(side){
        var label = "";
        Ext4.each(this.measures, function(m){
            if (m.measure.yAxis == side){
                if (label.indexOf(m.origLabel) == -1)
                label += (label.length > 0 ? ", " : "") + m.origLabel;
            }
        });
        return label;
    },

    getDefaultTitle: function(){
        var title = "";
        Ext4.each(this.measures, function(m){
            if (title.indexOf(m.queryName) == -1)
                title += (title.length > 0 ? ", " : "") + m.queryName;
        });
        return title;
    },

    getPanelOptionValues : function() {
        return {
            dataFilterUrl: this.dataFilterUrl,
            dataFilterQuery: this.dataFilterQuery,
            measuresAndDimensions: this.measures
        };
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', this.requireDataRefresh);

        // reset the changes flags
        this.requireDataRefresh = false;
        this.hasChanges = false;
    }
});
