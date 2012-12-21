/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.InitialMeasurePanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding-top:25px;text-align: center',
            border: false,
            items: [],
            buttonAlign: 'center',
            buttons: []
        });

        this.callParent([config]);

        this.addEvents(
            'initialMeasuresStoreLoaded',
            'initialMeasureSelected'
        );
    },

    initComponent : function() {
        this.items = [{
            xtype: 'label',
            text: 'To get started, choose a Measure:'
        }];

        this.chooseMeasureBtn = Ext4.create('Ext.Button', {
            text: 'Choose a Measure',
            handler: this.showMeasureSelectionWindow,
            scope: this
        });

        this.buttons = [this.chooseMeasureBtn];

        this.callParent();
    },

    showMeasureSelectionWindow: function() {
        this.chooseMeasureBtn.disable();
        var win = Ext4.create('LABKEY.ext4.MeasuresDialog', {
            allColumns: false,
            multiSelect : false,
            closeAction:'hide',
            helpText: this.helpText,
            listeners: {
                scope: this,
                'beforeMeasuresStoreLoad': function (mp, data) {
                    // store the measure store JSON object for later use
                    this.measuresStoreData = data;
                    this.fireEvent('initialMeasuresStoreLoaded', data);
                },
                'measuresSelected': function (records, userSelected){
                    this.fireEvent('initialMeasureSelected', records[0].data);
                    win.hide();
                },
                'hide': function() {
                    if (this.chooseMeasureBtn.isVisible())
                        this.chooseMeasureBtn.enable();
                }
            }
        });
        win.show(this);
    }
});
