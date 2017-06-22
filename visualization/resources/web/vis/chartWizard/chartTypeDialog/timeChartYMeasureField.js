/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TimeChartYMeasureField', {
    extend: 'Ext.form.Panel',
    alias: 'widget.timechartymeasurefield',

    border: false,
    bodyStyle: 'background-color: transparent;',
    cls: 'y-measure-form',
    bubbleEvents: ['requiresDataRefresh'],

    selection: null,
    measure: null,
    defaultDimensionAggregate: 'AVG',

    initComponent: function ()
    {
        if (this.selection == null)
            this.selection = {measure: {}};
        this.measure = this.selection.measure;

        this.items = [
            this.getTitleCmp(),
            this.getMeasureDateColCombo(),
            this.getDimensionSeriesCombo(),
            this.getDimensionAggregateCombo()
        ];

        this.callParent();

        this.addEvents('measureMetadataRequestPending', 'measureMetadataRequestComplete');

        this.fireEvent('measureMetadataRequestPending');
        this.getMeasureDateStore().load();

        this.fireEvent('measureMetadataRequestPending');
        this.getDimensionSeriesStore().load();
    },

    getTitleCmp : function()
    {
        if (!this.titleCmp)
        {
            this.titleCmp = Ext4.create('Ext.Component', {
                cls: 'y-measure-label',
                html: Ext4.String.htmlEncode('Measure: ' + this.measure.label + ' from ' + (this.measure.queryLabel || this.measure.queryName))
            })
        }

        return this.titleCmp;
    },

    getMeasureDateColCombo : function()
    {
        if (!this.measureDateColCombo)
        {
            this.measureDateColCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: 'Interval End Date',
                width: 360,
                labelWidth: 130,
                margin: '10px 0 0 0',
                store: this.getMeasureDateStore(),
                queryMode: 'local',
                valueField: 'name',
                displayField: 'label',
                forceSelection: true,
                editable: false,
                listeners: {
                    scope: this,
                    change: function(combo, newValue)
                    {
                        this.fireEvent('requiresDataRefresh');
                    }
                }
            });
        }

        return this.measureDateColCombo;
    },

    getMeasureDateStore : function()
    {
        if (!this.measureDateStore)
        {
            this.measureDateStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.MeasureModel',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('visualization', 'getMeasures', LABKEY.ActionURL.getContainer(), {
                        filters: [LABKEY.Query.Visualization.Filter.create({schemaName: this.measure.schemaName, queryName: this.measure.queryName})],
                        dateMeasures: true
                    }),
                    reader: {
                        type: 'json',
                        root: 'measures',
                        idProperty:'id'
                    }
                },
                sorters: {property: 'label', direction: 'ASC'},
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        // since the ParticipantVisit/VisitDate will almost always be the date the users
                        // wants for multiple measures, make sure that it is added to the store
                        var visitDateStr = LABKEY.vis.TimeChartHelper.getStudySubjectInfo().nounSingular + "Visit/VisitDate";
                        if (store.find('name', visitDateStr) == -1)
                        {
                            store.add(LABKEY.vis.MeasureModel.create({
                                schemaName: this.measure.schemaName,
                                queryName: this.measure.queryName,
                                name: visitDateStr,
                                label: "Visit Date",
                                type: "TIMESTAMP"
                            }));
                        }

                        // if this is a saved report, we will have a measure date to select
                        // otherwise, try a few hard-coded options
                        var dateCol = Ext4.isObject(this.selection.dateOptions) ? this.selection.dateOptions.dateCol : null,
                            index = 0;

                        // backwards compatibility support for mapping ParticipantVisit/VisitDate to study subject based dateCol name
                        if (Ext4.isObject(dateCol) && dateCol.name == 'ParticipantVisit/VisitDate')
                            dateCol.name = visitDateStr;

                        if (Ext4.isObject(dateCol) && Ext4.isString(dateCol.name))
                            index = this.getStoreIndexByName(this.getMeasureDateStore(), dateCol.name, dateCol.queryName);
                        else if (store.find('name', visitDateStr) > -1)
                            index = store.find('name', visitDateStr);

                        if(store.getAt(index))
                            this.setMeasureDateCol(store.getAt(index).data);

                        this.fireEvent('measureMetadataRequestComplete');
                    }
                }
            });
        }

        return this.measureDateStore;
    },

    setMeasureDateCol: function(value)
    {
        if (this.getMeasureDateStore().getCount() > 0)
        {
            var index = this.getStoreIndexByName(this.getMeasureDateStore(), value.name, value.queryName);
            if (index > -1)
                this.getMeasureDateColCombo().setValue(this.getMeasureDateStore().getAt(index).get('name'));
        }
    },

    getMeasureDateColData: function(){
        var value = this.getMeasureDateColCombo().getValue();
        if (value)
        {
            var record = this.getMeasureDateColCombo().findRecordByValue(value);
            if (record)
            {
                var data = Ext4.clone(record.data);
                if (!Ext4.isDefined(data.schemaName))
                    data.schemaName = this.measure.schemaName;
                if (!Ext4.isDefined(data.queryName))
                    data.schemaName = this.measure.queryName;
                return data;
            }
        }

        return null;
    },

    getDimensionSeriesCombo : function()
    {
        if (!this.dimensionSeriesCombo)
        {
            this.dimensionSeriesCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: 'Divide Data Into Series By',
                width: 360,
                labelWidth: 200,
                margin: '10px 0 0 0',
                store: this.getDimensionSeriesStore(),
                queryMode: 'local',
                valueField: 'name',
                displayField: 'label',
                forceSelection: true,
                editable: false,
                listeners: {
                    scope: this,
                    change: function(combo, newValue)
                    {
                        var isNone = newValue == '[None]';
                        this.getDimensionAggregateCombo().setDisabled(isNone);
                        if (isNone)
                            this.getDimensionAggregateCombo().setValue(this.defaultDimensionAggregate);

                        this.fireEvent('requiresDataRefresh');
                    }
                }
            });
        }

        return this.dimensionSeriesCombo;
    },

    getDimensionSeriesStore : function()
    {
        if (!this.dimensionSeriesStore)
        {
            this.dimensionSeriesStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.DimensionModel',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, this.measure),
                    reader: {
                        type: 'json',
                        root: 'dimensions',
                        idProperty:'id'
                    }
                },
                sorters: {sorterFn: function(objA, objB) {
                    var a = objA.get('label'), b = objB.get('label');
                    if (a == b) return 0;
                    else if (a == '[None]') return -1;
                    else if (b == '[None]') return 1;
                    return a.localeCompare(b);
                }},
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        // loop through the records to remove Subject as a dimension option
                        var subjectColName = LABKEY.vis.TimeChartHelper.getStudySubjectInfo().columnName;
                        Ext4.each(store.getRange(), function(record) {
                            if (record.get('name') == subjectColName || record.get('name') == subjectColName + '/' + subjectColName)
                            {
                                store.remove(record);
                                return false; // break;
                            }
                        }, this);

                        // Issue 23557: use dimension displayColumn for dataset columns that have a lookup
                        Ext4.each(store.getRange(), function(record) {
                            if (record.raw.lookup && record.raw.lookup.displayColumn) {
                                var displayColumn = record.raw.lookup.displayColumn;
                                record.set('name', record.get('name') + '/' + displayColumn);
                            }
                        }, this);

                        // if we don't have any dimension options, hide the form fields
                        if (store.getCount() == 0)
                        {
                            this.getDimensionSeriesCombo().hide();
                            this.getDimensionAggregateCombo().hide();
                        }

                        // add a 'None' option selected by default
                        store.insert(0, LABKEY.vis.DimensionModel.create({
                            name: '[None]', label: '[None]'
                        }));

                        if (Ext4.isObject(this.selection.dimension) && Ext4.isString(this.selection.dimension.name))
                            this.setDimensionSeries(this.selection.dimension);
                        else
                            this.getDimensionSeriesCombo().setValue('[None]');

                        this.fireEvent('measureMetadataRequestComplete');
                    }
                }
            });
        }

        return this.dimensionSeriesStore;
    },

    setDimensionSeries: function(value)
    {
        if (this.getDimensionSeriesStore().getCount() > 0)
        {
            var index = this.getStoreIndexByName(this.getDimensionSeriesStore(), value.name, value.queryName);
            if (index > -1)
                this.getDimensionSeriesCombo().setValue(this.getDimensionSeriesStore().getAt(index).get('name'));
        }
    },

    getDimensionSeriesData: function(){
        var value = this.getDimensionSeriesCombo().getValue();
        if (Ext4.isDefined(value))
        {
            if (value != '[None]')
            {
                var record = this.getDimensionSeriesCombo().findRecordByValue(value);
                if (record)
                    return Ext4.clone(record.data);
            }
            else
            {
                return {};
            }
        }

        return this.selection.dimension || {};
    },

    getDimensionAggregateCombo : function()
    {
        if (!this.dimensionAggregateComboBox)
        {
            // get the list of aggregate options from LABKEY.Query.Visualization.Aggregate
            var aggregates = [];
            Ext4.Object.each(LABKEY.Query.Visualization.Aggregate, function(key, value){
                aggregates.push([key]);
            }, this);

            this.dimensionAggregateComboBox = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: 'Display Duplicate Values As',
                width: 360,
                labelWidth: 200,
                margin: '10px 0 0 0',
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['name'],
                    data: aggregates,
                    sorters: {property: 'name', direction: 'ASC'}
                }),
                triggerAction: 'all',
                queryMode: 'local',
                valueField: 'name',
                displayField: 'name',
                editable: false,
                value: this.measure.aggregate || this.defaultDimensionAggregate,
                listeners: {
                    scope: this,
                    change: function(combo, newValue)
                    {
                        this.fireEvent('requiresDataRefresh');
                    }
                }
            });
        }

        return this.dimensionAggregateComboBox;
    },

    getStoreIndexByName : function(store, colName, queryName)
    {
        return store.findBy(function(record, id){
            return record.get('name') == colName && record.get('queryName') == queryName;
        }, this);
    },

    getMeasureAlias : function()
    {
        return LABKEY.vis.TimeChartHelper.getMeasureAlias(this.measure);
    },

    getValues : function()
    {
        return {
            dateCol: this.getMeasureDateColData(),
            dimension: this.getDimensionSeriesData(),
            dimensionAggregate: this.getDimensionAggregateCombo().getValue()
        };
    }
});