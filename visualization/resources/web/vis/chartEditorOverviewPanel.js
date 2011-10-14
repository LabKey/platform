/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();
$h = Ext.util.Format.htmlEncode;

LABKEY.vis.ChartEditorOverviewPanel = Ext.extend(Ext.Panel, {
    constructor : function(config){
        Ext.applyIf(config, {
            title: 'Overview',
            layout: 'card',
            autoWidth: true,
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            items: []
        });

        this.addEvents(
            'initialMeasuresStoreLoaded',
            'initialMeasureSelected',
            'saveChart',
            'saveThumbnailChecked'
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

        // Note that the following check means that Readers are allowed to save new charts (readers own new charts they're creating)- this is by design.
        var canSaveChanges = this.canSaveChanges();
        var canSaveSharedCharts = this.canSaveSharedCharts();
        var savedReport = this.isSavedReport();
        var currentlyShared = (savedReport && this.reportInfo.shared) || (!savedReport && canSaveSharedCharts);
        var createdBy = savedReport ? this.reportInfo.createdBy : LABKEY.Security.currentUser.id;

        // Second item in the card layout: panel for users with insert permissions (shows desription and report name/description input fields)
        // a report by that name already exists within the container, if the user can update, ask if they would like to replace
        this.saveBtn = new Ext.Button({
            text: "Save",
            hidden: !this.canSaveChanges(), 
            disabled: !canSaveChanges,
            handler: function() {
                var formVals = this.saveChartFormPanel.getForm().getValues();

                // report name is required for saving
                if(!formVals.reportName){
                   Ext.Msg.show({
                        title: "Error",
                        msg: "Report name must be specified when saving a chart.",
                        buttons: Ext.MessageBox.OK,
                        icon: Ext.MessageBox.ERROR
                   });
                   return;
                }

                // the save button will not allow for replace if this is a new chart,
                // but will force replace if this is a change to a saved chart
                var shared = typeof formVals.reportShared == "string" ? 'true' == formVals.reportShared : (new Boolean(formVals.reportShared)).valueOf();
                this.fireEvent('saveChart', 'Save', (typeof this.reportInfo == "object"), formVals.reportName, formVals.reportDescription, shared, this.saveThumbnail, canSaveSharedCharts, createdBy);
            },
            scope: this,
            formBind: true
        });

        // save as button, initially hidden if not rendering a saved chart
        this.saveAsBtn = new Ext.Button({
            text: "Save As",
            hidden: !savedReport || LABKEY.Security.currentUser.isGuest,
            handler: function() {
                var formVals = this.saveChartFormPanel.getForm().getValues();

                // the save as button does not allow for replace initially
                var shared = typeof formVals.reportShared == "string" ? 'true' == formVals.reportShared : (new Boolean(formVals.reportShared)).valueOf();
                this.fireEvent('saveChart', 'Save As', false, formVals.reportName, formVals.reportDescription, shared, this.saveThumbnail, canSaveSharedCharts, createdBy);
            },
            scope: this
        });

        this.saveChartFormPanel = new Ext.FormPanel({
            title: '',
            autoHeight: true,
            autoWidth: true,
            monitorResize: true,
            buttonAlign: 'right',
            border: false,
            labelWidth: 125,
            monitorValid: true,
            items: [
                new Ext.form.TextField({
                    id: 'reportName',
                    name: 'reportName',
                    fieldLabel: 'Report Name',
                    hidden: savedReport || !canSaveChanges,
                    value: (savedReport ? this.reportInfo.name : null),
                    allowBlank: true,
                    anchor: '100%',
                    maxLength: 200
                }),
                new Ext.form.DisplayField({
                    id: 'reportNameDisplay',
                    name: 'reportNameDisplay',
                    fieldLabel: 'Report Name',
                    hidden: !savedReport && canSaveChanges, 
                    value: $h(savedReport ? this.reportInfo.name : null),
                    anchor: '100%'
                }),
                new Ext.form.TextArea({
                    id: 'reportDescription',
                    name: 'reportDescription',
                    fieldLabel: 'Report Description',
                    hidden: !canSaveChanges,
                    value: (savedReport ? this.reportInfo.description : null),
                    allowBlank: true,
                    anchor: '100%',
                    height: 35
                }),
                new Ext.form.DisplayField({
                    id: 'reportDescriptionDisplay',
                    name: 'reportDescriptionDisplay',
                    fieldLabel: 'Report Description',
                    hidden: canSaveChanges,
                    value: $h(savedReport ? this.reportInfo.description : null),
                    anchor: '100%'
                }),
                new Ext.form.RadioGroup({
                    id: 'reportShared',
                    name: 'reportShared',
                    fieldLabel: 'Viewable By',
                    anchor: '100%',
                    items : [
                            { name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', disabled: !canSaveSharedCharts, checked: currentlyShared },
                            { name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', disabled: !canSaveSharedCharts, checked: !currentlyShared }
                        ]
                }),
                new Ext.form.Checkbox({
                    id: 'reportSaveThumbnail',
                    name: 'reportSaveThumbnail',
                    fieldLabel: 'Save Thumbnail',
                    anchor: '100%',
                    checked: this.saveThumbnail,
                    value: this.saveThumbnail,
                    listeners: {
                        scope: this,
                        'check': function(cmp, checked){
                            this.saveThumbnail = checked;
                            this.fireEvent('saveThumbnailChecked', this.saveThumbnail);
                        }
                    }
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
                columnWidth: 0.45,
                border: false,
                bodyStyle: 'padding: 5px 20px 5px 5px',
                html: chartEditorDescription
            },{
                columnWidth: 0.55,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: [this.saveChartFormPanel]
            }]
        });

        this.items = items;

        // determine the initial active item to set for this chart layout
        this.activeItem = savedReport ? 1 : 0;

        this.on('activate', function(){
           this.doLayout();
        }, this);

        LABKEY.vis.ChartEditorOverviewPanel.superclass.initComponent.call(this);
    },

    isSavedReport : function()
    {
        return (typeof this.reportInfo == "object");
    },

    isChartCreator : function()
    {
        return (!this.isSavedReport() || this.reportInfo.createdBy == LABKEY.Security.currentUser.id);
    },

    isChartOwner : function()
    {
        return (!this.isSavedReport() || this.reportInfo.ownerId == LABKEY.Security.currentUser.id);
    },

    canSaveChanges : function()
    {
        if (LABKEY.Security.currentUser.isGuest)
            return false;
        // Note that the following check means that Readers are allowed to save new charts (readers own new charts they're creating)- this is by design.
        return (LABKEY.Security.currentUser.isAdmin || this.isChartCreator());
    },

    canSaveSharedCharts : function()
    {
        return LABKEY.Security.currentUser.canInsert && this.canSaveChanges();
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
            items: [new LABKEY.vis.MeasuresPanel({
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
                        this.fireEvent('initialMeasuresStoreLoaded', data);
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
    },

    updateOverview: function(reportInfo, saveThumbnail){
        this.reportInfo = reportInfo;

        // update the name/description field values accordingly
        if(typeof this.reportInfo == 'object'){
            this.saveChartFormPanel.getComponent('reportName').hide();
            this.saveChartFormPanel.getComponent('reportNameDisplay').setValue($h(this.reportInfo.name));
            this.saveChartFormPanel.getComponent('reportNameDisplay').show();
            this.saveChartFormPanel.getComponent('reportDescription').setValue(this.reportInfo.description);
            this.saveChartFormPanel.getComponent('reportShared').setValue(this.reportInfo.shared);
            this.saveChartFormPanel.getComponent('reportSaveThumbnail').setValue(saveThumbnail);

            // if the user can update, show save button (which is now for replacing the saved report)
            (this.canSaveChanges() ? this.saveBtn.show() : this.saveBtn.hide());
            // Always show the save as button
            this.saveAsBtn.show();
        }

        // change the active card layout item
        this.getLayout().setActiveItem(1);
        this.doLayout();
    },

    getSaveThumbnail: function() {
        return this.saveThumbnail;
    }
});
