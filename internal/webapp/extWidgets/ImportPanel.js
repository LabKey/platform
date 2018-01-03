/*
 * Copyright (c) 2011-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/extWidgets/ExcelUploadPanel.js");
LABKEY.requiresScript("/extWidgets/Ext4FormPanel.js");

Ext4.define('LABKEY.ext.ImportPanel', {
    extend: 'Ext.tab.Panel',
    initComponent: function(){
        this.viewName = this.viewName || '~~INSERT~~';

        this.store = this.store || Ext4.create('LABKEY.ext4.data.Store', {
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            maxRows: 0,
            autoLoad: true,
            listeners: {
                load: function(store){
                    delete store.maxRows;
                }
            }
        });

        Ext4.apply(this, {
            activeTab: this.activeTab || 0,
            defaults: {
                style: 'padding: 10px;'
            },
            items: [{
                title: 'Import Single',
                xtype: 'labkey-formpanel',
                store: this.store,
                listeners: {
                    uploadcomplete: function(panel, response){
                        window.location = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.getParameter('returnURL') || LABKEY.ActionURL.getParameter('returnUrl') || LABKEY.ActionURL.buildURL('project', 'begin');
                    }
                }
//            },{
//                title: 'Import Multiple',
//                xtype: 'labkey-gridpanel',
//                store: Ext4.create('LABKEY.ext4.data.Store', {
//                    schemaName: this.schemaName,
//                    queryName: this.queryName,
//                    viewName: this.viewName,
//                    maxRows: 0
//                })
            },{
                title: 'Import Spreadsheet',
                xtype: 'labkey-exceluploadpanel',
                store: this.store, //saves redundant loading
                showAlertOnSuccess: false,
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName,
                columns: this.columns,
                listeners: {
                    uploadcomplete: function(panel, response){
                        Ext4.Msg.alert("Success", response.successMessage, function(btn){
                            window.location = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.getParameter('returnURL') || LABKEY.ActionURL.getParameter('returnUrl') || LABKEY.ActionURL.buildURL('project', 'begin');
                        }, this);
                    }
                }
            }]
        });

        this.callParent(arguments);
    }
});