/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

    options: ['', 'Linear', 'Polynomial', '3 Parameter', 'Three Parameter', '4 Parameter', 'Four Parameter', 'Five Parameter', '5 Parameter'],

    initComponent: function() {
        if (this.initData == null)
            this.initData = {};

        this.items = [
            this.getTrendlineTypeCombo()
        ];

        this.callParent();
    },

    getTrendlineTypeStore: function() {
        if (!this.trendlineTypeStore) {
            const data = [];
            for (let i = 0; i < this.options.length; i++) {
                const option = LABKEY.vis.GenericChartHelper.TRENDLINE_OPTIONS[this.options[i]];
                if (option) {
                    data.push([option.value, option.label, option.showMin, option.showMax, option.schemaPrefix]);
                }
            }

            this.trendlineTypeStore = Ext4.create('Ext.data.ArrayStore', {
                fields: ['value','label','showMin','showMax', 'schemaPrefix'],
                data: data
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

    getTrendlineTypeCombo: function() {
        if (!this.trendlineTypeCombo) {
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

    getValue: function() {
        return {
            trendlineType: this.getTrendlineTypeCombo().getValue()
        };
    }
});