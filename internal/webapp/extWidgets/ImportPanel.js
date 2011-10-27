/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


LABKEY.requiresScript("/extWidgets/ExcelUploadPanel.js");
LABKEY.requiresScript("/extWidgets/Ext4FormPanel.js");

Ext4.define('LABKEY.ext.ImportPanel', {
    extend: 'Ext.tab.Panel',
    initComponent: function(){
        Ext4.apply(this, {
            activeTab: 0,
            defaults: {
                style: 'padding: 10px;'
            },
            items: [{
                title: 'Import Single',
                xtype: 'labkey-formpanel',
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName
//            },{
//                title: 'Import Multiple',
//                xtype: 'labkey-gridpanel',
//                store: Ext4.create('LABKEY.ext4.Store', {
//                    schemaName: this.schemaName,
//                    queryName: this.queryName,
//                    viewName: this.viewName,
//                    maxRows: 0
//                })
            },{
                title: 'Import Spreadsheet',
                xtype: 'labkey-exceluploadpanel',
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName
            }]
        });

        this.callParent(arguments);
    }
});