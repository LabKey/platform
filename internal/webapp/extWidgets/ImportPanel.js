

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
            },{
                title: 'Import Bulk',
                xtype: 'labkey-exceluploadpanel',
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName
            }]
        });

        this.callParent(arguments);
    }
});