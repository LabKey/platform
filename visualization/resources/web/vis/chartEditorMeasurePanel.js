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
            'subjectDimensionIdentified',
            'measureDimensionSelected',
            'seriesPerSubjectChecked'
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
            handler: function() {
                delete this.changeMeasureSelection;
                var win = new Ext.Window({
                    layout:'fit',
                    width:800,
                    height:550,
                    closeAction:'hide',
                    items: new LABKEY.vis.MeasuresPanel({
                        axis: [{
                            multiSelect: false,
                            name: "y-axis",
                            label: "Select data type for y-axis"
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
                                win.hide();
                                // call the changeMeasure function for this panel
                                this.changeMeasure();
                                // fire the measureSelected event so other panels can update as well
                                this.fireEvent('measureSelected', this.changeMeasureSelection);
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
            scope: this
        });

        columnTwoItems.push({
            id: 'series-per-subject-radio',
            xtype: 'radio',
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
                        this.fireEvent('seriesPerSubjectChecked');
                    }
                }
            }
        });

        var measureDimensionComboBox = new Ext.form.ComboBox({
            id: 'measure-dimension-combo',
            emptyText: '<Select Grouping Field>',
            //editable: false,
            triggerAction: 'all',
            mode: 'local',
            store: this.newDimensionStore(),
            valueField: 'name',
            displayField: 'label',
            disabled: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.fireEvent('measureDimensionSelected', record.data);
                }
            }
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            //id: 'measure-series-per-subject-dimension',
            defaults: {flex: 1},
            items: [
                {
                    xtype: 'radio',
                    name: 'measure_series',
                    inputValue: 'per_subject_and_dimension',
                    boxLabel: 'One Per ' + this.viewInfo.subjectNounSingular + ' and ',
                    width: 185,
                    height: 1,
                    listeners: {
                        scope: this,
                        'check': function(field, checked){
                            // when this radio option is selected, enable the dimension combo box
                            if(checked) {
                                // by default select the first item and then give the input focus
                                measureDimensionComboBox.enable();
                                measureDimensionComboBox.setValue(measureDimensionComboBox.getStore().getAt(0).get("name"));
                                measureDimensionComboBox.selectText();
                                measureDimensionComboBox.fireEvent('select', measureDimensionComboBox, measureDimensionComboBox.getStore().getAt(0), 0);
                            }
                            else {
                                measureDimensionComboBox.disable();
                                measureDimensionComboBox.reset();
                            }

                            // todo: fix this...
                            // show the 3rd option for the layout radio group on the chart(s) tab
                            //Ext.get('chart-layout-per-dimension').show();
                        }
                    }
                },
                measureDimensionComboBox
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

        LABKEY.vis.ChartEditorMeasurePanel.superclass.initComponent.call(this);
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
                    // loop through the records to get the subject dimension to be passed back to the editor
                    for(var i = 0; i < records.length; i++) {
                        if(records[i].data.name == this.viewInfo.subjectColumn) {
                            // fire the 'subjectDimensionIdentified' event
                            this.fireEvent('subjectDimensionIdentified', records[i].data);

                            // remove the record from this dimension store
                            store.remove(records[i]);
                        }
                    }

                    // if there are not any non-subject dimensions for this measure, then disable the option
                    // todo: disable option and combobox
                }
            }
        })
    },

    changeMeasure: function() {
        this.measure = this.changeMeasureSelection;

        // update the measure label
        Ext.getCmp('measure-label').setText(this.changeMeasureSelection.label + " from " + this.changeMeasureSelection.queryName);

        // set the series radio selection to one per participant

        Ext.getCmp('series-per-subject-radio').setValue(true);

        // update the measure dimension combo box
        var newStore = this.newDimensionStore();
        Ext.getCmp('measure-dimension-combo').bindStore(newStore);
    }

// todo: move this to ...
//        // if there is not dimension options for the given measure, disable the per dimension items
//        //if(measure.dimensions.length == 0) {
//        //    Ext.getCmp('measure-series-per-subject-dimension').disable();
//        //}
//        //else {
//        //    Ext.getCmp('measure-series-per-subject-dimension').enable();
//        //}
});
