/*
 * Copyright (c) 2011-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * NOTE: ExcelUploadPanel.js and Ext4FormPanel.js are required for this component
 */
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
                    scope: this,
                    uploadcomplete: this.goToReturnUrl
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
                    scope: this,
                    uploadcomplete: function(panel, response){
                        Ext4.Msg.alert("Success", response.successMessage, this.goToReturnUrl, this);
                    }
                }
            }]
        });

        this.callParent(arguments);
    },

    goToReturnUrl: function() {
        var returnUrl = LABKEY.ActionURL.getReturnUrl() || LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.getParameter('returnURL');

        if (!returnUrl) {
            // default to using the project-begin action
            returnUrl = LABKEY.ActionURL.buildURL('project', 'begin');
        }

        window.location = returnUrl;
    }
});