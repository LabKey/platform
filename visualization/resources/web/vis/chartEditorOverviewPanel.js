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
            buttonAlign: 'left',
            monitorValid: true,
            items: [],
            buttons: []
        });

        this.addEvents(
            'initialMeasureSelected',
            'saveChart'
        );

        LABKEY.vis.ChartEditorOverviewPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        this.items = [new Ext.Panel({
            title: '',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding-top:25px;text-align: center',
            buttonAlign: 'center',
            border: false,
            items: [{
                xtype: 'label',
                text: 'To get started, choose a Measure:'
            }],
            buttons: [{
                xtype: 'button',
                text: 'Choose a Measure',
                handler: this.showMeasureSelectionWindow,
                scope: this
            }]
        })];

        this.on('activate', function(){
           this.doLayout();
        }, this);

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
            closeAction:'hide',
            items: new LABKEY.vis.MeasuresPanel({
                axis: [{
                    multiSelect: false,
                    name: "y-axis",
                    label: "Choose a data measure:"
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
        this.reportInfo = reportInfo;

        // remove all of the items and buttons from the form
        this.removeAll();

        this.items.add(
            new Ext.form.TextField({
                name: 'reportName',
                fieldLabel: 'Name',
                readOnly: (typeof this.reportInfo == "object" ? true : false), // disabled for saved report
                value: (typeof this.reportInfo == "object" ? this.reportInfo.name : null),
                allowBlank: false,
                preventMark: true,
                anchor: '60%'
            })
        );

        this.items.add(
            new Ext.form.TextArea({
                name: 'reportDescription',
                fieldLabel: 'Description',
                value: (typeof this.reportInfo == "object" ? this.reportInfo.description : null),
                allowBlank: true,
                anchor: '60%'
            })
        );

        // check to see if the save buttons need to be added
        if(this.buttons.length == 0){
            this.addSaveButtons();
        }
        // if they are already added, we may need to show the Save As button if we now have a saved chart
        else if(typeof this.reportInfo == 'object'){
            this.saveAsBtn.show();
        }

        this.doLayout();
    },

    addSaveButtons: function(){
        this.saveBtn = new Ext.Button({
            text: "Save",
            handler: function() {
                var formVals = this.getForm().getValues();

                // report name is required for saving
                if(!formVals.reportName){
                   Ext.Msg.show({
                        title: "Error",
                        msg: "Name must be specified when saving a report.",
                        buttons: Ext.MessageBox.OK,
                        icon: Ext.MessageBox.ERROR
                   });
                   return;
                }

                // the save button will not allow for replace if this is a new chart,
                // but will force replace if this is a change to a saved chart
                this.fireEvent('saveChart', 'Save', (typeof this.reportInfo == "object" ? true : false), formVals.reportName, formVals.reportDescription);
            },
            scope: this,
            formBind: true
        });
        this.addButton(this.saveBtn);

        // add save as button, initially hidden if not rendering a saved chart
        this.saveAsBtn = new Ext.Button({
            text: "Save As",
            hidden: (typeof this.reportInfo == 'object' ? false : true),
            handler: function() {
                var formVals = this.getForm().getValues();

                // the save as button does not allow for replace initially
                this.fireEvent('saveChart', 'Save As', false, formVals.reportName, formVals.reportDescription);
            },
            scope: this,
            formBind: true
        });
        this.addButton(this.saveAsBtn);

        this.doLayout();
    }
});
