Ext4.define('LABKEY.vis.TrendlineField', {
    extend: 'Ext.form.Panel',

    border: false,
    bodyStyle: 'background-color: transparent;',
    height: 34,

    baseQueryKey: null,
    initData: null,

    options: ['', 'Linear', 'Polynomial', 'Three Parameter', '3 Parameter', 'Four Parameter', '4 Parameter', 'Five Parameter'],

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
            var data = [];
            for (var value in this.options) {
                var option = LABKEY.vis.GenericChartHelper.TRENDLINE_OPTIONS[this.options[value]];
                data.push([option.value, option.label, option.showMin, option.showMax, option.schemaPrefix]);
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