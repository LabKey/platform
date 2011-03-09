/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorOverviewPanel = Ext.extend(Ext.Panel, {
    constructor : function(config){
        Ext.applyIf(config, {
            title: 'Overview',
            layout: 'card',
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            items: []
        });

        this.addEvents(
            'initialMeasureSelected',
            'saveChart'
        );

        LABKEY.vis.ChartEditorOverviewPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        var items = [];

        var chartEditorDescription = 'A time chart allows you to view data for a selected measure over time.<br/><br/>'
            + 'You can use the tabs above to make changes to the chart.';

        // first item in card layout: intial choose measure panel when not showing saved report
        items.push(new Ext.Panel({
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
        }));

        // second item in the card layout: panel for users without insert perms (shows description and report name/description if available)
        var displayItems = [];
        this.nameDisplayField = new Ext.form.DisplayField({
            fieldLabel: 'Report Name',
            value: (typeof this.reportInfo == 'object' ? Ext.util.Format.htmlEncode(this.reportInfo.name) : null),
            border: false
        });

        this.descDisplayField = new Ext.form.DisplayField({
            fieldLabel: 'Report Description',
            value: (typeof this.reportInfo == 'object' ? Ext.util.Format.htmlEncode(this.reportInfo.description) : null),
            border: false
        });

        // only add the display fields to the panel if they have something to show
        if(typeof this.reportInfo == 'object'){
            displayItems.push(this.nameDisplayField);
            displayItems.push(this.descDisplayField);
        }

        items.push({
            layout: 'column',
            title: '',
            autoHeight: true,
            autoWidth: true,
            border: false,
            items: [{
                columnWidth: .6,
                border: false,
                bodyStyle: 'padding: 5px 20px 5px 5px',
                html: chartEditorDescription
            },{
                columnWidth: .4,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                labelWidth: 125,
                items: displayItems
            }]
        });

        // third item in the card layout: panel for users with insert permissions (shows desription and report name/description input fields)
        this.saveBtn = new Ext.Button({
            text: "Save",
            hidden: (typeof this.reportInfo == 'object' && !LABKEY.Security.currentUser.canUpdate ? true : false), // hide if user can't update a saved report
            handler: function() {
                var formVals = this.saveChartFormPanel.getForm().getValues();

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
                var shared = typeof formVals.reportShared == "string" ? 'true' == formVals.reportShared : new Boolean(formVals.reportShared);
                this.fireEvent('saveChart', 'Save', (typeof this.reportInfo == "object" ? true : false), formVals.reportName, formVals.reportDescription, shared);
            },
            scope: this,
            formBind: true
        });

        // save as button, initially hidden if not rendering a saved chart
        this.saveAsBtn = new Ext.Button({
            text: "Save As",
            hidden: (typeof this.reportInfo == 'object' ? false : true),
            handler: function() {
                var formVals = this.saveChartFormPanel.getForm().getValues();

                // the save as button does not allow for replace initially
                var shared = typeof formVals.reportShared == "string" ? 'true' == formVals.reportShared : new Boolean(formVals.reportShared);
                this.fireEvent('saveChart', 'Save As', false, formVals.reportName, formVals.reportDescription, shared);
            },
            scope: this,
            formBind: true
        });

        this.saveChartFormPanel = new Ext.FormPanel({
            title: '',
            autoHeight: true,
            autoWidth: true,
            buttonAlign: 'right',
            border: false,
            labelWidth: 125,
            items: [
                new Ext.form.TextField({
                    name: 'reportName',
                    fieldLabel: 'Report Name',
                    readOnly: (typeof this.reportInfo == "object" ? true : false), // disabled for saved report
                    value: (typeof this.reportInfo == "object" ? this.reportInfo.name : null),
                    allowBlank: false,
                    preventMark: true,
                    anchor: '100%'
                }),
                new Ext.form.TextArea({
                    name: 'reportDescription',
                    fieldLabel: 'Report Description',
                    value: (typeof this.reportInfo == "object" ? this.reportInfo.description : null),
                    allowBlank: true,
                    anchor: '100%',
                    height: 40
                }),
                new Ext.form.RadioGroup({
                    name: 'reportShared',
                    fieldLabel: 'Viewable by',
                    anchor: '100%',
                    items : [
                            { name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', checked: (typeof this.reportInfo == "object" ? this.reportInfo.shared : true) },
                            { name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', checked: !(typeof this.reportInfo == "object" ? this.reportInfo.shared : true) }
                        ]
                })
            ],
            buttons: [
                this.saveBtn,
                this.saveAsBtn
            ]
        });

        items.push({
            layout: 'column',
            title: '',
            autoHeight: true,
            autoWidth: true,
            border: false,
            items: [{
                columnWidth: 0.6,
                border: false,
                bodyStyle: 'padding: 5px 20px 5px 5px',
                html: chartEditorDescription
            },{
                columnWidth: 0.4,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: [this.saveChartFormPanel]
            }]
        });

        this.items = items;

        // determine the initial active item to set for this chart layout
        var active = 0;
        // if showing saved report and user can insert, show input fields; else, show displayfields
        if(typeof this.reportInfo == 'object'){
            active = (LABKEY.Security.currentUser.canInsert ? 2 : 1);
        }
        this.activeItem = active;

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
            items: [new LABKEY.vis.MeasuresPanel({
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
            })],
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

        // update the name/description field values accordingly
        if(typeof this.reportInfo == 'object'){
            this.saveChartFormPanel.getComponent(0).setValue(this.reportInfo.name);
            this.saveChartFormPanel.getComponent(0).setReadOnly(true); // disabled for saved report
            this.saveChartFormPanel.getComponent(1).setValue(this.reportInfo.description);
            this.saveChartFormPanel.getComponent(2).setValue(this.reportInfo.shared);

            // if the user can update, show save button (which is now for replacing the saved report)
            (LABKEY.Security.currentUser.canUpdate ? this.saveBtn.show() : this.saveBtn.hide());
            // if the user can insert, show them the save as button
            (LABKEY.Security.currentUser.canInsert ? this.saveAsBtn.show() : this.saveAsBtn.hide());
        }

        // change the active card layout item
        if(LABKEY.Security.currentUser.canInsert){
            this.getLayout().setActiveItem(2);
        }
        else{
            this.getLayout().setActiveItem(1);
        }

        this.doLayout();
    }
});
