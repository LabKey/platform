/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.DimensionFilterPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.dimensionfilerpanel',

    autoScroll: true,
    border: false,
    cls    : 'dimension-filter-panel rpf',

    dimension: null,
    showSelectDefault: false,
    bubbleEvents: ['chartDefinitionChanged'],

    initComponent : function()
    {
        if (this.dimension == null)
            this.dimension = {};

        this.title = this.dimension.label;

        this.items = [
            this.getDefaultDisplayField(),
            this.getGridPanel()
        ];

        this.callParent();

        this.addEvents('measureMetadataRequestPending', 'measureMetadataRequestComplete', 'chartDefinitionChanged');

        this.fireEvent('measureMetadataRequestPending');
        this.getGridStore().load();
    },

    getDefaultDisplayField : function()
    {
        if (!this.defaultDisplayField)
        {
            this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
                hideLabel: true,
                hidden: true,
                padding: 3,
                value: '<span style="font-size:75%;color:red;">Selecting 5 values by default</span>'
            });
        }

        return this.defaultDisplayField;
    },

    getGridPanel : function()
    {
        if (!this.gridPanel)
        {
            var sm = Ext4.create('Ext.selection.CheckboxModel', {});
            sm.on('selectionchange', function(selModel)
            {
                // update the values array for the selected grid rows
                this.dimension.values = [];
                Ext4.each(selModel.getSelection(), function(selectedRecord){
                    this.dimension.values.push(selectedRecord.get('value'));
                }, this);

                // sort the selected dimension array
                this.dimension.values.sort();

                this.fireEvent('chartDefinitionChanged');
            }, this, {buffer: 1000}); // buffer allows single event to fire if multiple selections made quickly

            this.gridPanel = Ext4.create('Ext.grid.GridPanel', {
                autoHeight: true,
                enableHdMenu: false,
                store: this.getGridStore(),
                viewConfig: {forceFit: true},
                sortableColumns: false,
                border: false,
                frame: false,
                header: false,
                selModel: sm,
                columns: [{
                    text: 'All',
                    dataIndex: 'value',
                    menuDisabled: true,
                    sortable: false,
                    resizable: false,
                    flex: 1,
                    renderer: function(value, p, record) {
                        var msg = Ext4.util.Format.htmlEncode(value);
                        p.tdAttr = 'data-qtip="' + msg + '"';
                        return msg;
                    }
                }],
                listeners: {
                    scope: this,
                    viewready: function (grid)
                    {
                        this.selectValues(true);
                    }
                }
            });
        }

        return this.gridPanel;
    },

    getGridStore : function()
    {
        if (!this.gridStore)
        {
            this.gridStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.SimpleValueModel',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, this.dimension),
                    reader: {
                        type: 'json',
                        root: 'values',
                        idProperty:'id'
                    }
                },
                sorters: [{property: 'value'}],
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        // if this is not a saved chart with pre-selected values, initially select the first 5 values
                        if (!Ext4.isArray(this.dimension.values) && store.getCount() > 0)
                        {
                            this.showSelectDefault = true;
                            this.dimension.values = [];
                            for (var i = 0; i < (store.getCount() < 5 ? store.getCount() : 5); i++)
                                this.dimension.values.push(store.getAt(i).get('value'));
                            this.selectValues(true);
                        }

                        this.fireEvent('measureMetadataRequestComplete');
                    }
                }
            });
        }

        return this.gridStore;
    },

    selectValues : function(suspendEvents)
    {
        if (!Ext4.isArray(this.dimension.values))
            return;

        var dimSelModel = this.getGridPanel().getSelectionModel();
        if (suspendEvents)
            dimSelModel.suspendEvents(false);

        for (var i = 0; i < this.dimension.values.length; i++)
        {
            var recIndex = this.getGridStore().find('value', this.dimension.values[i]);
            if (recIndex > -1)
                dimSelModel.select(recIndex, true);
        }

        if (suspendEvents)
            dimSelModel.resumeEvents();

        this.showDefaultSelectionMsg();
    },

    showDefaultSelectionMsg : function()
    {
        // show the selecting default text if necessary
        if (this.showSelectDefault)
        {
            // show the display for 5 seconds before hiding it again
            var me = this;
            this.getDefaultDisplayField().show();
            setTimeout(function ()
            {
                me.getDefaultDisplayField().hide();
            }, 5000);

            this.showSelectDefault = false;
        }
    },

    getDimensionAlias : function()
    {
        return LABKEY.vis.TimeChartHelper.getMeasureAlias(this.dimension);
    },

    getValues : function()
    {
        return this.dimension.values;
    }
});