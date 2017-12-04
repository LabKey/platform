/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.AdminWizardForm', {
    extend: 'Ext.form.Panel',

    constructor: function (config) {
        if (!config.listeners)
            config.listeners = {};

        if (!config.listeners.boxready) {
            config.listeners.boxready = {
                fn: function (panel) {
                    panel.doLayout();

                    var el = panel.getEl().parent();
                    var contentDiv = Ext4.query('div.labkey-wizard-content');
                    if (contentDiv.length > 0) {
                        el = Ext4.get(contentDiv[0]);
                    }
                    Ext4.EventManager.onWindowResize(function () {
                        panel.setWidth(el.getWidth() - 60); // 60px is total paddings
                    }, panel, {delay: 10});
                },
                single: true
            }
        }

        Ext4.apply(this, config);
        this.callParent(config);
    }

});