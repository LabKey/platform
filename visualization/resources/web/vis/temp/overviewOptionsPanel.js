/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();
$h = Ext.util.Format.htmlEncode;

LABKEY.vis.ChartEditorOverviewPanel = Ext.extend(Ext.Panel, {
    constructor : function(config){
        Ext.applyIf(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding-top:25px;text-align: center',
            border: false,
            items: [],
            buttonAlign: 'center',
            buttons: []
        });

        this.addEvents(
            'initialMeasuresStoreLoaded',
            'initialMeasureSelected'
        );

        LABKEY.vis.ChartEditorOverviewPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        this.items = [{
            xtype: 'label',
            text: 'To get started, choose a Measure:'
        }];

        this.buttons = [{
            xtype: 'button',
            text: 'Choose a Measure',
            handler: this.showMeasureSelectionWindow,
            scope: this
        }];

        LABKEY.vis.ChartEditorOverviewPanel.superclass.initComponent.call(this);
    },

    showMeasureSelectionWindow: function() {
        delete this.changeMeasureSelection;
        var win = new Ext.Window({
            cls: 'extContainer',
            title: 'Choose a Measure...',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            closeAction:'close',
            items: [new LABKEY.vis.MeasuresPanel({ // TODO: when the Time chart is upgraded to Ext4, use the Ext4 Measure Picker
                hideDemographicMeasures: true,
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
                    'beforeMeasuresStoreLoad': function (mp, data) {
                        // store the measure store JSON object for later use
                        this.measuresStoreData = data;
                        this.fireEvent('initialMeasuresStoreLoaded', data);
                    },
                    'measuresSelected': function (records, userSelected){
                        this.fireEvent('initialMeasureSelected', records[0].data);
                        win.close();
                    }
                }
            })],
            buttons: [{
                id: 'measure-selection-button',
                text:'Select',
                disabled:true,
                handler: function(){
                    if(this.changeMeasureSelection) {
                        this.fireEvent('initialMeasureSelected', this.changeMeasureSelection);
                        win.close();
                    }
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function(){
                    delete this.changeMeasureSelection;
                    win.close();
                },
                scope: this
            }]
        });
        win.show(this);
    }
});
