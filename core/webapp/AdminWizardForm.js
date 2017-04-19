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
                    if (LABKEY.experimental.useExperimentalCoreUI) {
                        var contentDiv = Ext4.query('div.labkey-wizard-content');
                        if (contentDiv.length > 0)
                            el = Ext4.get(contentDiv[0]);
                        Ext4.EventManager.onWindowResize(function () {
                            if (LABKEY.experimental.useExperimentalCoreUI)
                                panel.setWidth(el.getWidth() - 60); // 60px is total paddings
                        }, panel, {delay: 10});
                    }
                    else
                    {
                        Ext4.EventManager.onWindowResize(function () {
                            panel.setWidth(el.getBox().width);
                        }, panel, {delay: 200});
                    }
                },
                single: true
            }
        }

        Ext4.apply(this, config);
        this.callParent(config);
    }

});