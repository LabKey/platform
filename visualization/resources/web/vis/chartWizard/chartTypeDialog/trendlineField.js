/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TrendlineField', {
    extend: 'Ext.form.Panel',

    border: false,
    bodyStyle: 'background-color: transparent;',
    height: 34,

    baseQueryKey: null,
    initData: null,

    options: [
        ['', 'Point-to-Point', false, false, null],
        ['Linear', 'Linear Regression', false, false, null],
        ['Polynomial', 'Polynomial', false, false, null],
        ['Three Parameter', 'Nonlinear 3PL', false, true, 'assay'],
        ['3 Parameter', 'Nonlinear 3PL (Alternate)', false, true, 'assay'],
        ['Four Parameter', 'Nonlinear 4PL', true, true, 'assay'],
        ['4 Parameter', 'Nonlinear 4PL (Simplex)', false, false, 'assay'],
        ['Five Parameter', 'Nonlinear 5PL', true, true, 'assay'],
    ],

    initComponent : function()
    {
        if (this.initData == null)
            this.initData = {};

        this.items = [
            this.getTrendlineTypeCombo()
        ];

        this.callParent();
    },

    getTrendlineTypeStore : function() {
        if (!this.trendlineTypeStore) {
            this.trendlineTypeStore = Ext4.create('Ext.data.ArrayStore', {
                fields: ['value','label','showMin','showMax', 'schemaPrefix'],
                data: this.options
            });

            if (this.baseQueryKey) {
                this.trendlineTypeStore.filter([{
                    filterFn: function(item) {
                        return item.get('schemaPrefix') == null || this.baseQueryKey.startsWith(item.get('schemaPrefix'));
                    },
                    scope: this
                }]);
            }
        }

        return this.trendlineTypeStore;
    },

    getTrendlineTypeCombo : function() {
        if (!this.trendlineTypeCombo)
        {
            this.trendlineTypeCombo = Ext4.create('Ext.form.field.ComboBox', {
                name: 'trendlineType',
                fieldLabel: 'Type',
                labelWidth: 75,
                width: 300,
                padding: '5px 0 0 0',
                store: this.getTrendlineTypeStore(),
                queryMode: 'local',
                editable: false,
                forceSelection: true,
                displayField: 'label',
                valueField: 'value',
                value: this.initData.trendlineType || ''
            });
        }

        return this.trendlineTypeCombo;
    },

    getValue : function()
    {
        var values = {
            trendlineType: this.getTrendlineTypeCombo().getValue()
        };

        return values;
    }
});