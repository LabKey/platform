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
        this.items.push(this.getConfigPanel());

        this.callParent(arguments);
    },

    getConfigPanel : function(){

        if (!this.configPanel){
            this.configPanel = Ext4.create('Ext.panel.Panel', {
                itemId : 'configpanel',
                items : [{
                    xtype : 'panel',
                    height : 700
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
                this.getConfigPanel().getEl().unmask();
                this.getConfigPanel().removeAll();
                this.getConfigPanel().add({
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
    }
});