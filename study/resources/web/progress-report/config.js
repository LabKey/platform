/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * Main panel for the NAb QC interface.
 */

Ext4.define('LABKEY.ext4.ProgressReportConfig', {

    extend: 'Ext.panel.Panel',

    border: false,

    header : false,

    alias: 'widget.labkey-progress-report-config',

    padding: 10,

    constructor: function (config)
    {
        this.callParent([config]);
    },

    initComponent: function ()
    {
        // grab the json encoded assay config (if we are editing an existing report)
        if (this.reportConfig.json)
            this.reportConfig.assayConfig = Ext4.JSON.decode(this.reportConfig.json);

        this.items = [];
        this.items.push(this.getReportPanel());
        this.items.push(this.getAssayPanel());

        this.buttons = [{
                xtype   : 'button',
                text    : 'Save',
                scope   : this,
                handler : this.saveReport
            },{
                xtype   : 'button',
                text    : 'Cancel',
                scope   : this,
                handler : function(){
                    if (this.returnUrl)
                        window.location = this.returnUrl;
                    else
                        window.history.back();
                }
        }];

        this.callParent(arguments);
    },

    getReportPanel : function(){

        if (!this.reportPanel){

            var properties = [{
                xtype      : 'textfield',
                allowBlank : false,
                name       : 'viewName',
                labelWidth : 120,
                width      : 400,
                fieldLabel : 'Name',
                value      : this.reportConfig.reportName,
                listeners: {
                    scope : this,
                    change: function(cmp, newVal){
                        this.reportConfig.reportName = newVal;
                    }
                }
            },{
                xtype      : 'textarea',
                fieldLabel : 'Description',
                name       : 'description',
                value      : this.reportConfig.reportDescription,
                labelWidth : 120,
                width      : 400,
                listeners: {
                    scope  : this,
                    change : function(cmp, newVal){
                        this.reportConfig.reportDescription = newVal;
                    }
                }
            }];

            properties.push({
                xtype   : 'checkbox',
                checked     : this.reportConfig.shared,
                boxLabel    : 'Share this report with all users?',
                name        : 'shared',
                fieldLabel  : "Shared",
                uncheckedValue : false,
                labelWidth : 120,
                width      : 400,
                listeners: {
                    scope  : this,
                    change: function(cmp, newVal, oldVal){
                        this.reportConfig.shared = newVal;
                    }
                }
            });

            this.reportPanel = Ext4.create('Ext.form.Panel', {
                border  : false,
                frame   : false,
                flex    : 1.2,
                items   : properties
            });
        }
        return this.reportPanel;
    },

    getAssayPanel : function(){

        if (!this.configPanel){
            this.configPanel = Ext4.create('Ext.panel.Panel', {
                itemId : 'configpanel',
                items : [{
                    xtype : 'panel',
                    height : 200
                }],
                listeners : {
                    scope   : this,
                    render  : function(cmp) {
                        cmp.getEl().mask('Requesting Assay information');
                        this.getAssayInformation();
                    }
                }
            });
        }
        return this.configPanel;
    },

    getAssayInformation : function(){

        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'AssaySpecimen',
            scope: this,
            success: function(result)
            {
                this.getAssayPanel().getEl().unmask();
                this.getAssayPanel().removeAll();
                this.getAssayPanel().add({
                    xtype : 'panel',
                    border: false,
                    tpl   : this.getConfigTpl(),
                    data  : result
                });
            }
        });
    },

    /**
     * Generate the template for the run summary section
     * @returns {Array.<*>}
     */
    getConfigTpl : function(){

        return new Ext4.XTemplate('<table class="assay-summary">',
                '<tr><th></th><th>Name</th><th>Query</th></tr>',
                '<tpl for="rows">',
                    '<tr>',
                    '<td><span height="16px" class="fa fa-pencil"></span></td>',
                    '<td>{AssayName}</td>',
                    '<td>Add the Query Summary here</td>',
                    '</tr>',
                '</tpl>',
                '</table>'
        );
    },

    failureHandler : function(response)
    {
        this.getEl().unmask();
        var msg = response.status == 403 ? response.statusText : Ext4.JSON.decode(response.responseText).exception;
        Ext4.Msg.show({
            title:'Error',
            msg: msg,
            buttons: Ext4.Msg.OK,
            icon: Ext4.Msg.ERROR
        });
    },

    saveReport : function(){
        var form = this.reportPanel.getForm();
        if (form.isValid()){

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('study-reports', 'saveAssayProgressReport.api'),
                method: 'POST',
                jsonData: {
                    name    : this.reportConfig.reportName,
                    description : this.reportConfig.reportDescription,
                    shared      : this.reportConfig.shared,
                    reportId    : this.reportConfig.reportId
/*
                    jsonData : [
                        {
                            name : 'flow',
                            folder : 'home',
                            schemaName : 'lists',
                            queryName : 'customQuery1'
                        },{
                            name : 'Specimen Registry',
                            folder : 'home',
                            schemaName : 'lists',
                            queryName : 'specimenProgressReport'
                        }
                    ]
*/
                },
                success: function (response) {
                    var o = Ext4.JSON.decode(response.responseText);
                    if (this.returnUrl)
                        window.location = this.returnUrl;
                    else if (o.reportId){
                        window.location = LABKEY.ActionURL.buildURL('reports', 'runReport.view', null, {reportId : o.reportId});
                    }
                },
                failure: this.failureHandler,
                scope: this
            });
        }
        else {
            Ext4.Msg.show({
                title   :'Error',
                msg     : 'Please fill out all required fields.',
                buttons : Ext4.Msg.OK,
                icon    : Ext4.Msg.ERROR
            });
        }
    }
});