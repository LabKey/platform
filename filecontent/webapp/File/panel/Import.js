/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Import', {

    extend: 'Ext.Window',

    autoRequestImport: false,

    autoScroll: true,

    modal: true,

    title: 'Import Data',

    width: 725,

    browser: null,

    initComponent: function() {

        if (null == this.browser)
            console.error('A File.panel.Browser is required for the File.panel.Import configuration');

        this.items = this.getItemConfiguration();

        this.callParent(arguments);
    },

    /**
     * Returns an Array of item configurations based on the current state of the File.panel.Import
     */
    getItemConfiguration : function() {
        var items = [];

        return items;
    },

    requestImport : function(config) {

    }
});