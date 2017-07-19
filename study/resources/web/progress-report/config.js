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
                text    : 'Cancel'
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
//                value      : this.data.name
                listeners: {
                    scope : this,
                    change: function(cmp, newVal){
                        this.name = newVal;
                    }
                }
            },{
                xtype      : 'textarea',
                fieldLabel : 'Description',
                name       : 'description',
//                value      : this.data.description,
                labelWidth : 120,
                width      : 400,
                listeners: {
                    scope  : this,
                    change : function(cmp, newVal){
                        this.description = newVal;
                    }
                }
            }];

            var sharedName = "shared";
            if (this.disableShared) {
                // be sure to roundtrip the original shared value
                // since we are disabling the checkbox
                properties.push({
                    xtype : 'hidden',
                    name  : "shared",
//                    value : this.data.shared,
                    labelWidth : 120,
                    width      : 400
                });

                // rename the disabled checkbox
                sharedName = "hiddenShared";
            }

            properties.push({
                xtype   : 'checkbox',
//                inputValue  : this.data.shared,
//                checked     : this.data.shared,
                boxLabel    : 'Share this report with all users?',
                name        : sharedName,
                fieldLabel  : "Shared",
                disabled    : this.disableShared,
                uncheckedValue : false,
                labelWidth : 120,
                width      : 400,
                listeners: {
                    change: function(cmp, newVal, oldVal){
                        cmp.inputValue = newVal;
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
        console.log('save report');

        var form = this.reportPanel.getForm();
        if (form.isValid()){

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('study-reports', 'saveAssayProgressReport.api'),
                method: 'POST',
                jsonData: {
                    name    : this.name,
                    description : this.description
                },
                success: function (response) {
                    if (this.returnUrl)
                        window.location = this.returnUrl;
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