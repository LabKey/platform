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
        if (this.reportConfig.json){

            var jsonData = Ext4.JSON.decode(this.reportConfig.json);
            this.assayConfig = {};
            Ext4.each(jsonData, function(row){

                this.assayConfig[row.AssayName] = {
                    folderName  : row.folderName,
                    folderId    : row.folderId,
                    schemaName  : row.schemaName,
                    queryName   : row.queryName
                };
            }, this);
        }

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
                cls     : 'labkey-report-config',
                flex    : 1.2,
                items   : properties
            });
         }
        return this.reportPanel;
    },

    getAssayPanel : function(){

        if (!this.configPanel){
            this.configPanel = Ext4.create('Ext.panel.Panel', {
                tpl     : this.getConfigTpl(),
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
            success: function(result){
                this.assayData = result.rows;
                this.getAssayPanel().getEl().unmask();

                // merge any saved information
                if (this.assayConfig){

                    Ext4.each(this.assayData, function(row){

                        var cfg = this.assayConfig[row.AssayName];
                        if (cfg){
                            row.folderName = cfg.folderName;
                            row.folderId = cfg.folderId;
                            row.schemaName = cfg.schemaName;
                            row.queryName = cfg.queryName;
                        }
                    }, this);
                }
                this.getAssayPanel().update(this.assayData);
                this.registerClickHandlers();
            }
        });
    },

    getConfigTpl : function(){

        return new Ext4.XTemplate('<table class="assay-summary">',
                '<tr><th></th><th>Assay</th><th>Folder</th><th>Schema</th><th>Query</th></tr>',
                '<tpl for=".">',
                    '<tr>',
                    '<td><span dataindex="{[xindex-1]}" height="16px" class="fa fa-pencil"></span></td>',
                    '<td>{AssayName}</td>',
                    '<td>{folderName}</td>',
                    '<td>{schemaName}</td>',
                    '<td>{queryName}</td>',
                    '</tr>',
                '</tpl>',
                '</table>'
        );
    },

    registerClickHandlers : function() {
        var el = this.configPanel.getEl();
        if (el){
            var icons = el.query('span.fa.fa-pencil');
            if (icons && icons.length){

                Ext4.each(icons, function(icon) {

                    Ext4.get(icon).addListener('click', this.onEdit, this);
                }, this);
            }
        }
    },

    onEdit : function(e, cmp){

        var dataIdx = cmp.getAttribute('dataindex');
        var items = [{html: 'Choose the source query that will provide status information for this assay.', border : false}];
        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var folderField = Ext4.create('Ext.form.field.ComboBox',
            sqvModel.makeContainerComboConfig({
                name: 'Folder',
                value : this.assayData[dataIdx].folderId,
                labelWidth: 150,
                fieldLabel: 'Folder name',
                editable: false,
                width: 400,
                padding: '10px 0 0 0',
                allowBlank: true
            })
        );
        var schemaField = Ext4.create('Ext.form.field.ComboBox',
            sqvModel.makeSchemaComboConfig({
                name: 'Schema',
                initialValue : this.assayData[dataIdx].schemaName,
                labelWidth: 150,
                allowBlank: false,
                fieldLabel: 'Schema name',
                editable: false,
                disabled: false,
                width: 400,
                padding: '10px 0 0 0'
            })
        );
        var queryField = Ext4.create('Ext.form.field.ComboBox',
            sqvModel.makeQueryComboConfig({
                name: 'listTable',
                initialValue : this.assayData[dataIdx].queryName,
                forceSelection: true,
                fieldLabel: 'Query name',
                labelWidth: 150,
                allowBlank: false,
                width: 400,
                padding: '10px 0 0 0'
            })
        );
        items.push(folderField, schemaField, queryField);

        var window = Ext4.create('Ext.window.Window', {
            title   : 'Choose source query for status information',
            width   : 500,
            modal   : true,
            cls     : 'labkey-assay-config',
            items   : [{
                xtype   : 'form',
                border  : false,
                items   : items,
                padding : 10
            }],
            buttons: [{
                text: 'Submit',
                handler:  function() {

                    var form = window.down('form').getForm();
                    if (form.isValid()){

                        var folderName, folderId, schemaName, queryName;

                        // get the folder info
                        var folder = folderField.getValue();
                        if (folder){
                            var rec = folderField.findRecordByValue(folder);
                            if (rec){
                                folderName = rec.get('Name');
                                folderId = folder;
                            }
                        }

                        schemaName = schemaField.getValue();
                        queryName = queryField.getValue();

                        if (dataIdx){
                            var assayRec = this.assayData[dataIdx];

                            if (folderName)
                                assayRec.folderName = folderName;
                            if (folderId)
                                assayRec.folderId = folderId;
                            if (schemaName)
                                assayRec.schemaName = schemaName;
                            if (queryName)
                                assayRec.queryName = queryName;

                            this.getAssayPanel().update(this.assayData);
                            this.registerClickHandlers();
                        }
                        window.close();
                    }
                    else
                    {
                        Ext4.Msg.show({
                            title: "Error",
                            msg: 'All required fields must be specified.',
                            buttons: Ext4.MessageBox.OK,
                            icon: Ext4.MessageBox.ERROR
                        });
                    }
                },
                scope: this
            }],
            buttonAlign: 'right',
            scope: this
        });
        window.show();
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

            var data = [];
            Ext4.each(this.assayData, function(row){

                data.push({AssayName : row.AssayName,
                    folderName  : row.folderName,
                    folderId    : row.folderId,
                    schemaName  : row.schemaName,
                    queryName   : row.queryName
                })
            }, this);

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('study-reports', 'saveAssayProgressReport.api'),
                method: 'POST',
                jsonData: {
                    name    : this.reportConfig.reportName,
                    description : this.reportConfig.reportDescription,
                    shared      : this.reportConfig.shared,
                    reportId    : this.reportConfig.reportId,
                    jsonData    : data
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