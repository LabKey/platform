/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.define('LABKEY.vis.GenericOptionsPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config)
    {
        Ext4.apply(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            border: false,
            padding: 10,
            labelAlign: 'top',
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        this.callParent([config]);
    },

    specialKeyPressed: function(f, e) {
        if(e.getKey() == e.ENTER){
            this.applyButtonClicked();
        }
    },

    applyButtonClicked: function()
    {},

    getPanelOptionValues : function()
    {
        return {};
    },

    checkForChangesAndFireEvents : function()
    {},

    // called to reset the panel components based on the values in the parameter object,
    // this will likely be called on window cancel, ESC key, or when the close X is clicked 
    restoreValues : function(initValues) 
    {}
});
