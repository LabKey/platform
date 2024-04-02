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

Ext4.define('LABKEY.ext4.PHIStore', {
    extend: 'Ext.data.Store',

    constructor: function (config) {
        Ext4.apply(this, config);

        this.fields = ['label', 'value'];

        if (config.maxAllowedLevel) {
            let data = [];

            switch (config.maxAllowedLevel) {
                case 'Restricted':
                    data.push({ value: 'Restricted', label: 'Restricted, Full and Limited PHI' });
                case 'PHI':
                    data.push({ value: 'PHI', label: 'Full and Limited PHI' });
                case 'Limited':
                    data.push({ value: 'Limited', label: 'Limited PHI' });
                    break;
            }
            // want the PHI to be ordered from least to most restrictive
            this.data = data.reverse();
        }
        this.callParent(config);
    }
});

Ext4.define('LABKEY.ext4.PHIExport', {
    extend: 'Ext.container.Container',
    alias: 'widget.labkey_phi_option',

    constructor: function (config) {
        Ext4.applyIf(config, {
            maxAllowedLevel: 'NotPHI',
            fieldName: 'exportPhiLevel'
        });

        this.phiHelp = LABKEY.export.Util.helpPopup("Include PHI Columns", "Include all dataset and list columns, study properties, and specimen data that have been tagged with this PHI level or below.");
        let isIncludePhiChecked = config.maxAllowedLevel !== 'NotPHI';
        let phiStore = Ext4.create('LABKEY.ext4.PHIStore', {
            maxAllowedLevel: config.maxAllowedLevel
        });

        this.layout = 'hbox';
        this.items = [{
            xtype: 'checkbox',
            hideLabel: true,
            boxLabel: 'Include PHI Columns: ' + this.phiHelp.html + '&nbsp&nbsp',
            itemId: 'includePhi',
            name: 'includePhi',
            objectType: 'otherOptions',
            checked: isIncludePhiChecked,
            listeners: {
                change: function (cmp, checked) {
                    let combo = cmp.ownerCt.getComponent('phi_level');
                    if (combo) {
                        combo.setValue(checked ? config.maxAllowedLevel : 'NotPHI');
                        combo.setDisabled(!checked);
                    }
                }
            }
        }, {
            xtype: 'combobox',
            hideLabel: true,
            disabled: !isIncludePhiChecked,
            itemId: 'phi_level',
            name: config.fieldName,
            store: phiStore,
            displayField: 'label',
            valueField: 'value',
            queryMode: 'local',
            margin: '0 0 0 2',
            matchFieldWidth: false,
            width: 195,
            valueNotFoundText: 'NotPHI',
            value: config.maxAllowedLevel
        }];

        this.listeners = {
            render : function(cmp){cmp.phiHelp.callback();}
        };
        this.callParent(config);
    }
});

LABKEY.export = LABKEY.export || {};
LABKEY.export.Util = new function () {

    return {
        // javascript version of PageFlowUtil.helpPopup(), returns html and callback to be invoked after element is inserted into page.
        // Useful for including LabKey-style help in Ext components
        // CONSIDER: move to dom/Utils.js if this can be used elsewhere
        helpPopup: function (titleText, helpText) {
            const h = Ext4.util.Format.htmlEncode;
            const id = Ext4.id();
            const html = '<a id="' + id + '" href="#" tabindex="-1" class="_helpPopup"><span class="labkey-help-pop-up">?</span></a>';
            const callback = function () {
                LABKEY.Utils.attachEventHandler(id, "click", function () {
                    return showHelpDivDelay(this, titleText, h(helpText), 'auto');
                });
                LABKEY.Utils.attachEventHandler(id, "mouseover", function () {
                    return showHelpDivDelay(this, titleText, h(helpText), 'auto');
                });
                LABKEY.Utils.attachEventHandler(id, "mouseout", hideHelpDivDelay);
            };
            return { "html": html, "callback": callback };
        }
    }
};
