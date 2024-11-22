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

    initData: null,

    options: [
        ['', 'Point-to-Point', false, false],
        ['Linear', 'Linear Regression', false, false],
        ['Polynomial', 'Polynomial', false, false],
        ['3 Parameter', 'Nonlinear 3PL', false, true],
        ['4 Parameter', 'Nonlinear 4PL', false, false],
        ['Three Parameter', 'Three Parameter', false, true],
        ['Four Parameter', 'Four Parameter', true, true],
        ['Five Parameter', 'Five Parameter', true, true],
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

    getTrendlineTypeCombo : function()
    {
        if (!this.trendlineTypeCombo)
        {
            this.trendlineTypeCombo = Ext4.create('Ext.form.field.ComboBox', {
                name: 'trendlineType',
                fieldLabel: 'Type',
                labelWidth: 75,
                width: 300,
                padding: '5px 0 0 0',
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['value','label','showMin','showMax'],
                    data: this.options
                }),
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