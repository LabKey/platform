/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Admin', {

    extend : 'Ext.tab.Panel',

    constructor : function(config) {

        Ext4.apply(config, {
            defaults: {
                xtype: 'panel',
                border: false,
                margin: '5 0 0 0'
            }
        });

        Ext4.applyIf(config, {

        });

        this.callParent([config]);

//        this.addEvents();
    },

    initComponent : function() {
        this.items = this.getItems();

        this.callParent();
    },

    getItems: function(){
        return [
//            this.getActionsPanel(), TODO: Create the actions panel. Skipping for this sprint (13.1 Sprint 2)
            this.getFilePropertiesPanel(),
            this.getToolBarPanel(),
            this.getGeneralSettingsPanel()
        ];
    },

    getActionsPanel: function(){
        return {
            title: 'Actions',
            items: []
        };
    },

    getFilePropertiesPanel: function(){
        return {
            title: 'File Properties',
            items: []
        };
    },

    getToolBarPanel: function(){
        return {
            title: 'Toolbar and Grid Settings',
            items: []
        };
    },

    getGeneralSettingsPanel: function(){
        return {
            title: 'General Settings',
            items: []
        };
    }

});
