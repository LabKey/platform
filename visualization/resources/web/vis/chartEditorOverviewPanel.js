/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorOverviewPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.applyIf(config, {
            title: 'Overview',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding:5px',
            border: false,
            items: []
        });

        this.addEvents(
            'initialMeasureSelected',
            'saveChart'
        );

        LABKEY.vis.ChartEditorOverviewPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        this.items = [
            {
                xtype: 'label',
                text: 'To get started, choose a Measure:'
            },
            {
                xtype: 'button',
                text: 'Choose a Measure',
                handler: this.showMeasureSelectionWindow,
                scope: this
            }
        ];

        LABKEY.vis.ChartEditorOverviewPanel.superclass.initComponent.call(this);
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
                        this.fireEvent('initialMeasureSelected', this.changeMeasureSelection);
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

    updateOverview: function(reportInfo){
        this.removeAll();

        this.items.add(
            new Ext.form.Label({
                fieldLabel: 'Name',
                text: (typeof reportInfo == "object" ? reportInfo.name : '<Saved Report Name>')
            })
        );

        this.items.add(
            new Ext.form.Label({
                fieldLabel: 'Description',
                text: (typeof reportInfo == "object" ? reportInfo.description : '<Saved Report Description>')
            })
        );

        this.items.add(
            new Ext.Button({
                text: "Save",
                handler: function() {
                    // the save button will not allow for replace if this is a new chart,
                    // but will force replace if this is a change to a saved chart
                    this.fireEvent('saveChart', 'Save', (typeof reportInfo == "object" ? true : false));
                },
                scope: this
            })
        );

        this.items.add(
            new Ext.Button({
                text: "Save As",
                hidden: (typeof reportInfo == "object" ? false : true), // save as only needed for saved chart
                handler: function() {
                    this.fireEvent('saveChart', 'Save As', false);
                },
                scope: this
            })
        );

        this.doLayout();
    }
});
